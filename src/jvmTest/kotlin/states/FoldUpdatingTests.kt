package states

import io.github.sintrastes.yafrl.State
import io.github.sintrastes.yafrl.broadcastEvent
import io.github.sintrastes.yafrl.internal.newTimeline
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class FoldUpdatingTests {
    @Test
    fun `Fold node updates with new events`() {
        runBlocking(newTimeline()) {
            val events = broadcastEvent<Int>()

            val counter = State.fold(0, events) { state: Int, event: Int ->
                state + event
            }

            assertEquals(0, counter.current())

            // Increment by 1
            events.emit(1)

            assertEquals(1, counter.current())

            // Increment by 2
            events.emit(2)

            assertEquals(3, counter.current())
        }
    }
}