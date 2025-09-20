package io.github.yafrl.testing

import io.github.yafrl.EventState
import io.github.yafrl.SampleScope
import io.github.yafrl.Signal
import io.github.yafrl.annotations.FragileYafrlAPI
import io.github.yafrl.sample
import io.github.yafrl.timeline.Node
import io.github.yafrl.timeline.NodeID
import io.github.yafrl.timeline.logging.EventLogger
import io.github.yafrl.timeline.Timeline
import io.github.yafrl.timeline.debugging.ExternalAction
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.Sample
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.numericDouble
import io.kotest.property.asSample
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
    randomSource: RandomSource = RandomSource.default(),
    clockGenerator: Arb<Duration> = fpsClockGenerator(),
    check: SampleScope.() -> Unit
) {
    val timeline = Timeline.currentTimeline()

    repeat(traceLength) {
        randomStateSpaceAction(randomSource, clockGenerator, timeline)
            .performAction(timeline)
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
internal fun randomStateSpaceAction(
    randomSource: RandomSource,
    clockGenerator: Arb<Duration>,
    timeline: Timeline
): StateSpaceAction {
    val nodes = timeline.externalNodes

    val selected = if (nodes.size == 1) 0 else Random.nextInt(nodes.entries.indices)

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

        return StateSpaceAction.FireEvent(node.id, sample)
    } else {
        // Resolve the arbitrary instance from the node type.

        val arbitrary = resolve(kType)

        val state = arbitrary.sample(randomSource)

        return StateSpaceAction.UpdateValue(node.id, state)
    }
}

/**
 * Representation of a randomly generated action that can be made in a [Timeline].
 **/
sealed class StateSpaceAction {
    abstract val value: Sample<Any?>

    abstract val nodeID: NodeID

    abstract fun shrink(): List<StateSpaceAction>

    abstract fun performAction(timeline: Timeline)

    data class FireEvent(override val nodeID: NodeID, override val value: Sample<Any?>) : StateSpaceAction() {
        override fun shrink(): List<StateSpaceAction> {
            val shrinks = value.shrinks

            return shrinks.children.value.map { childTree ->
                FireEvent(nodeID = nodeID, value = childTree.asSample())
            }
        }

        @OptIn(FragileYafrlAPI::class)
        override fun performAction(timeline: Timeline) {
            val event = EventState.Fired(value.value)

            val node = timeline.graph.getNode(nodeID)!!

            // Update that event with a random value.
            timeline.updateNodeValue(
                node,
                event
            )
        }
    }

    data class UpdateValue(override val nodeID: NodeID, override val value: Sample<Any?>) : StateSpaceAction() {
        override fun shrink(): List<StateSpaceAction> {
            val shrinks = value.shrinks

            return shrinks.children.value.map { childTree ->
                UpdateValue(nodeID = nodeID, value = childTree.asSample())
            }
        }

        @OptIn(FragileYafrlAPI::class)
        override fun performAction(timeline: Timeline) {
            val node = timeline.graph.getNode(nodeID)!!

            // Update that event with a random value.
            timeline.updateNodeValue(
                node,
                value.value
            )
        }
    }

    companion object {
        /**
         * Utility to convert an [ExternalAction] into a [StateSpaceAction]
         *  so that shrinking can be used on it.
         **/
        fun fromExternalAction(action: ExternalAction): StateSpaceAction {
            // TODO: Currently (because of how Kotest works right now) we cannot do
            //  any recursive shrinking here, since we have no way of getting
            //  Sample<A> from Arb<A> and a pre-existing A.

            return when (action) {
                is ExternalAction.FireEvent -> FireEvent(
                    action.id,
                    Sample(action.value)
                )
                is ExternalAction.UpdateValue -> UpdateValue(
                    action.id,
                    Sample(action.value)
                )
            }
        }
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

    if (result != null) {
        println("Got failing sequence of actions of length ${result.actions.size}, attempting to shrink.")

        val actions = result.actions

        val shrunkActions = shrinkActions(setupState, actions) { actions ->
            val result = LTL.evaluate(
                proposition,
                actions.asIterable().iterator(),
                actions.size
            )

            result >= LTLResult.PresumablyTrue
        } ?: actions

        val shrunkStates = mutableListOf<W>()

        val signal = setupState()
        val timeline = Timeline.currentTimeline()

        shrunkStates += sample { signal.currentValue() }

        for (action in shrunkActions) {
            action.performAction(timeline)
            shrunkStates += sample { signal.currentValue() }
        }

        throw LTLPropositionInvalidated(
            numIterations = numIterations,
            states = shrunkStates,
            origActions = actions,
            actions = shrunkActions
        )
    }
}

class LTLPropositionInvalidated(
    val numIterations: Int,
    val states: List<Any?>,
    val origActions: List<StateSpaceAction>,
    val actions: List<StateSpaceAction>,
) : AssertionError() {
    private val timeline = Timeline.currentTimeline()

    private val formattedStates = states.map {
        "\n   => $it"
    }

    @OptIn(FragileYafrlAPI::class)
    val formattedEvents = listOf("[initial state]") + actions
        .map { event ->
            val label = timeline.externalNodes[event.nodeID]!!.node.toString()

            "\n - " + if (event.value == EventState.Fired(Unit)) {
                label
            } else {
                val formattedArg = if (event.value.value is EventState.Fired<*>) {
                    "${(event.value.value as EventState.Fired<*>).event}"
                } else {
                    event.value.value
                }
                "$label[$formattedArg]"
            }
        }

    val trace = formattedStates
        .zip(formattedEvents) { state, event -> listOf(event, state) }
        .flatten()
        .joinToString("")

    override val message =
        "Proposition invalidated after $numIterations runs and ${actions.size} actions (shrunk from ${origActions.size})" +
            " with the following trace : \n\n - " + trace + "\n\n"
}



data class LTLTestFailure<W>(
    val states: List<W>,
    val actions: List<StateSpaceAction>
)

// Returns trace on failure, null otherwise.
private fun <W> propositionHoldsFor(
    setupState: () -> Signal<W>,
    numIterations: Int,
    maxTraceLength: Int,
    clockGenerator: Arb<Duration>,
    proposition: LTLSyntax<W>.() -> LTL<W>,
    randomSource: RandomSource
): Pair<Int, LTLTestFailure<W>?> {
    var iterations = 0
    repeat(numIterations) {
        iterations++

        val state = setupState()

        val timeline = Timeline.currentTimeline()

        val stateTrace = mutableListOf<W>()

        val actionTrace = mutableListOf<StateSpaceAction>()

        val iterator = sequence {
            sample {
                // Get initial state.
                stateTrace += state.currentValue()
                yield(state.currentValue())

                while (true) {
                    val action = randomStateSpaceAction(randomSource, clockGenerator, timeline)
                    action.performAction(timeline)

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

        if (testResult == LTLResult.False) return iterations to LTLTestFailure(stateTrace, actionTrace)
    }

    return iterations to null
}

