
import io.github.yafrl.Signal
import io.github.yafrl.testing.LTLPropositionInvalidated
import io.github.yafrl.testing.atArbitraryState
import io.github.yafrl.testing.testPropositionHoldsFor
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.test.assertTrue

class StateTesting: FunSpec({
    test("Test state testing external states") {
        atArbitraryState(setupState = {
            val state1 = externalSignal(1)

            val state2 = externalSignal("test")

            state1.combineWith(state2) { x, y ->
                "$x, $y"
            }
        }, check = { combined ->
            assertTrue {
                combined.length >= 4
            }
        })
    }

    test("State testing invalid proposition throws AssertionError") {
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

    test("State testing shrinks actions to minimal size") {
        try {
            testPropositionHoldsFor(
                setupState = {
                    val inputEvent = externalEvent<Boolean>("input_event")

                    val sum = Signal.fold(0, inputEvent) { x, b -> if(b) x + 1 else x }

                    sum
                },
                proposition = {
                    val lessThanFive by condition { current < 5 }

                    always(lessThanFive)
                }
            )
        } catch (e: LTLPropositionInvalidated) {
            println("${e.actions.map { it.value.value }}")
            e.actions.size shouldBe 5
        }
    }
})