package behaviors

import io.github.sintrastes.yafrl.BroadcastEvent
import io.github.sintrastes.yafrl.behaviors.*
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
        Timeline.initializeTimeline(
            scope = this,
            initClock = {
                broadcastEvent<Duration>("event")
            }
        )

        val event = Timeline.currentTimeline().clock as BroadcastEvent<Duration>

        val behavior = Behavior.continuous { time ->
            val seconds = time.inWholeMilliseconds / 1000f

            seconds * 2
        }

        val sampled = behavior.sampleState()

        val initial = sampled.value

        delay(10.milliseconds)

        assertEquals(initial, sampled.value)

        event.send(0.1.seconds)

        assertNotEquals(initial, sampled.value)
    }

    test("Time transformation transforms time") {
        Timeline.initializeTimeline()

        val clock = Timeline.currentTimeline().clock as BroadcastEvent

        val behavior = Behavior.continuous { time -> 2 * time.inWholeSeconds }

        val transformed = behavior
            .transformTime { time -> (3 * time.inWholeMilliseconds).milliseconds }

        assertEquals(0, transformed.value)

        clock.send(1.0.seconds)

        assertEquals(1.0.seconds, Timeline.currentTimeline().time)

        assertEquals(6, transformed.value)
    }

    test("FlatMap switches between behaviors") {
        var selected = true
        var value1 = 0
        var value2 = 1

        val selectedBehavior = Behavior.sampled { selected }

        val behavior1 = Behavior.sampled { value1 }
        val behavior2 = Behavior.sampled { value2 }

        val behavior = selectedBehavior.flatMap { selected ->
            if (selected) behavior1 else behavior2
        }

        assertEquals(0, behavior.value)

        selected = false

        assertEquals(1, behavior.value)
    }

    test("Polynomials sum exactly") {
        val behavior1 = Behavior.polynomial(listOf(1.0,2.0))
        val behavior2 = Behavior.polynomial(listOf(3.0,4.0))
        val behavior3 = Behavior.polynomial(listOf(1.0))

        val summed = behavior1 + behavior2 + behavior3

        assertTrue(summed is Behavior.Polynomial)

        // Test opposite order

        val summed2 = behavior3 + behavior2 + behavior1

        assertTrue(summed2 is Behavior.Polynomial)
    }

    test("Until switches between behaviors") {
        val next = broadcastEvent<Behavior<Int>>()

        val initial = Behavior.continuous { 1 }

        val behavior = initial.until(next)

        assertEquals(1, behavior.value)

        next.send(Behavior.continuous { 2 })

        assertEquals(2, behavior.value)
    }
})