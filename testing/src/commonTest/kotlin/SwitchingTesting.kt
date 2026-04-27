
import io.github.yafrl.annotations.FragileYafrlAPI
import io.github.yafrl.runYafrl
import io.github.yafrl.testing.fpsClockGenerator
import io.github.yafrl.testing.randomStateSpaceAction
import io.kotest.core.spec.style.FunSpec
import io.kotest.property.RandomSource
import kotlin.test.assertTrue

@OptIn(FragileYafrlAPI::class)
class SwitchingTesting : FunSpec({
    test("Disabled nodes (signal switching): inactive flatMap branch is never selected") {
        runYafrl {
            val use = externalSignal(true)
            val state1 = externalSignal(0)
            val state2 = externalSignal(0)

            // state2 disabled when use=true; state1 disabled when use=false
            use.flatMap { b -> if (b) state1 else state2 }

            val randomSource = RandomSource.default()
            val clockGen = fpsClockGenerator()

            repeat(200) {
                val action = randomStateSpaceAction(randomSource, clockGen, timeline)
                val children = timeline.graph.getChildrenOf(action.nodeID)
                assertTrue(
                    children.isNotEmpty(),
                    "Node ${action.nodeID} has no children but was selected for simulation"
                )
                action.performAction(timeline)
            }
        }
    }

    test("Disabled nodes (event switching): inactive switchMap branch is never selected") {
        runYafrl {
            val use = externalSignal(true)
            val event1 = externalEvent<Unit>("event1")
            val event2 = externalEvent<Unit>("event2")

            // event2 disabled when use=true; event1 disabled when use=false
            use.switchMap { b -> if (b) event1 else event2 }

            val randomSource = RandomSource.default()
            val clockGen = fpsClockGenerator()

            repeat(200) {
                val action = randomStateSpaceAction(randomSource, clockGen, timeline)
                val children = timeline.graph.getChildrenOf(action.nodeID)
                assertTrue(
                    children.isNotEmpty(),
                    "Node ${action.nodeID} has no children but was selected for simulation"
                )
                action.performAction(timeline)
            }
        }
    }
})
