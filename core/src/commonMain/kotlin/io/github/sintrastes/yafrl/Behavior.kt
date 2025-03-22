package io.github.sintrastes.yafrl

import io.github.sintrastes.yafrl.internal.Timeline
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
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
interface Behavior<out A> {
    val value: A

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
    fun sample(times: Event<Any?>): Event<A> {
        return times.map {
            value
        }
    }

    /**
     * Sample a [Behavior] to produce a [State].
     *
     * See [sample].
     **/
    fun sampleState(times: Event<Any?>): State<A> {
        return State.hold(value, sample(times))
    }

    companion object {
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
        fun <A> continuous(f: (Duration) -> A): Behavior<A> {
            val initialTime = Clock.System.now()

            return object: Behavior<A> {
                override val value: A
                    get() {
                        val currentTime = Clock.System.now()
                        val difference = currentTime - initialTime

                        return f(difference)
                    }
            }
        }

        fun integral(f: Behavior<Float>): State<Float> {
            return f.integrate()
        }
    }
}

/** Integrate the behavior with respect to the current [Timeline]'s clock time. */
fun Behavior<Float>.integrate(): State<Float> {
    var previousValue = value

    val clock = Timeline.currentTimeline().clock

    return State.fold(0f, clock) { integral, dt ->
        val newValue = value

        // Convert the duration difference to seconds (using whole milliseconds)
        val dt = dt.inWholeMilliseconds / 1000f

        // Trapezoidal rule: add the area of the trapezoid between samples.
        val result = 0.5f * (previousValue + newValue) * dt

        previousValue = newValue

        integral + result
    }
}