package vectors

import io.github.sintrastes.yafrl.State.Companion.const
import io.github.sintrastes.yafrl.*
import io.github.sintrastes.yafrl.internal.Timeline
import io.github.sintrastes.yafrl.vector.Double2
import io.github.sintrastes.yafrl.vector.Double3
import io.github.sintrastes.yafrl.vector.Float2
import io.github.sintrastes.yafrl.vector.Float3
import io.github.sintrastes.yafrl.vector.ScalarSpace
import io.github.sintrastes.yafrl.vector.VectorSpace
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class VectorTests {
    @Test
    fun `Gravity simulation works`() {
        val clock by lazy { broadcastEvent<Duration>() }

        Timeline.initializeTimeline(
            initClock = {
                clock
            }
        )

        val gravityAcceleration = Float2(0f, -9.81f)

        val initialPosition = Float2(20f, 100f)

        val gravity = const(gravityAcceleration).integrate()

        val position = const(initialPosition) + gravity.integrate()

        assertEquals(
            Float2(20f, 100f),
            position.value
        )

        val samples = 100

        repeat(samples) {
            clock.send((1.0 / samples).seconds)
            position.value
        }

        assertTrue(
            abs(95.1 - position.value.y) <= 0.1
        )
    }

    @Test
    fun `Gravity simulation works (3D)`() {
        val clock by lazy { broadcastEvent<Duration>() }

        Timeline.initializeTimeline(
            initClock = {
                clock
            }
        )

        val gravityAcceleration = Float3(0f, 0f, -9.81f)

        val initialPosition = Float3(20f, 30f, 100f)

        val gravity = const(gravityAcceleration).integrate()

        val position = const(initialPosition) + gravity.integrate()

        assertEquals(
            Float3(20f, 30f, 100f),
            position.value
        )

        val samples = 100

        repeat(samples) {
            clock.send((1.0 / samples).seconds)
            position.value
        }

        assertTrue(
            abs(95.1 - position.value.z) <= 0.1
        )
    }

    @Test
    fun `Double vector arithmetic works`() {
        val x = Double2(1.0, 2.0)

        val y = Double2(3.0, 4.0)

        with (VectorSpace.double2()) {
            val z = 2.0 * x + y

            assertEquals(Double2(5.0, 8.0), z)

            assertEquals(Double2(2.5, 4.0), z / 2.0)
        }
    }

    @Test
    fun `3D Double vector arithmetic works`() {
        val x = Double3(1.0, 2.0, 3.0)

        val y = Double3(4.0, 5.0, 6.0)

        with (VectorSpace.double3()) {
            val z = 2.0 * x + y

            assertEquals(Double3(6.0, 9.0, 12.0), z)

            assertEquals(Double3(2.5, 3.5, 4.5), (x + y) / 2.0)

            assertEquals(Double3(-3.0, -3.0, -3.0), x - y)
        }
    }

    @Test
    fun `Float vector arithmetic works`() {
        val x = 1f
        val y = 2f

        with (ScalarSpace.float()) {
            val z = 2f * x + y

            assertEquals(4f, z)
        }
    }
}