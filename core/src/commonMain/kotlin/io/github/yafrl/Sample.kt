package io.github.yafrl

import io.github.yafrl.annotations.FragileYafrlAPI
import io.github.yafrl.behaviors.Behavior
import io.github.yafrl.timeline.Timeline
import io.github.yafrl.timeline.TimelineScope
import io.github.yafrl.timeline.current
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration

/**
 * Introduces a context from which behaviors and states can be sampled.
 **/
@OptIn(FragileYafrlAPI::class) inline fun <R> sample(
    body: SampleScope.() -> R
): R {
    val scope = object: SampleScope {
        override val clock: Event<Duration>
            get() = Timeline.currentTimeline().clock

        override val timeBehavior: Signal<Duration>
            get() = Timeline.currentTimeline().timeBehavior

        override fun <A> Behavior<A>.sampleValue(): A {
            return sampleValueAt(Timeline.currentTimeline().time)
        }

        override fun <A> Signal<A>.currentValue(): A {
            return node.current()
        }
    }

    return scope.body()
}

inline fun <R> runYafrl(
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    body: SampleScope.() -> R
): R {
    Timeline.initializeTimeline(scope = scope)
    return sample(body)
}

/**
 * Interface providing a context where [Behavior]s and [Signal]s can be sampled.
 *
 * Compare with [MonadSample]() from reflex.
 **/
interface SampleScope: TimelineScope {
    fun <A> Behavior<A>.sampleValue(): A

    fun <A> Signal<A>.currentValue(): A
}