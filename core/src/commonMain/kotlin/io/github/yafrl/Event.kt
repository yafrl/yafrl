package io.github.yafrl

import io.github.yafrl.annotations.ExperimentalYafrlAPI
import io.github.yafrl.annotations.FragileYafrlAPI
import io.github.yafrl.behaviors.Behavior
import io.github.yafrl.timeline.HasTimeline
import io.github.yafrl.timeline.Node
import io.github.yafrl.timeline.Timeline
import io.github.yafrl.timeline.debugging.ExternalNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.reflect.typeOf
import kotlin.text.set
import kotlin.time.Duration

/**
 * An event is a value which is defined in instantaneous moments at time.
 *
 * In other words, from a denotational perspective it can be thought
 *  of as being equivalent to a `List<Pair<Time, A>>`.
 *
 * Visually, you can think of a time as a graph, where the x-axis is time,
 *  and the y-axis is the value of the events:
 *
 * ```
 * ^
 * |              *
 * |        *               *
 * |   *                *            *
 * |                            *  *
 * |---------------------------------->
 * ```
 *
 * For convenience, an [Event] is also a [kotlinx.coroutines.flow.Flow], and so it can be [collect]ed on,
 *  but it may have slightly different behavior than most flows you are used to from
 *  kotlinx.coroutines such as [kotlinx.coroutines.flow.MutableSharedFlow].
 *
 * To use an [Event] idiomatically, you should avoid using [collect] unless absolutely
 *  necessary for your application -- and if necessary, [collect] should only be used
 *  at the "edges" of your application.
 *
 * Conceptually, an [Event] can be viewed as a [Signal]
 **/
open class Event<out A> @OptIn(FragileYafrlAPI::class)
internal constructor(
    @property:FragileYafrlAPI val node: Node<EventState<A>>
) {
    @FragileYafrlAPI
    suspend fun collect(collector: FlowCollector<A>) {
        node.collect { value ->
            if (value is EventState.Fired) {
                collector.emit(value.event)
            }
        }
    }

    companion object
}

open class EventScope(override val timeline: Timeline) : HasTimeline {
    /**
     * Applies the passed function [f] to each event that is emitted,
     *  producing a new transformed [Event] stream.
     *
     * Note: [f] should be a pure function.
     **/
    @OptIn(FragileYafrlAPI::class)
    fun <A, B> Event<A>.map(f: SampleScope.(A) -> B): Event<B> {
        return Event(
            timeline.createMappedNode(
                parent = node,
                f = {
                    it.map { f(it) }
                },
                initialValue = { EventState.None },
                onNextFrame = { node ->
                    node.rawValue = EventState.None
                }
            )
        )
    }

    fun <A, B> Event<A>.tag(behavior: Behavior<B>): Event<B> = map { behavior.sampleValue() }

    fun <A, B: Any> Event<A>.mapNotNull(f: (A) -> B?): Event<B> {
        return map { f(it) }.filter { it != null }.map { it!! }
    }

    /**
     * Applies the supplied function to each element of the
     *  [Event], and produces an event that only emits if
     *  the function evaluates to true.
     **/
    @OptIn(FragileYafrlAPI::class)
    fun <A> Event<A>.filter(f: (A) -> Boolean): Event<A> {
        return Event(
            timeline.createMappedNode(
                parent = node,
                f = { event ->
                    if (event is EventState.Fired && f(event.event)) {
                        event
                    } else {
                        EventState.None
                    }
                },
                onNextFrame = { node ->
                    node.rawValue = EventState.None
                }
            )
        )
    }

    /**
     * Returns an [Event] that only fires if the [condition]
     * does not hold at a particular time.
     **/
    @OptIn(FragileYafrlAPI::class)
    fun <A> Event<A>.gate(condition: Behavior<Boolean>): Event<A> {
        return Event(
            timeline.createMappedNode(
                parent = node,
                f = { event ->
                    if (event is EventState.Fired && !condition.sampleValueAt(timeline.time)) {
                        event
                    } else {
                        EventState.None
                    }
                },
                onNextFrame = { node ->
                    node.rawValue = EventState.None
                }
            )
        )
    }

    @OptIn(FragileYafrlAPI::class)
    fun <A> Event<A>.asSignal(): Signal<EventState<A>> {
        return Signal(
            timeline.createMappedNode(
                parent = node,
                f = { event ->
                    event
                },
                onNextFrame = { node ->
                    node.rawValue = EventState.None
                }
            )
        )
    }

    /**
     * Create an event that triggers every [delayTime].
     **/
    fun Event.Companion.tick(delayTime: Duration): Event<Duration> {
        val event = internalBroadcastEvent<Duration>()

        val scope = timeline.scope

        scope.launch {
            while (isActive) {
                delay(delayTime)
                event.send(delayTime)
            }
        }

        return event
    }

    /**
     * Merges [Event]s using the [Leftmost][MergeStrategy.Leftmost] strategy.
     **/
    fun <A> Event.Companion.merged(
        vararg events: Event<A>
    ): Event<A> {
        return mergedWith(
            MergeStrategy.Leftmost(),
            *events
        )
    }

    fun <A> Event.Companion.merged(
        events: List<Event<A>>
    ) = merged(*events.toTypedArray())

    /**
     * Merges [Event]s using the supplied [MergeStrategy] to handle
     *  the case of simultaneous events.
     **/
    @OptIn(FragileYafrlAPI::class)
    fun <A> Event.Companion.mergedWith(
        strategy: MergeStrategy<A>,
        vararg events: Event<A>
    ): Event<A> {
        val parentNodes = events.map {
            it.node
        }

        var node: Node<EventState<A>>? = null
        node = timeline.createCombinedNode(
            parentNodes,
            { values ->
                val selected = strategy.mergeEvents(
                    values
                        .filterIsInstance<EventState.Fired<A>>()
                        .map { it.event }
                )

                if (selected != null) {
                    EventState.Fired(selected)
                } else {
                    EventState.None
                }
            },
            {
                node!!.rawValue = EventState.None
            }
        )

        return Event(node)
    }

    @OptIn(FragileYafrlAPI::class)
    fun <A> Event.Companion.mergeAll(
        vararg events: Event<A>
    ): Event<List<A>> {
        val parentNodes = events.map {
            it.node
        }

        var node: Node<EventState<List<A>>>? = null
        node = timeline.createCombinedNode(
            parentNodes,
            { values ->
                val selected = values
                    .filterIsInstance<EventState.Fired<A>>()
                    .map { it.event }

                if (selected.isNotEmpty()) {
                    EventState.Fired(selected)
                } else {
                    EventState.None
                }
            },
            {
                node!!.rawValue = EventState.None
            }
        )

        return Event(node)
    }

    fun <A, B> on(event: Event<B>, reducer: SampleScope.(A, B) -> A): Event<(A) -> A> {
        return event.map { event ->
            { state: A -> reducer(state, event) }
        }
    }

    /**
     * Blocks occurrence of events until the [window] of time has passed,
     *  after which the latest event will be emitted.
     **/
    @OptIn(FragileYafrlAPI::class)
    fun <A> Event<A>.debounced(window: Duration): Event<A> {
        val debounced = internalBroadcastEvent<A>()

        val scope = timeline.scope

        var lastTime: Instant? = null
        var lastEvent: A? = null

        var job: Job? = null

        val mutex = Mutex()

        node.collectSync { event ->
            if (event is EventState.Fired) {
                job?.cancel()
                job = scope.launch(Dispatchers.Default) {
                    mutex.withLock {
                        val currentTime = Clock.System.now()

                        lastEvent = event.event

                        if (lastTime != null) {
                            val elapsed = currentTime - lastTime!!

                            if (elapsed > window) {
                                debounced.send(lastEvent!!)
                            } else {
                                delay(window - elapsed)
                                debounced.send(lastEvent!!)
                            }
                        }

                        lastTime = currentTime
                    }
                }
            }
        }

        return debounced
    }

    /**
     * Creates a modified [Event] that emits events at a frequency of at most
     *  [duration].
     **/
    @OptIn(FragileYafrlAPI::class)
    fun <A> Event<A>.throttled(duration: Duration): Event<A> {
        val throttled = internalBroadcastEvent<A>()

        val scope = timeline.scope

        var lastTime: Instant? = null
        var latestEvent: A? = null

        var firstEvent = true

        node.collectSync { event ->
            if (event is EventState.Fired) {
                lastTime = Clock.System.now()
                latestEvent = event.event
                if (firstEvent) {
                    throttled.send(latestEvent!!)
                    firstEvent = false
                }
            }
        }

        scope.launch {
            while (isActive) {
                if (latestEvent != null) {
                    if (!firstEvent) {
                        delay(duration)

                        throttled.send(latestEvent!!)
                    }
                }
            }
        }

        return throttled
    }

    /** Creates a [BroadcastEvent] for internal implementation purposes. */
    @OptIn(FragileYafrlAPI::class)
    fun <A> internalBroadcastEvent(
        label: String? = null
    ): BroadcastEvent<A> {
        val initialValue = lazy { EventState.None }

        val node = timeline.createNode(
            value = initialValue,
            onNextFrame = { node ->
                // NOTE: This seems to cause an issue with clock ticks.
                node.rawValue = EventState.None
            },
            label = label
        )

        return BroadcastEvent(timeline, node)
    }

    /**
     * Constructs a new external [BroadcastEvent], which is intended to represent
     *  events external to the current [Timeline]. (See also [externalSignal] for the
     *  [Signal] variant of this).
     *
     * Since this is intended to represent external events, it is an anti-pattern to
     *  call [BroadcastEvent.send] from logical code that manipulates [Signal]s and
     *  [Event]s.
     *
     * For example, the following would be an incorrect use of [externalEvent]:
     *
     * ```kotlin
     * val someEvent: Event<Int> = ...
     *
     * val newEvent = externalEvent<Int>()
     *
     * scope.launch {
     *     someEvent.collect { event ->
     *         if (event % 2 == 0) {
     *             newEvent.send(event * 3)
     *         }
     *     }
     * }
     * ```
     *
     * If using yafrl's FRP state testing utilities, this will confuse the test generator into thinking
     *  that `newEvent` above is an input to the system, and thus can have arbitrary values emitted to it,
     *  when in fact this is not the case, and `newEvent` is entirely dependent on `someEvent`.
     *
     * Further, the above can be written much more simply as:
     *
     * ```kotlin
     * val someEvent: Event<Int> = ...
     *
     * val newEvent = someEvent
     *     .filter { it % 2 == 0 }
     *     .map { it * 3 }
     * ```
     *
     * If you really do need access to a more imperative API, and yafrl does not provide another
     *  way of implementing your desired logic, you should use [internalBroadcastEvent] for any
     *  internal events.
     **/
    @OptIn(FragileYafrlAPI::class)
    inline fun <reified A> externalEvent(
        label: String? = null
    ): BroadcastEvent<A> {
        val event = internalBroadcastEvent<A>(label)

        // If the user creates a broadcast event, assume it is an "external"
        //  input to the system.
        timeline.externalNodes[event.node.id] = ExternalNode(
            typeOf<EventState<A>>(),
            event.node
        )

        return event
    }

    /**
     * [onEvent] acts similarly to [map][Event.map], however, rather than transforming
     *  each value of the input [Event] by a pure synchronous function, [onEvent] will
     *  launch a [perform] coroutine for every frame in which [trigger] has fired.
     *
     * Essentially, [onEvent] lets us perform some action "outside" the regular [Timeline],
     *  and responds with a new [Event] that fires when said actions complete.
     *
     * Unlike [map][Event.map], for [onEvent] the [perform] action is encouraged to be
     *  a side-effecting function (making API calls, fetching data, etc...) if appropriate.
     *
     * Example:
     *
     * ```
     * val buttonClick: Event<Unit> = submitButton.clicks()
     *
     * val submitResponse: Event<DataResponse> = onEvent(buttonClick) {
     *     fetchData()
     * }
     * ```
     **/
    @OptIn(FragileYafrlAPI::class)
    inline fun <A, reified B> onEvent(
        trigger: Event<A>,
        crossinline perform: suspend (A) -> B
    ): Event<B> {
        // Note: For now treat this as an external event, since the result from
        // perform could be nondeterministic.
        val responseEvent = externalEvent<B>()

        val scope = timeline.scope


        trigger.node.collectSync { event ->
            if (event is EventState.Fired<A>) {
                scope.launch {
                    val result = perform(event.event)

                    responseEvent.send(result)
                }
            }
        }


        return responseEvent
    }
}

/**
 * A [BroadcastEvent] is an [Event] that can have new values emitted to it.
 *
 * Usually used as an internal API to build up a new [Event].
 *
 * Created with the [externalEvent] function.
 **/
open class BroadcastEvent<A> @OptIn(FragileYafrlAPI::class) @FragileYafrlAPI constructor(
    private val timeline: Timeline,
    node: Node<EventState<A>>
) : Event<A>(node) {
    @OptIn(FragileYafrlAPI::class)
    fun send(value: A) {
        timeline.updateNodeValue(node, EventState.Fired(value))
    }
}

/**
 * Used to help represent [Event]s as nodes.
 *
 * At each time an event is either [EventState.Fired],
 *  or there is [EventState.None].
 **/
@FragileYafrlAPI
sealed interface EventState<out A> {
    fun <B> map(f: (A) -> B): EventState<B>

    fun isFired(): Boolean

    data class Fired<A>(val event: A) : EventState<A> {
        override fun <B> map(f: (A) -> B): EventState<B> {
            return Fired(f(event))
        }

        override fun isFired(): Boolean = true
    }

    data object None : EventState<Nothing> {
        override fun isFired(): Boolean = false

        override fun <B> map(f: (Nothing) -> B): EventState<B> {
            return None
        }
    }
}