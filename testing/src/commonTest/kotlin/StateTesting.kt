import io.github.yafrl.Signal
import io.github.yafrl.externalEvent
import io.github.yafrl.externalSignal
import io.github.yafrl.timeline.Timeline
import io.github.yafrl.testing.atArbitraryState
import io.github.yafrl.testing.testPropositionHoldsFor
import io.kotest.assertions.throwables.shouldThrow
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

    test("State testing fails") {
        Timeline.initializeTimeline()

        shouldThrow<AssertionError> {
            testPropositionHoldsFor(
                setupState = {
                    val addEvent = externalEvent<Unit>("add")

                    val sum = Signal.fold(0, addEvent) { x, _ -> x + 1 }

                    sum
                },
                proposition = {
                    val lessThanFive by condition { current < 5 }

                    always(lessThanFive)
                }
            )
        }
    }
})