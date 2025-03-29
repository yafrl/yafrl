package io.github.yafrl.testing

import io.github.sintrastes.yafrl.EventState
import io.github.sintrastes.yafrl.annotations.FragileYafrlAPI
import io.github.sintrastes.yafrl.internal.Timeline
import io.kotest.property.arbitrary.next
import io.kotest.property.resolution.resolve
import kotlin.random.Random
import kotlin.random.nextInt

/**
 * Advances the FRP graph to an arbitrary state in the state space.
 **/
@OptIn(FragileYafrlAPI::class)
fun atArbitraryState(
    maxSteps: Int = 100,
    check: () -> Unit
) {
    val timeline = Timeline.currentTimeline()

    repeat(maxSteps) {
        val nodes = timeline.externalNodes

        val selected = Random.nextInt(nodes.entries.indices)

        val (kType, node) = nodes.entries.elementAt(selected).value

        if (kType.classifier == EventState::class) {
            println("Got event of type: ${kType.arguments.first().type}")

            // Resolve the arbitrary instance from the node type.
            val arbitrary = resolve(kType.arguments.first().type!!)

            val event = EventState.Fired(arbitrary.next())

            // Update that event with a random value.
            timeline.updateNodeValue(
                node,
                event
            )
        } else {
            // Resolve the arbitrary instance from the node type.

            val arbitrary = resolve(kType)

            val state = arbitrary.next()

            // Update that state with a random value.
            timeline.updateNodeValue(
                node,
                state
            )
        }


    }

    // Run the test
    check()
}

