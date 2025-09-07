import io.github.yafrl.Signal
import io.github.yafrl.externalEvent
import io.github.yafrl.externalSignal
import io.github.yafrl.testing.LTLPropositionInvalidated
import io.github.yafrl.timeline.Timeline
import io.github.yafrl.testing.atArbitraryState
import io.github.yafrl.testing.testPropositionHoldsFor
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
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

    test("State testing invalid proposition throws AssertionError") {
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

    // TODO: This test is not the best way to test our shrinking algorithm,
    //  as the action length is already 5 before shrinking.
    test("State testing shrinks actions to minimal size") {
        Timeline.initializeTimeline()

        try {
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
        } catch (e: LTLPropositionInvalidated) {
            e.actions.size shouldBe 5
        }
    }
})