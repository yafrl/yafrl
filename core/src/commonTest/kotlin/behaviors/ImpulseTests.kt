package behaviors

import io.github.sintrastes.yafrl.BroadcastEvent
import io.github.sintrastes.yafrl.annotations.ExperimentalYafrlAPI
import io.github.sintrastes.yafrl.behaviors.integrate
import io.github.sintrastes.yafrl.broadcastEvent
import io.github.sintrastes.yafrl.impulse
import io.github.sintrastes.yafrl.internal.Timeline
import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.numericDouble
import io.kotest.property.checkAll
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalYafrlAPI::class)
class ImpulseTests: FunSpec({
    test("Impulses integrate to same value for arbitrary delta time") {
        checkAll(Arb.numericDouble(1.0, 15.0)) { dt ->
            Timeline.initializeTimeline()

            val clock = Timeline.currentTimeline().clock as BroadcastEvent

            val impulseEvent = broadcastEvent<Unit>()

            val impulse = impulseEvent.impulse(0.0, 1.0)

            val integral = impulse.integrate()

            assertEquals(0.0, integral.value)

            impulseEvent.send(Unit)

            clock.send(dt.milliseconds)

            assertTrue(abs(1.0 -  integral.value) < 0.01, "expected 1.0, but got ${integral.value}")
        }
    }
})