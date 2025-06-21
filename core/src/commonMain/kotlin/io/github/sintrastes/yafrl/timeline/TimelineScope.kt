package io.github.sintrastes.yafrl.timeline

import io.github.sintrastes.yafrl.Event
import io.github.sintrastes.yafrl.Signal
import kotlin.time.Duration

interface TimelineScope {
    val clock: Event<Duration>
    val timeBehavior: Signal<Duration>
}