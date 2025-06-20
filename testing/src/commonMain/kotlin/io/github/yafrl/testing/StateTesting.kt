package io.github.yafrl.testing

import io.github.sintrastes.yafrl.EventState
import io.github.sintrastes.yafrl.Signal
import io.github.sintrastes.yafrl.annotations.FragileYafrlAPI
import io.github.sintrastes.yafrl.internal.EventLogger
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
 *  or state updates from a [io.github.sintrastes.yafrl.BindingSignal].
 **/
@OptIn(FragileYafrlAPI::class)
internal fun randomlyStepStateSpace(timeline: Timeline) {
    val nodes = timeline.externalNodes

    val selected = Random.nextInt(nodes.entries.indices)

    val (kType, node) = nodes.entries.elementAt(selected).value

    if (kType.classifier == EventState::class) {
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
@OptIn(FragileYafrlAPI::class)
fun <W> testPropositionHoldsFor(
    setupState: () -> Signal<W>,
    numIterations: Int = 100,
    maxTraceLength: Int = 50,
    proposition: LTLSyntax<W>.() -> LTL<W>
) {
    val (numIterations, result) = propositionHoldsFor(
        setupState,
        numIterations,
        maxTraceLength,
        proposition
    )

    val timeline = Timeline.currentTimeline()

    val formattedStates = (result ?: listOf()).map {
        "\n   => $it"
    }

    val formattedEvents = listOf("[initial state]") + timeline
        .reportEvents()
        .map { event ->
            val label = timeline.externalNodes[event.id]!!.node.toString()

            "\n - " + if (event.value == EventState.Fired(Unit)) {
                label
            } else {
                val formattedArg = if (event.value is EventState.Fired<*>) {
                    "${(event.value as EventState.Fired<*>).event}"
                } else {
                    event.value
                }
                "$label[$formattedArg]"
            }
        }

    val trace = formattedStates
        .zip(formattedEvents) { state, event -> listOf(event, state) }
        .flatten()
        .joinToString("")

    if (result != null) {
        throw IllegalStateException(
            "Proposition invalidated after ${numIterations} runs, " +
                    "with the following trace: \n\n - " + trace + "\n\n"
        )
    }
}

// Returns trace on failure, null otherwise.
private fun <W> propositionHoldsFor(
    setupState: () -> Signal<W>,
    numIterations: Int,
    maxTraceLength: Int,
    proposition: LTLSyntax<W>.() -> LTL<W>
): Pair<Int, List<W>?> {
    var iterations = 0
    repeat(numIterations) {
        iterations++

        val timeline = Timeline.initializeTimeline(
            eventLogger = EventLogger.InMemory()
        )

        val state = setupState()

        val trace = mutableListOf<W>()

        val iterator = sequence {
            // Get initial state.
            trace += state.value
            yield(state.value)

            while (true) {
                randomlyStepStateSpace(timeline)
                trace += state.value
                yield(state.value)
            }
        }.asIterable().iterator()

        val testResult = LTL.evaluate(
            proposition,
            iterator,
            maxTraceLength
        )

        if (testResult == LTLResult.False) return iterations to trace
    }

    return iterations to null
}

