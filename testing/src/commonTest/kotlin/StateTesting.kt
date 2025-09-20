import io.github.yafrl.Signal
import io.github.yafrl.externalEvent
import io.github.yafrl.externalSignal
import io.github.yafrl.testing.LTLPropositionInvalidated
import io.github.yafrl.timeline.Timeline
import io.github.yafrl.testing.atArbitraryState
import io.github.yafrl.testing.testPropositionHoldsFor
import io.github.yafrl.timeline.logging.EventLogger
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
                    Timeline.initializeTimeline()

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
        Timeline.initializeTimeline(
            eventLogger = EventLogger.InMemory()
        )

        try {
            testPropositionHoldsFor(
                setupState = {
                    Timeline.initializeTimeline(
                        eventLogger = EventLogger.InMemory()
                    )

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