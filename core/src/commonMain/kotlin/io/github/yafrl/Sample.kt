package io.github.yafrl

import io.github.yafrl.annotations.FragileYafrlAPI
import io.github.yafrl.behaviors.Behavior
import io.github.yafrl.timeline.Timeline
import io.github.yafrl.timeline.TimelineScope
import io.github.yafrl.timeline.current
import io.github.yafrl.timeline.logging.EventLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration

/**
 * Introduces a context from which behaviors and states can be sampled.
 *
 * Within a single frame, repeated calls to [SampleScope.sampleValue] on the same
 *  [Behavior] reference will always return the same value. The per-frame cache
 *  lives on the [Timeline] (see [Timeline.cachedSampleValue]), so this guarantee
 *  holds across distinct [sample] invocations inside the same frame.
 **/
@OptIn(FragileYafrlAPI::class) inline fun <R> TimelineScope.sample(
    body: SampleScope.() -> R
): R {
    val scope = object: SampleScope(timeline) {
        override fun <A> Behavior<A>.sampleValue(): A {
            return timeline.cachedSampleValue(this)
        }

        override fun <A> Signal<A>.currentValue(): A {
            return node.current(timeline)
        }
    }

    return scope.body()
}

inline fun <R> runYafrl(
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    timeTravel: Boolean = false,
    lazy: Boolean = true,
    eventLogger: EventLogger = EventLogger.Disabled,
    body: SampleScope.() -> R
): R {
    val timeline = Timeline
        .initializeTimeline(
            scope = scope,
            timeTravel = timeTravel,
            lazy = lazy,
            eventLogger = eventLogger
        )

    return TimelineScope(timeline).sample(body)
}

/**
 * Interface providing a context where [Behavior]s and [Signal]s can be sampled.
 *
 * Compare with [MonadSample]() from reflex.
 **/
abstract class SampleScope(timeline: Timeline): TimelineScope(timeline) {
    abstract fun <A> Behavior<A>.sampleValue(): A

    abstract fun <A> Signal<A>.currentValue(): A
}