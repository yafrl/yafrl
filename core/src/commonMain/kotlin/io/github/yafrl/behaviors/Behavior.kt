@file:OptIn(ExperimentalYafrlAPI::class)

package io.github.yafrl.behaviors

import io.github.yafrl.Event
import io.github.yafrl.EventState
import io.github.yafrl.Signal
import io.github.yafrl.annotations.ExperimentalYafrlAPI
import io.github.yafrl.annotations.FragileYafrlAPI
import io.github.yafrl.timeline.BehaviorID
import io.github.yafrl.timeline.Timeline
import io.github.yafrl.timeline.current
import io.github.yafrl.vector.VectorSpace
import io.github.yafrl.SampleScope
import io.github.yafrl.SignalScope
import io.github.yafrl.behaviors.Behavior.Continuous
import io.github.yafrl.behaviors.Behavior.Impulse
import io.github.yafrl.behaviors.Behavior.Polynomial
import io.github.yafrl.behaviors.Behavior.Sampled
import io.github.yafrl.sample
import io.github.yafrl.timeline.TimelineScope
import io.github.yafrl.timeline.debugging.ExternalBehavior
import kotlin.math.pow
import kotlin.reflect.typeOf
import kotlin.time.Duration

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
 *  functions of time. In yafrl, behaviors can even be nondeterministic, acting as good
 *  representations of external inputs to a program.
 **/
sealed interface Behavior<out A> {
    /**
     * Calculates the value at the specified time.
     *
     * **Note**: If not used properly, this API can cause runtime crashes, as for
     *  efficiency reasons, some behaviors assume times will be accessed in a
     *  strictly monotonic increasing order.
     *
     * Please use [SampleScope.sampleValue] instead to get the value of a behavior
     *  at the current frame. Or see [transformTime] if you need to modify the
     *  time sampling behavior of a [Behavior].
     **/
    @FragileYafrlAPI
    fun sampleValueAt(time: Duration): A

    /**
     * Used to support dirac impulses in behaviors.
     *
     * `measureImpulses` returns the part of the integral of the behavior
     *   belonging to dirac impulses in the range [time, time + dt], and should be used
     *   in the implementation of the integral of behaviors.
     **/
    @ExperimentalYafrlAPI
    fun measureImpulses(time: Duration, dt: Duration): A

    val parentBehaviors: List<BehaviorID>

    companion object

    /**
     * A behavior representing a dirac impulse derived from an event.
     **/
    @OptIn(FragileYafrlAPI::class)
    @ExperimentalYafrlAPI
    class Impulse<A, B>(
        internal val timeline: Timeline,
        internal val zeroValue: B,
        internal val event: Event<A>,
        internal val onEvent: (A) -> B
    ) : Behavior<B> {
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

        override fun sampleValueAt(time: Duration): B {
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

        override val parentBehaviors: List<BehaviorID>
            get() = listOf()
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
        fun exactIntegral(accum: (A, A) -> A): Polynomial<A> = with(vectorSpace.with(accum)) {
            val newCoefficients = listOf(zero) + coefficients
                .mapIndexed { i, c ->
                    c / (i + 1)
                }

            Polynomial(vectorSpace.with(accum), newCoefficients)
        }

        @FragileYafrlAPI
        override fun sampleValueAt(time: Duration): A = with(vectorSpace) {
            val seconds = time.inWholeMilliseconds / 1000f

            return coefficients
                .mapIndexed { i, c ->
                    c * seconds.pow(i)
                }
                .foldRight(zero) { x, y -> x + y }
        }

        override fun measureImpulses(time: Duration, dt: Duration): A = with(vectorSpace) {
            return zero
        }

        override val parentBehaviors: List<BehaviorID>
            get() = listOf()
    }

    /**
     * A generic continuous function of time.
     **/
    class Continuous<A>(
        internal val vectorSpace: Lazy<VectorSpace<A>>,
        internal val f: (Duration) -> A
    ) : Behavior<A> {
        @FragileYafrlAPI
        override fun sampleValueAt(time: Duration): A {
            return f(time)
        }

        override fun measureImpulses(time: Duration, dt: Duration): A = with(vectorSpace.value) {
            return zero
        }

        override val parentBehaviors: List<BehaviorID>
            get() = listOf()
    }

    /**
     * A non-numeric behavior derived from sampling an external signal.
     **/
    class Sampled<A>(
        val id: BehaviorID,
        private val instance: () -> VectorSpace<A>,
        private val current: () -> A
    ) : Behavior<A> {
        @FragileYafrlAPI
        override fun sampleValueAt(time: Duration): A {
            return current()
        }

        override fun measureImpulses(time: Duration, dt: Duration): A = with(instance()) {
            return zero
        }

        override val parentBehaviors: List<BehaviorID>
            get() = listOf(id)
    }

    class Sum<A>(
        val vectorSpace: VectorSpace<A>,
        val first: Behavior<A>,
        val second: Behavior<A>
    ) : Behavior<A>, VectorSpace<A> by vectorSpace {
        @FragileYafrlAPI
        override fun sampleValueAt(time: Duration): A {
            return first.sampleValueAt(time) + second.sampleValueAt(time)
        }

        override fun measureImpulses(time: Duration, dt: Duration): A {
            return first.measureImpulses(time, dt) + second.measureImpulses(time, dt)
        }

        override val parentBehaviors: List<BehaviorID>
            get() = first.parentBehaviors + second.parentBehaviors
    }
}

open class BehaviorScope(timeline: Timeline) : SignalScope(timeline) {
    /**
     * Apply a function to the time used to sample the behavior.
     **/
    fun <A> Behavior<A>.transformTime(transform: (Duration) -> Duration): Behavior<A> {
        return Transformed(transform, this)
    }

    /** Implementation of a behavior to which a time transformation has been applied. */
    private class Transformed<A>(
        private val transformation: (Duration) -> Duration,
        private val behavior: Behavior<A>
    ) : Behavior<A> {
        @OptIn(FragileYafrlAPI::class)
        override fun sampleValueAt(time: Duration): A {
            return behavior.sampleValueAt(transformation(time))
        }

        override fun measureImpulses(time: Duration, dt: Duration): A {
            return behavior.measureImpulses(transformation(time), dt)
        }

        override val parentBehaviors: List<BehaviorID>
            get() = behavior.parentBehaviors
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
    fun <A> Behavior<A>.until(event: Event<Behavior<@UnsafeVariance A>>): Behavior<A> {
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
    fun <A> Behavior<A>.sample(
        times: Event<Any?> = timeline.clock
    ): Event<A> {
        return times.map { sampleValue() }
    }

    fun <A, B> Behavior<A>.map(f: SampleScope.(A) -> B): Behavior<B> {
        return Mapped(this, f)
    }

    fun <A, B> Behavior<A>.flatMap(f: SampleScope.(A) -> Behavior<B>): Behavior<B> {
        return Flattened(Mapped(this, f))
    }

    /** Implementation of a mapped behavior. */
    private inner class Mapped<A, B>(
        private val original: Behavior<A>,
        private val f: SampleScope.(A) -> B
    ) : Behavior<B> {
        @FragileYafrlAPI
        override fun sampleValueAt(time: Duration): B {
            return TimelineScope(timeline).sample { f(original.sampleValueAt(time)) }
        }

        override fun measureImpulses(time: Duration, dt: Duration): B {
            return TimelineScope(timeline).sample { f(original.measureImpulses(time, dt)) }
        }

        override val parentBehaviors: List<BehaviorID>
            get() = original.parentBehaviors
    }

    /** Implementation of a flattened behavior. */
    private class Flattened<A>(
        private val original: Behavior<Behavior<A>>
    ) : Behavior<A> {

        @FragileYafrlAPI
        override fun sampleValueAt(time: Duration): A {
            return original
                .sampleValueAt(time)
                .sampleValueAt(time)
        }

        @OptIn(FragileYafrlAPI::class)
        override fun measureImpulses(time: Duration, dt: Duration): A {
            return original
                .sampleValueAt(time)
                .measureImpulses(time, dt)
        }

        // TODO: What about the inner behavior?
        override val parentBehaviors: List<BehaviorID>
            get() = original.parentBehaviors
    }

    /**
     * Sample a [Behavior] to produce a [Signal].
     *
     * See [sample].
     **/
    @OptIn(FragileYafrlAPI::class)
    fun <A> Behavior<A>.sampleState(
        times: Event<Any?> = timeline.timeBehavior.updated()
    ): Signal<A> {
        return Signal.Companion.hold(sampleValueAt(timeline.time), sample(times))
    }

    /**
     * Builds a behavior whose value remains constant for all times.
     **/
    inline fun <reified A> Behavior.Companion.const(value: A): Behavior<A> {
        return Polynomial(VectorSpace.instance(), listOf(value))
    }

    /**
     * Builds a behavior polynomial in time from the list of [coefficients].
     **/
    inline fun <reified A> Behavior.Companion.polynomial(coefficients: List<A>): Behavior.Polynomial<A> {
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
     *
     * NOTE: The function used to define continuous should be pure.
     *  For impure / non-deterministic behaviors, you should use
     *  [sampled] instead.
     **/
    inline fun <reified A> Behavior.Companion.continuous(
        noinline f: (Duration) -> A
    ): Behavior<A> {
        return Continuous(lazy { VectorSpace.instance<A>() }, f)
    }

    @OptIn(FragileYafrlAPI::class)
    inline fun <reified A> Behavior.Companion.sampled(noinline current: SampleScope.() -> A): Behavior<A> {
        val result = Sampled(
            timeline.newBehaviorID(),
            { VectorSpace.instance<A>() },
            {
                // TODO: Probably want to track the dependencies here.
                TimelineScope(timeline).sample { current() }
            }
        )

        timeline.graph.addBehavior(
            ExternalBehavior(
                typeOf<A>(),
                result
            )
        )

        return result
    }

    inline fun <reified T> Behavior.Companion.integral(f: Behavior<T>): Behavior<T> {
        return f.integrate()
    }

    inline fun <reified T> integral(f: Behavior<T>): Behavior<T> {
        return f.integrate()
    }

    @ExperimentalYafrlAPI
    inline fun <A, reified B> Behavior.Companion.impulse(event: Event<A>, zeroValue: B, noinline onEvent: (A) -> B): Behavior<B> {
        return Impulse(timeline, zeroValue, event, onEvent)
    }

    /** Negate the value of the behavior at all times. */
    operator fun Behavior<Boolean>.not(): Behavior<Boolean> {
        return map { !it }
    }

    /**
     * Constructs a behavior whose value is equal to the state's current behavior's value
     *  at all times.
     *
     * This can be viewed as a behavior that switches to a new behavior whenever the [Signal]
     *  updates.
     *
     *  Compare with [switcher](https://hackage.haskell.org/package/reflex-0.9.3.3/docs/Reflex-Class.html#v:switcher)
     *  from [reflex-frp](https://reflex-frp.org/) and see also [Behavior.until].
     **/
    @OptIn(FragileYafrlAPI::class)
    fun <A> Signal<Behavior<A>>.switcher(): Behavior<A> {
        return Until(lazy { this.node.current(timeline) }, this.updated())
    }

    /**
     * Builds a dirac impulse whose value is equal to the event values when the even has fired,
     *  and whole value is equal to [zero] otherwise
     **/
    @OptIn(FragileYafrlAPI::class)
    @ExperimentalYafrlAPI
    inline fun <reified A> Event<A>.impulse(zero: @UnsafeVariance A): Behavior<A> = Behavior.impulse(this, zero) { it }

    /**
     * Builds a dirac impulse whose value is equal to [value] whenever the event fires, and [zero]
     *  otherwise.
     **/
    @OptIn(FragileYafrlAPI::class)
    @ExperimentalYafrlAPI
    inline fun <A, reified B> Event<A>.impulse(zero: B, value: B): Behavior<B> = Behavior.impulse(this, zero) { value }

    /**
     * Utility to convert a [Signal] into a behavior whose values are interpreted as the
     *  piecewise function of the values of the [Signal].
     **/
    @OptIn(FragileYafrlAPI::class)
    inline fun <reified A> Signal<A>.asBehavior(): Behavior<A> {
        if (VectorSpace.hasInstance<A>()) {
            // Use a switcher so we can get an exact polynomial integral piecewise.
            return map { Behavior.const(it) }.switcher()
        } else {
            // If no instance exists, just use sampled so we do not try to get an
            // instance that does not exist at runtime.
            return Behavior.sampled { node.current(timeline) }
        }
    }

    /**
     * Integrate the behavior with respect to the current [Timeline]'s clock time.
     *
     * Note: [T] must have a [VectorSpace] instance -- otherwise the function will throw an
     *  [IllegalArgumentException] at runtime.
     *
     * @param initial Optional constant of integration -- uses the zero value of the vector space by default.
     **/
    inline fun <reified T> Behavior<T>.integrate(initial: T? = null): Behavior<T> {
        val vectorSpace = VectorSpace.instance<T>()
        return with(vectorSpace) {
            integrateWith(vectorSpace, initial ?: vectorSpace.zero, { x, y -> x + y })
        }
    }

    /**
     * Integrate the behavior with respect to the current [Timeline]'s clock time, supplying
     *  an explicit [VectorSpace] instance.
     **/
    fun <T> Behavior<T>.integrate(vectorSpace: VectorSpace<T>, initial: T? = null): Behavior<T> {
        return with(vectorSpace) {
            integrateWith(vectorSpace, initial ?: vectorSpace.zero, { x, y -> x + y })
        }
    }

    inline fun <reified T> Behavior<T>.integrateWith(initial: T, noinline accum: SampleScope.(T, T) -> T): Behavior<T> {
        return integrateWith(VectorSpace.instance<T>(), initial, accum)
    }

    @OptIn(FragileYafrlAPI::class)
    fun <T> Behavior<T>.integrateWith(vectorSpace: VectorSpace<T>, initial: T, accum: SampleScope.(T, T) -> T): Behavior<T> {
        return when(this) {
            // Polynomials can be integrated exactly.
            // TODO: Adding this messes up the yafrl-gdx example for some reason
            // is Behavior.Polynomial -> this.exactIntegral(accum)

            // Switchers / Until can be integrated piece-wise to get a more exact
            //  result in the case of polynomials.
            // TODO: Adding this in messes up the integration tests. Must not be accurate.
//        is Until -> Until(
//            lazy { initialBehavior.value.integrateWith(vectorSpace, initial, accum) },
//            event.map { it.integrateWith(vectorSpace, initial, accum) }
//        )

//        is Behavior.Sum -> Behavior.Sum(
//            vectorSpace.with(accum),
//            first.integrateWith(vectorSpace, zero, accum),
//            second.integrateWith(vectorSpace, initial, accum)
//        )

            // Generic numeric integral that takes impulses into account
            else -> IntegratedBehavior(
                timeline,
                vectorSpace.with { x, y ->
                    TimelineScope(timeline).sample { accum(x, y) }
                },
                initial,
                this@integrateWith
            )
        }
    }
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

    override fun sampleValueAt(time: Duration): A {
        return (current ?: initialBehavior.value)
            .sampleValueAt(time)
    }

    override fun measureImpulses(time: Duration, dt: Duration): A {
        return (current ?: initialBehavior.value)
            .measureImpulses(time, dt)
    }

    // TODO: What about when this updates?
    override val parentBehaviors: List<BehaviorID>
        get() = initialBehavior.value.parentBehaviors
}
