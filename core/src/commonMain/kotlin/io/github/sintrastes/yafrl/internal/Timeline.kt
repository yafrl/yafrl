package io.github.sintrastes.yafrl.internal

import io.github.sintrastes.yafrl.Event
import io.github.sintrastes.yafrl.State
import io.github.sintrastes.yafrl.EventState
import io.github.sintrastes.yafrl.annotations.FragileYafrlAPI
import io.github.sintrastes.yafrl.broadcastEvent
import io.github.sintrastes.yafrl.internalBindingState
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.measureTime

/**
 * Simple implementation of a push-pull FRP system using a graph
 *  of nodes.
 *
 * Changes are propagated by marking child nodes as dirty when a parent value
 *  changes.
 *
 * Dirty values are recomputed lazily upon request.
 **/
class Timeline(
    val scope: CoroutineScope,
    private val timeTravelEnabled: Boolean,
    private val debugLogging: Boolean,
    private val lazy: Boolean,
    initClock: (State<Boolean>) -> Event<Duration>
) : SynchronizedObject() {
    // Needs to be internal because we can't undo a "pause" event.
    @OptIn(FragileYafrlAPI::class)
    val pausedState by lazy { internalBindingState(false, "paused_state") }

    // The clock is lazily initialized so that if an explicit clock is not used,
    //  it will not start ticking.
    val clock: Event<Duration> by lazy {
        initClock(pausedState)
        // Theoretically gate should work for this, but not
        // currently working.
        //.gate(pausedState)
    }

    private var latestID = -1

    private fun newID(): NodeID {
        latestID++

        return NodeID(latestID)
    }

    /////////// Basically we are building up a doubly-linked DAG here: /////////////

    var latestFrame: Long = -1
    var currentFrame: Long = -1

    // Indexed by frame number.
    val previousStates: MutableMap<Long, GraphState> = mutableMapOf()

    data class GraphState(
        val nodeValues: PersistentMap<NodeID, Any?>,
        val children: PersistentMap<NodeID, PersistentList<NodeID>>
    )

    fun persistState() {
        if (timeTravelEnabled) {
            previousStates[latestFrame] = GraphState(
                nodes
                    .mapValues { it.value._rawValue }
                    .toPersistentMap(),
                children
            )
        }
    }

    fun resetState(frame: Long) = synchronized(this) {
        val time = measureTime {
            if (debugLogging) println("Resetting to frame ${frame}, event was: ${eventTrace.getOrNull(frame.toInt())}")
            val nodeValues = previousStates[frame]
                ?.nodeValues ?: return@synchronized

            nodes.values.forEach { node ->
                val resetValue = nodeValues[node.id]

                if (resetValue != null) {
                    updateNodeValue(node, resetValue)
                }
            }

            children = previousStates[frame]!!.children
            latestFrame = frame
        }

        if (debugLogging) println("Reset state in ${time}")
    }

    fun rollbackState() {
        resetState(latestFrame - 1)
    }

    fun nextState() {
        resetState(latestFrame + 1)
    }

    private var nodes: PersistentMap<NodeID, Node<Any?>> = persistentMapOf()

    // Map from a node ID to it's set of child nodes
    private var children: PersistentMap<NodeID, PersistentList<NodeID>> = persistentMapOf()

    ///////////////////////////////////////////////////////////////////////////////

    data class ExternalEvent(
        val id: NodeID,
        val value: Any?
    )

    /**
     * Log of all external events that have been emitted into the timeline.
     *
     * Only works if [timeTravelEnabled]
     **/
    internal val eventTrace = mutableListOf<ExternalEvent>()

    val onNextFrameListeners = mutableListOf<() -> Unit>()

    /** Keep track of any external (or "input") nodes to the system. */
    internal val externalNodes = mutableMapOf<NodeID, Node<Any?>>()

    internal fun <A> createNode(
        value: Lazy<A>,
        onUpdate: (Node<A>) -> A = { it -> it.rawValue },
        onNextFrame: ((Node<A>) -> Unit)? = null,
        label: String? = null,
    ): Node<A> = synchronized(this) {
        //frameNumber++

        val id = newID()

        var newNode: Node<A>? = null
        newNode = Node(
            value,
            id,
            { onUpdate(newNode!!) },
            onNextFrame,
            label ?: id.toString()
        )

        nodes.put(id, newNode)

        persistState()

        return newNode
    }

    internal fun <A, B> createMappedNode(
        parent: Node<A>,
        f: (A) -> B,
        initialValue: Lazy<B> = lazy { f(fetchNodeValue(parent) as A) },
        onNextFrame: ((Node<B>) -> Unit)? = null
    ): Node<B> = synchronized(this) {
        //frameNumber++

        val mapNodeID = newID()

        var mappedNode: Node<B>? = null
        mappedNode = Node(
            initialValue = initialValue,
            id = mapNodeID,
            recompute = {
                val parentValue = fetchNodeValue(parent) as A

                val result = f(parentValue)

                result
            },
            onNextFrame = onNextFrame
        )

        nodes = nodes.put(mapNodeID, mappedNode)

        val mapChildren = children[parent.id]

        children = if (mapChildren != null) {
            children.put(parent.id, mapChildren.add(mapNodeID))
        } else {
            children.put(parent.id, persistentListOf(mapNodeID))
        }

        persistState()

        return mappedNode
    }

    internal fun <A, B> createFoldNode(
        initialValue: A,
        eventNode: Node<EventState<B>>,
        reducer: (A, B) -> A
    ): Node<A> = synchronized(this) {
        //frameNumber++

        val foldNodeID = newID()

        var currentValue = initialValue

        val foldNode = Node(
            lazy { initialValue },
            foldNodeID,
            {
                val event = fetchNodeValue(eventNode) as EventState<B>
                if (event is EventState.Fired) {
                    currentValue = reducer(currentValue, event.event)
                }

                currentValue
            }
        )

        nodes = nodes.put(foldNodeID, foldNode)

        val eventChildren = children[eventNode.id]

        children = if (eventChildren != null) {
            children.put(eventNode.id, eventChildren.add(foldNodeID))
        } else {
            children.put(eventNode.id, persistentListOf(foldNodeID))
        }

        persistState()

        return foldNode
    }

    internal fun <A> createCombinedNode(
        parentNodes: List<Node<Any?>>,
        combine: (List<Any?>) -> A,
        onNextFrame: ((Node<A>) -> Unit)? = null
    ): Node<A> = synchronized(this) {
        //frameNumber++

        val combinedNodeID = newID()

        val initialValue = lazy { combine(parentNodes.map { it.rawValue }) }

        val combinedNode = Node(
            initialValue,
            combinedNodeID,
            {
                combine(parentNodes.map { fetchNodeValue(it) as A })
            },
            onNextFrame
        )

        nodes = nodes.put(combinedNodeID, combinedNode)

        for (parentNode in parentNodes) {
            val nodeChildren = children[parentNode.id]

            children = if (nodeChildren != null) {
                children.put(parentNode.id, nodeChildren.add(combinedNodeID))
            } else {
                children.put(parentNode.id, persistentListOf(combinedNodeID))
            }
        }

        persistState()

        return combinedNode
    }

    // Note: This is the entrypoint for a new "frame" in the timeline.
    internal fun updateNodeValue(
        node: Node<Any?>,
        newValue: Any?
    ) = synchronized(this) {
        for (listener in onNextFrameListeners) {
            listener()
        }

        onNextFrameListeners.clear()

        node.rawValue = newValue

        if (timeTravelEnabled && externalNodes.contains(node.id)) {
            latestFrame++
            currentFrame++

            eventTrace += ExternalEvent(node.id, node.rawValue)

            println("${latestFrame}: Updating node ${node.label} to $newValue")
        }

        for (listener in node.syncOnValueChangedListeners) {
            listener.invoke(newValue)
        }

        scope.launch {
            for (listener in node.onValueChangedListeners) {
                listener.emit(newValue)
            }
        }

        updateChildNodes(node)

        if (node.onNextFrame != null) {
            onNextFrameListeners.add { node.onNextFrame!!.invoke(node) }
        }

        persistState()
    }

    private fun updateChildNodes(
        node: Node<Any?>
    ) {
        val childNodes = children[node.id] ?: listOf()

        for (childID in childNodes) {
            val child = nodes[childID]!!

            if (debugLogging) println("Updating child node of ${node.label}")

            if (lazy && child.onValueChangedListeners.size == 0 &&
                child.syncOnValueChangedListeners.size == 0) {
                if (debugLogging) println("Marking child ${child.label} dirty")
                // If not listening, we can mark the node dirty
                child.dirty = true
            } else {
                // Otherwise, we are forced to calculate the node's value
                val newValue = child.recompute()

                if(debugLogging) println("Recomputing child ${child.label} := $newValue")

                child.rawValue = newValue

                // As well as invoking any listeners on the child.
                for (listener in child.syncOnValueChangedListeners) {
                    listener.invoke(child.rawValue)
                }

                scope.launch {
                    for (listener in child.onValueChangedListeners) {
                        listener.emit(child.rawValue)
                    }
                }
            }

            updateChildNodes(child)
        }
    }

    internal fun fetchNodeValue(
        node: Node<Any?>
    ): Any? = synchronized(this) {
        if (!node.dirty) {
            return node.rawValue
        } else {
            node.rawValue = node.recompute()
            node.dirty = false
        }

        return node.rawValue
    }

    companion object {
        private var _timeline: Timeline? = null

        fun initializeTimeline(
            scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
            timeTravel: Boolean = false,
            debug: Boolean = false,
            lazy: Boolean = true,
            // Use a trivial (discrete) clock by default.
            initClock: (State<Boolean>) -> Event<Duration> = {
                broadcastEvent<Duration>("clock")
            }
        ) {
            _timeline = Timeline(scope, timeTravel, debug, lazy, initClock)
        }

        fun currentTimeline(): Timeline {
            return _timeline
                ?: error("Timeline must be initialized with Timeline.initializeTimeline().")
        }
    }
}

fun <A> Node<A>.current(): A {
    val graph = Timeline.currentTimeline()

    return graph.fetchNodeValue(this) as A
}