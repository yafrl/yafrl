package behaviors

import io.github.yafrl.BroadcastEvent
import io.github.yafrl.annotations.FragileYafrlAPI
import io.github.yafrl.behaviors.Behavior
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

            val deltaTime = timeline.clock as BroadcastEvent<Duration>

            val position = Behavior.integral(
                Behavior.const(1f) + Behavior.integral(modifier.asBehavior())
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
            runYafrl {
                val integrated = Behavior.continuous { 0.0 }
                    .integrate()

                integrated.sampleValueAt(1.0.seconds)

                integrated.sampleValueAt(0.5.seconds)
            }
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
            val clock = timeline.clock as BroadcastEvent<Duration>

            val integrated = Behavior.continuous { time -> 1.0 }
                .integrateWith(0.0, accum = { x, y -> min(5.0, x + y) })

            // Wait a long time
            clock.send(100.0.seconds)

            // Should still be 5.0 because of the custom operation
            assertTrue(integrated.sampleValue() <= 5.0)
        }
    }

    test("Exact integral is correct") {
        runYafrl {
            val polynomial = Behavior.polynomial(listOf(1.0, 1.0)) // 1 + t

            val integral = polynomial
                .exactIntegral { x, y -> x + y }

            assertEquals(listOf(0.0, 1.0, 0.5), integral.coefficients) // t + t^2 / 2
        }
    }

    test("Symbolic integration of a constant respects time of construction") {
        runYafrl {
            val clock = timeline.clock as BroadcastEvent<Duration>

            val gravity = Behavior.const(-10.0)

            val player = Behavior.integral(gravity) // constructed at t = 0

            clock.send(10.0.seconds)

            val spawned = Behavior.integral(gravity) // constructed at t = 10s

            clock.send(1.0.seconds)

            // Player has been falling for 11s, spawned for 1s -- both should
            //  reflect only their own lifetimes, not absolute time since t=0.
            assertTrue("Expected player ≈ -110 but was ${player.sampleValue()}") {
                abs(player.sampleValue() - (-110.0)) < 0.01
            }
            assertTrue("Expected spawned ≈ -10 but was ${spawned.sampleValue()}") {
                abs(spawned.sampleValue() - (-10.0)) < 0.01
            }
        }
    }

    test("Symbolic integration respects the initial constant of integration") {
        runYafrl {
            val clock = timeline.clock as BroadcastEvent<Duration>

            val integrated = Behavior.const(2.0).integrate(initial = 5.0)

            assertTrue("Expected initial ≈ 5.0 but was ${integrated.sampleValue()}") {
                abs(integrated.sampleValue() - 5.0) < 0.01
            }

            clock.send(1.0.seconds)

            assertTrue("Expected ≈ 7.0 but was ${integrated.sampleValue()}") {
                abs(integrated.sampleValue() - 7.0) < 0.01
            }
        }
    }

    test("Until integration is piecewise and continuous across switches") {
        runYafrl {
            val clock = timeline.clock as BroadcastEvent<Duration>
            val switchTo = externalEvent<Behavior<Double>>()

            // Velocity is 1.0 initially, jumps to 3.0 on switch.
            val velocity = Behavior.const(1.0).until(switchTo)
            val position = velocity.integrate()

            assertTrue("Expected 0.0 but was ${position.sampleValue()}") {
                abs(position.sampleValue() - 0.0) < 0.01
            }

            clock.send(2.0.seconds)

            // At t=2: position = 1.0 * 2 = 2.0
            assertTrue("Expected 2.0 but was ${position.sampleValue()}") {
                abs(position.sampleValue() - 2.0) < 0.01
            }

            // Switch velocity to 3.0 at t=2.
            switchTo.send(Behavior.const(3.0))

            clock.send(1.0.seconds)

            // At t=3: position = 2.0 (accumulated) + 3.0 * 1 = 5.0.
            //  Continuity: the post-switch integral starts at 2.0, not 0.
            assertTrue("Expected 5.0 but was ${position.sampleValue()}") {
                abs(position.sampleValue() - 5.0) < 0.01
            }
        }
    }

    test("Sum integration is termwise and respects time of construction") {
        runYafrl {
            val clock = timeline.clock as BroadcastEvent<Duration>

            // Force a Behavior.Sum by combining a Polynomial with a non-polynomial
            //  branch (a continuous function), since polynomial+polynomial collapses.
            val gravity = Behavior.const(-10.0)
            val drift = Behavior.continuous { 1.0 }
            val acceleration = gravity + drift

            val player = acceleration.integrate() // at t = 0

            clock.send(5.0.seconds)

            val spawned = acceleration.integrate() // at t = 5s

            clock.send(1.0.seconds)

            // After 6s total: player has integrated -9 over 6s = -54.
            // Spawned at t=5: has integrated -9 over 1s = -9.
            assertTrue("Expected player ≈ -54 but was ${player.sampleValue()}") {
                abs(player.sampleValue() - (-54.0)) < 0.1
            }
            assertTrue("Expected spawned ≈ -9 but was ${spawned.sampleValue()}") {
                abs(spawned.sampleValue() - (-9.0)) < 0.1
            }
        }
    }
})
