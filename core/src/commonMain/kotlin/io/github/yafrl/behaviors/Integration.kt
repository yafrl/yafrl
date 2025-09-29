package io.github.yafrl.behaviors

import io.github.yafrl.SampleScope
import io.github.yafrl.annotations.ExperimentalYafrlAPI
import io.github.yafrl.annotations.FragileYafrlAPI
import io.github.yafrl.timeline.BehaviorID
import io.github.yafrl.timeline.Timeline
import io.github.yafrl.vector.VectorSpace
import kotlin.math.abs
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Implementation of a generic integrated behavior using numeric methods
 *  (Simpson's Rule).
 **/
internal class IntegratedBehavior<T>(
    private val timeline: Timeline,
    private val vectorSpace: VectorSpace<T>,
    private val initial: T,
    private val behavior: Behavior<T>
) : Behavior<T> {
    val stepSizeMS = 150

    var lastSampled: Duration = timeline.time
    var lastValue: T = initial

    val values = mutableMapOf<Duration, T>(lastSampled to initial)

    @FragileYafrlAPI
    @OptIn(ExperimentalYafrlAPI::class)
    override fun sampleValueAt(time: Duration): T = run {
        val cached = values[time]

        println("Sampling value at $time, last sampled: $lastSampled")

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
            val stepDur = dt / subdivisions           // Duration of each sub‐interval
            val h = dt.inWholeMilliseconds / 1000.0 / // total width in seconds
                    subdivisions                      // width of each sub‐interval in seconds

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

    override val parentBehaviors: List<BehaviorID>
        get() = behavior.parentBehaviors
}