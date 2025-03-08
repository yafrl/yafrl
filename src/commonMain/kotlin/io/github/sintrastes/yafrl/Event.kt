package io.github.sintrastes.yafrl

import io.github.sintrastes.yafrl.internal.Node
import kotlinx.coroutines.flow.Flow
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
class Event<A> internal constructor(
    private val node: Node<EventState<A>>
) : Flow<A>
{
    override suspend fun collect(collector: FlowCollector<A>) {
        node.collect { value ->
            if (value is EventState.Fired) {
                collector.emit(value.event)
            }
        }
    }
}

/**
 * Used to help represent [Event]s as nodes.
 *
 * At each time an event is either [EventState.Fired],
 *  or there is [EventState.None].
 **/
internal sealed interface EventState<out A> {
    data class Fired<A>(val event: A): EventState<A>

    object None : EventState<Any?>
}