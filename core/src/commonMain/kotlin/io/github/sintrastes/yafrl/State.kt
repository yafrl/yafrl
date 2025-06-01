package io.github.sintrastes.yafrl

import io.github.sintrastes.yafrl.annotations.FragileYafrlAPI
import io.github.sintrastes.yafrl.behaviors.Behavior
import io.github.sintrastes.yafrl.behaviors.switcher
import io.github.sintrastes.yafrl.internal.Node
import io.github.sintrastes.yafrl.internal.Timeline
import io.github.sintrastes.yafrl.internal.current
import io.github.sintrastes.yafrl.vector.Float2
import io.github.sintrastes.yafrl.vector.Float3
import io.github.sintrastes.yafrl.vector.VectorSpace
import kotlinx.coroutines.flow.FlowCollector
import kotlin.jvm.JvmName
import kotlin.reflect.typeOf

/**
 * A flow can be thought of as a combination of a [io.github.sintrastes.yafrl.behaviors.Behavior] and an
 *  [Event].
 *
 * Like a [io.github.sintrastes.yafrl.behaviors.Behavior], a [State] has a [current] value which can be
 *  sampled at any time.
 *
 * Like an [Event], a [State] will automatically influence derived [State]s
 *  when the underlying state changes -- in other words, it is _reactive_.
 *
 * Following our graphical analogies for [Event] and [io.github.sintrastes.yafrl.behaviors.Behavior], a [State]
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
 * They are very similar to [kotlinx.coroutines.flow.StateFlow]s in this sense -- only better.
 **/
open class State<out A> @FragileYafrlAPI constructor(
    @property:FragileYafrlAPI val node: Node<A>
) {
    @OptIn(FragileYafrlAPI::class)
    override fun toString(): String {
        return "State($node)"
    }

    @OptIn(FragileYafrlAPI::class)
    val label get() = node.label

    @OptIn(FragileYafrlAPI::class)
    open val value get() = node.current()

    @OptIn(FragileYafrlAPI::class)
    fun labeled(label: String): State<A> {
        node.label = label
        return this
    }

    /**
     * Launches a handler that asynchronously listens to updates
     *  on the state.
     *
     * Comparable to [Flow.collect][kotlinx.coroutines.flow.Flow.collect].
     **/
    @FragileYafrlAPI
    suspend fun collectAsync(collector: FlowCollector<A>) {
        node.collect(collector)
    }

    @FragileYafrlAPI
    fun collectSync(collector: (A) -> Unit) {
        node.collectSync(collector)
    }

    /**
     * Applies the passed function [f] to each state,
     *  producing a new transformed [State] value.
     *
     * Note: [f] should be a pure function.
     **/
    @OptIn(FragileYafrlAPI::class)
    fun <B> map(f: (A) -> B): State<B> {
        val graph = Timeline.currentTimeline()

        return State(graph.createMappedNode(node, f))
    }

    fun <B> flatMap(f: (A) -> State<B>): State<B> {
        return map(f).flatten()
    }

    /**
     * Get the [Event] with just the updates associated with a [State].
     **/
    @OptIn(FragileYafrlAPI::class)
    fun updated(): Event<A> {
        val graph = Timeline.currentTimeline()

        return Event(
            graph.createMappedNode(
                parent = node,
                initialValue = lazy { EventState.None },
                f = { EventState.Fired(it) }
            )
        )
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
    @OptIn(FragileYafrlAPI::class)
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

    @OptIn(FragileYafrlAPI::class)
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

    @OptIn(FragileYafrlAPI::class)
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

    @OptIn(FragileYafrlAPI::class)
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
        @OptIn(FragileYafrlAPI::class)
        fun <A> const(value: A): State<A> {
            return internalBindingState(lazy { value })
        }

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
        @OptIn(FragileYafrlAPI::class)
        fun <A, B> fold(initial: A, events: Event<B>, reducer: (A, B) -> A): State<A> {
            val graph = Timeline.currentTimeline()

            return State(
                graph.createFoldNode(initial, events.node, reducer)
            )
        }

        @OptIn(FragileYafrlAPI::class)
        fun <A> combineAll(
            vararg states: State<A>
        ): State<List<A>> {
            val timeline = Timeline.currentTimeline()

            val combined = timeline.createCombinedNode(
                parentNodes = states.map { it.node },
                combine = { values ->
                    values.map { it as A }
                }
            )

            return State(combined)
        }

        /**
         * Produce a new [State] by providing an initial value, which is held
         *  constant until the [update] function occurs, at which point
         *  it will hold that value until the next update.
         */
        @OptIn(FragileYafrlAPI::class)
        fun <A> hold(initial: A, update: Event<A>): State<A> {
            val timeline = Timeline.currentTimeline()

            val state = internalBindingState(lazy { initial })

            update.node.collectSync { updated ->
                if (updated is EventState.Fired<A>) {
                    timeline.updateNodeValue(state.node, updated.event, internal = true)
                }
            }

            return state
        }

        @OptIn(FragileYafrlAPI::class)
        fun <A> holdLazy(initial: Lazy<A>, update: Event<A>): State<A> {
            val timeline = Timeline.currentTimeline()

            val state = internalBindingState(initial)

            update.node.collectSync { updated ->
                if (updated is EventState.Fired<A>) {
                    timeline.updateNodeValue(state.node, updated.event, internal = true)
                }
            }

            return state
        }
    }
}

operator fun State<Float>.plus(other: State<Float>): State<Float> {
    return combineWith(other) { x, y -> x + y }
}

@JvmName("plusFloat2")
operator fun State<Float2>.plus(other: State<Float2>): State<Float2> = with(VectorSpace.float2()) {
    return combineWith(other) { x, y ->
        x + y
    }
}

@JvmName("plusFloat23")
operator fun State<Float3>.plus(other: State<Float3>): State<Float3> = with(VectorSpace.float3()) {
    return combineWith(other) { x, y ->
        x + y
    }
}

/**
 * Utility to convert a [State] into a behavior whose values are interpreted as the
 *  piecewise function of the values of the [State].
 **/
@OptIn(FragileYafrlAPI::class)
inline fun <reified A> State<A>.asBehavior(): Behavior<A> {
    if (VectorSpace.hasInstance<A>()) {
        // Use a switcher so we can get an exact polynomial integral piecewise.
        return map { Behavior.const(it) }.switcher()
    } else {
        // If no instance exists, just use sampled so we do not try to get an
        // instance that does not exist at runtime.
        return Behavior.sampled { this.value }
    }
}

/**
 * Construct a `State<A>` from a nested `State<State<A>>` by updating whenever
 *  either the inner or outer [State] updates.
 *
 * Compare with the Monad instance of [Dynamic](https://hackage.haskell.org/package/reflex-0.9.3.3/docs/Reflex-Class.html#t:Dynamic) in [reflex-frp](https://reflex-frp.org/).
 **/
@OptIn(FragileYafrlAPI::class)
fun <A> State<State<A>>.flatten(): State<A> {
    val timeline = Timeline.currentTimeline()

    var currentState = value

    val flattened = internalBindingState(lazy { currentState.value })

    var collector: ((A) -> Unit)? = null

    // Note: Order of registration for these collects is important here.

    collectSync { newState ->
        // Remove the old collector when the state changes.
        collector?.let { currentState.node.unregisterSync(it) }

        // Update the current value to the new state's current value.
        currentState = newState

        // Note: Needs to be updates to the raw value so we don't invoke a new frame.
        timeline.updateNodeValue(flattened.node, currentState.node.rawValue, internal = true)

        // Collect on value updates to the new state
        collector = { newValue ->
            timeline.updateNodeValue(flattened.node, newValue, internal = true)
        }
        currentState.collectSync(collector!!)
    }

    // Collect on value updates to the initial state.
    collector = { newValue ->
        timeline.updateNodeValue(flattened.node, newValue, internal = true)
    }
    currentState.collectSync(collector)

    return flattened
}

/**
 * Builds a [State] that updates with a list of all input states
 *  whenever any of the input states updates.
 *
 * Example usage:
 *
 * ```
 * val stateA = bindingState("1")
 *
 * val stateB = bindingState("2")
 *
 * val combined = listOf(stateA, stateB).sequenceState()
 *
 * assert(combined.value == listOf("1", "2")
 *
 * stateA.value = "A"
 *
 * assert(combined.value == listOf("A", "2")
 * ```
 **/
@OptIn(FragileYafrlAPI::class)
fun <A> List<State<A>>.sequenceState(): State<List<A>> {
    return State.combineAll(
        *this.toTypedArray()
    )
}

/**
 * Variant of [State] that can be [setTo] a new value.
 *
 * Constructed with the [bindingState] function.
 **/
class BindingState<A> internal constructor(
    node: Node<A>
): State<A>(node) {
    @OptIn(FragileYafrlAPI::class)
    override var value: A
        get() = super.value
        set(value) {
            val graph = Timeline.currentTimeline()

            return graph.updateNodeValue(node, value)
        }
}

/**
 * Construct a [BindingState] -- which is a [State] whose value can be updated to new
 *  values arbitrarily.
 *
 * [BindingState]s can be thought of (together with [BroadcastEvent]s) as the "inputs"
 *  to the Yafrl FRP state graph, and thus should typically only be used as a means of
 *  integrating with external systems -- rather than for business logic.
 **/
@OptIn(FragileYafrlAPI::class)
inline fun <reified A> bindingState(value: A, label: String? = null): BindingState<A> {
    val timeline = Timeline.currentTimeline()

    val state = internalBindingState(lazy { value }, label)

    timeline.externalNodes[state.node.id] = Timeline.ExternalNode(typeOf<A>(), state.node)

    return state
}

/**
 * Internal version of [bindingState] used for states which should be considered
 * "internal" implementation details of the graph.
 **/
@FragileYafrlAPI
fun <A> internalBindingState(value: Lazy<A>, label: String? = null): BindingState<A> {
    val graph = Timeline.currentTimeline()

    return BindingState(
        graph.createNode(
            value = value,
            label = label
        )
    )
}