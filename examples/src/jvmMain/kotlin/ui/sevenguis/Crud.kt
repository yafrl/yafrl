package ui.sevenguis

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.yafrl.Signal
import io.github.yafrl.compose.YafrlCompose
import io.github.yafrl.compose.composeState
import io.github.yafrl.timeline.Timeline
import io.github.yafrl.timeline.TimelineScope

object Crud {
    data class Person(val id: Int, val first: String, val last: String)

    data class State(
        val entries: List<Person>,
        val nextId: Int,
        val selectedId: Int?,
        val filter: String,
        val firstField: String,
        val lastField: String
    )

    private val initialEntries = listOf(
        Person(0, "Hans", "Emil"),
        Person(1, "Max", "Mustermann"),
        Person(2, "Roman", "Tisch")
    )

    class ViewModel(timeline: Timeline) : TimelineScope(timeline) {
        val filterChanged = externalEvent<String>()
        val firstChanged = externalEvent<String>()
        val lastChanged = externalEvent<String>()
        val selectionChanged = externalEvent<Int?>()
        val createClicked = externalEvent<Unit>()
        val updateClicked = externalEvent<Unit>()
        val deleteClicked = externalEvent<Unit>()

        val state: Signal<State> = Signal.fold(
            State(initialEntries, initialEntries.size, null, "", "", ""),
            on(filterChanged) { s, f -> s.copy(filter = f) },
            on(firstChanged) { s, f -> s.copy(firstField = f) },
            on(lastChanged) { s, l -> s.copy(lastField = l) },
            on(selectionChanged) { s, id ->
                val p = s.entries.find { it.id == id }
                s.copy(
                    selectedId = id,
                    firstField = p?.first ?: s.firstField,
                    lastField = p?.last ?: s.lastField
                )
            },
            on(createClicked) { s, _ ->
                val newPerson = Person(s.nextId, s.firstField, s.lastField)
                s.copy(entries = s.entries + newPerson, nextId = s.nextId + 1)
            },
            on(updateClicked) { s, _ ->
                if (s.selectedId == null) s
                else s.copy(entries = s.entries.map { p ->
                    if (p.id == s.selectedId) p.copy(first = s.firstField, last = s.lastField) else p
                })
            },
            on(deleteClicked) { s, _ ->
                s.copy(entries = s.entries.filterNot { it.id == s.selectedId }, selectedId = null)
            }
        )

        val filtered: Signal<List<Person>> = state.map { s ->
            val prefix = s.filter.lowercase()
            s.entries.filter { p ->
                val name = "${p.last}, ${p.first}".lowercase()
                name.contains(prefix)
            }
        }
    }

    @Composable
    fun View() = YafrlCompose {
        val vm = remember { ViewModel(timeline) }
        val state by remember { vm.state.composeState(timeline) }
        val filtered by remember { vm.filtered.composeState(timeline) }

        Row(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.width(250.dp).padding(end = 16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text("Filter prefix: ", modifier = Modifier.padding(end = 4.dp))
                    TextField(
                        value = state.filter,
                        onValueChange = { vm.filterChanged.send(it) },
                        singleLine = true
                    )
                }

                LazyColumn(
                    modifier = Modifier.height(200.dp).fillMaxWidth()
                ) {
                    items(filtered) { person ->
                        val isSelected = person.id == state.selectedId
                        Text(
                            text = "${person.last}, ${person.first}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isSelected) Color(0xFFBBDEFB) else Color.Transparent)
                                .clickable { vm.selectionChanged.send(person.id) }
                                .padding(8.dp)
                        )
                    }
                }
            }

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text("Name: ", modifier = Modifier.width(60.dp))
                    TextField(
                        value = state.firstField,
                        onValueChange = { vm.firstChanged.send(it) },
                        label = { Text("First") },
                        singleLine = true,
                        modifier = Modifier.width(120.dp).padding(end = 8.dp)
                    )
                    TextField(
                        value = state.lastField,
                        onValueChange = { vm.lastChanged.send(it) },
                        label = { Text("Last") },
                        singleLine = true,
                        modifier = Modifier.width(120.dp)
                    )
                }

                Row(modifier = Modifier.padding(top = 8.dp)) {
                    Button(
                        onClick = { vm.createClicked.send(Unit) },
                        enabled = state.firstField.isNotBlank() || state.lastField.isNotBlank(),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Create")
                    }
                    Button(
                        onClick = { vm.updateClicked.send(Unit) },
                        enabled = state.selectedId != null,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Update")
                    }
                    Button(
                        onClick = { vm.deleteClicked.send(Unit) },
                        enabled = state.selectedId != null
                    ) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}

fun main() {
    application {
        val state = rememberWindowState(width = 620.dp, height = 380.dp)
        Window(onCloseRequest = ::exitApplication, state = state, title = "CRUD") {
            Crud.View()
        }
    }
}
