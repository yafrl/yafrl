package io.github.sintrastes.yafrl.behaviors

import io.github.sintrastes.yafrl.annotations.ExperimentalYafrlAPI
import io.github.sintrastes.yafrl.annotations.FragileYafrlAPI
import io.github.sintrastes.yafrl.timeline.Timeline
import io.github.sintrastes.yafrl.vector.VectorSpace
import kotlin.math.abs
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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

inline fun <reified T> Behavior<T>.integrateWith(initial: T, noinline accum: (T, T) -> T): Behavior<T> {
    return integrateWith(VectorSpace.instance<T>(), initial, accum)
}

@OptIn(FragileYafrlAPI::class)
fun <T> Behavior<T>.integrateWith(vectorSpace: VectorSpace<T>, initial: T, accum: (T, T) -> T): Behavior<T> {
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
        else -> IntegratedBehavior(vectorSpace.with(accum), initial, this@integrateWith)
    }
}

/**
 * Implementation of a generic integrated behavior using numeric methods
 *  (Simpson's Rule).
 **/
internal class IntegratedBehavior<T>(
    private val vectorSpace: VectorSpace<T>,
    private val initial: T,
    private val behavior: Behavior<T>
) : Behavior<T> {
    val stepSizeMS = 150

    var lastSampled: Duration = Timeline.currentTimeline().time
    var lastValue: T = initial

    val values = mutableMapOf<Duration, T>(lastSampled to initial)

    @FragileYafrlAPI
    @OptIn(ExperimentalYafrlAPI::class)
    override fun sampleValueAt(time: Duration): T = run {
        val cached = values[time]

        // Check prevents a stack overflow.
        if (time == 0.0.seconds) {
            return initial
        } else if (cached != null) {
            return cached
        } else {
            // Assumption: Monotonic increasing
            require(time > lastSampled) {
                "The time $time was not greater than the last sampled value ($lastSampled)"
            }

            val dt = abs((lastSampled - time).inWholeMilliseconds).milliseconds

            val subdivisions = max(2, 2 * (dt.inWholeMilliseconds / stepSizeMS).toInt())

            // Set up endpoints and step
            val start = lastSampled // time - dt
            val stepDur = dt / subdivisions                  // Duration of each sub‐interval
            val h = dt.inWholeMilliseconds / 1000.0 /  // total width in seconds
                    subdivisions                   // width of each sub‐interval in seconds

            with(vectorSpace) {
                // Simpson’s rule weights: 1, 4, 2, 4, …, 2, 4, 1
                var sum: T = behavior.sampleValueAt(start)

                for (i in 1 until subdivisions) {
                    val t = start + stepDur * i
                    sum = sum + if (i % 2 == 0) {
                        2.0 * behavior.sampleValueAt(t)
                    } else {
                        4.0 * behavior.sampleValueAt(t)
                    }
                }

                sum = sum + behavior.sampleValueAt(start + dt)

                // Also add in anything from definite integrals.
                val impulses = behavior.measureImpulses(start, dt)

                // Multiply by h/3
                val result = lastValue + (h / 3.0) * sum + impulses

                // lastValue = result
                values[time] = result
                lastValue = result
                lastSampled = time

                result
            }
        }
    }

    @OptIn(ExperimentalYafrlAPI::class)
    override fun measureImpulses(time: Duration, dt: Duration): T = with(vectorSpace) {
        return zero
    }
}