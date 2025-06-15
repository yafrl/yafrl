import io.github.sintrastes.yafrl.bindingState
import io.github.sintrastes.yafrl.internal.Timeline
import io.github.yafrl.testing.atArbitraryState
import io.kotest.core.spec.style.FunSpec
import kotlin.test.assertTrue

class StateTesting: FunSpec({
    test("Test state testing external states") {
        Timeline.initializeTimeline()

        val state1 = bindingState(1)

        val state2 = bindingState("test")

        val combined = state1.combineWith(state2) { x, y ->
            "$x, $y"
        }

        atArbitraryState {
            assertTrue {
                combined.value.length >= 4
            }
        }
    }
})