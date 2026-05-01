package ui.sevenguis

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.yafrl.Signal
import io.github.yafrl.compose.YafrlCompose
import io.github.yafrl.compose.composeState
import io.github.yafrl.timeline.Timeline
import io.github.yafrl.timeline.TimelineScope

object Timer {
    class ViewModel(timeline: Timeline) : TimelineScope(timeline) {
        val duration = externalSignal(15f)
        val reset = externalEvent<Unit>()

        val elapsed: Signal<Float> = Signal.fold(
            0f,
            on(clock.map { it.inWholeMilliseconds.toFloat() / 1000f }) { acc, dt ->
                minOf(acc + dt, duration.currentValue())
            },
            on(reset) { _, _ -> 0f }
        )
    }

    @Composable
    fun View() = YafrlCompose {
        val vm = remember { ViewModel(timeline) }
        val elapsed by remember { vm.elapsed.composeState(timeline) }
        val duration by remember { vm.duration.composeState(timeline) }

        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.padding(bottom = 8.dp)) {
                Text("Elapsed Time: ", modifier = Modifier.alignByBaseline())
                Text("${"%.1f".format(elapsed)}s", modifier = Modifier.alignByBaseline())
            }

            LinearProgressIndicator(
                progress = if (duration > 0f) (elapsed / duration).coerceIn(0f, 1f) else 0f,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            Row(modifier = Modifier.padding(bottom = 8.dp)) {
                Text("Duration: ", modifier = Modifier.alignByBaseline())
                Text("${"%.1f".format(duration)}s", modifier = Modifier.alignByBaseline())
            }

            Slider(
                value = duration,
                onValueChange = { vm.duration.value = it },
                valueRange = 0f..30f,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            Button(onClick = { vm.reset.send(Unit) }) {
                Text("Reset Timer")
            }
        }
    }
}

fun main() {
    application {
        val state = rememberWindowState(width = 350.dp, height = 250.dp)
        Window(onCloseRequest = ::exitApplication, state = state, title = "Timer") {
            Timer.View()
        }
    }
}
