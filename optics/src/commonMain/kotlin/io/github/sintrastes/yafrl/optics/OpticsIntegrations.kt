package io.github.sintrastes.yafrl.optics

import arrow.optics.Lens
import arrow.optics.Prism
import io.github.sintrastes.yafrl.Event
import io.github.sintrastes.yafrl.State

/** Embed an event in a larger set of events via a [Prism]. */
fun <A, B> Event<A>.embed(inclusion: Prism<B, A>): Event<B> {
    return map {
        inclusion.reverseGet(it)
    }
}

/** Focs on a smaller part of a [State] by applying a [Lens]. */
fun <A, B> State<A>.focus(lens: Lens<A, B>): State<B> {
    return map {
        lens.get(it)
    }
}

