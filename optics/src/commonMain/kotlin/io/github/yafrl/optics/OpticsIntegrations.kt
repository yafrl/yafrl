package io.github.yafrl.optics

import arrow.optics.Lens
import arrow.optics.Prism
import io.github.yafrl.BindingSignal
import io.github.yafrl.Event
import io.github.yafrl.Signal
import io.github.yafrl.annotations.FragileYafrlAPI
import io.github.yafrl.sample
import io.github.yafrl.signal
import io.github.yafrl.timeline.Timeline

/** Embed an event in a larger set of events via a [Prism]. */
fun <A, B> Event<A>.embed(timeline: Timeline, inclusion: Prism<B, A>): Event<B> = with(timeline.timelineScope) {
    map { inclusion.reverseGet(it) }
}

/** Focus on a smaller part of a [Signal] by applying a [Lens]. */
fun <A, B> Signal<A>.focus(timeline: Timeline, lens: Lens<A, B>): Signal<B> = with(timeline.timelineScope) {
    map { lens.get(it) }
}

/**
 * Focus on a smaller part of a [BindingSignal] by applying a [Lens]
 **/
@OptIn(FragileYafrlAPI::class)
fun <A, B> BindingSignal<A>.focus(timeline: Timeline, lens: Lens<A, B>): BindingSignal<B> = with(timeline.timelineScope) {
    val state = internalBindingState(lazy { lens.get(value) })

    this@focus.collectSync { newValue ->
        val newValue = lens.get(newValue)

        if (state.value != newValue) {
            state.value = newValue
        }
    }

    state.collectSync { newValue ->
        val newValue = lens.set(value, newValue)

        if (value != newValue) {
            value = newValue
        }
    }

    return state
}

typealias SignalFunction<A, B> = (Signal<A>) -> Signal<B>

/**
 * Embed a signal function into a larger signal function using a lens.
 **/
fun <A, B> SignalFunction<B, B>.embed(timeline: Timeline, lens: Lens<A, B>): SignalFunction<A, A> = { input ->
    with(timeline.timelineScope) {
        // Take the transformation
        val focusedTransform: SignalFunction<B, B> = this@embed

        // Apply it to the focused input to get an output signal on the foci
        val focusedOutput: Signal<B> = focusedTransform(
            input.map { lens.get(it) }
        )

        // Use the lens setter to create a signal of endomorphisms of the whole
        val wholeTransform: Signal<(A) -> A> = focusedOutput.map { y: B ->
            { x: A -> lens.set(x, y) }
        }

        // Use the applicative instance to apply the transformation on the whole to the input.
        signal {
            wholeTransform.bind()
                .invoke(input.bind())
        }
    }
}

interface ProductBuilder<A> {
    fun <B> SignalFunction<B, B>.bind(lens: Lens<A, B>)
}

/**
 * Build a signal transformer from the product of several signal transformers.
 **/
fun <A> Signal.Companion.product(
    timeline: Timeline,
    builder: ProductBuilder<A>.() -> Unit
): (Signal<A>) -> Signal<A> {
    val signals = mutableListOf<SignalFunction<A, A>>()

    val scope = object : ProductBuilder<A> {
        override fun <B> SignalFunction<B, B>.bind(lens: Lens<A, B>) {
            signals.add(embed(timeline, lens))
        }
    }

    scope.builder()

    val id = { x: Signal<A> -> x }

    return signals.fold(id) { x, y ->
        { it: Signal<A> -> x.invoke(y.invoke(it)) }
    }
}
