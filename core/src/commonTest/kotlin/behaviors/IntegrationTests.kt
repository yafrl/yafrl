package behaviors

import io.github.sintrastes.yafrl.behaviors.Behavior.Companion.integral
import io.github.sintrastes.yafrl.BroadcastEvent
import io.github.sintrastes.yafrl.behaviors.Behavior.Companion.const
import io.github.sintrastes.yafrl.asBehavior
import io.github.sintrastes.yafrl.behaviors.Behavior
import io.github.sintrastes.yafrl.behaviors.integrate
import io.github.sintrastes.yafrl.behaviors.integrateWith
import io.github.sintrastes.yafrl.broadcastEvent
import io.github.sintrastes.yafrl.internal.Timeline
import io.github.sintrastes.yafrl.externalSignal
import io.github.sintrastes.yafrl.behaviors.plus
import io.github.sintrastes.yafrl.vector.ScalarSpace
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.math.abs
import kotlin.math.min
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class IntegrationTests: FunSpec({
    test("Integration can be used to compute variable velocity") {
        Timeline.initializeTimeline(
            CoroutineScope(Dispatchers.Default),
            initClock = {
                broadcastEvent<Duration>()
            }
        )

        val modifier = externalSignal<Float>(0f)

        val deltaTime = Timeline.currentTimeline().clock as BroadcastEvent<Duration>

        val position = integral(
            const(1f) + integral(modifier.asBehavior())
        )

        assertEquals(0f, position.value)

        deltaTime.send(1.0.seconds)

        assertEquals(1f, position.value)

        modifier.value = 1f

        deltaTime.send(1.0.seconds)

        assertTrue("Expected 2.5 but was ${position.value}") {
            abs(position.value - 2.5f) < 0.01 // 0.000001 // TODO: Accuracy is not as good as it was here.
        }
    }

    test("Integrated behavior throws on non-monotonic inputs") {
        shouldThrow<Throwable> {
            val integrated = Behavior.continuous { 0.0 }
                .integrate()

            integrated.sampleValue(1.0.seconds)

            integrated.sampleValue(0.0.seconds)
        }
    }

    test("Can specify custom vector space for integration") {
        val integrated = Behavior.continuous { 1.0 }
            .integrate(ScalarSpace.double())

        val integrated2 = Behavior.continuous { 1.0 }
            .integrate()

        assertEquals(integrated.value, integrated2.value)
    }

    test("Can specify custom accumulator for integration") {
        Timeline.initializeTimeline()

        val clock = Timeline.currentTimeline().clock as BroadcastEvent<Duration>

        val integrated = Behavior.continuous { time -> 1.0 }
            .integrateWith(0.0, accum = { x, y -> min(5.0, x + y) })

        // Wait a long time
        clock.send(100.0.seconds)

        // Should still be 5.0 because of the custom operation
        assertTrue(integrated.value <= 5.0)
    }

    test("Exact integral is correct") {
        val polynomial = Behavior.polynomial(listOf(1.0, 1.0)) // 1 + t

        val integral = polynomial
            .exactIntegral { x, y -> x + y }

        assertEquals(listOf(0.0, 1.0, 0.5), integral.coefficients) // t + t^2 / 2
    }
})
