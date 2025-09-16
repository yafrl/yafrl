package io.github.yafrl.timeline

import io.github.yafrl.Event
import io.github.yafrl.Signal
import io.github.yafrl.EventState
import io.github.yafrl.SampleScope
import io.github.yafrl.annotations.FragileYafrlAPI
import io.github.yafrl.behaviors.Behavior
import io.github.yafrl.externalEvent
import io.github.yafrl.internalBindingState
import io.github.yafrl.sample
import io.github.yafrl.timeline.debugging.EventLogger
import io.github.yafrl.timeline.debugging.ExternalBehavior
import io.github.yafrl.timeline.debugging.ExternalEvent
import io.github.yafrl.timeline.debugging.ExternalNode
import io.github.yafrl.timeline.debugging.SnapshotDebugger
import io.github.yafrl.timeline.debugging.TimeTravelDebugger
import io.github.yafrl.timeline.graph.Graph
import io.github.yafrl.timeline.graph.MutableGraph
import io.github.yafrl.timeline.graph.PersistentGraph
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
    internal val debugLogging: Boolean,
    internal val eventLogger: EventLogger,
    @property:FragileYafrlAPI val graph: Graph,
    private val initTimeTravel: (Timeline) -> TimeTravelDebugger,
    private val lazy: Boolean,
    initClock: (Signal<Boolean>) -> Event<Duration>
) : SynchronizedObject(), EventLogger by eventLogger {
    /**
     * Index to keep track of the latest frame that has been created in the timeline.
     **/
    internal var latestFrame: Long = -1
    /**
     * Index to keep track of the current frame of the timeline.
     **/
    internal var currentFrame: Long = -1

    internal var behaviorsSampled = mutableMapOf<BehaviorID, Any?>()

    val timeTravel = initTimeTravel(this)

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

    ///////////////////////////////////////////////////////////////////////////////

    val onNextFrameListeners = mutableListOf<() -> Unit>()

    /**
     * Keep track of any external (or "input") nodes to the system so they can
     *  be used for testing / debugging.
     *
     * See [ExternalNode].
     **/
    @FragileYafrlAPI
    val externalNodes = mutableMapOf<NodeID, ExternalNode>()

    /**
     * Creates a new node without any parent / input nodes.
     *
     * Typically this is used to construct input / "external" nodes
     *  to the timeline graph.
     *
     * @param value Lazily initialized initial value of the node.
     * @param onUpdate How to recompute the node when marked dirty.
     * @param onNextFrame Optional action to perform on the frame immediately after
     *  a node is updated (typically used for events, to reset state to [EventState.None])
     * @param onRollback Action to perform when the state has been reset to a different frame
     *  during time travel (see [TimeTravelDebugger]).
     **/
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

    /**
     * Special case of [createCombinedNode] -- creates a new node depending on
     *  a single other node as it's input / parent.
     *
     * Note: Despite the name, this can be used to create any kind of derived node,
     *  just just a "mapping" operation. For instance, this is used also to implement
     *  [Event.filter].
     **/
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
            trackedSample {
                initialValue()
            }
        }

        var mappedNode: Node<B>? = null
        mappedNode = Node(
            initialValue = initialValue,
            id = mapNodeID,
            recompute = {
                trackedSample {
                    try {
                        val parentValue = fetchNodeValue(parent) as A

                        f(parentValue)
                    } catch (e: ClassCastException) {
                        throw IllegalStateException("Tried to cast ${parent.label} with the wrong type", e)
                    }
                }
            },
            onNextFrame = onNextFrame
        )

        graph.addNode(mappedNode)

        graph.addChild(parent.id, mapNodeID)

        timeTravel.persistState()

        return mappedNode
    }

    /**
     * Creates a node whose value updates by the action specified by
     *  the [reducer].
     *
     * The node will start with [initialValue], and whenever [eventNode] fires,
     *  the value will update according to the [reducer].
     **/
    @OptIn(FragileYafrlAPI::class)
    fun <A, B> createFoldNode(
        initialValue: A,
        eventNode: Node<EventState<B>>,
        reducer: SampleScope.(A, B) -> A
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
                    trackedSample {
                        currentValue = reducer(currentValue, event.event)

                        if (timeTravelEnabled) {
                            events += event.event
                        }
                    }
                }

                currentValue
            },
            onRollback = { node, frame ->
                // TODO: Not sure if this is the right scope.
                sample {
                    val resetTo = frame - createdFrame

                    events = events.take(resetTo.toInt())

                    currentValue = events.fold(initialValue, { x, y -> reducer(x, y) })

                    node.rawValue = currentValue
                }
            }
        )

        graph.addNode(foldNode)

        graph.addChild(eventNode.id, foldNodeID)

        timeTravel.persistState()

        return foldNode
    }

    /**
     * Creates a node that updates whenever any of its [parentNodes] updates.
     *
     * @param parentNodes The list of nodes used as inputs to the combined node.
     * @param combine Function that acts on the value of its parent nodes to produce
     *  the recomputed value of the combined node.
     * @param onNextFrame Optional action to perform after the node's value has been
     *  updated (typically only used for events, to reset to [EventState.None] on the
     *  next frame).
     **/
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

    /**
     * Updates the value of the [node] in the [Timeline], yielding a new frame so long as
     *  this is not an [internal] update.
     *
     * This will either update the values uf any dependent (child) nodes in the graph,
     *  or at the very least mark them dirty so that the values can be updated on-demand.
     *
     * @param node The node being updated.
     * @param newValue The new value for the node
     * @param behaviorsSampled A map of the value of any behaviors that were sampled this frame.
     * @param internal Whether or not this is an "internal" update -- i.e. a node that is being
     *  updated as an internal implementation detail of some operation, which should not introduce
     *  a new frame.
     **/
    @FragileYafrlAPI
    fun updateNodeValue(
        node: Node<Any?>,
        newValue: Any?,
        internal: Boolean = false,
        resetting: Boolean = false
    ) = synchronized(this) {
        if (!resetting && !internal) {
            behaviorsSampled = mutableMapOf()
        }

        if (debugLogging && !internal) println("Updating node ${node.label} to ${newValue}")

        if (!internal) {
            for (listener in onNextFrameListeners) {
                if (debugLogging) println("Invoking on next frame listener for ${node.label}")
                listener()
            }

            onNextFrameListeners.clear()
        }

        node.rawValue = newValue

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

        updateChildNodes(behaviorsSampled, node)

        if (!internal && externalNodes.contains(node.id)) {
            latestFrame++
            currentFrame++

            eventLogger.logEvent(ExternalEvent(behaviorsSampled, node.id, node.rawValue))

            if (debugLogging) println("${latestFrame}: Updating node ${node.label} to $newValue")
        }

        if (!internal && !resetting) timeTravel.persistState()
    }

    /**
     * Propagate the changes to a [node] throughout the entire state graph, either
     *  marking child notes as "dirty" (i.e. need to be re-computed / updated), or
     *  re-calculating the values directly if needed.
     **/
    @OptIn(FragileYafrlAPI::class)
    private fun updateChildNodes(
        behaviorsSampled: MutableMap<BehaviorID, Any?>,
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

            updateChildNodes(behaviorsSampled, child)
        }
    }

    /**
     * Performs an eager "fetch" of a node's value, recomputing the value
     *  if dirty to ensure the value is up-to-date with its parents in the state
     *  graph.
     **/
    @OptIn(FragileYafrlAPI::class)
    internal fun fetchNodeValue(
        node: Node<Any?>
    ): Any? = synchronized(this) {
        if (!node.dirty) {
            if (debugLogging) println("Node ${node} was not dirty, returning ${node.rawValue}")
            return node.rawValue
        } else {
            node.rawValue = node.recompute()
            node.dirty = false

            if (debugLogging) println("Recomputed node ${node.id}: ${node.rawValue}")
        }

        return node.rawValue
    }

    companion object {
        private var _timeline: Timeline? = null

        /**
         * Initialize a new yafrl [Timeline] with the specified configuration options.
         *
         * @param scope The coroutine scope associated with the timeline, to be used to launch
         *  any coroutines needed for asnychronous actions.
         * @param timeTravel Determines whether time travel debugging (see [TimeTravelDebugger]) is
         *  enabled for the timeline.
         * @param debug Determines whether debug logging is enabled for internal timeline operations.
         * @param lazy If true (default behavior), state updates are not immediately propagated through
         *  the graph on changes, but rather updates are computed lazily (on-demand / as needed).
         * @param eventLogger Configures the [EventLogger] used by default in the timeline. Disabled by defautl.
         * @param initClock Determines how the timeline's clock is initialized. By default uses a discrete clock
         *  usable by testing. For animation / graphical applications this should be hooked into the event loop of the graphics
         *  processing pipeline to emit on each frame.
         **/
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

            val initDebugger = { timeline: Timeline ->
                if (timeTravel) SnapshotDebugger(timeline) else TimeTravelDebugger.Disabled
            }

            _timeline = Timeline(
                scope,
                timeTravel,
                debug,
                eventLogger,
                graph,
                initDebugger,
                lazy,
                initClock
            )

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
    internal fun <R> trackedSample(body: SampleScope.() -> R): R {
        val scope = object: SampleScope {
            override fun <A> Behavior<A>.sampleValue(): A {
                if (this is Behavior.Sampled<A>) {
                    if (behaviorsSampled.contains(this.id)) {
                        return behaviorsSampled[id] as A
                    }

                    val value = sampleValueAt(time)

                    behaviorsSampled[id] = value

                    return value
                } else {
                    return sampleValueAt(time)
                }
            }

            override fun <A> Signal<A>.currentValue(): A {
                // For a signal we can just add a dependency on the node.
                // graph.addChild(node.id, id)

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

/**
 * Utility to get the current value of a node.
 *
 * Should only be used internally. For high-level application purposes
 *  please use [SampleScope.currentValue] or [SampleScope.sampleValue]
 *  instead.
 **/
@FragileYafrlAPI
fun <A> Node<A>.current(): A {
    val graph = Timeline.currentTimeline()

    return graph.fetchNodeValue(this) as A
}