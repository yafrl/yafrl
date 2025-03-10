package io.github.sintrastes.yafrl

import io.github.sintrastes.yafrl.internal.Node
import io.github.sintrastes.yafrl.internal.Timeline
import io.github.sintrastes.yafrl.internal.current
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow

/**
 * A flow can be thought of as a combination of a [Behavior] and an
 *  [Event].
 *
 * Like a [Behavior], a [State] has a [current] value which can be
 *  sampled at any time.
 *
 * Like an [Event], a [State] will automatically influence derived [State]s
 *  when the underlying state changes -- in other words, it is _reactive_.
 *
 * Following our graphical analogies for [Event] and [Behavior], a [State]
 *  can be thought of as a stepwise function.
 *
 * ```
 * ^
 * |                       ********
 * |          ****
 * |  ********                     ********
 * |              *********
 * ---------------------------------------->
 * ```
 *
 * [State]s are incredibly useful for representing the state of
 *  various components in an application which need to be consumed
 *  in responsive user interfaces.
 *
 * They are very similar to [StateFlow]s in this sense -- only better.
 **/
open class State<A> internal constructor(
    internal val node: Node<A>
): Flow<A>, Behavior<A> {
    override suspend fun collect(collector: FlowCollector<A>) {
        node.collect(collector)
    }

    /**
     * Return the current value of the state.
     **/
    override fun current(): A {
        return node.current()
    }

    /**
     * Applies the passed function [f] to each state,
     *  producing a new transformed [State] value.
     *
     * Note: [f] should be a pure function.
     **/
    fun <B> map(f: (A) -> B): State<B> {
        val graph = Timeline.currentTimeline()

        return State(graph.createMappedNode(node, f))
    }

    /**
     * Combine two states together into a single [State] by applying a function
     *  to the two input states.
     *
     * Example:
     *
     * ```
     * val countA: State<Int> = ...
     * val countB: State<Int> = ...
     *
     * val sum = countA.combineWith(countB) { x, y -> x + y }
     * ```
     **/
    fun <B, C> combineWith(other: State<B>, op: (A, B) -> C): State<C> {
        TODO()
    }

    fun <B, C, D> combineWith(other: State<B>, op: (A, B, C) -> D): State<D> {
        TODO()
    }

    fun <B, C, D, E> combineWith(other: State<B>, op: (A, B, C, D) -> E): State<E> {
        TODO()
    }

    fun <B, C, D, E, F> combineWith(other: State<B>, op: (A, B, C, D, E) -> F): State<F> {
        TODO()
    }

    companion object {
        /**
         * Construct a [State] by suppling an [initial] value, a set of [events]
         *  driving the updates of the [State], together with a [reducer] describing
         *  how new events update the existing state.
         *
         *  Example:
         *
         * ```
         * enum class CounterEvent {
         *     Increment,
         *     Decrement;
         * }
         *
         * val events: Event<CounterEvent> = ...
         *
         * val counter: State<Int> = State.fold(0, incrementEvents) { state, event ->
         *     when (event) {
         *         is CounterEvent.Increment -> state + 1
         *         is CounterEvent.Decrement -> state - 1
         *     }
         * }
         * ```
         **/
        fun <A, B> fold(initial: A, events: Event<B>, reducer: (A, B) -> A): State<A> {
            val graph = Timeline.currentTimeline()

            return State(
                graph.createFoldNode(initial, events.node, reducer)
            )
        }
    }
}

/**
 * Variant of [State] that can be [setTo] a new value.
 *
 * Constructed with the [mutableStateOf] function.
 **/
class MutableState<A> internal constructor(
    node: Node<A>
): State<A>(node) {
    var value: A
        get() = current()
        set(value) {
            val graph = Timeline.currentTimeline()

            return graph.updateNodeValue(node, value)
        }
}

fun <A> mutableStateOf(value: A): MutableState<A> {
    val graph = Timeline.currentTimeline()

    return MutableState(graph.createNode(lazy { value }))
}