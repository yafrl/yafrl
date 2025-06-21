package states

import io.github.yafrl.Signal
import io.github.yafrl.externalEvent
import io.github.yafrl.runYafrl
import io.github.yafrl.timeline.Timeline
import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.assertEquals

class FoldUpdatingTests: FunSpec({
    test("Fold node updates with new events") {
        runYafrl {
            val events = externalEvent<Int>()

            val counter = Signal.fold(0, events) { state: Int, event: Int ->
                state + event
            }

            assertEquals(0, counter.currentValue())

            // Increment by 1
            events.send(1)

            assertEquals(1, counter.currentValue())

            // Increment by 2
            events.send(2)

            assertEquals(3, counter.currentValue())
        }
    }
})