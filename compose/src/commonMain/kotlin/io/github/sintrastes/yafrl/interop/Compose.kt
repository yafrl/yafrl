package io.github.sintrastes.yafrl.interop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import io.github.sintrastes.yafrl.Behavior
import io.github.sintrastes.yafrl.Event
import io.github.sintrastes.yafrl.annotations.FragileYafrlAPI
import io.github.sintrastes.yafrl.State
import io.github.sintrastes.yafrl.broadcastEvent
import io.github.sintrastes.yafrl.internal.Timeline
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Utility function to assist with using a [io.github.sintrastes.yafrl.State]
 *  in Jetpack Compose, by converting it to a [androidx.compose.runtime.State].
 **/
@OptIn(FragileYafrlAPI::class)
fun <A> State<A>.composeState(): androidx.compose.runtime.State<A> {
    val state = mutableStateOf(value = value)

    val scope = Timeline.currentTimeline().scope

    scope.launch {
        collectAsync { updatedState ->
            state.value = updatedState
        }
    }

    return state
}

/**
 * Utility to get an every that fires for every frame in a
 *  Jetpack Compose application.
 *
 * Each event consists of the [Duration] of the most recent frame.
 **/
@Composable
fun newComposeFrameClock(
    paused: Behavior<Boolean>
): Event<Duration> {
    val clock = remember {
        broadcastEvent<Duration>(
            label = "compose_frame_clock"
        )
    }

    var lastTime: Long = -1

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { time ->
                if (lastTime > 0 && !paused.value) {
                    clock.send((time - lastTime).nanoseconds)
                }

                lastTime = time
            }
        }
    }

    return clock
}