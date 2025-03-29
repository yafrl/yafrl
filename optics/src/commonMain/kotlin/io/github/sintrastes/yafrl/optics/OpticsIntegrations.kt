package io.github.sintrastes.yafrl.optics

import arrow.optics.Lens
import arrow.optics.Prism
import io.github.sintrastes.yafrl.BindingState
import io.github.sintrastes.yafrl.Event
import io.github.sintrastes.yafrl.State
import io.github.sintrastes.yafrl.annotations.FragileYafrlAPI
import io.github.sintrastes.yafrl.bindingState

/** Embed an event in a larger set of events via a [Prism]. */
fun <A, B> Event<A>.embed(inclusion: Prism<B, A>): Event<B> {
    return map {
        inclusion.reverseGet(it)
    }
}

/** Focus on a smaller part of a [State] by applying a [Lens]. */
fun <A, B> State<A>.focus(lens: Lens<A, B>): State<B> {
    return map {
        lens.get(it)
    }
}

/**
 * Focus on a smaller part of a [BindingState] by applying a [Lens]
 **/
@OptIn(FragileYafrlAPI::class)
inline fun <A, reified B> BindingState<A>.focus(lens: Lens<A, B>): BindingState<B> {
    val state = bindingState(lens.get(value))

    this.collectSync { newValue ->
        state.value = lens.get(newValue)
    }

    state.collectSync { newValue ->
        value = lens.set(value, newValue)
    }

    return state
}

