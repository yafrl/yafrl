package ui.sevenguis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.material.TextField
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
import io.github.yafrl.timeline.Timeline
import io.github.yafrl.timeline.TimelineScope

object TemperatureConverter {
    enum class Source { CELSIUS, FAHRENHEIT }
    data class TempState(val text: String, val source: Source)

    class ViewModel(timeline: Timeline) : TimelineScope(timeline) {
        val celsiusInput = externalEvent<String>()
        val fahrenheitInput = externalEvent<String>()

        private val state = Signal.fold(
            TempState("0", Source.CELSIUS),
            Event.merged(
                celsiusInput.map { TempState(it, Source.CELSIUS) },
                fahrenheitInput.map { TempState(it, Source.FAHRENHEIT) }
            )
        ) { _, new -> new }

        val celsiusDisplay: Signal<String> = state.map { s ->
            when (s.source) {
                Source.CELSIUS -> s.text
                Source.FAHRENHEIT -> s.text.toDoubleOrNull()
                    ?.let { "%.2f".format((it - 32.0) * 5.0 / 9.0) }
                    ?: ""
            }
        }

        val fahrenheitDisplay: Signal<String> = state.map { s ->
            when (s.source) {
                Source.FAHRENHEIT -> s.text
                Source.CELSIUS -> s.text.toDoubleOrNull()
                    ?.let { "%.2f".format(it * 9.0 / 5.0 + 32.0) }
                    ?: ""
            }
        }
    }

    @Composable
    fun View() = YafrlCompose {
        val vm = remember { ViewModel(timeline) }
        val celsius by remember { vm.celsiusDisplay.composeState(timeline) }
        val fahrenheit by remember { vm.fahrenheitDisplay.composeState(timeline) }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            TextField(
                value = celsius,
                onValueChange = { vm.celsiusInput.send(it) },
                label = { Text("Celsius") },
                modifier = Modifier.width(150.dp)
            )
            Text("=")
            TextField(
                value = fahrenheit,
                onValueChange = { vm.fahrenheitInput.send(it) },
                label = { Text("Fahrenheit") },
                modifier = Modifier.width(150.dp)
            )
        }
    }
}

fun main() {
    application {
        val state = rememberWindowState(width = 400.dp, height = 150.dp)
        Window(onCloseRequest = ::exitApplication, state = state, title = "Temperature Converter") {
            TemperatureConverter.View()
        }
    }
}
