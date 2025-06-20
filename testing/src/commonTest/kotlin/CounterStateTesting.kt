import io.github.sintrastes.yafrl.Event
import io.github.sintrastes.yafrl.broadcastEvent
import io.github.sintrastes.yafrl.State
import io.github.sintrastes.yafrl.annotations.FragileYafrlAPI
import io.github.sintrastes.yafrl.internal.Timeline
import io.github.sintrastes.yafrl.state
import io.github.yafrl.testing.ConditionScope
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

    fun setupCounter() = run {
        val increment = broadcastEvent<Unit>("increment")
            .map { { count: Int -> count + 1 } }

        val decrement = broadcastEvent<Unit>("decrement")
            .map { { count: Int -> count - 1 } }

        val actions = Event.merged(increment, decrement)

        val counter = State.fold(0, actions) { state, action ->
            action(state)
        }

        state {
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
                val clickedIncrement = condition("clicked_increment") {
                    current.incrementClicked
                }

                val countHasIncremented = condition {
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
                val clickedDecrement = condition("clicked_decrement") {
                    current.decrementClicked
                }

                val countHasDecremented = condition("count_decremented") {
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