package debugging

import io.github.yafrl.externalSignal
import io.github.yafrl.externalEvent
import io.github.yafrl.sample
import io.github.yafrl.timeline.Timeline
import io.kotest.core.spec.style.FunSpec
import kotlin.test.assertEquals

class TimeTravel: FunSpec({
    test("Time travel resets state") {
        Timeline.initializeTimeline(
            timeTravel = true
        )

        val count = externalSignal(0)

        assertEquals(0, count.value)

        count.value += 1

        assertEquals(1, count.value)

        count.value += 1

        assertEquals(2, count.value)

        Timeline.currentTimeline().timeTravel.rollbackState()

        assertEquals(1, count.value)
    }

    test("Time travel continues at previous state for binding state") {
        Timeline.initializeTimeline(
            timeTravel = true
        )

        val count = externalSignal(0)

        assertEquals(0, count.value)

        count.value += 1

        assertEquals(1, count.value)

        count.value += 1

        assertEquals(2, count.value)

        Timeline.currentTimeline().timeTravel.rollbackState()

        assertEquals(1, count.value)

        count.value += 1

        assertEquals(2, count.value)
    }

    test("Time travel continues at previous state for folded events") {
        Timeline.initializeTimeline(
            timeTravel = true
        )

        val clicks = externalEvent<Unit>()

        val count = clicks.scan(0) { it, _ -> it + 1 }

        sample {
            assertEquals(0, count.currentValue())

            clicks.send(Unit)

            assertEquals(1, count.currentValue())

            clicks.send(Unit)

            assertEquals(2, count.currentValue())

            Timeline.currentTimeline().timeTravel.rollbackState()

            assertEquals(1, count.currentValue())

            clicks.send(Unit)

            assertEquals(2, count.currentValue())
        }
    }
})