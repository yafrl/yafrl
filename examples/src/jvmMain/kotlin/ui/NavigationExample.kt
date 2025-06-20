package ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Button
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.sintrastes.yafrl.Event
import io.github.sintrastes.yafrl.broadcastEvent
import io.github.sintrastes.yafrl.compose.YafrlCompose
import io.github.sintrastes.yafrl.compose.composeState
import io.github.sintrastes.yafrl.externalSignal

class NavigationComponent {
    sealed class ScreenData {
        data class Counter1(val count: Int) : ScreenData()

        data class Counter2(val count: Int) : ScreenData()
    }

    class ViewModel(
        counter1Clicks: Event<Unit>,
        counter2Clicks: Event<Unit>,
        currentScreen: io.github.sintrastes.yafrl.Signal<String>
    ) {
        val counter1 = CounterComponent.ViewModel(counter1Clicks).count

        val counter2 = CounterComponent.ViewModel(counter2Clicks).count

        val screenData = currentScreen.flatMap { screen: String ->
            if (screen == "Screen 1") {
                counter1.map { ScreenData.Counter1(it) }
            } else {
                counter2.map { ScreenData.Counter2(it) }
            }
        }
    }

    @Composable
    fun view() = YafrlCompose {
        val clicks1 = remember { broadcastEvent<Unit>() }

        val clicks2 = remember { broadcastEvent<Unit>() }

        val tabIndexState = remember { externalSignal(0) }

        val tabs = remember { listOf("Screen 1", "Screen 2") }

        val selectedState = remember { tabIndexState.map { tabs[it] } }

        val tabIndex by remember { tabIndexState.composeState() }

        val viewModel = remember { ViewModel(clicks1, clicks2, selectedState) }

        val screenData by remember {
            viewModel.screenData
                .composeState()
        }

        TabRow(
            selectedTabIndex = tabIndex,
            tabs = {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        text = { Text(title) },
                        selected = tabIndex == index,
                        onClick = { tabIndexState.value = index }
                    )
                }
            }
        )

        when (screenData) {
            is ScreenData.Counter1 -> {
                Column {
                    Text("Count: ")
                    Text((screenData as ScreenData.Counter1).count.toString())
                    Button(
                        content = { Text("Increment") },
                        onClick = {
                            clicks1.send(Unit)
                        }
                    )
                }
            }

            is ScreenData.Counter2 -> {
                Column {
                    Text("Count: ")
                    Text((screenData as ScreenData.Counter2).count.toString())
                    Button(
                        content = { Text("Increment") },
                        onClick = {
                            clicks2.send(Unit)
                        }
                    )
                }
            }
        }
    }
}

fun main(args: Array<String>) {
    application {
        val state = rememberWindowState(
            width = 330.dp,
            height = 270.dp
        )

        Window(
            onCloseRequest = ::exitApplication,
            state = state,
            title = "Navigation Test"
        ) {
            NavigationComponent().view()
        }
    }
}