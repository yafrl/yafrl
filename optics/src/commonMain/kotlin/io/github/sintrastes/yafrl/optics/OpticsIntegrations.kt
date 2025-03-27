package io.github.sintrastes.yafrl.optics

import arrow.optics.Prism
import io.github.sintrastes.yafrl.Event

/** Embed an event in a larger set of events via a [Prism]. */
fun <A, B> Event<A>.embed(inclusion: Prism<B, A>): Event<B> {
    return map {
        inclusion.reverseGet(it)
    }
}
