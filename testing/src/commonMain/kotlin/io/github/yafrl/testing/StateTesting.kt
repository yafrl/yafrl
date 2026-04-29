package io.github.yafrl.testing

import io.github.yafrl.EventState
import io.github.yafrl.SampleScope
import io.github.yafrl.Signal
import io.github.yafrl.annotations.FragileYafrlAPI
import io.github.yafrl.runYafrl
import io.github.yafrl.sample
import io.github.yafrl.timeline.BehaviorID
import io.github.yafrl.timeline.Node
import io.github.yafrl.timeline.NodeID
import io.github.yafrl.timeline.logging.EventLogger
import io.github.yafrl.timeline.Timeline
import io.github.yafrl.timeline.TimelineScope
import io.github.yafrl.timeline.debugging.ExternalAction
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.Sample
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.numericDouble
import io.kotest.property.resolution.resolve
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.reflect.KType
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
 * Resolves a Kotest [Arb] for the given [KType], using [clockGenerator] for
 *  [Duration] values. Returns `Arb<Any?>` via unchecked cast.
 **/
@Suppress("UNCHECKED_CAST")
internal fun arbFor(type: KType, clockGenerator: Arb<Duration>): Arb<Any?> = when (type) {
    typeOf<Duration>() -> clockGenerator as Arb<Any?>
    typeOf<Unit>() -> (arbitrary { Unit }) as Arb<Any?>
    else -> resolve(type)
}

/**
 * Builds the [Timeline.behaviorMockProvider] hook used during state-testing
 *  of external behaviors. When installed on a [Timeline], every per-frame
 *  first sample of an [io.github.yafrl.behaviors.Behavior.Sampled] is
 *  produced by resolving a Kotest [Arb] from the behavior's registered
 *  [KType] instead of invoking its real `current` lambda.
 *
 * Does not record draws. Use [BehaviorDrawRecorder] when shrink trees are needed.
 **/
internal fun arbBehaviorMockProvider(
    randomSource: RandomSource,
    clockGenerator: Arb<Duration>
): (BehaviorID, KType) -> Any? = { _, type ->
    arbFor(type, clockGenerator).sample(randomSource).value
}

/**
 * Installs a provider on [timeline] that replays [recorded] behavior values
 *  verbatim, falling back to fresh draws via [fallback] for any behavior not
 *  in the recorded map (e.g. a behavior that was inactive during the original run).
 **/
internal fun replayBehaviorProvider(
    recorded: Map<BehaviorID, Sample<Any?>>,
    fallback: (BehaviorID, KType) -> Any?
): (BehaviorID, KType) -> Any? = { id, type ->
    recorded[id]?.value ?: fallback(id, type)
}

/**
 * Records the [Sample] (value + shrink tree) drawn for each external behavior
 *  during a single frame. Install [provider] on [Timeline.behaviorMockProvider];
 *  call [beginFrame] before each action fires, then [snapshot] after the frame's
 *  state has been read to capture all draws for that frame.
 **/
internal class BehaviorDrawRecorder(
    private val randomSource: RandomSource,
    private val clockGenerator: Arb<Duration>
) {
    private val currentFrameDraws = mutableMapOf<BehaviorID, Sample<Any?>>()

    val provider: (BehaviorID, KType) -> Any? = { id, type ->
        val sample = arbFor(type, clockGenerator).sample(randomSource)
        currentFrameDraws[id] = sample
        sample.value
    }

    fun beginFrame() { currentFrameDraws.clear() }

    fun snapshot(): Map<BehaviorID, Sample<Any?>> = currentFrameDraws.toMap()
}

/**
 * Advances the FRP graph to an arbitrary state in the state space, after
 *  which assertions can be run with [check].
 *
 * Useful for checking simple invariants in an FRP system.
 *
 * If [randomizeBehaviors] is `true`, every external behavior
 *  ([io.github.yafrl.behaviors.Behavior.Companion.sampled]) will return a
 *  random value drawn from a Kotest `Arb` for its registered type instead
 *  of invoking its real sampling lambda. Per-frame constancy is preserved.
 **/
@OptIn(FragileYafrlAPI::class)
fun <T> atArbitraryState(
    traceLength: Int = 100,
    randomSource: RandomSource = RandomSource.default(),
    clockGenerator: Arb<Duration> = fpsClockGenerator(),
    randomizeBehaviors: Boolean = false,
    setupState: TimelineScope.() -> Signal<T>,
    check: (T) -> Unit
) = runYafrl {
    if (randomizeBehaviors) {
        timeline.behaviorMockProvider = arbBehaviorMockProvider(randomSource, clockGenerator)
    }

    val signal = setupState()

    repeat(traceLength) {
        randomStateSpaceAction(randomSource, clockGenerator, timeline)
            .performAction(timeline)
    }

    // Run the test
    check(signal.currentValue())
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
    val allNodes = timeline.externalNodes

    // Only simulate nodes that are currently "active" (have at least one downstream child).
    // Nodes with no children are disabled -- e.g. an inactive branch of a flatMap/switch.
    val activeNodes = allNodes.filter { (nodeId, _) ->
        timeline.graph.getChildrenOf(nodeId).isNotEmpty()
    }
    val nodes = if (activeNodes.isEmpty()) allNodes else activeNodes

    val selected = if (nodes.size == 1) 0 else Random.nextInt(nodes.entries.indices)

    val (kType, node) = nodes.entries.elementAt(selected).value

    if (kType.classifier == EventState::class) {
        val type = kType.arguments.first().type!!

        // Resolve the arbitrary instance from the node type.
        val arbitrary = if (type == typeOf<Duration>()) {
            clockGenerator
        } else if (type == typeOf<Unit>()) {
            arbitrary { Unit }
        } else  {
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
 *
 * [behaviorValues] holds the [Sample] (value + Kotest shrink tree) drawn for each
 *  external behavior during the frame this action fired. It is populated when
 *  [randomizeBehaviors] is `true`; it is empty otherwise.
 *
 * Having the shrink tree lets the shrinker try smaller behavior values alongside
 *  the existing event/signal value shrinking, using the same recursive list-shrinking
 *  strategy.
 **/
sealed class StateSpaceAction {
    abstract val value: Sample<Any?>

    abstract val nodeID: NodeID

    abstract val behaviorValues: Map<BehaviorID, Sample<Any?>>

    abstract fun shrink(): List<StateSpaceAction>

    abstract fun performAction(timeline: Timeline)

    data class FireEvent(
        override val nodeID: NodeID,
        override val value: Sample<Any?>,
        override val behaviorValues: Map<BehaviorID, Sample<Any?>> = emptyMap()
    ) : StateSpaceAction() {
        override fun shrink(): List<StateSpaceAction> {
            // In Kotest 6.x, RTree<A>.value is () -> A (a lazy producer).
            // childTree.asSample() wraps the RTree itself as the Sample value, which is wrong.
            // Call childTree.value() to get the actual shrunken value.
            val valueShrinks = value.shrinks.children.value.map { childTree ->
                copy(value = Sample(childTree.value(), childTree))
            }
            val behaviorShrinks = behaviorValues.flatMap { (id, sample) ->
                sample.shrinks.children.value.map { childTree ->
                    copy(behaviorValues = behaviorValues + (id to Sample(childTree.value(), childTree)))
                }
            }
            return valueShrinks + behaviorShrinks
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

    data class UpdateValue(
        override val nodeID: NodeID,
        override val value: Sample<Any?>,
        override val behaviorValues: Map<BehaviorID, Sample<Any?>> = emptyMap()
    ) : StateSpaceAction() {
        override fun shrink(): List<StateSpaceAction> {
            val valueShrinks = value.shrinks.children.value.map { childTree ->
                copy(value = Sample(childTree.value(), childTree))
            }
            val behaviorShrinks = behaviorValues.flatMap { (id, sample) ->
                sample.shrinks.children.value.map { childTree ->
                    copy(behaviorValues = behaviorValues + (id to Sample(childTree.value(), childTree)))
                }
            }
            return valueShrinks + behaviorShrinks
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
 *
 * If [randomizeBehaviors] is `true`, every external behavior
 *  ([io.github.yafrl.behaviors.Behavior.Companion.sampled]) will return a
 *  random value drawn from a Kotest `Arb` for its registered type instead
 *  of invoking its real sampling lambda. Per-frame constancy is preserved.
 **/
@OptIn(FragileYafrlAPI::class)
fun <W> testPropositionHoldsFor(
    setupState: TimelineScope.() -> Signal<W>,
    numIterations: Int = 100,
    maxTraceLength: Int = 50,
    clockGenerator: Arb<Duration> = fpsClockGenerator(),
    randomSource: RandomSource = RandomSource.default(),
    randomizeBehaviors: Boolean = false,
    proposition: LTLSyntax<W>.() -> LTL<W>
) {
    val (numIterations, result) = propositionHoldsFor(
        setupState,
        numIterations,
        maxTraceLength,
        clockGenerator,
        proposition,
        randomSource,
        randomizeBehaviors
    )

    if (result != null) {
        println("Got failing sequence of actions of length ${result.actions.size}, attempting to shrink.")

        val actions = result.actions

        val shrunkActions = shrinkActions(setupState, actions, randomizeBehaviors, randomSource, clockGenerator) { actions ->
            val result = LTL.evaluate(
                proposition,
                actions.asIterable().iterator(),
                actions.size
            )

            result >= LTLResult.PresumablyTrue
        } ?: actions

        val shrunkStates = mutableListOf<W>()

        runYafrl {
            var currentBehaviorValues: Map<BehaviorID, Sample<Any?>> = emptyMap()
            if (randomizeBehaviors) {
                val fallback = arbBehaviorMockProvider(randomSource, clockGenerator)
                timeline.behaviorMockProvider = { id, type ->
                    currentBehaviorValues[id]?.value ?: fallback(id, type)
                }
            }

            val signal = setupState()

            shrunkStates += sample { signal.currentValue() }

            for (action in shrunkActions) {
                currentBehaviorValues = action.behaviorValues
                action.performAction(timeline)
                shrunkStates += sample { signal.currentValue() }
            }

            throw LTLPropositionInvalidated(
                timeline = timeline,
                numIterations = numIterations,
                states = shrunkStates,
                origActions = actions,
                actions = shrunkActions
            )
        }
    }
}

class LTLPropositionInvalidated(
    private val timeline: Timeline,
    val numIterations: Int,
    val states: List<Any?>,
    val origActions: List<StateSpaceAction>,
    val actions: List<StateSpaceAction>,
) : AssertionError() {
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
@OptIn(FragileYafrlAPI::class)
private fun <W> propositionHoldsFor(
    setupState: TimelineScope.() -> Signal<W>,
    numIterations: Int,
    maxTraceLength: Int,
    clockGenerator: Arb<Duration>,
    proposition: LTLSyntax<W>.() -> LTL<W>,
    randomSource: RandomSource,
    randomizeBehaviors: Boolean
): Pair<Int, LTLTestFailure<W>?> {
    var iterations = 0
    repeat(numIterations) {
        iterations++

        val stateTrace = mutableListOf<W>()

        val actionTrace = mutableListOf<StateSpaceAction>()

        val iterator = runYafrl {
            val recorder = if (randomizeBehaviors) {
                BehaviorDrawRecorder(randomSource, clockGenerator).also {
                    timeline.behaviorMockProvider = it.provider
                }
            } else null

            val state = setupState()

            sequence {
                sample {
                    // Get initial state.
                    stateTrace += state.currentValue()
                    yield(state.currentValue())

                    while (true) {
                        recorder?.beginFrame()

                        val action = randomStateSpaceAction(randomSource, clockGenerator, timeline)
                        action.performAction(timeline)

                        val currentState = state.currentValue()
                        stateTrace += currentState

                        // Capture behavior draws before yielding: state.currentValue() above
                        // triggered the lazy recompute (sampling behaviors), and we must record
                        // them now. If we did this after yield, LTL.evaluate stopping on the
                        // failing state would leave the last action's behavior values unrecorded.
                        val capturedBehaviors = recorder?.snapshot() ?: emptyMap()
                        actionTrace += when (action) {
                            is StateSpaceAction.FireEvent -> action.copy(behaviorValues = capturedBehaviors)
                            is StateSpaceAction.UpdateValue -> action.copy(behaviorValues = capturedBehaviors)
                        }

                        yield(currentState)
                    }
                }
            }.asIterable().iterator()
        }

        val testResult = LTL.evaluate(
            proposition,
            iterator,
            maxTraceLength
        )

        if (testResult == LTLResult.False) {
            return iterations to LTLTestFailure(
                stateTrace,
                actionTrace
            )
        }
    }

    return iterations to null
}

