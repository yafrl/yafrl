package states

import io.github.sintrastes.yafrl.Signal
import io.github.sintrastes.yafrl.broadcastEvent
import io.github.sintrastes.yafrl.internal.Timeline
import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.assertEquals

class FoldUpdatingTests: FunSpec({
    beforeTest {
        Timeline.initializeTimeline(
            CoroutineScope(Dispatchers.Default)
        )
    }

    test("Fold node updates with new events") {
        val events = broadcastEvent<Int>()

        val counter = Signal.fold(0, events) { state: Int, event: Int ->
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
})