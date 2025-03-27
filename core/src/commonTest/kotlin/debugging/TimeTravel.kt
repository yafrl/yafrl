package debugging

import io.github.sintrastes.yafrl.bindingState
import io.github.sintrastes.yafrl.internal.Timeline
import kotlin.test.Test
import kotlin.test.assertEquals

class TimeTravel {
    @Test
    fun `Time travel resets state`() {
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
}