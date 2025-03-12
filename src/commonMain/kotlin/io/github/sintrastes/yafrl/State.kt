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
): Behavior<A> {
    @FragileYafrlAPI
    suspend fun collect(collector: FlowCollector<A>) {
        node.collect(collector)
    }

    /**
     * Return the current value of the state.
     **/
    override val value: A get() {
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
    fun <B, C> combineWith(state2: State<B>, op: (A, B) -> C): State<C> {
        val timeline = Timeline.currentTimeline()

        val combined = timeline.createCombinedNode(
            parentNodes = listOf(this.node, state2.node),
            combine = { values ->
                val first = values[0] as A
                val second = values[1] as B

                op(first, second)
            }
        )

        return State(combined)
    }

    fun <B, C, D> combineWith(state2: State<B>, state3: State<C>, op: (A, B, C) -> D): State<D> {
        val timeline = Timeline.currentTimeline()

        val combined = timeline.createCombinedNode(
            parentNodes = listOf(this.node, state2.node, state3.node),
            combine = { values ->
                val first = values[0] as A
                val second = values[1] as B
                val third = values[2] as C

                op(first, second, third)
            }
        )

        return State(combined)
    }

    fun <B, C, D, E> combineWith(
        state2: State<B>,
        state3: State<C>,
        state4: State<D>,
        op: (A, B, C, D) -> E
    ): State<E> {
        val timeline = Timeline.currentTimeline()

        val combined = timeline.createCombinedNode(
            parentNodes = listOf(this.node, state2.node, state3.node, state4.node),
            combine = { values ->
                val first = values[0] as A
                val second = values[1] as B
                val third = values[2] as C
                val fourth = values[3] as D

                op(first, second, third, fourth)
            }
        )

        return State(combined)
    }

    fun <B, C, D, E, F> combineWith(
        state2: State<B>,
        state3: State<C>,
        state4: State<D>,
        state5: State<E>,
        op: (A, B, C, D, E) -> F
    ): State<F> {
        val timeline = Timeline.currentTimeline()

        val combined = timeline.createCombinedNode(
            parentNodes = listOf(this.node, state2.node, state3.node, state4.node, state5.node),
            combine = { values ->
                val first = values[0] as A
                val second = values[1] as B
                val third = values[2] as C
                val fourth = values[3] as D
                val fifth = values[4] as E

                op(first, second, third, fourth, fifth)
            }
        )

        return State(combined)
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
    override var value: A
        get() = super.value
        set(value) {
            val graph = Timeline.currentTimeline()

            return graph.updateNodeValue(node, value)
        }
}

fun <A> mutableStateOf(value: A): MutableState<A> {
    val graph = Timeline.currentTimeline()

    return MutableState(graph.createNode(lazy { value }))
}