package ui.sevenguis

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object FlightBooker {
    enum class FlightType(val label: String) {
        ONE_WAY("one-way flight"),
        RETURN("return flight")
    }

    data class State(
        val flightType: FlightType,
        val departText: String,
        val returnText: String,
        val booked: String?
    )

    private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun parseDate(text: String): LocalDate? = try {
        LocalDate.parse(text, dateFormat)
    } catch (e: DateTimeParseException) {
        null
    }

    fun isBookable(state: State): Boolean {
        val depart = parseDate(state.departText) ?: return false
        return when (state.flightType) {
            FlightType.ONE_WAY -> true
            FlightType.RETURN -> {
                val ret = parseDate(state.returnText) ?: return false
                !ret.isBefore(depart)
            }
        }
    }

    private fun buildBookingMessage(state: State): String {
        return when (state.flightType) {
            FlightType.ONE_WAY -> "You have booked a one-way flight on ${state.departText}."
            FlightType.RETURN -> "You have booked a return flight departing on ${state.departText} and returning on ${state.returnText}."
        }
    }

    private fun today(): String = LocalDate.now().format(dateFormat)

    class ViewModel(timeline: Timeline) : TimelineScope(timeline) {
        val flightTypeChanged = externalEvent<FlightType>()
        val departChanged = externalEvent<String>()
        val returnChanged = externalEvent<String>()
        val bookClicked = externalEvent<Unit>()

        val state: Signal<State> = Signal.fold(
            State(FlightType.ONE_WAY, today(), today(), null),
            on(flightTypeChanged) { s, t -> s.copy(flightType = t, booked = null) },
            on(departChanged) { s, d -> s.copy(departText = d, booked = null) },
            on(returnChanged) { s, r -> s.copy(returnText = r, booked = null) },
            on(bookClicked) { s, _ ->
                if (isBookable(s)) s.copy(booked = buildBookingMessage(s)) else s
            }
        )
    }

    @Composable
    fun View() = YafrlCompose {
        val vm = remember { ViewModel(timeline) }
        val state by remember { vm.state.composeState(timeline) }

        var dropdownExpanded by remember { mutableStateOf(false) }

        val departValid = parseDate(state.departText) != null
        val returnValid = parseDate(state.returnText) != null

        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedButton(
                onClick = { dropdownExpanded = true },
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(state.flightType.label)
            }

            DropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false }
            ) {
                FlightType.entries.forEach { type ->
                    DropdownMenuItem(onClick = {
                        vm.flightTypeChanged.send(type)
                        dropdownExpanded = false
                    }) {
                        Text(type.label)
                    }
                }
            }

            TextField(
                value = state.departText,
                onValueChange = { vm.departChanged.send(it) },
                label = { Text("Departure date") },
                colors = TextFieldDefaults.textFieldColors(
                    backgroundColor = if (departValid) Color.Unspecified else Color(0xFFFFCCCC)
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            TextField(
                value = state.returnText,
                onValueChange = { if (state.flightType == FlightType.RETURN) vm.returnChanged.send(it) },
                label = { Text("Return date") },
                enabled = state.flightType == FlightType.RETURN,
                colors = TextFieldDefaults.textFieldColors(
                    backgroundColor = when {
                        state.flightType == FlightType.ONE_WAY -> Color.Unspecified
                        returnValid -> Color.Unspecified
                        else -> Color(0xFFFFCCCC)
                    }
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Button(
                onClick = { vm.bookClicked.send(Unit) },
                enabled = isBookable(state),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text("Book")
            }

            state.booked?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

fun main() {
    application {
        val state = rememberWindowState(width = 400.dp, height = 380.dp)
        Window(onCloseRequest = ::exitApplication, state = state, title = "Flight Booker") {
            FlightBooker.View()
        }
    }
}
