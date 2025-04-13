package io.github.sintrastes.yafrl

import io.github.sintrastes.yafrl.annotations.FragileYafrlAPI
import io.github.sintrastes.yafrl.internal.Timeline
import io.github.sintrastes.yafrl.vector.Float2
import io.github.sintrastes.yafrl.vector.Float3
import io.github.sintrastes.yafrl.vector.VectorSpace
import kotlin.jvm.JvmName
import kotlin.math.pow
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
 *  functions of time.
 **/
sealed interface Behavior<out A> {
    /** Calculates the value at the specified time. */
    fun sampleValue(time: Duration): A

    /**
     * Calculate the current value of the behavior at the given [time],
     *  integrating from `time - dt` to `time`.
     **/
    fun definiteIntegral(time: Duration, dt: Duration): A

    val value: A
        get() = sampleValue(Timeline.currentTimeline().time)

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

    class Mapped<A, B>(
        private val original: Behavior<A>,
        private val f: (A) -> B
    ) : Behavior<B> {
        override fun sampleValue(time: Duration): B {
            return f(original.sampleValue(time))
        }

        override fun definiteIntegral(time: Duration, dt: Duration): B {
            return f(original.definiteIntegral(time, dt))
        }
    }

    /**
     * Sample a [Behavior] to produce a [State].
     *
     * See [sample].
     **/
    fun sampleState(times: Event<Any?> = Timeline.currentTimeline().clock): State<A> {
        return State.hold(value, sample(times))
    }

    companion object {
        inline fun <reified A> const(value: A): Behavior<A> {
            return Polynomial(VectorSpace.instance(), listOf(value))
        }

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

        fun <A> sampled(current: () -> A): Behavior<A> {
            return Sampled(current)
        }

        inline fun <reified T> integral(f: Behavior<T>): Behavior<T> {
            return f.integrate()
        }

        fun <A, B> impulse(event: Event<A>, zeroValue: B, onEvent: (A) -> B): Behavior<B> {
            return Impulse(zeroValue, event, onEvent)
        }
    }

    /**
     * A behavior representing a dirac impulse derived from an event.
     **/
    @OptIn(FragileYafrlAPI::class)
    class Impulse<A, B>(
        internal val zeroValue: B,
        internal val event: Event<A>,
        internal val onEvent: (A) -> B
    ): Behavior<B> {
        val timeline = Timeline.currentTimeline()

        val impulses = mutableMapOf<Duration, A>()

        init {
            // Whenever the event updates, keep track of the times the event has been
            // fired
            event.node.collectSync { value ->
                if (value is EventState.Fired) {
                    impulses[timeline.time] = value.event
                }
            }
        }

        override fun sampleValue(time: Duration): B {
            return impulses.get(time)?.let(onEvent) ?: zeroValue
        }

        override fun definiteIntegral(time: Duration, dt: Duration): B {
            // Return the sum of all occurrences of impulses in the time between time - dt and time.
            return impulses.entries.sortedBy { it.key }
                .dropWhile { it.key < time - dt }
                .takeWhile { it.key <= time }
                .lastOrNull()
                ?.value
                ?.let(onEvent)
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
        internal fun integrated(): Polynomial<A> = with(vectorSpace) {
            val newCoefficients = listOf(zero) + coefficients
                .mapIndexed { i, c -> c / (i + 1) }

            Polynomial(vectorSpace, newCoefficients)
        }

        override fun sampleValue(time: Duration): A = with(vectorSpace) {
            val seconds = time.inWholeMilliseconds / 1000f

            return coefficients
                .mapIndexed { i, c ->
                    c * seconds.pow(i)
                }
                .foldRight(zero) { x, y -> x + y }
        }

        override fun definiteIntegral(time: Duration, dt: Duration): A = with(vectorSpace) {
            val indefinite = integrated()

            return indefinite.sampleValue(time) - indefinite.sampleValue(time - dt)
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

        override fun definiteIntegral(time: Duration, dt: Duration): A = with(vectorSpace.value) {
            (dt.inWholeMilliseconds / 1000.0) * f(time)
        }
    }

    /**
     * A non-numeric behavior derived from sampling an external signal.
     **/
    class Sampled<A>(private val current: () -> A): Behavior<A> {
        override fun sampleValue(time: Duration): A {
            return current()
        }

        override fun definiteIntegral(time: Duration, dt: Duration): A {
            return current()
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

        override fun definiteIntegral(time: Duration, dt: Duration): A {
            return first.definiteIntegral(time, dt) + second.definiteIntegral(time, dt)
        }
    }
}

operator fun Behavior<Boolean>.not(): Behavior<Boolean> {
    return map { !it }
}

/**
 * Integrate the behavior with respect to the current [Timeline]'s clock time.
 *
 * Note: [T] must have a [VectorSpace] instance -- otherwise the function will throw an
 *  [IllegalArgumentException] at runtime.
 **/
inline fun <reified T> Behavior<T>.integrate(): Behavior<T> {
    return integrate(VectorSpace.instance<T>())
}

fun <T> Behavior<T>.integrate(vectorSpace: VectorSpace<T>): Behavior<T> {
    return when(this) {
        is Behavior.Polynomial -> this.integrated()
        else -> integrateNumeric(vectorSpace)
    }
}

fun <T> Behavior<T>.integrateNumeric(vectorSpace: VectorSpace<T>): State<T> {
    val timeline =  Timeline.currentTimeline()
    val clock = timeline.clock

    return with (vectorSpace) {
        State.fold(zero, clock) { integral, dt ->
            integral + definiteIntegral(timeline.time, dt)
        }
    }
}

inline fun <reified T> Behavior<T>.integrateWith(initial: T, crossinline accum: (T, T) -> T): State<T> {
    return integrateWith(VectorSpace.instance<T>(), initial, accum)
}

inline fun <T> Behavior<T>.integrateWith(vectorSpace: VectorSpace<T>, initial: T, crossinline accum: (T, T) -> T): State<T> {
    val timeline = Timeline.currentTimeline()
    val clock = timeline.clock

    return with (vectorSpace) {
        State.fold(initial, clock) { integral, dt ->

            accum(integral, definiteIntegral(timeline.time, dt))
        }
    }
}

operator fun Behavior<Float>.plus(other: Behavior<Float>): Behavior<Float> = addBehavior(other)

@JvmName("plusInt")
operator fun Behavior<Int>.plus(other: Behavior<Int>): Behavior<Int> = addBehavior(other)

@JvmName("plusFloat2")
operator fun Behavior<Float2>.plus(other: Behavior<Float2>): Behavior<Float2> = addBehavior(other)

@JvmName("plusFloat3")
operator fun Behavior<Float3>.plus(other: Behavior<Float3>): Behavior<Float3> = addBehavior(other)

internal inline fun <reified A> Behavior<A>.addBehavior(other: Behavior<A>): Behavior<A> = with(VectorSpace.instance<A>()) {
    when(this@addBehavior) {
        is Behavior.Polynomial<A> -> {
            when (other) {
                is Behavior.Polynomial<A> -> {
                    var coefficients = coefficients
                    var otherCoefficients = other.coefficients

                    if (other.coefficients.size > coefficients.size) {
                        val difference = other.coefficients.size - coefficients.size

                        coefficients += (0 until difference).map { zero }
                    }

                    if (coefficients.size > other.coefficients.size) {
                        val difference = coefficients.size - other.coefficients.size

                        otherCoefficients += (0 until difference).map { zero }
                    }

                    Behavior.Polynomial(
                        vectorSpace,
                        coefficients.zip(otherCoefficients) { x, y -> x + y }
                    )
                }

                else -> Behavior.Sum(this@with, this@addBehavior, other)
            }
        }
        else -> Behavior.Sum(this@with, this@addBehavior, other)
    }
}