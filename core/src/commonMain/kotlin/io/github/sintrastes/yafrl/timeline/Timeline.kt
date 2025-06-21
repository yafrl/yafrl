package io.github.sintrastes.yafrl.timeline

import io.github.sintrastes.yafrl.Event
import io.github.sintrastes.yafrl.Signal
import io.github.sintrastes.yafrl.EventState
import io.github.sintrastes.yafrl.SampleScope
import io.github.sintrastes.yafrl.annotations.FragileYafrlAPI
import io.github.sintrastes.yafrl.behaviors.Behavior
import io.github.sintrastes.yafrl.externalEvent
import io.github.sintrastes.yafrl.internalBindingState
import io.github.sintrastes.yafrl.sample
import io.github.sintrastes.yafrl.timeline.graph.Graph
import io.github.sintrastes.yafrl.timeline.graph.MutableGraph
import io.github.sintrastes.yafrl.timeline.graph.PersistentGraph
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
import kotlin.reflect.KType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
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
    private val eventLogger: EventLogger,
    private val graph: Graph<NodeID, Node<*>>,
    private val lazy: Boolean,
    initClock: (Signal<Boolean>) -> Event<Duration>
) : SynchronizedObject(), EventLogger by eventLogger {
    @OptIn(FragileYafrlAPI::class)
    val time: Duration
        get() = fetchNodeValue(timeBehavior.node) as Duration

    val timeBehavior by lazy {
        clock.scan(0.0.seconds) { x, y ->
            x + y
        }
    }

    // Needs to be internal because we can't undo a "pause" event.
    @OptIn(FragileYafrlAPI::class)
    val pausedState by lazy {
        internalBindingState(
            lazy { false },
            "__internal_paused"
        )
    }

    // The clock is lazily initialized so that if an explicit clock is not used,
    //  it will not start ticking.
    val clock: Event<Duration> by lazy {
        initClock(pausedState)
        // Theoretically gate should work for this, but not
        // currently working.
        //.gate(pausedState)
    }

    private var latestNodeID = -1

    private fun newID(): NodeID {
        latestNodeID++

        return NodeID(latestNodeID)
    }

    private var latestBehaviorID = -1

    @FragileYafrlAPI
    fun newBehaviorID(): BehaviorID {
        latestBehaviorID++

        return BehaviorID(latestBehaviorID)
    }

    /////////// Basically we are building up a doubly-linked DAG here: /////////////

    var latestFrame: Long = -1
    var currentFrame: Long = -1

    // Indexed by frame number.
    val previousStates: MutableMap<Long, GraphState> = mutableMapOf()

    data class GraphState(
        val nodeValues: Map<NodeID, Any?>,
        val children: Map<NodeID, Collection<NodeID>>
    )

    @OptIn(FragileYafrlAPI::class)
    fun persistState() {
        if (timeTravelEnabled) {
            val nodes = graph.getCurrentNodeMap()
            val children = graph.getCurrentChildMap()

            if (debugLogging) println("Persisting state in frame ${latestFrame}, ${nodes.size} nodes")
            previousStates[latestFrame] = GraphState(
                nodes
                    .mapValues { it.value.rawValue },
                children
            )
        }
    }

    @OptIn(FragileYafrlAPI::class)
    fun resetState(frame: Long) = synchronized(this) {
        val time = measureTime {
            if (debugLogging) println("Resetting to frame ${frame}, event was: ${eventLogger.reportEvents().getOrNull(frame.toInt())}")

            val nodeValues = previousStates[frame]
                ?.nodeValues ?: run {
                    if (debugLogging) println("No previous state found for frame ${frame}")
                    return@synchronized
                }

            val nodes = graph.getCurrentNodes()

            nodes.forEach { node ->
                if (node.label == "__internal_paused") return@forEach

                val resetValue = nodeValues[node.id]

                if (resetValue != null) {
                    updateNodeValue(node, resetValue)
                    node.onRollback?.invoke(node, frame)
                }
            }

            graph.resetChildren(previousStates[frame]!!.children)
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

    ///////////////////////////////////////////////////////////////////////////////

    data class ExternalEvent(
        val id: NodeID,
        val value: Any?
    )

    val onNextFrameListeners = mutableListOf<() -> Unit>()

    data class ExternalNode(
        val type: KType,
        val node: Node<*>
    )

    /**
     * An external behavior is a non-deterministic [Behavior.sampled] behavior
     * that we need to track in the timeline for reproducibility.
     **/
    data class ExternalBehavior(
        val type: KType,
        val behavior: Behavior.Sampled<*>
    )

    /** Keep track of any external (or "input") nodes to the system. */
    @FragileYafrlAPI
    val externalNodes = mutableMapOf<NodeID, ExternalNode>()

    @FragileYafrlAPI
    fun <A> createNode(
        value: Lazy<A>,
        onUpdate: (Node<A>) -> A = { it -> it.rawValue },
        onNextFrame: ((Node<A>) -> Unit)? = null,
        onRollback: ((node: Node<A>, frame: Long) -> Unit)? = null,
        label: String? = null,
    ): Node<A> = synchronized(this) {
        val id = newID()

        var newNode: Node<A>? = null
        newNode = Node(
            value,
            id,
            { onUpdate(newNode!!) },
            onNextFrame,
            onRollback,
            label ?: id.toString()
        )

        graph.addNode(newNode)

        // This persist state causes a stack overflow in drawing test with time travel enabled
        //persistState()

        return newNode
    }

    @OptIn(FragileYafrlAPI::class)
    internal fun <A, B> createMappedNode(
        parent: Node<A>,
        f: SampleScope.(A) -> B,
        initialValue: SampleScope.() -> B = {
            f(fetchNodeValue(parent) as A)
        },
        onNextFrame: ((Node<B>) -> Unit)? = null
    ): Node<B> = synchronized(this) {
        val mapNodeID = newID()

        val initialValue = lazy {
            trackedSample(mapNodeID) {
                initialValue()
            }
        }

        var mappedNode: Node<B>? = null
        mappedNode = Node(
            initialValue = initialValue,
            id = mapNodeID,
            recompute = {
                // On recompute we don't need to track the sample.
                sample {
                    val parentValue = fetchNodeValue(parent) as A

                    val result = f(parentValue)

                    result
                }
            },
            onNextFrame = onNextFrame
        )

        graph.addNode(mappedNode)

        graph.addChild(parent.id, mapNodeID)

        persistState()

        return mappedNode
    }

    @OptIn(FragileYafrlAPI::class)
    fun <A, B> createFoldNode(
        initialValue: A,
        eventNode: Node<EventState<B>>,
        reducer: (A, B) -> A
    ): Node<A> = synchronized(this) {
        val foldNodeID = newID()

        val createdFrame = currentFrame

        var events = listOf<B>()

        var currentValue = initialValue

        val foldNode = Node(
            initialValue = lazy { initialValue },
            id = foldNodeID,
            recompute = {
                val event = fetchNodeValue(eventNode) as EventState<B>
                if (event is EventState.Fired) {
                    currentValue = reducer(currentValue, event.event)

                    if (timeTravelEnabled) {
                        events += event.event
                    }
                }

                currentValue
            },
            onRollback = { node, frame ->
                val resetTo = frame - createdFrame

                events = events.take(resetTo.toInt())

                currentValue = events.fold(initialValue, reducer)

                node.rawValue = currentValue
            }
        )

        graph.addNode(foldNode)

        graph.addChild(eventNode.id, foldNodeID)

        persistState()

        return foldNode
    }

    @FragileYafrlAPI
    fun <A> createCombinedNode(
        parentNodes: List<Node<Any?>>,
        combine: (List<Any?>) -> A,
        onNextFrame: ((Node<A>) -> Unit)? = null
    ): Node<A> = synchronized(this) {
        val combinedNodeID = newID()

        val initialValue = lazy { combine(parentNodes.map { it.rawValue }) }

        val combinedNode = Node(
            initialValue = initialValue,
            id = combinedNodeID,
            recompute = {
                combine(parentNodes.map { fetchNodeValue(it) as A })
            },
            onNextFrame = onNextFrame
        )

        graph.addNode(combinedNode)

        for (parentNode in parentNodes) {
            graph.addChild(parentNode.id, combinedNode.id)
        }

        // This persist state causes a stack overflow in drawing test with time travel enabled
        // persistState()

        return combinedNode
    }

    // Note: This is the entrypoint for a new "frame" in the timeline.
    @FragileYafrlAPI
    fun updateNodeValue(
        node: Node<Any?>,
        newValue: Any?,
        internal: Boolean = false
    ) = synchronized(this) {
        if (debugLogging && !internal) println("Updating node ${node.label} to ${newValue}")

        if (!internal) {
            for (listener in onNextFrameListeners) {
                if (debugLogging) println("Invoking on next frame listener for ${node.label}")
                listener()
            }

            onNextFrameListeners.clear()
        }

        node.rawValue = newValue

        if (!internal && externalNodes.contains(node.id)) {
            latestFrame++
            currentFrame++

            eventLogger.logEvent(ExternalEvent(node.id, node.rawValue))

            if (debugLogging) println("${latestFrame}: Updating node ${node.label} to $newValue")
        }

        for (listener in node.syncOnValueChangedListeners) {
            listener.invoke(newValue)
        }

        if (node.onValueChangedListeners.isNotEmpty()) {
            scope.launch {
                for (listener in node.onValueChangedListeners) {
                    listener.emit(newValue)
                }
            }
        }

        if (!internal && node.onNextFrame != null) {
            if (debugLogging) println("Adding on next frame listener for ${node.label}")
            onNextFrameListeners.add { node.onNextFrame!!.invoke(node) }
        }

        updateChildNodes(node)

        persistState()
    }

    @OptIn(FragileYafrlAPI::class)
    private fun updateChildNodes(
        node: Node<Any?>
    ) {
        val childNodes = graph.getChildrenOf(node.id)

        for (childID in childNodes) {
            val child = graph.getNode(childID)!!

            if (child.onNextFrame != null) {
                if (debugLogging) println("Adding on next frame listener for ${child.label}")
                onNextFrameListeners.add { child.onNextFrame!!.invoke(child) }
            }

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

                if (child.onValueChangedListeners.isNotEmpty()) {
                    scope.launch {
                        for (listener in child.onValueChangedListeners) {
                            listener.emit(child.rawValue)
                        }
                    }
                }
            }

            updateChildNodes(child)
        }
    }

    @OptIn(FragileYafrlAPI::class)
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
            eventLogger: EventLogger = EventLogger.Disabled,
            // Use a trivial (discrete) clock by default.
            initClock: (Signal<Boolean>) -> Event<Duration> = {
                externalEvent<Duration>("clock")
            }
        ): Timeline {
            val graph = if (timeTravel) PersistentGraph() else MutableGraph()

            _timeline = Timeline(scope, timeTravel, debug, eventLogger, graph, lazy, initClock)
            return _timeline!!
        }

        fun currentTimeline(): Timeline {
            return _timeline
                ?: error("Timeline must be initialized with Timeline.initializeTimeline().")
        }
    }

    /**
     * Introduces a new [SampleScope] that is being used to modify a newly created node
     *  so that we can keep track of the dependencies between nodes.
     **/
    @OptIn(FragileYafrlAPI::class)
    internal fun <R> trackedSample(id: NodeID, body: SampleScope.() -> R): R {
        val scope = object: SampleScope {
            override fun <A> Behavior<A>.sampleValue(): A {
                // For a behavior it will be trickier. Maybe need to add a new type
                // of node to represent them?
                TODO("Not yet implemented")
            }

            override fun <A> Signal<A>.currentValue(): A {
                // For a signal we can just add a dependency on the node.
                graph.addChild(node.id, id)

                return node.current()
            }

            override val clock: Event<Duration>
                get() = this@Timeline.clock

            override val timeBehavior: Signal<Duration>
                get() = this@Timeline.timeBehavior
        }

        return scope.body()
    }
}

fun <A> Node<A>.current(): A {
    val graph = Timeline.currentTimeline()

    return graph.fetchNodeValue(this) as A
}