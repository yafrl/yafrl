package ui.sevenguis

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.yafrl.Event
import io.github.yafrl.Signal
import io.github.yafrl.compose.YafrlCompose
import io.github.yafrl.compose.composeState
import io.github.yafrl.timeline.TimelineScope

object Counter {
    class ViewModel(scope: TimelineScope, clicks: Event<Unit>) {
        val count = with(scope) {
            Signal.fold(0, clicks) { state, _ -> state + 1 }
        }
    }

    @Composable
    fun View() = YafrlCompose {
        val clicks = remember { externalEvent<Unit>() }
        val vm = remember { ViewModel(this, clicks) }
        val count by remember { vm.count.composeState(timeline) }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Button(
                onClick = { clicks.send(Unit) },
                modifier = Modifier.padding(end = 16.dp)
            ) {
                Text("Count")
            }
            Text("$count")
        }
    }
}

fun main() {
    application {
        val state = rememberWindowState(width = 300.dp, height = 150.dp)
        Window(onCloseRequest = ::exitApplication, state = state, title = "Counter") {
            Counter.View()
        }
    }
}
