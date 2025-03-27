package vectors

import io.github.sintrastes.yafrl.State.Companion.const
import io.github.sintrastes.yafrl.*
import io.github.sintrastes.yafrl.internal.Timeline
import io.github.sintrastes.yafrl.vector.Float2
import io.github.sintrastes.yafrl.vector.Float3
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
            abs(95.1 - position.value.y) <= 0.1
        )
    }
}