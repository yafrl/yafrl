import io.github.sintrastes.yafrl.State
import io.github.sintrastes.yafrl.annotations.FragileYafrlAPI
import io.github.sintrastes.yafrl.broadcastEvent
import io.github.sintrastes.yafrl.state
import io.github.yafrl.testing.testPropositionHoldsFor
import io.kotest.core.spec.style.FunSpec
import kotlin.random.Random

class CountdownTimer(
    val startButton: Button,
    val count: State<Int>
) {
    data class Snapshot(
        val buttonClicked: Boolean,
        val cuttonText: String,
        val count: Int
    )

    @OptIn(FragileYafrlAPI::class)
    fun snapshot() = state {
        Snapshot(
            startButton.clicks.asSignal().bind().isFired(),
            startButton.text.bind(),
            count.bind()
        )
    }

    companion object {
        fun new() = run {
            val buttonClicks = broadcastEvent<Unit>()

            val button = Button(
                text = State.const("Start"),
                clicks = buttonClicks
            )

            // On click, reset the countdown to a random number between 10 and 100.
            val count = State.fold(0, buttonClicks) { _, _ ->
                Random.nextInt(10, 100)
            }

            CountdownTimer(
                button,
                count
            )
        }
    }
}

class CountdownTesting : FunSpec({
    test("Countdown timer spec") {
        testPropositionHoldsFor(
            setupState = { CountdownTimer.new().snapshot() },
            proposition = {
                val lessThanZero = condition { current.count < 0 }

                always(!lessThanZero)
            }
        )
    }
})