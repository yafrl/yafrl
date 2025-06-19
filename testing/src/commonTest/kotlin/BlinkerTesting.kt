import io.github.sintrastes.yafrl.BroadcastEvent
import io.github.sintrastes.yafrl.State
import io.github.sintrastes.yafrl.annotations.FragileYafrlAPI
import io.github.sintrastes.yafrl.asBehavior
import io.github.sintrastes.yafrl.broadcastEvent
import io.github.sintrastes.yafrl.internal.Timeline
import io.github.yafrl.testing.testPropositionHoldsFor
import io.kotest.core.spec.style.FunSpec

data class Button(
    val text: State<String>,
    val clicks: BroadcastEvent<Unit>
)

data class Blinker(
    val button: Button,
    val lightOn: State<Boolean>
) {
    companion object {
        fun new() = run {
            val buttonClicks = broadcastEvent<Unit>()

            val isBlinking = State.fold(false, buttonClicks) { state, _ -> !state }

            val buttonText = isBlinking.map { if (true) "Disable" else "Enable" }

            val toggleButton = Button(
                buttonText,
                buttonClicks
            )

            val clock = Timeline.currentTimeline().clock

            // The light should toggle when it is blinking
            val toggleBlink = clock
                .gate(isBlinking.asBehavior())
                .map { { lightOn: Boolean -> !lightOn} }

            val lightOn = State.fold(false, toggleBlink) { lightOn, _dt ->
                !lightOn
            }

            Blinker(
                toggleButton,
                lightOn
            )
        }
    }

    @OptIn(FragileYafrlAPI::class)
    fun snapshot() = run {
        button.text.combineWith(
            button.clicks.asSignal().map { it.isFired() },
            lightOn
        ) {  text, clicked, lightOn ->
            BlinkerState(text, clicked, lightOn)
        }
    }
}

data class BlinkerState(
    val buttonText: String,
    val buttonClicked: Boolean,
    val lightOn: Boolean
)

class BlinkerTesting : FunSpec ({
    test("Blinker specification") {
        testPropositionHoldsFor(
            setupState = {
                Blinker.new()
                    .snapshot()
            },
            proposition = {
                val paused = run {
                    val showingEnable = condition { current.buttonText == "Enable" }
                    val lightOff = condition { current.lightOn == false }

                    showingEnable and lightOff
                }

                val blinking = run {
                    val showingDisable = condition { current.buttonText == "Disable" }

                    val lightOn = condition { current.lightOn }
                    val lightOff = condition { !current.lightOn }

                    val isBlinking = (lightOn and immediately(lightOff)) or
                            (lightOff and immediately(lightOn))

                    showingDisable and (paused releases isBlinking)
                }

                always(
                    paused or blinking
                )
            }
        )
    }
})