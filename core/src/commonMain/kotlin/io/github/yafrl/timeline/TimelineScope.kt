package io.github.yafrl.timeline

import io.github.yafrl.Event
import io.github.yafrl.Signal
import kotlin.time.Duration

interface TimelineScope {
    val clock: Event<Duration>
    val timeBehavior: Signal<Duration>
}