import io.github.sintrastes.yafrl.BroadcastEvent
import io.github.sintrastes.yafrl.Event
import io.github.sintrastes.yafrl.State
import io.github.sintrastes.yafrl.behaviors.not
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
            val buttonClicks = broadcastEvent<Unit>("button_click")

            val isBlinking = State.fold(false, buttonClicks) { state, _ -> !state }

            val buttonText = isBlinking.map { if (it) "Disable" else "Enable" }

            val toggleButton = Button(
                buttonText,
                buttonClicks
            )

            val clock = Timeline.currentTimeline().clock

            // The light should toggle when it is blinking
            val toggleBlink = clock
                .gate(!isBlinking.asBehavior())
                .map { { lightOn: Boolean -> !lightOn} }

            // The light should toggle back off when done blinking.
            val revertToOff = isBlinking.updated().filter { !it }
                .map { { lightOn: Boolean -> false } }

            val toggleActions = Event.merged(toggleBlink, revertToOff)

            val lightOn = State.fold(false, toggleActions) { state, action -> action(state) }

            Blinker(
                toggleButton,
                lightOn
            )
        }
    }

    @OptIn(FragileYafrlAPI::class)
    fun snapshot() = run {
        button.text.combineWith(
            button.clicks.asSignal(),
            lightOn
        ) {  text, clicked, lightOn ->
            BlinkerState(text, clicked.isFired(), lightOn)
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
            setupState = { Blinker.new().snapshot() },
            proposition = {
                val paused = run {
                    val showingEnable = condition("showing_enable") { current.buttonText == "Enable" }
                    val lightOff = condition("light_off") { current.lightOn == false }

                    showingEnable and lightOff
                }

                val blinking = run {
                    val showingDisable = condition("showing_disable") { current.buttonText == "Disable" }

                    val lightOn = condition("light_on") { current.lightOn }
                    val lightOff = condition("light_off") { !current.lightOn }

                    val isBlinking = (lightOn and next(lightOff)) or
                            (lightOff and next(lightOn))

                    showingDisable and (next(paused) releases isBlinking)
                }

                always(paused or blinking)
            }
        )
    }
})