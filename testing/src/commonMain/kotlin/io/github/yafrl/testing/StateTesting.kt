package io.github.yafrl.testing

import io.github.sintrastes.yafrl.EventState
import io.github.sintrastes.yafrl.SampleScope
import io.github.sintrastes.yafrl.Signal
import io.github.sintrastes.yafrl.annotations.FragileYafrlAPI
import io.github.sintrastes.yafrl.sample
import io.github.sintrastes.yafrl.timeline.EventLogger
import io.github.sintrastes.yafrl.timeline.Timeline
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.next
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
    clockGenerator: Arb<Duration> = fpsClockGenerator(),
    check: SampleScope.() -> Unit
) {
    val timeline = Timeline.currentTimeline()

    repeat(traceLength) {
        randomlyStepStateSpace(clockGenerator, timeline)
    }

    // Run the test
    sample {
        check()
    }
}

/**
 * Performs a random valid action in the current [Timeline], simulating an
 *  arbitrary external action (i.e. events from a [io.github.sintrastes.yafrl.BroadcastEvent],
 *  or state updates from a [io.github.sintrastes.yafrl.BindingSignal].
 **/
@OptIn(FragileYafrlAPI::class)
internal fun randomlyStepStateSpace(
    clockGenerator: Arb<Duration>,
    timeline: Timeline
) {
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
    clockGenerator: Arb<Duration> = fpsClockGenerator(),
    proposition: LTLSyntax<W>.() -> LTL<W>
) {
    val (numIterations, result) = propositionHoldsFor(
        setupState,
        numIterations,
        maxTraceLength,
        clockGenerator,
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
    clockGenerator: Arb<Duration>,
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
            sample {
                // Get initial state.
                trace += state.currentValue()
                yield(state.currentValue())

                while (true) {
                    randomlyStepStateSpace(clockGenerator, timeline)
                    trace += state.currentValue()
                    yield(state.currentValue())
                }
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

