package io.github.yafrl.testing

import io.github.yafrl.EventState
import io.github.yafrl.SampleScope
import io.github.yafrl.Signal
import io.github.yafrl.annotations.FragileYafrlAPI
import io.github.yafrl.sample
import io.github.yafrl.timeline.NodeID
import io.github.yafrl.timeline.debugging.EventLogger
import io.github.yafrl.timeline.Timeline
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.Sample
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.numericDouble
import io.kotest.property.resolution.resolve
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.reflect.typeOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Sane default for an [Arb] that generates clock ticks.
 *
 * Generates values around the times specified for the given [framerate],
 *  with values that can randomly deviate from that average by [delta].
 **/
fun fpsClockGenerator(frameRate: Double = 60.0, delta: Duration = 2.0.milliseconds) = arbitrary {
    val avgFrameDuration = frameRate / 1000.0

    Arb.numericDouble(
        avgFrameDuration - delta.inWholeMilliseconds,
        avgFrameDuration + delta.inWholeMilliseconds
    )
        .bind()
        .milliseconds
}

/**
 * Advances the FRP graph to an arbitrary state in the state space, after
 *  which assertions can be run with [check].
 *
 * Useful for checking simple invariants in an FRP system.
 **/
fun atArbitraryState(
    traceLength: Int = 100,
    rs: RandomSource = RandomSource.default(),
    clockGenerator: Arb<Duration> = fpsClockGenerator(),
    check: SampleScope.() -> Unit
) {
    val timeline = Timeline.currentTimeline()

    repeat(traceLength) {
        randomlyStepStateSpace(rs, clockGenerator, timeline)
    }

    // Run the test
    sample {
        check()
    }
}

/**
 * Performs a random valid action in the current [Timeline], simulating an
 *  arbitrary external action (i.e. events from a [io.github.yafrl.BroadcastEvent],
 *  or state updates from a [io.github.yafrl.BindingSignal].
 *
 * Returns the step that was performed when stepping through the state space.
 **/
@OptIn(FragileYafrlAPI::class)
internal fun randomlyStepStateSpace(
    randomSource: RandomSource,
    clockGenerator: Arb<Duration>,
    timeline: Timeline
): StateSpaceAction {
    val nodes = timeline.externalNodes

    val selected = Random.nextInt(nodes.entries.indices)

    val (kType, node) = nodes.entries.elementAt(selected).value

    if (kType.classifier == EventState::class) {
        val type = kType.arguments.first().type!!

        // Resolve the arbitrary instance from the node type.
        val arbitrary = if (type == typeOf<Duration>()) {
            clockGenerator
        } else {
            resolve(type)
        }

        val sample = arbitrary.sample(randomSource)

        val event = EventState.Fired(sample.value)

        // Update that event with a random value.
        timeline.updateNodeValue(
            node,
            event
        )

        return StateSpaceAction.FireEvent(node.id, sample)
    } else {
        // Resolve the arbitrary instance from the node type.

        val arbitrary = resolve(kType)

        val state = arbitrary.sample(randomSource)

        // Update that state with a random value.
        timeline.updateNodeValue(
            node,
            state.value
        )

        return StateSpaceAction.UpdateValue(node.id, state)
    }
}

sealed class StateSpaceAction {
    data class FireEvent(val nodeID: NodeID, val value: Sample<Any?>) : StateSpaceAction()

    data class UpdateValue(val nodeID: NodeID, val value: Sample<Any?>) : StateSpaceAction()
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
    clockGenerator: Arb<Duration> = fpsClockGenerator(),
    randomSource: RandomSource = RandomSource.default(),
    proposition: LTLSyntax<W>.() -> LTL<W>
) {
    val (numIterations, result) = propositionHoldsFor(
        setupState,
        numIterations,
        maxTraceLength,
        clockGenerator,
        proposition,
        randomSource
    )

    val timeline = Timeline.currentTimeline()

    val formattedStates = (result?.first ?: listOf()).map {
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
        // TODO: Try to shrink the result to find a minimal example here


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
    clockGenerator: Arb<Duration>,
    proposition: LTLSyntax<W>.() -> LTL<W>,
    randomSource: RandomSource
): Pair<Int, Pair<List<W>, List<StateSpaceAction>>?> {
    var iterations = 0
    repeat(numIterations) {
        iterations++

        val timeline = Timeline.initializeTimeline(
            eventLogger = EventLogger.InMemory()
        )

        val state = setupState()

        val stateTrace = mutableListOf<W>()

        val actionTrace = mutableListOf<StateSpaceAction>()

        val iterator = sequence {
            sample {
                // Get initial state.
                stateTrace += state.currentValue()
                yield(state.currentValue())

                while (true) {
                    val action = randomlyStepStateSpace(randomSource, clockGenerator, timeline)
                    actionTrace += action
                    stateTrace += state.currentValue()
                    yield(state.currentValue())
                }
            }
        }.asIterable().iterator()

        val testResult = LTL.evaluate(
            proposition,
            iterator,
            maxTraceLength
        )

        if (testResult == LTLResult.False) return iterations to (stateTrace to actionTrace)
    }

    return iterations to null
}

