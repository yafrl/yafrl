package io.github.yafrl.timeline

import io.github.yafrl.Event
import io.github.yafrl.Signal
import io.github.yafrl.behaviors.BehaviorScope
import kotlin.time.Duration

open class TimelineScope(timeline: Timeline): HasTimeline, BehaviorScope(timeline) {
    val clock: Event<Duration>
        get() = timeline.clock

    val timeBehavior: Signal<Duration>
        get() = timeline.timeBehavior
}

interface HasTimeline {
    val timeline: Timeline
}