package io.github.sintrastes.yafrl

import io.github.sintrastes.yafrl.internal.Node
import io.github.sintrastes.yafrl.internal.nodeGraph
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.FlowCollector

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
 * For convenience, an [Event] is also a [Flow], and so it can be [collect]ed on,
 *  but it may have slightly different behavior than most flows you are used to from
 *  kotlinx.coroutines such as [MutableSharedFlow].
 *
 * To use an [Event] idiomatically, you should avoid using [collect] unless absolutely
 *  necessary for your application -- and if necessary, [collect] should only be used
 *  at the "edges" of your application.
 **/
open class Event<A> internal constructor(
    internal val node: Node<EventState<A>>
) : Flow<A> {
    override suspend fun collect(collector: FlowCollector<A>) {
        node.collect { value ->
            if (value is EventState.Fired) {
                collector.emit(value.event)
            }
        }
    }

    /**
     * Applies the passed function [f] to each event that is emitted,
     *  producing a new transformed [Event] stream.
     *
     * Note: [f] should be a pure function.
     **/
    suspend fun <B> map(f: (A) -> B): Event<B> {
        val graph = currentCoroutineContext().nodeGraph!!

        return Event(
            graph.createMappedNode(
                parent = node,
                f = { it.map(f) },
                onNextFrame = { node ->
                    node.rawValue = EventState.None
                }
            )
        )
    }

    /**
     * Applies the supplied function to each element of the
     *  [Event], and produces an event that only emits if
     *  the function evaluates to true.
     **/
    suspend fun filter(f: (A) -> Boolean): Event<A> {
        val graph = currentCoroutineContext().nodeGraph!!

        return Event(
            graph.createMappedNode(
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

    companion object {
        /**
         * Merges [Event]s using the [Leftmost][MergeStrategy.Leftmost] strategy.
         **/
        suspend fun <A> merged(
            vararg events: Event<A>
        ): Event<A> {
            return mergedWith(
                MergeStrategy.Leftmost(),
                *events
            )
        }

        /**
         * Merges [Event]s using the supplied [MergeStrategy] to handle
         *  the case of simultaneous events.
         **/
        suspend fun <A> mergedWith(
            strategy: MergeStrategy<A>,
            vararg events: Event<A>
        ): Event<A> {
            val graph = currentCoroutineContext().nodeGraph!!

            val parentNodes = events.map {
                it.node
            }

            var node: Node<EventState<A>>? = null
            node = graph.createCombinedNode(
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
    }
}

/**
 * A [BroadcastEvent] is an [Event] that can have new values emitted to it.
 *
 * Usually used as an internal API to build up a new [Event].
 *
 * Created with the [broadcastEvent] function.
 **/
open class BroadcastEvent<A> internal constructor(
    node: Node<EventState<A>>
): Event<A>(node) {
    suspend fun emit(value: A) {
        val graph = currentCoroutineContext().nodeGraph!!

        graph.updateNodeValue(node, EventState.Fired(value))
    }
}

/**
 * Constructs a new [BroadcastEvent].
 **/
suspend fun <A> broadcastEvent(): BroadcastEvent<A> {
    val graph = currentCoroutineContext().nodeGraph!!

    val initialValue = lazy { EventState.None }

    val node = graph.createNode(
        value = initialValue,
        onUpdate = { node ->
            node.rawValue
        },
        onNextFrame = { node ->
            node.rawValue = EventState.None
        }
    )

    return BroadcastEvent(node)
}

/**
 * Used to help represent [Event]s as nodes.
 *
 * At each time an event is either [EventState.Fired],
 *  or there is [EventState.None].
 **/
internal sealed interface EventState<out A> {
    fun <B> map(f: (A) -> B): EventState<B>

    data class Fired<A>(val event: A): EventState<A> {
        override fun <B> map(f: (A) -> B): EventState<B> {
            return Fired(f(event))
        }
    }

    data object None : EventState<Nothing> {
        override fun <B> map(f: (Nothing) -> B): EventState<B> {
            return None
        }
    }
}