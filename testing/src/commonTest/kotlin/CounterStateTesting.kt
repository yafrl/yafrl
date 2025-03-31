import io.github.sintrastes.yafrl.broadcastEvent
import io.github.sintrastes.yafrl.State
import io.github.sintrastes.yafrl.internal.Timeline
import io.github.yafrl.testing.atArbitraryState
import io.kotest.core.spec.style.FunSpec
import kotlin.test.Test
import kotlin.test.assertTrue

class CounterStateTesting: FunSpec({
    test("Test state-testing a counter") {
        Timeline.initializeTimeline()

        val clicks = broadcastEvent<Unit>()

        val counter = State.fold(0, clicks) { state, _ ->
            state + 1
        }

        atArbitraryState {
            assertTrue {
                counter.value >= 0
            }
        }
    }
})