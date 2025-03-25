package behaviors

import io.github.sintrastes.yafrl.Behavior
import io.github.sintrastes.yafrl.broadcastEvent
import io.github.sintrastes.yafrl.internal.Timeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class BehaviorTests {
    @Test
    fun `Mapped behavior works as intended`() {
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

    @Test
    fun `Sample retains value until event`() = runTest {
        Timeline.initializeTimeline(this)

        val event = broadcastEvent<Unit>()

        val behavior = Behavior.continuous { time ->
            val seconds = time.inWholeMilliseconds / 1000f

            seconds * 2
        }

        val sampled = behavior.sampleState(event)

        val initial = sampled.value

        withContext(Dispatchers.Default) {
            delay(10.milliseconds)
        }

        assertEquals(initial, sampled.value)

        event.send(Unit)

        assertNotEquals(initial, sampled.value)
    }
}