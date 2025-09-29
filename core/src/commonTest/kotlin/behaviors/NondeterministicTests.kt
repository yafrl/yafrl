package behaviors

import io.github.yafrl.Signal
import io.github.yafrl.annotations.FragileYafrlAPI
import io.github.yafrl.behaviors.Behavior
import io.github.yafrl.runYafrl
import io.github.yafrl.timeline.BehaviorID
import io.github.yafrl.timeline.NodeID
import io.github.yafrl.timeline.logging.EventLogger
import io.kotest.core.spec.style.FunSpec
import kotlin.random.Random
import kotlin.test.assertEquals

@OptIn(FragileYafrlAPI::class)
class NondeterministicTests : FunSpec({
    test("Test event trace for a nondeterministic behavior when mapping an event") {
        runYafrl(eventLogger = EventLogger.InMemory()) {
            // Example of a button that when pressed shows the user a
            // random number.

            // This showcases how sampled behaviors can introduce controlled randomness
            // into your application.

            val random = Behavior.sampled { Random.nextInt() }

            val buttonClick = externalEvent<Unit>("button_click")

            val text = Signal.hold(
                "Click to get a number.",
                buttonClick.map {
                    "Your number is: ${random.sampleValue()}"
                }
            )

            buttonClick.send(Unit)

            println("After button click")

            // The event trace for this should contain what random value was sampled
            val events = timeline.eventLogger.reportEvents()

            assertEquals(NodeID(0), events[0].externalAction.id)
            assertEquals(Unit, events[0].externalAction.value)
            assertEquals(1, events[0].behaviorsSampled.size)
            assertEquals(BehaviorID(0), events[0].behaviorsSampled.keys.first())
        }
    }
})