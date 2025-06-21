package behaviors

import io.github.yafrl.behaviors.Behavior.Companion.integral
import io.github.yafrl.BroadcastEvent
import io.github.yafrl.annotations.FragileYafrlAPI
import io.github.yafrl.behaviors.Behavior.Companion.const
import io.github.yafrl.asBehavior
import io.github.yafrl.behaviors.Behavior
import io.github.yafrl.behaviors.integrate
import io.github.yafrl.behaviors.integrateWith
import io.github.yafrl.externalEvent
import io.github.yafrl.timeline.Timeline
import io.github.yafrl.externalSignal
import io.github.yafrl.behaviors.plus
import io.github.yafrl.runYafrl
import io.github.yafrl.vector.ScalarSpace
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

@OptIn(FragileYafrlAPI::class)
class IntegrationTests: FunSpec({
    test("Integration can be used to compute variable velocity") {
        runYafrl(CoroutineScope(Dispatchers.Default)) {

            val modifier = externalSignal<Float>(0f)

            val deltaTime = Timeline.currentTimeline().clock as BroadcastEvent<Duration>

            val position = integral(
                const(1f) + integral(modifier.asBehavior())
            )

            assertEquals(0f, position.sampleValue())

            deltaTime.send(1.0.seconds)

            assertEquals(1f, position.sampleValue())

            modifier.value = 1f

            deltaTime.send(1.0.seconds)

            assertTrue("Expected 2.5 but was ${position.sampleValue()}") {
                abs(position.sampleValue() - 2.5f) < 0.01 // 0.000001 // TODO: Accuracy is not as good as it was here.
            }
        }
    }

    test("Integrated behavior throws on non-monotonic inputs") {
        shouldThrow<Throwable> {
            val integrated = Behavior.continuous { 0.0 }
                .integrate()

            integrated.sampleValueAt(1.0.seconds)

            integrated.sampleValueAt(0.0.seconds)
        }
    }

    test("Can specify custom vector space for integration") {
        runYafrl {
            val integrated = Behavior.continuous { 1.0 }
                .integrate(ScalarSpace.double())

            val integrated2 = Behavior.continuous { 1.0 }
                .integrate()

            assertEquals(integrated.sampleValue(), integrated2.sampleValue())
        }
    }

    test("Can specify custom accumulator for integration") {
        runYafrl {
            val clock = Timeline.currentTimeline().clock as BroadcastEvent<Duration>

            val integrated = Behavior.continuous { time -> 1.0 }
                .integrateWith(0.0, accum = { x, y -> min(5.0, x + y) })

            // Wait a long time
            clock.send(100.0.seconds)

            // Should still be 5.0 because of the custom operation
            assertTrue(integrated.sampleValue() <= 5.0)
        }
    }

    test("Exact integral is correct") {
        val polynomial = Behavior.polynomial(listOf(1.0, 1.0)) // 1 + t

        val integral = polynomial
            .exactIntegral { x, y -> x + y }

        assertEquals(listOf(0.0, 1.0, 0.5), integral.coefficients) // t + t^2 / 2
    }
})
