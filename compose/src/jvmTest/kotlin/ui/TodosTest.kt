package ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.sintrastes.yafrl.BroadcastEvent
import io.github.sintrastes.yafrl.Event
import io.github.sintrastes.yafrl.State
import io.github.sintrastes.yafrl.broadcastEvent
import io.github.sintrastes.yafrl.internal.Timeline
import io.github.sintrastes.yafrl.interop.YafrlCompose
import io.github.sintrastes.yafrl.interop.composeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test

object TodosComponent {
    data class TodoState(
        val number: Int,
        val contents: String
    )

    class ViewModel(
        clicks: Event<Unit>,
        textUpdates: Event<TodoState>
    ) {
        val items = State
            .fold(listOf<TodoState>(), clicks) { items, _click ->
                items + listOf(TodoState(items.size, ""))
            }
            .fold(textUpdates) { items, update ->
                val newItems = items.toMutableList()

                if (update.number < newItems.size) {
                    newItems[update.number] = update
                }

                newItems
            }
    }

    @Composable
    fun TodoItem(
        updated: BroadcastEvent<TodoState>,
        number: Int,
        contents: String
    ) {
        Row(
            Modifier
                .padding(8.dp)
        ) {
            Text(
                "${number + 1}. ",
                fontWeight = FontWeight.Bold
            )

            BasicTextField(
                value = contents,
                onValueChange = { newValue ->
                updated.send(
                    TodoState(number, newValue)
                )
            })
        }
    }

    @Composable
    fun view() = YafrlCompose {
        val clicks = remember { broadcastEvent<Unit>() }

        val textChanged = remember { broadcastEvent<TodoState>()}

        val viewModel = remember { ViewModel(clicks, textChanged) }

        val todoItems by remember { viewModel.items.composeState() }

        Column {
            Row(
                modifier = Modifier
                    .padding(8.dp)
            ) {
                Text(
                    "Create TODO: ",
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
                        .padding(start = 8.dp)
                        .align(Alignment.CenterVertically)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .padding(16.dp)
            ) {
                items (todoItems) { item ->
                    TodoItem(textChanged, item.number, item.contents)
                }
            }
        }
    }
}

class TodosTest {
    // Disabled by default
    @Test
    fun `run todo list example`() {
        // Open a window with the view.
        application {
            val state = rememberWindowState(
                width = 630.dp,
                height = 370.dp
            )

            Window(
                onCloseRequest = ::exitApplication,
                state = state,
                title = "TODOs"
            ) {
                TodosComponent.view()
            }
        }
    }
}