package io.github.sintrastes.yafrl.internal

import kotlinx.coroutines.test.TestDispatcher
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

/** Utility to get the current system time (or simulated time, if running in a coroutine test). */
val CoroutineContext.currentTime : Instant
    get() = (this[ContinuationInterceptor] as? TestDispatcher)
        ?.scheduler?.currentTime?.let { Instant.fromEpochSeconds(it) }
        ?: Clock.System.now()