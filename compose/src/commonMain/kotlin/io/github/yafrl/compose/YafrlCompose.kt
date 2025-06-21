package io.github.yafrl.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.yafrl.behaviors.Behavior
import io.github.yafrl.Event
import io.github.yafrl.Signal
import io.github.yafrl.throttled
import io.github.yafrl.annotations.FragileYafrlAPI
import io.github.yafrl.asBehavior
import io.github.yafrl.externalEvent
import io.github.yafrl.timeline.Timeline
import io.github.yafrl.timeline.current
import io.github.yafrl.sample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Wraps the passed [body] in a container with controls for use in
 *  time-travel debugging.
 **/
@OptIn(FragileYafrlAPI::class)
@Composable
fun YafrlCompose(
    timeTravelDebugger: Boolean = false,
    debugLogs: Boolean = false,
    showFPS: Boolean = false,
    lazy: Boolean = true,
    body: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()

    var clockInitialized by remember { mutableStateOf(false) }

    remember {
        Timeline.initializeTimeline(
            scope = CoroutineScope(Dispatchers.Default),
            debug = debugLogs,
            timeTravel = timeTravelDebugger,
            lazy = lazy,
            initClock = { pausedState ->
                scope.launch {
                    clockInitialized = true
                }

                newComposeFrameClock(scope, pausedState.asBehavior())
            }
        )
    }

    Column {
        if (timeTravelDebugger) {
            val pausedState = remember { Timeline.currentTimeline().pausedState }

            val paused by remember {
                pausedState.composeState()
            }

            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp)
            ) {
                Button(
                    modifier = Modifier
                        .padding(6.dp),
                    enabled = paused || !clockInitialized,
                    content = { Text("<") },
                    onClick = {
                        Timeline.currentTimeline()
                            .timeTravel.rollbackState()
                    }
                )

                Button(
                    modifier = Modifier
                        .padding(6.dp)
                        .width(120.dp)
                        .semantics { contentDescription = "Pause button" },
                    enabled = clockInitialized,
                    content = {
                        if (!paused) {
                            Text("Pause")
                        } else {
                            Text("Un-pause")
                        }
                    },
                    onClick = {
                        pausedState.value = !paused
                    }
                )


                Button(
                    modifier = Modifier.padding(6.dp),
                    enabled = paused || !clockInitialized,
                    content = { Text(">") },
                    onClick = {
                        Timeline.currentTimeline()
                            .timeTravel.nextState()
                    }
                )
            }
        }

        if (showFPS && clockInitialized) {
            val runningAverage = remember {
                Timeline.currentTimeline().clock
                    .window(10)
                    .map {
                        if (it.isNotEmpty()) it.sumOf { it.inWholeMilliseconds } / it.size else null
                    }
                    .throttled(0.5.seconds)
            }

            val fps by remember {
                Signal.fold("--", runningAverage) { _, avgFrameTime ->
                    if (avgFrameTime != null) {
                        (1000f / avgFrameTime).roundToInt().toString()
                    } else {
                        "--"
                    }
                }
                    .composeState()
            }

            Text(text = "FPS: $fps")
        }

        body()
    }
}

/**
 * Utility function to assist with using a [io.github.yafrl.Signal]
 *  in Jetpack Compose, by converting it to a [androidx.compose.runtime.State].
 **/
@OptIn(FragileYafrlAPI::class)
fun <A> Signal<A>.composeState(): androidx.compose.runtime.State<A> {
    val state = mutableStateOf(value = node.current())

    collectSync { updatedState ->
        if (updatedState != state.value) {
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
fun newComposeFrameClock(
    scope: CoroutineScope,
    paused: Behavior<Boolean>
): Event<Duration> = sample {
    val clock = externalEvent<Duration>(
        label = "compose_frame_clock"
    )

    scope.launch {
        var lastTime: Long = -1

        while (isActive) {
            withFrameNanos { time ->
                if (lastTime > 0 && !paused.sampleValue()) {
                    clock.send((time - lastTime).nanoseconds)
                }

                lastTime = time
            }
        }
    }

    clock
}