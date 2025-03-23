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
import io.github.sintrastes.yafrl.interop.YafrlCompose
import io.github.sintrastes.yafrl.interop.composeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals

object CounterComponent {
    class ViewModel(
        clicks: Event<Unit>
    ) {
        val count = State.fold(0, clicks) { state, _click ->
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

class CounterTest {
    @Test
    fun `Counter resets state after two events with timetravel debugger`() {
        Timeline.initializeTimeline(
            CoroutineScope(Dispatchers.Default),
            debug = true
        )

        val clicks = broadcastEvent<Unit>()

        val viewModel = CounterComponent.ViewModel(clicks)

        assertEquals(0, viewModel.count.value)

        clicks.send(Unit)

        // Note: Shouldn't have to do this to get the click to register.
        viewModel.count.value

        clicks.send(Unit)

        assertEquals(2, viewModel.count.value)

        Timeline.currentTimeline()
            .rollbackState()

        assertEquals(1, viewModel.count.value)
    }

    @Test
    fun `Counter resets state with timetravel debugger`() {
        Timeline.initializeTimeline(
            CoroutineScope(Dispatchers.Default),
            debug = true
        )

        val clicks = broadcastEvent<Unit>()

        val viewModel = CounterComponent.ViewModel(clicks)

        assertEquals(0, viewModel.count.value)

        clicks.send(Unit)

        assertEquals(1, viewModel.count.value)

        Timeline.currentTimeline()
            .rollbackState()

        assertEquals(0, viewModel.count.value)
    }

    // Disabled by default
    // @Test
    fun `run counter example`() {
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
}