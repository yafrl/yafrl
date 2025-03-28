package io.github.yafrl.testing

import io.github.sintrastes.yafrl.EventState
import io.github.sintrastes.yafrl.annotations.FragileYafrlAPI
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.resolution.default

@OptIn(FragileYafrlAPI::class)
inline fun <reified A> Arb.Companion.eventState(): Arb<EventState<A>> = arbitrary {
    EventState.Fired(Arb.default<A>().bind())
}
