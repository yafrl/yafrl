package ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.sintrastes.yafrl.Event
import io.github.sintrastes.yafrl.State
import io.github.sintrastes.yafrl.broadcastEvent
import io.github.sintrastes.yafrl.internal.Timeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

object CounterComponent {
    class ViewModel(
        clicks: Event<Unit>
    ) {
        val count = State.fold(0, clicks) { state, _click ->
            state + 1
        }
    }

    @Composable
    fun View() {
        val clicks = remember { broadcastEvent<Unit>() }
        val viewModel = remember { ViewModel(clicks) }

        val count by viewModel.count
            .collectAsState(0)

        Column {
            Row(
                modifier = Modifier
                    .padding(16.dp)
            ) {
                Text(
                    "Increment: ",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                )

                Button(
                    content = { Text("Click me!") },
                    onClick = {
                        clicks.send(Unit)
                    },
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .align(Alignment.CenterVertically)
                )
            }

            Row(
                modifier = Modifier
                    .padding(16.dp)
            ) {
                Text(
                    "Count: ",
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "$count"
                )
            }
        }
    }
}

class CounterTest {
    // Disabled by default
    // @Test
    fun `run counter example`() {
        Timeline.initializeTimeline(
            CoroutineScope(Dispatchers.Default)
        )

        // Open a window with the view.
        application {
            val state = rememberWindowState(
                width = 230.dp,
                height = 170.dp
            )

            Window(
                onCloseRequest = ::exitApplication,
                state = state,
                title = "Counter"
            ) {
                CounterComponent.View()
            }
        }
    }
}