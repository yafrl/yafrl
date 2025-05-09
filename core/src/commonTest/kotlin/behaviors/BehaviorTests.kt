package behaviors

import io.github.sintrastes.yafrl.Behavior
import io.github.sintrastes.yafrl.broadcastEvent
import io.github.sintrastes.yafrl.internal.Timeline
import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class BehaviorTests : FunSpec({
    test("Mapped behavior works as intended") {
        Timeline.initializeTimeline(this)

        val behavior = Behavior.continuous { time ->
            val seconds = time.inWholeMilliseconds / 1000f

            sin(seconds)
        }

        val mapped = behavior.map { it * 2 }

        val value = behavior.value
        val mappedValue = mapped.value

        val difference = abs(value * 2 - mappedValue)

        assertTrue("Difference between mapped and unmapped value was $difference") {
            difference < 0.1f
        }
    }

    test("Sample retains value until event") {
        val event = broadcastEvent<Duration>()

        Timeline.initializeTimeline(
            scope = this,
            initClock = {
                event
            }
        )

        val behavior = Behavior.continuous { time ->
            val seconds = time.inWholeMilliseconds / 1000f

            seconds * 2
        }

        val sampled = behavior.sampleState(event)

        val initial = sampled.value

        delay(10.milliseconds)

        assertEquals(initial, sampled.value)

        event.send(0.1.seconds)

        assertNotEquals(initial, sampled.value)
    }
})