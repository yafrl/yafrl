package io.github.sintrastes.yafrl.optics

import arrow.optics.Lens
import arrow.optics.Prism
import io.github.sintrastes.yafrl.BindingSignal
import io.github.sintrastes.yafrl.Event
import io.github.sintrastes.yafrl.Signal
import io.github.sintrastes.yafrl.annotations.FragileYafrlAPI
import io.github.sintrastes.yafrl.internalBindingState

/** Embed an event in a larger set of events via a [Prism]. */
fun <A, B> Event<A>.embed(inclusion: Prism<B, A>): Event<B> {
    return map {
        inclusion.reverseGet(it)
    }
}

/** Focus on a smaller part of a [Signal] by applying a [Lens]. */
fun <A, B> Signal<A>.focus(lens: Lens<A, B>): Signal<B> {
    return map {
        lens.get(it)
    }
}

/**
 * Focus on a smaller part of a [BindingSignal] by applying a [Lens]
 **/
@OptIn(FragileYafrlAPI::class)
fun <A, B> BindingSignal<A>.focus(lens: Lens<A, B>): BindingSignal<B> {
    val state = internalBindingState(lazy { lens.get(value) })

    this.collectSync { newValue ->
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

