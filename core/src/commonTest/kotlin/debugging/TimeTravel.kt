package debugging

import io.github.sintrastes.yafrl.bindingState
import io.github.sintrastes.yafrl.internal.Timeline
import io.kotest.core.spec.style.FunSpec
import kotlin.test.assertEquals

class TimeTravel: FunSpec({
    test("Time travel resets state") {
        Timeline.initializeTimeline(
            timeTravel = true
        )

        val count = bindingState(0)

        assertEquals(0, count.value)

        count.value += 1

        assertEquals(1, count.value)

        count.value += 1

        assertEquals(2, count.value)

        Timeline.currentTimeline().rollbackState()

        assertEquals(1, count.value)
    }
})