package io.github.yafrl

import io.github.yafrl.annotations.FragileYafrlAPI
import io.github.yafrl.timeline.HasTimeline
import io.github.yafrl.timeline.Node
import io.github.yafrl.timeline.Timeline
import io.github.yafrl.timeline.current
import io.github.yafrl.timeline.debugging.ExternalNode
import io.github.yafrl.vector.Float2
import io.github.yafrl.vector.Float3
import io.github.yafrl.vector.VectorSpace
import kotlinx.coroutines.flow.FlowCollector
import kotlin.jvm.JvmName
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * A flow can be thought of as a combination of a [io.github.yafrl.behaviors.Behavior] and an
 *  [Event].
 *
 * Like a [io.github.yafrl.behaviors.Behavior], a [Signal] has a [current] value which can be
 *  sampled at any time.
 *
 * Like an [Event], a [Signal] will automatically influence derived [Signal]s
 *  when the underlying state changes -- in other words, it is _reactive_.
 *
 * Following our graphical analogies for [Event] and [io.github.yafrl.behaviors.Behavior], a [Signal]
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
 * [Signal]s are incredibly useful for representing the state of
 *  various components in an application which need to be consumed
 *  in responsive user interfaces.
 *
 * They are very similar to [kotlinx.coroutines.flow.StateFlow]s in this sense -- only better.
 **/
open class Signal<out A> @FragileYafrlAPI constructor(
    @property:FragileYafrlAPI val node: Node<A>
) {
    @OptIn(FragileYafrlAPI::class)
    override fun toString(): String {
        return "Signal($node)"
    }

    @OptIn(FragileYafrlAPI::class)
    val label get() = node.label

    @OptIn(FragileYafrlAPI::class)
    fun labeled(label: String): Signal<A> {
        node.label = label
        return this
    }

    companion object
}

open class SignalScope(timeline: Timeline): HasTimeline, EventScope(timeline) {
    /**
     * Launches a handler that asynchronously listens to updates
     *  on the state.
     *
     * Comparable to [Flow.collect][kotlinx.coroutines.flow.Flow.collect].
     **/
    @FragileYafrlAPI
    suspend fun <A> Signal<A>.collectAsync(collector: FlowCollector<A>) {
        node.collect(collector)
    }

    @FragileYafrlAPI
    fun <A> Signal<A>.collectSync(collector: (A) -> Unit) {
        node.collectSync(collector)
    }

    /**
     * Applies the passed function [f] to each state,
     *  producing a new transformed [Signal] value.
     *
     * Note: [f] should be a pure function.
     **/
    @OptIn(FragileYafrlAPI::class)
    fun <A, B> Signal<A>.map(f: SampleScope.(A) -> B): Signal<B> {
        return Signal(timeline.createMappedNode(node, f))
    }

    fun <A, B> Signal<A>.flatMap(f: SampleScope.(A) -> Signal<B>): Signal<B> {
        return map(f).flatten()
    }

    fun <A, B> Signal<A>.switchMap(f: SampleScope.(A) -> Event<B>): Event<B> {
        return map(f).switch()
    }

    /**
     * Get the [Event] with just the updates associated with a [Signal].
     **/
    @OptIn(FragileYafrlAPI::class)
    fun <A> Signal<A>.updated(): Event<A> {
        return Event(
            timeline.createMappedNode(
                parent = node,
                initialValue = { EventState.None },
                f = { EventState.Fired(it) }
            )
        )
    }

    /**
     * Combine two states together into a single [Signal] by applying a function
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
    fun <A, B, C> Signal<A>.combineWith(state2: Signal<B>, op: (A, B) -> C): Signal<C> {
        val combined = timeline.createCombinedNode(
            parentNodes = listOf(this.node, state2.node),
            combine = { values ->
                val first = values[0] as A
                val second = values[1] as B

                op(first, second)
            }
        )

        return Signal(combined)
    }

    @OptIn(FragileYafrlAPI::class)
    fun <A, B, C, D> Signal<A>.combineWith(state2: Signal<B>, state3: Signal<C>, op: (A, B, C) -> D): Signal<D> {
        val combined = timeline.createCombinedNode(
            parentNodes = listOf(this.node, state2.node, state3.node),
            combine = { values ->
                val first = values[0] as A
                val second = values[1] as B
                val third = values[2] as C

                op(first, second, third)
            }
        )

        return Signal(combined)
    }

    @OptIn(FragileYafrlAPI::class)
    fun <A, B, C, D, E> Signal<A>.combineWith(
        state2: Signal<B>,
        state3: Signal<C>,
        state4: Signal<D>,
        op: (A, B, C, D) -> E
    ): Signal<E> {
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

        return Signal(combined)
    }

    @OptIn(FragileYafrlAPI::class)
    fun <A, B, C, D, E, F> Signal<A>.combineWith(
        state2: Signal<B>,
        state3: Signal<C>,
        state4: Signal<D>,
        state5: Signal<E>,
        op: (A, B, C, D, E) -> F
    ): Signal<F> {
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

        return Signal(combined)
    }

    @OptIn(FragileYafrlAPI::class)
    fun <A> Signal.Companion.const(value: A): Signal<A> {
        return internalBindingState(lazy { value })
    }

    /**
     * Construct a [Signal] by suppling an [initial] value, a set of [events]
     *  driving the updates of the [Signal], together with a [reducer] describing
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
    fun <A, B> Signal.Companion.fold(initial: A, events: Event<B>, reducer: SampleScope.(A, B) -> A): Signal<A> {
        return Signal(
            timeline.createFoldNode(initial, events.node, reducer)
        )
    }

    fun <A> Signal.Companion.fold(
        initial: A,
        vararg actions: Event<(A) -> A>
    ) = fold(initial, Event.merged(*actions)) { state, action ->
        action(state)
    }

    @OptIn(FragileYafrlAPI::class)
    fun <A> Signal.Companion.combineAll(
        vararg states: Signal<A>
    ): Signal<List<A>> {
        val combined = timeline.createCombinedNode(
            parentNodes = states.map { it.node },
            combine = { values ->
                values.map { it as A }
            }
        )

        return Signal(combined)
    }

    /**
     * Produce a new [Signal] by providing an initial value, which is held
     *  constant until the [update] function occurs, at which point
     *  it will hold that value until the next update.
     */
    @OptIn(FragileYafrlAPI::class)
    fun <A> Signal.Companion.hold(initial: A, update: Event<A>): Signal<A> {
        val state = internalBindingState(lazy { initial })

        update.node.collectSync { updated ->
            if (updated is EventState.Fired<A>) {
                timeline.updateNodeValue(state.node, updated.event, internal = true)
            }
        }

        return state
    }

    operator fun Signal<Float>.plus(other: Signal<Float>): Signal<Float> {
        return combineWith(other) { x, y -> x + y }
    }

    @JvmName("plusFloat2")
    operator fun Signal<Float2>.plus(other: Signal<Float2>): Signal<Float2> = with(VectorSpace.float2()) {
        return combineWith(other) { x, y ->
            x + y
        }
    }

    @JvmName("plusFloat23")
    operator fun Signal<Float3>.plus(other: Signal<Float3>): Signal<Float3> = with(VectorSpace.float3()) {
        return combineWith(other) { x, y ->
            x + y
        }
    }

    /**
     * Constructs a flattened [Event] from a changing [Signal] of events over time.
     *
     * Compare with switchDyn in reflex.
     **/
    @OptIn(FragileYafrlAPI::class)
    fun <A> Signal<Event<A>>.switch(): Event<A> {
        val resultEvents = internalBroadcastEvent<A>()

        var currentEvent = node.current(timeline)
        val eventListener = { event: EventState<A> ->
            if (event is EventState.Fired<A>) resultEvents.send(event.event)
        }

        currentEvent.node.collectSync(eventListener)

        node.collectSync { newEvents ->
            currentEvent.node.unregisterSync(eventListener)
            currentEvent = newEvents
            newEvents.node.collectSync(eventListener)
        }

        return resultEvents
    }

    /**
     * Construct a `State<A>` from a nested `State<State<A>>` by updating whenever
     *  either the inner or outer [Signal] updates.
     *
     * Compare with the Monad instance of [Dynamic](https://hackage.haskell.org/package/reflex-0.9.3.3/docs/Reflex-Class.html#t:Dynamic) in [reflex-frp](https://reflex-frp.org/).
     **/
    @OptIn(FragileYafrlAPI::class)
    fun <A> Signal<Signal<A>>.flatten(): Signal<A> {
        var currentState = node.current(timeline)

        val flattened = internalBindingState(lazy { currentState.node.current(timeline) })

        timeline.graph.addChild(currentState.node.id, flattened.node.id)

        var collector: ((A) -> Unit)? = null

        // Note: Order of registration for these collects is important here.

        collectSync { newState ->
            // Remove the old collector when the state changes.
            collector?.let { currentState.node.unregisterSync(it) }

            // Remove the old parent-child relationship
            timeline.graph.removeChild(currentState.node.id, flattened.node.id)

            // Update the current value to the new state's current value.
            currentState = newState

            // Add in the new parent-child relationship
            timeline.graph.addChild(currentState.node.id, flattened.node.id)

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
     * Builds a [Signal] that updates with a list of all input states
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
    fun <A> List<Signal<A>>.sequenceState(): Signal<List<A>> {
        return Signal.combineAll(
            *this.toTypedArray()
        )
    }

    /**
     * Construct an external [BindingSignal] -- which is a [Signal] whose value can be updated to new
     *  values arbitrarily.
     *
     * external [BindingSignal]s can be thought of (together with external [BroadcastEvent]s)
     *  as the "inputs" to the Yafrl FRP state graph, and thus should typically only be used
     *  as a means of integrating with external systems -- rather than for business logic.
     *
     * If you need a [BindingSignal] for internal use to implement some buisness logic,
     *  this practice is generally discouraged, but [internalBindingState] can be used
     *  for this purpose if it is necessary.
     **/
    @OptIn(FragileYafrlAPI::class)
    inline fun <reified A> externalSignal(
        value: A,
        label: String? = null
    ): BindingSignal<A> = externalSignal(value, typeOf<A>(), label)

    @OptIn(FragileYafrlAPI::class)
    fun <A> externalSignal(value: A, kType: KType, label: String? = null): BindingSignal<A> {
        val state = internalBindingState(lazy { value }, label)

        timeline.externalNodes[state.node.id] = ExternalNode(kType, state.node)

        return state
    }

    /**
     * Internal version of [externalSignal] used for states which should be considered
     * "internal" implementation details of the graph.
     **/
    @FragileYafrlAPI
    fun <A> internalBindingState(value: Lazy<A>, label: String? = null): BindingSignal<A> {
        return BindingSignal(
            timeline,
            timeline.createNode(
                value = value,
                label = label
            )
        )
    }

    /**
     * Method version of [Signal.fold], for easier use in method chains.
     **/
    fun <A, B> Event<A>.scan(initial: B, reducer: SampleScope.(B, A) -> B): Signal<B> {
        return Signal.fold(initial, this, reducer)
    }

    /** Method version of [Signal.hold].  */
    fun <A> Event<A>.hold(initial: @UnsafeVariance A): Signal<A> {
        return Signal.hold(initial, this)
    }

    /**
     * Takes and event, and constructs a moving window of [size]
     *  when events occur.
     *
     * This is useful for things like calculating moving averages
     *  for the values of an [Event].
     **/
    fun <A> Event<A>.window(size: Int): Event<List<A>> = Signal.fold(listOf<A>(), this) { window, newValue ->
        if (window.size < size) {
            window + listOf(newValue)
        } else {
            window.drop(1) + listOf(newValue)
        }
    }
        .updated()
}

/**
 * Variant of [Signal] that can be [setTo] a new value.
 *
 * Constructed with the [externalSignal] function.
 **/
class BindingSignal<A> internal constructor(
    private val timeline: Timeline,
    node: Node<A>
): Signal<A>(node) {
    @OptIn(FragileYafrlAPI::class)
    var value: A
        get() = super.node.current(timeline)
        set(value) {
            return timeline.updateNodeValue(node, value)
        }
}