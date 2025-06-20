import io.github.sintrastes.yafrl.Signal
import io.github.sintrastes.yafrl.annotations.FragileYafrlAPI
import io.github.sintrastes.yafrl.externalEvent
import io.github.sintrastes.yafrl.signal
import io.github.yafrl.testing.testPropositionHoldsFor
import io.kotest.core.spec.style.FunSpec
import kotlin.random.Random

class CountdownTimer(
    val startButton: Button,
    val count: Signal<Int>
) {
    data class Snapshot(
        val buttonClicked: Boolean,
        val cuttonText: String,
        val count: Int
    )

    @OptIn(FragileYafrlAPI::class)
    fun snapshot() = signal {
        Snapshot(
            startButton.clicks.asSignal().bind().isFired(),
            startButton.text.bind(),
            count.bind()
        )
    }

    companion object {
        fun new() = run {
            val buttonClicks = externalEvent<Unit>()

            val button = Button(
                text = Signal.const("Start"),
                clicks = buttonClicks
            )

            // On click, reset the countdown to a random number between 10 and 100.
            val count = Signal.fold(0, buttonClicks) { _, _ ->
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