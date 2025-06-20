package debugging

import io.github.sintrastes.yafrl.externalSignal
import io.github.sintrastes.yafrl.broadcastEvent
import io.github.sintrastes.yafrl.internal.Timeline
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

        Timeline.currentTimeline().rollbackState()

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

        Timeline.currentTimeline().rollbackState()

        assertEquals(1, count.value)

        count.value += 1

        assertEquals(2, count.value)
    }

    test("Time travel continues at previous state for folded events") {
        Timeline.initializeTimeline(
            timeTravel = true
        )

        val clicks = broadcastEvent<Unit>()

        val count = clicks.scan(0) { it, _ -> it + 1 }

        assertEquals(0, count.value)

        clicks.send(Unit)

        assertEquals(1, count.value)

        clicks.send(Unit)

        assertEquals(2, count.value)

        Timeline.currentTimeline().rollbackState()

        assertEquals(1, count.value)

        clicks.send(Unit)

        assertEquals(2, count.value)
    }
})