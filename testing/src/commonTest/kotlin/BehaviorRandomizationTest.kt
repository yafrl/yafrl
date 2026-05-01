
import io.github.yafrl.Signal
import io.github.yafrl.annotations.FragileYafrlAPI
import io.github.yafrl.behaviors.Behavior
import io.github.yafrl.runYafrl
import io.github.yafrl.sample
import io.github.yafrl.testing.LTLPropositionInvalidated
import io.github.yafrl.testing.arbBehaviorMockProvider
import io.github.yafrl.testing.atArbitraryState
import io.github.yafrl.testing.fpsClockGenerator
import io.github.yafrl.testing.testPropositionHoldsFor
import io.github.yafrl.timeline.BehaviorID
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.RandomSource
import io.kotest.property.Sample
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration

@OptIn(FragileYafrlAPI::class)
class BehaviorRandomizationTest : FunSpec({

    // ---------- Functional tests --------------------------------------------

    test("randomized behavior values reach downstream signals; real lambda is never invoked") {
        // In a lazy timeline Signal.fold only recomputes when currentValue() is read.
        // Read after each tick so every draw is captured.
        val seenValues = mutableListOf<Int>()
        runYafrl {
            timeline.behaviorMockProvider = arbBehaviorMockProvider(
                RandomSource.seeded(1L),
                fpsClockGenerator()
            )
            val temp = Behavior.sampled<Int> {
                error("real sampling lambda must not run under randomizeBehaviors=true")
            }
            val tick = externalEvent<Unit>("tick")
            val signal = Signal.fold(0, tick) { _, _ -> temp.sampleValue() }

            repeat(20) {
                tick.send(Unit)
                seenValues += sample { signal.currentValue() }
            }
        }

        assertEquals(20, seenValues.size, "Should have collected 20 randomized samples")
        assertTrue(seenValues.toSet().size > 1, "Randomized samples should not all be identical")
    }

    test("frame constancy is preserved under randomization") {
        runYafrl {
            timeline.behaviorMockProvider = arbBehaviorMockProvider(
                RandomSource.seeded(2L),
                fpsClockGenerator()
            )
            val b = Behavior.sampled<Int> {
                error("real sampling lambda must not run under randomizeBehaviors=true")
            }
            sample {
                val first = b.sampleValue()
                repeat(10) {
                    b.sampleValue() shouldBe first
                }
            }
        }
    }

    test("default mode (randomizeBehaviors=false) leaves real sampling untouched") {
        var realCalls = 0
        runYafrl {
            // No provider installed -- default behavior.
            val b = Behavior.sampled<Int> {
                realCalls++
                42
            }
            sample {
                b.sampleValue() shouldBe 42
                b.sampleValue() shouldBe 42 // per-frame cache hit
            }
        }
        assertEquals(1, realCalls, "Real sampling lambda should be called exactly once per frame")
    }

    test("atArbitraryState with randomizeBehaviors=true works end-to-end") {
        atArbitraryState(
            traceLength = 50,
            randomSource = RandomSource.seeded(42L),
            randomizeBehaviors = true,
            setupState = {
                val temp = Behavior.sampled<Int> {
                    error("real sampling lambda must not run under randomizeBehaviors=true")
                }
                val tick = externalEvent<Unit>("tick")
                Signal.fold(0, tick) { _, _ -> temp.sampleValue() }
            },
            check = { _ ->
                // Just verifying no exception is thrown
            }
        )
    }

    // ---------- Replay determinism stress tests -----------------------------

    /**
     * Drives a fixed event sequence and captures per-tick behavior values.
     * Reads signal.currentValue() after each tick so the fold recomputes eagerly,
     * matching exactly the pattern used in the testPropositionHoldsFor replay path.
     **/
    fun captureSingleBehaviorTrace(seed: Long, ticks: Int = 50): List<Int> {
        val trace = mutableListOf<Int>()
        runYafrl {
            timeline.behaviorMockProvider = arbBehaviorMockProvider(
                RandomSource.seeded(seed),
                fpsClockGenerator()
            )
            val temp = Behavior.sampled<Int> { error("not allowed") }
            val tick = externalEvent<Unit>("tick")
            val signal = Signal.fold(0, tick) { _, _ -> temp.sampleValue() }

            repeat(ticks) {
                tick.send(Unit)
                trace += sample { signal.currentValue() }
            }
        }
        return trace
    }

    test("replay determinism: single behavior, dense sampling -- same seed produces same trace") {
        val run1 = captureSingleBehaviorTrace(seed = 1234L)
        val run2 = captureSingleBehaviorTrace(seed = 1234L)
        assertEquals(run1, run2, "Replays with the same seed must produce identical behavior traces")
        assertEquals(50, run1.size)
    }

    test("replay determinism: different seeds produce different traces (sanity check)") {
        val run1 = captureSingleBehaviorTrace(seed = 1234L)
        val run2 = captureSingleBehaviorTrace(seed = 5678L)
        assertNotEquals(run1, run2, "Distinct seeds should produce distinct traces")
    }

    data class MixedTrace(
        val ints: List<Int>,
        val doubles: List<Double>,
        val durations: List<Duration>
    )

    fun captureMixedBehaviorTrace(seed: Long, ticks: Int = 30): MixedTrace {
        var ints = mutableListOf<Int>()
        var doubles = mutableListOf<Double>()
        var durations = mutableListOf<Duration>()
        runYafrl {
            timeline.behaviorMockProvider = arbBehaviorMockProvider(
                RandomSource.seeded(seed),
                fpsClockGenerator()
            )
            val intB = Behavior.sampled<Int> { error("not allowed") }
            val doubleB = Behavior.sampled<Double> { error("not allowed") }
            val durB = Behavior.sampled<Duration> { error("not allowed") }

            val tick = externalEvent<Unit>("tick")

            // One fold per type so each type's sequence is captured cleanly.
            val intSig = Signal.fold(0, tick) { _, _ -> intB.sampleValue() }
            val doubleSig = Signal.fold(0.0, tick) { _, _ -> doubleB.sampleValue() }
            val durSig = Signal.fold(Duration.ZERO, tick) { _, _ -> durB.sampleValue() }

            repeat(ticks) {
                tick.send(Unit)
                sample {
                    ints += intSig.currentValue()
                    doubles += doubleSig.currentValue()
                    durations += durSig.currentValue()
                }
            }
        }
        return MixedTrace(ints, doubles, durations)
    }

    test("replay determinism: multiple behaviors, mixed types -- per-behavior traces stable across runs") {
        val run1 = captureMixedBehaviorTrace(seed = 99L)
        val run2 = captureMixedBehaviorTrace(seed = 99L)
        assertEquals(run1.ints, run2.ints, "Int behavior trace must replay identically")
        assertEquals(run1.doubles, run2.doubles, "Double behavior trace must replay identically")
        assertEquals(run1.durations, run2.durations, "Duration behavior trace must replay identically")
        assertEquals(30, run1.ints.size)
    }

    fun captureEventBehaviorInterleaved(seed: Long, ticks: Int = 20): List<Pair<Int, Int>> {
        val trace = mutableListOf<Pair<Int, Int>>()
        runYafrl {
            timeline.behaviorMockProvider = arbBehaviorMockProvider(
                RandomSource.seeded(seed),
                fpsClockGenerator()
            )
            val temp = Behavior.sampled<Int> { error("not allowed") }
            // External event whose payload is a deterministic sequence so we
            // can compare behavior draws across runs independently of event values.
            val tick = externalEvent<Int>("tick")
            val signal = Signal.fold(0 to 0, tick) { _, payload ->
                payload to temp.sampleValue()
            }

            for (i in 0 until ticks) {
                tick.send(i)
                trace += sample { signal.currentValue() }
            }
        }
        return trace
    }

    test("replay determinism: behaviors interleaved with events -- same seed yields identical state trace") {
        val run1 = captureEventBehaviorInterleaved(seed = 7L)
        val run2 = captureEventBehaviorInterleaved(seed = 7L)
        assertEquals(run1, run2)
        assertEquals(20, run1.size)
        // Deterministic payloads; only behavior draws vary across seeds.
        assertEquals((0 until 20).toList(), run1.map { it.first })
    }

    fun captureBranchToggleTrace(seed: Long, toggles: List<Boolean>): List<Int> {
        val trace = mutableListOf<Int>()
        runYafrl {
            timeline.behaviorMockProvider = arbBehaviorMockProvider(
                RandomSource.seeded(seed),
                fpsClockGenerator()
            )
            val branchA = Behavior.sampled<Int> { error("not allowed (A)") }
            val branchB = Behavior.sampled<Int> { error("not allowed (B)") }

            val whichBranch = externalSignal(true, "which")
            val tick = externalEvent<Unit>("tick")

            // Sample from whichever branch is currently selected.
            val signal = Signal.fold(0, tick) { _, _ ->
                if (whichBranch.currentValue()) branchA.sampleValue()
                else branchB.sampleValue()
            }

            for (toggle in toggles) {
                whichBranch.value = toggle
                tick.send(Unit)
                trace += sample { signal.currentValue() }
            }
        }
        return trace
    }

    test("replay determinism: conditional sampling via branch toggle -- same seed yields identical trace") {
        val toggles = listOf(true, true, false, true, false, false, true, false, true, false)
        val run1 = captureBranchToggleTrace(seed = 314L, toggles = toggles)
        val run2 = captureBranchToggleTrace(seed = 314L, toggles = toggles)
        assertEquals(run1, run2)
        assertEquals(toggles.size, run1.size)
    }

    test("end-to-end replay: same seed and action sequence yields identical state traces") {
        // Directly exercises the code path of the testPropositionHoldsFor failure-replay
        // block: construct a fresh runYafrl with the same seed, replay the same explicit
        // actions, and verify the state trace is identical.
        fun stateTrace(seed: Long): List<Int> {
            val trace = mutableListOf<Int>()
            runYafrl {
                timeline.behaviorMockProvider = arbBehaviorMockProvider(
                    RandomSource.seeded(seed),
                    fpsClockGenerator()
                )
                val temp = Behavior.sampled<Int> { error("not allowed") }
                val tick = externalEvent<Unit>("tick")
                val signal = Signal.fold(0, tick) { acc, _ -> acc + temp.sampleValue() }

                trace += sample { signal.currentValue() }
                repeat(15) {
                    tick.send(Unit)
                    trace += sample { signal.currentValue() }
                }
            }
            return trace
        }
        assertEquals(stateTrace(2718L), stateTrace(2718L))
    }

    // ---------- Behavior value shrinking ------------------------------------

    test("behavior values are included in shrink candidates") {
        // A proposition that fails once the running sum of a randomized Int behavior
        // exceeds 100.  The shrinker should reduce both the action sequence length and
        // the individual behavior draws toward the minimum that still triggers failure
        // (i.e. a single tick where the behavior value is exactly 100).
        val ex = shouldThrow<LTLPropositionInvalidated> {
            testPropositionHoldsFor(
                setupState = {
                    val temp = Behavior.sampled<Int> { error("not allowed under test") }
                    val tick = externalEvent<Unit>("tick")
                    Signal.fold(0, tick) { acc, _ -> acc + temp.sampleValue() }
                },
                numIterations = 500,
                maxTraceLength = 20,
                randomSource = RandomSource.seeded(42L),
                randomizeBehaviors = true,
                proposition = {
                    val belowThreshold by condition { current < 100 }
                    always(belowThreshold)
                }
            )
        }

        // Shrinker must produce a non-empty trace.
        assertTrue(ex.actions.isNotEmpty(), "Shrunk trace must be non-empty")

        // The final state must actually violate the proposition.
        assertTrue(ex.states.last() as Int >= 100, "Last state must be >= threshold")

        // Behavior values should have been shrunk: the shrinker should have found a
        // much smaller failing value than typical random Int draws.  Kotest's Int arb
        // shrinks toward 0, so the sum in the failing state should be close to 100.
        // We use a loose upper bound (< 1_000) rather than an exact value because the
        // precise result depends on the Kotest shrink-tree bisection path.
        assertTrue((ex.states.last() as Int) < 1_000,
            "Shrinker should reduce behavior value well below random draw magnitude")
    }

    test("shrinking produces a valid replay: shrunk trace re-executes without error") {
        // Verify that the replay block in testPropositionHoldsFor works end-to-end:
        // catch the error, manually replay the shrunk actions with the recorded
        // behavior values, and confirm the same final state is reproduced.
        val ex = shouldThrow<LTLPropositionInvalidated> {
            testPropositionHoldsFor(
                setupState = {
                    val temp = Behavior.sampled<Int> { error("not allowed") }
                    val tick = externalEvent<Unit>("tick")
                    Signal.fold(0, tick) { acc, _ -> acc + temp.sampleValue() }
                },
                numIterations = 500,
                maxTraceLength = 20,
                randomSource = RandomSource.seeded(99L),
                randomizeBehaviors = true,
                proposition = {
                    val belowThreshold by condition { current < 100 }
                    always(belowThreshold)
                }
            )
        }

        // Replay the exact same shrunk actions with the recorded behavior values.
        val replayedStates = mutableListOf<Int>()
        runYafrl {
            var currentBehaviorValues = emptyMap<BehaviorID, Sample<Any?>>()
            val fallback = arbBehaviorMockProvider(RandomSource.seeded(99L), fpsClockGenerator())
            timeline.behaviorMockProvider = { id, type ->
                currentBehaviorValues[id]?.value ?: fallback(id, type)
            }

            val temp = Behavior.sampled<Int> { error("not allowed") }
            val tick = externalEvent<Unit>("tick")
            val signal = Signal.fold(0, tick) { acc, _ -> acc + temp.sampleValue() }

            replayedStates += sample { signal.currentValue() }
            for (action in ex.actions) {
                currentBehaviorValues = action.behaviorValues
                action.performAction(timeline)
                replayedStates += sample { signal.currentValue() }
            }
        }

        assertEquals(ex.states, replayedStates,
            "Manual replay with recorded behavior values must reproduce the exact state trace")
    }
})
