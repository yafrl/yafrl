package behaviors

import io.github.sintrastes.yafrl.BroadcastEvent
import io.github.sintrastes.yafrl.annotations.ExperimentalYafrlAPI
import io.github.sintrastes.yafrl.asBehavior
import io.github.sintrastes.yafrl.behaviors.Behavior
import io.github.sintrastes.yafrl.behaviors.integrate
import io.github.sintrastes.yafrl.behaviors.plus
import io.github.sintrastes.yafrl.bindingState
import io.github.sintrastes.yafrl.broadcastEvent
import io.github.sintrastes.yafrl.impulse
import io.github.sintrastes.yafrl.internal.Timeline
import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.numericDouble
import io.kotest.property.checkAll
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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

    test("Impulses are maintained under flatmap") {
        Timeline.initializeTimeline()

        val clock = Timeline.currentTimeline().clock as BroadcastEvent

        val switch = bindingState(true)

        val impulseEvent1 = broadcastEvent<Unit>("event1")
        val impulseEvent2 = broadcastEvent<Unit>("event2")

        val impulse1 = impulseEvent1.impulse(0.0, 1.0)
        val impulse2 = impulseEvent2.impulse(0.0, 1.0)

        val behavior = switch.asBehavior()
            .flatMap { switch -> if (switch) impulse1 else impulse2 }

        val integrated = behavior.integrate()

        assertEquals(0.0, integrated.value)

        impulseEvent1.send(Unit)
        clock.send(1.0.milliseconds)

        assertTrue(abs(1.0 - integrated.value) < 0.01)

        impulseEvent2.send(Unit)
        clock.send(1.0.milliseconds)

        assertTrue(abs(2.0 - integrated.value) < 0.01)
    }

    test("Impulses are maintained under map") {
        Timeline.initializeTimeline()

        val clock = Timeline.currentTimeline().clock as BroadcastEvent

        val impulseEvent = broadcastEvent<Unit>()
        val impulse = impulseEvent.impulse(0.0, 1.0)

        val mapped = impulse.map { it * 2 }

        val integrated = mapped.integrate()

        impulseEvent.send(Unit)
        clock.send(1.0.milliseconds)

        assertTrue(abs(2.0 - integrated.value) < 0.01)
    }

    test("Impulses are maintained under transform") {
        Timeline.initializeTimeline()

        val clock = Timeline.currentTimeline().clock as BroadcastEvent

        val impulseEvent = broadcastEvent<Unit>()
        val impulse = impulseEvent.impulse(0.0, 1.0)

        val transformed = impulse.transformTime { it * 2 }

        val integrated = transformed.integrate()

        impulseEvent.send(Unit)
        clock.send(1.0.milliseconds)

        assertTrue(abs(1.0 - integrated.value) < 0.01, "${integrated.value}")
    }

    test("Sampled behavior has no impulses") {
        Timeline.initializeTimeline()

        val sampledBehavior = Behavior.sampled { 1.0 }.integrate()

       assertEquals(0.0, sampledBehavior.value)
    }

    test("Impulses are maintained when summed") {
        Timeline.initializeTimeline()

        val clock = Timeline.currentTimeline().clock as BroadcastEvent

        val impulseEvent1 = broadcastEvent<Unit>("event1")
        val impulseEvent2 = broadcastEvent<Unit>("event2")

        val impulse1 = impulseEvent1.impulse(0.0, 1.0)
        val impulse2 = impulseEvent2.impulse(0.0, 1.0)

        val summed = (impulse1 + impulse2).integrate()

        assertEquals(0.0, summed.value)

        impulseEvent1.send(Unit)
        impulseEvent2.send(Unit)

        clock.send(1.0.milliseconds)

        assertTrue(abs(2.0 - summed.value) < 0.01, "Value was ${summed.value}")
    }

    test("Impulses are maintained when summed with other behaviors.") {
        Timeline.initializeTimeline()

        val clock = Timeline.currentTimeline().clock as BroadcastEvent

        val impulseEvent1 = broadcastEvent<Unit>("event1")

        val impulse = impulseEvent1.impulse(0.0, 1.0)

        val constBehavior = Behavior.const(2.0)

        val behavior = (constBehavior + impulse).integrate()

        impulseEvent1.send(Unit)

        clock.send(1.0.seconds)

        assertTrue(abs(3.0 - behavior.value) < 0.1, "Value was ${behavior.value}")
    }

    test("Sampled behavior has no impulses") {
        Timeline.initializeTimeline()

        val clock = Timeline.currentTimeline().clock as BroadcastEvent

        val sampled = Behavior.sampled { 0.0 }
            .integrate()

        clock.send(1.0.seconds)

        assertEquals(0.0, sampled.value)
    }

    test("Continuous behavior has no impulses") {
        Timeline.initializeTimeline()

        val clock = Timeline.currentTimeline().clock as BroadcastEvent

        val sampled = Behavior.continuous { 0.0 }
            .integrate()

        clock.send(1.0.seconds)

        assertEquals(0.0, sampled.value)
    }
})