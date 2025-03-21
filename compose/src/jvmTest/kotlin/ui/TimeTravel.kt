package ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.sintrastes.yafrl.Event
import io.github.sintrastes.yafrl.State
import io.github.sintrastes.yafrl.annotations.FragileYafrlAPI
import io.github.sintrastes.yafrl.internal.Timeline
import io.github.sintrastes.yafrl.internalMutableStateOf
import io.github.sintrastes.yafrl.interop.composeState
import io.github.sintrastes.yafrl.interop.newComposeFrameClock
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Wraps the passed [body] in a container with controls for use in
 *  time-travel debugging.
 **/
@OptIn(FragileYafrlAPI::class)
@Composable
fun TimeTravel(
    body: @Composable (clock: Event<Duration>) -> Unit
) {
    val pausedState = remember {
        // Needs to be internal because we can't undo a "pause" event.
        internalMutableStateOf(false, "paused_state")
    }

    val clock = newComposeFrameClock(pausedState)
    // Theoretically gate should work for this, but not
    // currently working.
    //.gate(pausedState)

    val paused by remember {
        pausedState.composeState()
    }

    val runningAverage = remember {
        clock
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

    Column {
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
        ) {
            Button(
                modifier = Modifier
                    .padding(6.dp),
                enabled = paused,
                content = { Text("<") },
                onClick = {
                    Timeline.currentTimeline()
                        .rollbackState()
                }
            )
            Button(
                modifier = Modifier
                    .padding(6.dp)
                    .width(120.dp),
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
                enabled = paused,
                content = { Text(">") },
                onClick = {
                Timeline.currentTimeline()
                    .nextState()
                }
            )
        }

        body(clock)
    }

    Text(text = "FPS: $fps")
}