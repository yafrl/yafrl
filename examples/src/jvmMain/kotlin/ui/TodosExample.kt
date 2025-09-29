package ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.yafrl.Event
import io.github.yafrl.Signal
import io.github.yafrl.compose.YafrlCompose
import io.github.yafrl.compose.composeState
import io.github.yafrl.timeline.Timeline
import io.github.yafrl.timeline.TimelineScope
import kotlin.collections.plus

object TodosComponent {
    data class TodoState(
        val completed: Boolean,
        val number: Int,
        val contents: String
    )

    class ViewModel(timeline: Timeline): TimelineScope(timeline) {
        fun addNew() = clicks.send(Unit)

        fun dismissCompleted() = dismissEvent.send(Unit)

        private val dismissEvent = externalEvent<Unit>()
        private val clicks = externalEvent<Unit>()
        val textUpdates = externalEvent<TodoState>()
        val markComplete = externalEvent<Int>()

        private val actions = Event.merged(
            clicks.map {
                { items: List<TodoState> ->
                    items + listOf(TodoState(false, items.size, ""))
                }
            },
            textUpdates.map { update ->
                { items ->
                    val newItems = items.toMutableList()

                    if (update.number < newItems.size) {
                        newItems[update.number] = update
                    }

                    newItems
                }
            },
            markComplete.map { index ->
                { items ->
                    val newItems = items.toMutableList()

                    val item = newItems[index]

                    newItems[index] = item.copy(completed = true)

                    newItems
                }
            },
            dismissEvent.map {
                { items ->
                    items
                        // Remove any completed items
                        .filterNot(TodoState::completed)
                        // Update numberings
                        .mapIndexed { i, item -> item.copy(number = i) }
                }
            }
        )

        val items = Signal.fold(listOf<TodoState>(), actions) { state, action ->
            action(state)
        }
    }

    @Composable
    fun TodoItem(
        item: TodoState,
        viewModel: ViewModel,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(8.dp)
        ) {
            Text(
                modifier = Modifier
                    .padding(8.dp),
                text = "${item.number + 1}. ",
                fontWeight = FontWeight.Bold
            )

            TextField(
                modifier = Modifier
                    .width(500.dp),
                enabled = !item.completed,
                value = item.contents,
                onValueChange = { newValue ->
                    viewModel.textUpdates.send(
                        item.copy(contents = newValue)
                    )
                })

            Checkbox(
                checked = item.completed,
                onCheckedChange = {
                    viewModel.markComplete
                        .send(item.number)
                }
            )
        }
    }

    @Composable
    fun view() = YafrlCompose {
        val viewModel = remember { ViewModel(timeline) }

        val todoItems by remember { viewModel.items.composeState(timeline) }

        Column {
            Row(
                modifier = Modifier
                    .padding(8.dp)
            ) {
                Button(
                    content = { Text("New TODO") },
                    onClick = viewModel::addNew,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .align(Alignment.CenterVertically)
                )

                Button(
                    content = { Text("Dismiss Completed") },
                    onClick = viewModel::dismissCompleted
                )
            }

            LazyColumn(
                modifier = Modifier
                    .padding(16.dp)
            ) {
                items(todoItems) { item ->
                    TodoItem(item, viewModel)
                }
            }
        }
    }
}

fun main(args: Array<String>) {
    // Open a window with the view.
    application {
        val state = rememberWindowState(
            width = 626.dp,
            height = 1028.dp
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