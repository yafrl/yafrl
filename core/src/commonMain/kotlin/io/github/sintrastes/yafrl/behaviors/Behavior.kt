@file:OptIn(ExperimentalYafrlAPI::class)

package io.github.sintrastes.yafrl.behaviors

import io.github.sintrastes.yafrl.Event
import io.github.sintrastes.yafrl.EventState
import io.github.sintrastes.yafrl.State
import io.github.sintrastes.yafrl.annotations.ExperimentalYafrlAPI
import io.github.sintrastes.yafrl.annotations.FragileYafrlAPI
import io.github.sintrastes.yafrl.internal.Timeline
import io.github.sintrastes.yafrl.internalBindingState
import io.github.sintrastes.yafrl.vector.Float2
import io.github.sintrastes.yafrl.vector.Float3
import io.github.sintrastes.yafrl.vector.VectorSpace
import kotlin.jvm.JvmName
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A behavior is a value of type [A] whose value varies over time.
 *
 * It can be thought of from a denotational perspective as a function
 *  `(Time) -> A`.
 *
 * Visually, you can think of a [Behavior] as a graph where the x-axis
 *  represents time, the y-axis represents different values of [A], and
 *  for each time value there is a different value of [A], for example:
 *
 *  ```
 *  ^
 *  |   *       **
 *  |  * **       *
 *  | *    *   *   *
 *  |       ***      ***
 *  -------------------->
 *  ```
 *
 * [Behavior]s have no other restrictions, and can be either continuous or discrete
 *  functions of time.
 **/
sealed interface Behavior<out A> {
    /** Calculates the value at the specified time. */
    fun sampleValue(time: Duration): A

    /**
     * Used to support dirac impulses in behaviors.
     *
     * `measureImpulses` returns the part of the integral of the behavior
     *   belonging to dirac impulses in the range [time, time + dt], and should be used
     *   in the implementation of the integral of behaviors.
     **/
    @ExperimentalYafrlAPI
    fun measureImpulses(time: Duration, dt: Duration): A

    val value: A
        get() = sampleValue(Timeline.currentTimeline().time)

    /**
     * Apply a function to the time used to sample the behavior.
     **/
    fun transformTime(transform: (Duration) -> Duration): Behavior<A> {
        return Transformed(transform, this)
    }

    /** Implementation of a behavior to which a time transformation has been applied. */
    private class Transformed<A>(
        private val transformation: (Duration) -> Duration,
        private val behavior: Behavior<A>
    ) : Behavior<A> {
        override fun sampleValue(time: Duration): A {
            return behavior.sampleValue(transformation(time))
        }

        override fun measureImpulses(time: Duration, dt: Duration): A {
            return behavior.measureImpulses(transformation(time), dt)
        }

        override fun toString() = "Transformed($behavior)"
    }

    /**
     * Constructs a new behavior whose values are equal to those of this behavior
     *  up until [event] is fired, at which point the behavior's value will switch
     *  to match the values of the [event]'s inner behavior.
     *
     * The underlying behavior will switch every time [event] fires.
     *
     * See also [switcher].
     **/
    fun until(event: Event<Behavior<@UnsafeVariance A>>): Behavior<A> {
        return Until(lazy { this }, event)
    }

    /**
     * Sample a [Behavior] at the occurrences of [times],
     *  returning a new [Event] with the values of the behavior
     *  at those times.
     *
     * Useful for producing a sampled version of a continuous behavior.
     *
     * Example:
     *
     * ```
     * val signal: Behavior<Double> = ...
     *
     * val sampled = signal
     *     .sample(Event.tick(1.second))
     * ```
     *
     * Compare with [tag](https://hackage.haskell.org/package/reflex-0.9.3.3/docs/Reflex-Class.html#v:tag) from Reflex.
     **/
    fun sample(
        times: Event<Any?> = Timeline.currentTimeline().clock
    ): Event<A> {
        return times.map { value }
    }

    fun <B> map(f: (A) -> B): Behavior<B> {
        return Mapped(this, f)
    }

    fun <B> flatMap(f: (A) -> Behavior<B>): Behavior<B> {
        return Flattened(Mapped(this, f))
    }

    /** Implementation of a mapped behavior. */
    private class Mapped<A, B>(
        private val original: Behavior<A>,
        private val f: (A) -> B
    ) : Behavior<B> {
        override fun sampleValue(time: Duration): B {
            return f(original.sampleValue(time))
        }

        override fun measureImpulses(time: Duration, dt: Duration): B {
            return f(original.measureImpulses(time, dt))
        }

        override fun toString() = "Mapped($original)"
    }

    /** Implementation of a flattened behavior. */
    private class Flattened<A>(
        private val original: Behavior<Behavior<A>>
    ) : Behavior<A> {
        override fun sampleValue(time: Duration): A {
            return original
                .sampleValue(time)
                .sampleValue(time)
        }

        override fun measureImpulses(time: Duration, dt: Duration): A {
            return original
                .sampleValue(time)
                .measureImpulses(time, dt)
        }
    }

    /**
     * Sample a [Behavior] to produce a [State].
     *
     * See [sample].
     **/
    fun sampleState(
        times: Event<Any?> = Timeline.currentTimeline().timeBehavior.updated()
    ): State<A> {
        return State.Companion.hold(value, sample(times)) // TODO: Using hold lazy breaks things here, but it may also break platformer to not do it?
    }

    companion object {
        /**
         * Builds a behavior whose value remains constant for all times.
         **/
        inline fun <reified A> const(value: A): Behavior<A> {
            return Polynomial(VectorSpace.instance(), listOf(value))
        }

        /**
         * Builds a behavior polynomial in time from the list of [coefficients].
         **/
        inline fun <reified A> polynomial(coefficients: List<A>): Behavior<A> {
            return Polynomial(VectorSpace.instance(), coefficients)
        }

        /**
         * Create a [Behavior] from a continuous function of time since
         * the initial state of the behavior.
         *
         * Example:
         *
         * ```
         * val wave = Behavior.continuous { time ->
         *     sin(time.seconds)
         * }
         * ```
         **/
        inline fun <reified A> continuous(
            noinline f: (Duration) -> A
        ): Behavior<A> {
            return Continuous(lazy { VectorSpace.instance<A>() }, f)
        }

        inline fun <reified A> sampled(noinline current: () -> A): Behavior<A> {
            return Sampled({ VectorSpace.instance<A>() }, current)
        }

        inline fun <reified T> integral(f: Behavior<T>): Behavior<T> {
            return f.integrate()
        }

        @ExperimentalYafrlAPI
        inline fun <A, reified B> impulse(event: Event<A>, zeroValue: B, noinline onEvent: (A) -> B): Behavior<B> {
            return Impulse(zeroValue, event, onEvent)
        }
    }

    /**
     * A behavior representing a dirac impulse derived from an event.
     **/
    @OptIn(FragileYafrlAPI::class)
    @ExperimentalYafrlAPI
    class Impulse<A, B>(
        internal val zeroValue: B,
        internal val event: Event<A>,
        internal val onEvent: (A) -> B
    ) : Behavior<B> {
        val timeline = Timeline.currentTimeline()

        val impulses = mutableMapOf<Duration, B>()

        init {
            // Whenever the event updates, keep track of the times the event has been
            // fired
            event.node.collectSync { value ->
                if (value is EventState.Fired) {
                    impulses[timeline.time] = onEvent(value.event)
                }
            }
        }

        override fun sampleValue(time: Duration): B {
            return impulses.get(time) ?: zeroValue
        }

        override fun measureImpulses(time: Duration, dt: Duration): B = run {
            // Return the sum of all occurrences of impulses in the time between time - dt and time.
            impulses.entries.sortedBy { it.key }
                .dropWhile { it.key < time - dt }
                .takeWhile { it.key <= time }
                .lastOrNull()
                ?.value
                ?: zeroValue
        }
    }

    /**
     * A polynomial in time.
     *
     * For example: [2,3,5] would correspond to:
     *
     *   f(t) = 2 + 3*t + 5*t^2
     *
     * Note that the coefficients can be values of any vector space,
     *  and hence do not have to be scalars.
     **/
    class Polynomial<A>(
        internal val vectorSpace: VectorSpace<A>,
        internal val coefficients: List<A>
    ) : Behavior<A> {
        /**
         * Gets the exact symbolic integral of the polynomial behavior.
         **/
        internal fun exactIntegral(accum: (A, A) -> A): Polynomial<A> = with(vectorSpace.with(accum)) {
            val newCoefficients = listOf(zero) + coefficients
                .mapIndexed { i, c -> c / (i + 1) }

            Polynomial(vectorSpace.with(accum), newCoefficients)
        }

        override fun sampleValue(time: Duration): A = with(vectorSpace) {
            val seconds = time.inWholeMilliseconds / 1000f

            return coefficients
                .mapIndexed { i, c ->
                    c * seconds.pow(i)
                }
                .foldRight(zero) { x, y -> x + y }
        }

        override fun measureImpulses(time: Duration, dt: Duration): A = with(vectorSpace) {
            //val indefinite = integrated()

            return zero // indefinite.sampleValue(time) - indefinite.sampleValue(time - dt)
        }
    }

    /**
     * A generic continuous function of time.
     **/
    class Continuous<A>(
        internal val vectorSpace: Lazy<VectorSpace<A>>,
        internal val f: (Duration) -> A
    ) : Behavior<A> {
        override fun sampleValue(time: Duration): A {
            return f(time)
        }

        override fun measureImpulses(time: Duration, dt: Duration): A = with(vectorSpace.value) {
            //val integral = this@Continuous.integrate(this)

            return zero // integral.sampleValue(time) - integral.sampleValue(time - dt)
        }
    }

    /**
     * A non-numeric behavior derived from sampling an external signal.
     **/
    class Sampled<A>(
        private val instance: () -> VectorSpace<A>,
        private val current: () -> A
    ) : Behavior<A> {
        override fun sampleValue(time: Duration): A {
            return current()
        }

        override fun measureImpulses(time: Duration, dt: Duration): A = with(instance()) {
            return zero // (dt.inWholeMilliseconds / 1000.0) * current()
        }
    }

    class Sum<A>(
        val vectorSpace: VectorSpace<A>,
        val first: Behavior<A>,
        val second: Behavior<A>
    ) : Behavior<A>, VectorSpace<A> by vectorSpace {
        override fun sampleValue(time: Duration): A {
            return first.sampleValue(time) + second.sampleValue(time)
        }

        override fun measureImpulses(time: Duration, dt: Duration): A {
            return first.measureImpulses(time, dt) + second.measureImpulses(time, dt)
        }
    }
}

/** Negate the value of the behavior at all times. */
operator fun Behavior<Boolean>.not(): Behavior<Boolean> {
    return map { !it }
}

/**
 * Constructs a behavior whose value is equal to the state's current behavior's value
 *  at all times.
 *
 * This can be viewed as a behavior that switches to a new behavior whenever the [State]
 *  updates.
 *
 *  Compare with [switcher](https://hackage.haskell.org/package/reflex-0.9.3.3/docs/Reflex-Class.html#v:switcher)
 *  from [reflex-frp](https://reflex-frp.org/) and see also [Behavior.until].
 **/
fun <A> State<Behavior<A>>.switcher(): Behavior<A> {
    return Until(lazy { this.value }, this.updated())
}

@OptIn(FragileYafrlAPI::class)
internal class Until<A>(
    internal val initialBehavior: Lazy<Behavior<A>>,
    internal val event: Event<Behavior<A>>
) : Behavior<A> {
    var current: Behavior<A>? = null

    init {
        // Update the current behavior whenever the event fires.
        event.node.collectSync { nodeValue ->
            if (nodeValue is EventState.Fired) {
                current = nodeValue.event
            }
        }
    }

    override fun sampleValue(time: Duration): A {
        return (current ?: initialBehavior.value)
            .sampleValue(time)
    }

    override fun measureImpulses(time: Duration, dt: Duration): A {
        return (current ?: initialBehavior.value)
            .measureImpulses(time, dt)
    }
}
