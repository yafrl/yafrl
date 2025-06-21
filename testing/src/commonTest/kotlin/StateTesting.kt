import io.github.yafrl.externalSignal
import io.github.yafrl.timeline.Timeline
import io.github.yafrl.testing.atArbitraryState
import io.kotest.core.spec.style.FunSpec
import kotlin.test.assertTrue

class StateTesting: FunSpec({
    test("Test state testing external states") {
        Timeline.initializeTimeline()

        val state1 = externalSignal(1)

        val state2 = externalSignal("test")

        val combined = state1.combineWith(state2) { x, y ->
            "$x, $y"
        }

        atArbitraryState {
            assertTrue {
                combined.currentValue().length >= 4
            }
        }
    }
})