package behaviors

import io.github.yafrl.BroadcastEvent
import io.github.yafrl.SampleScope
import io.github.yafrl.annotations.ExperimentalYafrlAPI
import io.github.yafrl.annotations.FragileYafrlAPI
import io.github.yafrl.behaviors.Behavior
import io.github.yafrl.behaviors.plus
import io.github.yafrl.runYafrl
import io.github.yafrl.sample
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlin.random.Random
import kotlin.test.assertNotEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tests that lock in the **per-frame constancy** semantic property:
 *
 * > Within a single frame, every call to `Behavior.sampleValue()` on the same
 * > `Behavior` reference returns the same value.
 *
 * Frames begin when an external event fires, an external signal updates, or
 * the clock advances; until the next such update, the per-frame cache pins
 * the value of every `Behavior.Sampled`, which transitively keeps every
 * composite behavior constant within the frame.
 **/
@OptIn(
    FragileYafrlAPI::class,
    ExperimentalYafrlAPI::class,
    io.kotest.common.ExperimentalKotest::class
)
class PerFrameConstancyTests : FunSpec({

    // ----- Per-constructor unit tests ---------------------------------------

    test("const is constant within a frame") {
        runYafrl {
            val b = Behavior.const(7)
            sample {
                b.sampleValue() shouldBe b.sampleValue()
                b.sampleValue() shouldBe 7
            }
        }
    }

    test("continuous is constant within a frame") {
        runYafrl {
            val b = Behavior.continuous { it.inWholeMilliseconds }
            sample {
                val first = b.sampleValue()
                b.sampleValue() shouldBe first
            }
        }
    }

    test("polynomial is constant within a frame") {
        runYafrl {
            val b = Behavior.polynomial(listOf(1.0, 2.0, 3.0))
            sample {
                val first = b.sampleValue()
                b.sampleValue() shouldBe first
            }
        }
    }

    test("sampled is constant within a frame even with non-deterministic source") {
        runYafrl {
            val b = Behavior.sampled { Random.nextInt() }
            sample {
                val first = b.sampleValue()
                repeat(10) {
                    b.sampleValue() shouldBe first
                }
            }
        }
    }

    test("impulse is constant within a frame") {
        runYafrl {
            val event = externalEvent<Int>()
            val b = event.impulse(0, 1)
            sample {
                b.sampleValue() shouldBe b.sampleValue()
            }
            event.send(42)
            sample {
                b.sampleValue() shouldBe b.sampleValue()
            }
        }
    }

    // ----- Per-combinator unit tests ----------------------------------------

    test("map preserves per-frame constancy with non-deterministic leaf") {
        runYafrl {
            val leaf = Behavior.sampled { Random.nextInt() }
            val mapped = leaf.map { it * 2 }
            sample {
                val first = mapped.sampleValue()
                repeat(10) {
                    mapped.sampleValue() shouldBe first
                }
            }
        }
    }

    test("flatMap preserves per-frame constancy with non-deterministic leaf") {
        runYafrl {
            val outer = Behavior.sampled { Random.nextInt(0, 2) }
            val inner1 = Behavior.sampled { Random.nextInt() }
            val inner2 = Behavior.sampled { Random.nextInt() }
            val flat = outer.flatMap { selector ->
                if (selector == 0) inner1 else inner2
            }
            sample {
                val first = flat.sampleValue()
                repeat(10) {
                    flat.sampleValue() shouldBe first
                }
            }
        }
    }

    test("sum preserves per-frame constancy") {
        runYafrl {
            val a = Behavior.sampled { Random.nextInt() }
            val b = Behavior.sampled { Random.nextInt() }
            val summed = a + b
            sample {
                val first = summed.sampleValue()
                repeat(10) {
                    summed.sampleValue() shouldBe first
                }
            }
        }
    }

    test("until preserves per-frame constancy") {
        runYafrl {
            val next = externalEvent<Behavior<Int>>()
            val initial = Behavior.sampled { Random.nextInt() }
            val b = initial.until(next)
            sample {
                val first = b.sampleValue()
                repeat(10) {
                    b.sampleValue() shouldBe first
                }
            }
        }
    }

    test("switcher preserves per-frame constancy") {
        runYafrl {
            val sig = externalSignal<Behavior<Int>>(Behavior.sampled { Random.nextInt() })
            val b = sig.switcher()
            sample {
                val first = b.sampleValue()
                repeat(10) {
                    b.sampleValue() shouldBe first
                }
            }
        }
    }

    test("asBehavior preserves per-frame constancy") {
        runYafrl {
            val sig = externalSignal(0)
            val b = sig.asBehavior()
            sample {
                val first = b.sampleValue()
                repeat(10) {
                    b.sampleValue() shouldBe first
                }
            }
        }
    }

    test("sampleState preserves per-frame constancy of underlying behavior") {
        runYafrl {
            val leaf = Behavior.sampled { Random.nextInt() }
            // Sample state is a Signal whose current value is captured from the behavior
            // when sampled. Sampling the leaf again in the same frame must agree.
            val signal = leaf.sampleState()
            sample {
                val a = leaf.sampleValue()
                val b = signal.currentValue()
                // The signal's initial value was captured from the same leaf in this frame,
                // so it must equal the cached leaf value.
                a shouldBe b
            }
        }
    }

    // ----- Cross-`sample{}` and cache-reset behaviour -----------------------

    test("constancy holds across distinct sample blocks within one frame") {
        runYafrl {
            val b = Behavior.sampled { Random.nextInt() }
            val first = sample { b.sampleValue() }
            val second = sample { b.sampleValue() }
            first shouldBe second
        }
    }

    test("cache resets across frame boundaries when underlying source has changed") {
        runYafrl {
            val sig = externalSignal(0)
            var counter = 0
            val b = Behavior.sampled { counter++ }

            val first = sample { b.sampleValue() }

            // External signal update -> new frame -> cache reset.
            sig.value = 99

            val second = sample { b.sampleValue() }

            assertNotEquals(first, second)
        }
    }

    test("clock tick creates a frame boundary that resets the cache") {
        runYafrl {
            val clock = timeline.clock as BroadcastEvent<Duration>
            var counter = 0
            val b = Behavior.sampled { counter++ }

            val first = sample { b.sampleValue() }

            clock.send(16.milliseconds)

            val second = sample { b.sampleValue() }

            assertNotEquals(first, second)
        }
    }

    test("repeated samples in same frame call underlying source at most once") {
        runYafrl {
            var calls = 0
            val b = Behavior.sampled { calls++; Random.nextInt() }
            sample {
                repeat(50) { b.sampleValue() }
            }
            calls shouldBe 1
        }
    }

    // ----- Property-based tests ---------------------------------------------

    // The kotest property runner re-runs the test body multiple times with random
    // generators. To make the runs cheap, we keep iteration count modest.
    val propertyConfig = PropTestConfig(iterations = 50)

    /**
     * Builds an arbitrary `Behavior<Int>` tree composed of constructors and
     * combinators. Leaves include a non-deterministic `sampled` source so that
     * any failure of the constancy property would be observable.
     *
     * The generator must be invoked **inside** a `runYafrl { ... }` block so
     * that `Behavior.sampled`, `Behavior.const`, etc. resolve their enclosing
     * `BehaviorScope`. We therefore expose it as a function over a
     * `SampleScope`.
     */
    fun SampleScope.arbBehavior(depth: Int): Arb<Behavior<Int>> {
        val constLeaf: Arb<Behavior<Int>> =
            Arb.int(-1000, 1000).map { Behavior.const(it) }

        val continuousLeaf: Arb<Behavior<Int>> =
            Arb.int(-1000, 1000).map { c -> Behavior.continuous { c } }

        val sampledLeaf: Arb<Behavior<Int>> =
            arbitrary { Behavior.sampled { Random.nextInt() } }

        if (depth <= 0) return Arb.choice(constLeaf, continuousLeaf, sampledLeaf)

        val sub = arbBehavior(depth - 1)

        val mapped: Arb<Behavior<Int>> =
            Arb.bind(sub, Arb.int(-10, 10)) { b, k -> b.map { it + k } }

        val flatMapped: Arb<Behavior<Int>> =
            Arb.bind(sub, sub) { a, b ->
                a.flatMap { x -> if (x % 2 == 0) a else b }
            }

        val summed: Arb<Behavior<Int>> =
            Arb.bind(sub, sub) { a, b -> a + b }

        return Arb.choice(constLeaf, continuousLeaf, sampledLeaf, mapped, flatMapped, summed)
    }

    test("Property A: random Behavior trees are constant within a frame") {
        checkAll(propertyConfig, Arb.int(2..15)) { n ->
            runYafrl {
                val b: Behavior<Int> = arbBehavior(depth = 3).single()
                sample {
                    val values = List(n) { b.sampleValue() }
                    values.distinct().size shouldBe 1
                }
            }
        }
    }

    test("Property B: leaf Sampled is constant across random external actions") {
        checkAll(propertyConfig, Arb.list(Arb.int(0, 2), 5..15)) { actions ->
            runYafrl {
                val sig = externalSignal(0)
                val ev = externalEvent<Int>()
                val leaf = Behavior.sampled { Random.nextInt() }

                for (action in actions) {
                    when (action) {
                        0 -> sig.value = sig.currentValue() + 1
                        1 -> ev.send(action)
                        else -> (timeline.clock as BroadcastEvent<Duration>)
                            .send(16.milliseconds)
                    }
                    sample {
                        val first = leaf.sampleValue()
                        repeat(5) { leaf.sampleValue() shouldBe first }
                    }
                }
            }
        }
    }

    test("Property C: random tree x random external action trace stays constant") {
        checkAll(
            propertyConfig,
            Arb.list(Arb.int(0, 1), 3..8),
            Arb.int(2..8)
        ) { actions, n ->
            runYafrl {
                val sig = externalSignal(0)
                val b: Behavior<Int> = arbBehavior(depth = 2).single()

                for (action in actions) {
                    when (action) {
                        0 -> sig.value = sig.currentValue() + 1
                        else -> (timeline.clock as BroadcastEvent<Duration>)
                            .send(16.milliseconds)
                    }
                    sample {
                        val values = List(n) { b.sampleValue() }
                        values.distinct().size shouldBe 1
                    }
                }
            }
        }
    }
})

/**
 * Small helper to extract a single sample from an `Arb` synchronously.
 *
 * The kotest property API does not let us use the random generator inline
 * outside `checkAll`, but for these tests we only need a few one-off random
 * trees; pulling a single sample with the default RNG is sufficient.
 **/
private fun <A> Arb<A>.single(): A =
    this.sample(io.kotest.property.RandomSource.default()).value
