package io.github.yafrl.optics

import arrow.optics.Lens
import arrow.optics.Prism
import io.github.yafrl.BindingSignal
import io.github.yafrl.Event
import io.github.yafrl.Signal
import io.github.yafrl.annotations.FragileYafrlAPI
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

