package states

import io.github.sintrastes.yafrl.State
import io.github.sintrastes.yafrl.broadcastEvent
import io.github.sintrastes.yafrl.internal.Timeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FoldUpdatingTests {
    @BeforeTest
    fun `init timeline`() {
        Timeline.initializeTimeline(
            CoroutineScope(Dispatchers.Default)
        )
    }

    @Test
    fun `Fold node updates with new events`() {
        val events = broadcastEvent<Int>()

        val counter = State.fold(0, events) { state: Int, event: Int ->
            state + event
        }

        assertEquals(0, counter.value)

        // Increment by 1
        events.send(1)

        assertEquals(1, counter.value)

        // Increment by 2
        events.send(2)

        assertEquals(3, counter.value)
    }
}