package debugging

import io.github.yafrl.Signal
import io.github.yafrl.behaviors.Behavior
import io.github.yafrl.runYafrl
import io.github.yafrl.sample
import io.github.yafrl.timeline.Timeline
import io.kotest.core.spec.style.FunSpec
import kotlin.random.Random
import kotlin.test.assertEquals

class TimeTravel: FunSpec({
    test("Time travel resets state") {
        runYafrl(timeTravel = true) {

            val count = externalSignal(0)

            assertEquals(0, count.value)

            count.value += 1

            assertEquals(1, count.value)

            count.value += 1

            assertEquals(2, count.value)

            timeline.timeTravel.rollbackState()

            assertEquals(1, count.value)
        }
    }

    test("Time travel continues at previous state for binding state") {
        runYafrl(timeTravel = true) {

            val count = externalSignal(0)

            assertEquals(0, count.value)

            count.value += 1

            assertEquals(1, count.value)

            count.value += 1

            assertEquals(2, count.value)

            timeline.timeTravel.rollbackState()

            assertEquals(1, count.value)

            count.value += 1

            assertEquals(2, count.value)
        }
    }

    test("Time travel continues at previous state for folded events") {
        runYafrl(timeTravel = true) {

            val clicks = externalEvent<Unit>()

            val count = clicks.scan(0) { it, _ -> it + 1 }

            sample {
                assertEquals(0, count.currentValue())

                clicks.send(Unit)

                assertEquals(1, count.currentValue())

                clicks.send(Unit)

                assertEquals(2, count.currentValue())

                timeline.timeTravel.rollbackState()

                assertEquals(1, count.currentValue())

                clicks.send(Unit)

                assertEquals(2, count.currentValue())
            }
        }
    }

    test("Time-travel preserves previous values of behaviors") {
        runYafrl(timeTravel = true) {
            var numSamples = 0

            val random = Behavior.sampled {
                numSamples++
                Random.nextInt()
            }

            val buttonClick = externalEvent<Unit>("button_click")

            val text = Signal.hold(
                "Click to get a number.",
                buttonClick.map {
                    "Your number is: ${random.sampleValue()}"
                }
            )

            buttonClick.send(Unit)

            val initialText = text.currentValue()

            buttonClick.send(Unit)

            timeline.timeTravel.rollbackState()

            val afterReset = text.currentValue()

            assertEquals(initialText, afterReset)

            assertEquals(2, numSamples)
        }
    }
})