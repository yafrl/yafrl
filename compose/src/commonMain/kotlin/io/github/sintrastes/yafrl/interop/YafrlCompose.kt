package io.github.sintrastes.yafrl.interop

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
import androidx.compose.ui.unit.dp
import io.github.sintrastes.yafrl.Behavior
import io.github.sintrastes.yafrl.Event
import io.github.sintrastes.yafrl.State
import io.github.sintrastes.yafrl.annotations.FragileYafrlAPI
import io.github.sintrastes.yafrl.broadcastEvent
import io.github.sintrastes.yafrl.internal.Timeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    showFPS: Boolean = false,
    body: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()

    var clockInitialized by remember { mutableStateOf(false) }

    remember {
        Timeline.initializeTimeline(
            scope = CoroutineScope(Dispatchers.Default),
            debug = timeTravelDebugger,
            initClock = { pausedState ->
                scope.launch {
                    clockInitialized = true
                }

                newComposeFrameClock(scope, pausedState)
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
                            .rollbackState()
                    }
                )

                println("Clock initialized: ${clockInitialized}")

                Button(
                    modifier = Modifier
                        .padding(6.dp)
                        .width(120.dp),
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
                            .nextState()
                    }
                )
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
                    State.fold("--", runningAverage) { _, avgFrameTime ->
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
        }

        body()
    }
}

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
            if (updatedState != state.value) {
                println("Updating state of ${hashCode()}, ${label}: $updatedState")
                state.value = updatedState
            }
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
): Event<Duration> {
    val clock = broadcastEvent<Duration>(
        label = "compose_frame_clock"
    )

    scope.launch {
        var lastTime: Long = -1

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