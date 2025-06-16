package io.github.yafrl.testing

import io.github.sintrastes.yafrl.EventState
import io.github.sintrastes.yafrl.State
import io.github.sintrastes.yafrl.annotations.FragileYafrlAPI
import io.github.sintrastes.yafrl.internal.Timeline
import io.kotest.property.arbitrary.next
import io.kotest.property.resolution.resolve
import kotlin.random.Random
import kotlin.random.nextInt

/**
 * Advances the FRP graph to an arbitrary state in the state space, after
 *  which assertions can be run with [check].
 *
 * Useful for checking simple invariants in an FRP system.
 **/
fun atArbitraryState(
    traceLength: Int = 100,
    check: () -> Unit
) {
    val timeline = Timeline.currentTimeline()

    repeat(traceLength) {
        randomlyStepStateSpace(timeline)
    }

    // Run the test
    check()
}

/**
 * Performs a random valid action in the current [Timeline], simulating an
 *  arbitrary external action (i.e. events from a [io.github.sintrastes.yafrl.BroadcastEvent],
 *  or state updates from a [io.github.sintrastes.yafrl.BindingState].
 **/
@OptIn(FragileYafrlAPI::class)
internal fun randomlyStepStateSpace(timeline: Timeline) {
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

/**
 * Randomly execute actions in the state graph, testing a linear temporal logic
 *  proposition [proposition] against the state returned by [setupState].
 *
 * Will run [numIterations] total, between which [setupState] and the timeline
 *  will be re-initialized.
 *
 * For each trace, at most [maxTraceLength] actions in the state graph will be
 *  taken.
 **/
fun <W> propositionHoldsFor(
    setupState: () -> State<W>,
    numIterations: Int = 100,
    maxTraceLength: Int = 50,
    proposition: LTLSyntax<W>.() -> LTL<W>
): LTLResult {
    var result = LTLResult.True

    repeat(numIterations) {
        val timeline = Timeline.initializeTimeline()

        val state = setupState()

        val iterator = object: Iterator<W> {
            override fun next(): W {
                randomlyStepStateSpace(timeline)
                return state.value
            }

            override fun hasNext(): Boolean = true
        }

        val testResult = LTL.evaluate(
            proposition,
            iterator,
            maxTraceLength
        )

        result = result and testResult

        if (result == LTLResult.False) return LTLResult.False
    }

    return result
}

