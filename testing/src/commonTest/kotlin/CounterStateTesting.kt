import io.github.yafrl.Event
import io.github.yafrl.externalEvent
import io.github.yafrl.Signal
import io.github.yafrl.annotations.FragileYafrlAPI
import io.github.yafrl.timeline.Timeline
import io.github.yafrl.signal
import io.github.yafrl.testing.atArbitraryState
import io.github.yafrl.testing.testPropositionHoldsFor
import io.kotest.core.spec.style.FunSpec
import kotlin.test.assertTrue

data class CounterTestState(
    val incrementClicked: Boolean,
    val decrementClicked: Boolean,
    val count: Int
)

@OptIn(FragileYafrlAPI::class)
class CounterStateTesting: FunSpec({
    test("Test state-testing a counter") {
        Timeline.initializeTimeline()

        val clicks = externalEvent<Unit>()

        val counter = Signal.fold(0, clicks) { state, _ ->
            state + 1
        }

        atArbitraryState {
            assertTrue {
                counter.currentValue() >= 0
            }
        }
    }

    fun setupCounter() = run {
        val increment = externalEvent<Unit>("increment")
            .map { { count: Int -> count + 1 } }

        val decrement = externalEvent<Unit>("decrement")
            .map { { count: Int -> count - 1 } }

        val actions = Event.merged(increment, decrement)

        val counter = Signal.fold(0, actions) { state, action ->
            action(state)
        }

        signal {
            CounterTestState(
                increment.asSignal().bind().isFired(),
                decrement.asSignal().bind().isFired(),
                counter.bind()
            )
        }
    }

    test("Incrementing counter always works") {
        testPropositionHoldsFor(
            setupState = ::setupCounter,
            proposition = {
                val clickedIncrement by condition {
                    current.incrementClicked
                }

                val countHasIncremented by condition {
                    previous == null || current.count ==
                            previous!!.count + 1
                }

                always(
                    clickedIncrement implies
                        countHasIncremented
                )
            }
        )
    }

    test("Decrementing counter always works") {
        testPropositionHoldsFor(
            setupState = ::setupCounter,
            proposition = {
                val clickedDecrement by condition {
                    current.decrementClicked
                }

                val countHasDecremented by condition {
                    previous == null || current.count ==
                            previous!!.count - 1
                }

                always(
                    clickedDecrement implies
                        countHasDecremented
                )
            }
        )
    }
})