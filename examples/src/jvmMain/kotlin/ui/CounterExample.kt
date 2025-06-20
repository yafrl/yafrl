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
import io.github.sintrastes.yafrl.Signal
import io.github.sintrastes.yafrl.broadcastEvent
import io.github.sintrastes.yafrl.compose.YafrlCompose
import io.github.sintrastes.yafrl.compose.composeState

object CounterComponent {
    class ViewModel(
        clicks: Event<Unit>
    ) {
        val count = Signal.fold(0, clicks) { state, _click ->
            state + 1
        }
    }

    @Composable
    fun View() = YafrlCompose(
        timeTravelDebugger = true
    ) {
        val clicks = remember { broadcastEvent<Unit>() }
        val viewModel = remember { ViewModel(clicks) }

        val count by remember { viewModel.count.composeState() }

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

fun main(args: Array<String>) {
    // Open a window with the view.
    application {
        val state = rememberWindowState(
            width = 330.dp,
            height = 270.dp
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