package ui.sevenguis

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import arrow.core.None
import arrow.core.Option
import arrow.core.toOption
import io.github.yafrl.Signal
import io.github.yafrl.compose.YafrlCompose
import io.github.yafrl.compose.composeState
import io.github.yafrl.timeline.Timeline
import io.github.yafrl.timeline.TimelineScope
import kotlin.math.pow
import kotlin.math.sqrt

object CircleDrawer {
    data class Circle(val id: Int, val cx: Float, val cy: Float, val r: Float)

    data class State(
        val circles: List<Circle>,
        val nextId: Int,
        val undoStack: List<List<Circle>>,
        val redoStack: List<List<Circle>>,
        // Transient adjustment state (live, not committed until window closes)
        val adjustingId: Int?,
        val adjustingStartR: Float,
        val liveR: Float
    )

    private fun dist(c: Circle, o: Offset) =
        sqrt((c.cx - o.x).pow(2) + (c.cy - o.y).pow(2))

    class ViewModel(timeline: Timeline) : TimelineScope(timeline) {
        val mousePosition = externalSignal(Offset(Float.NaN, Float.NaN))
        val canvasLeftClicked = externalEvent<Offset>()
        val startAdjusting = externalEvent<Int>()  // carries the circle ID to adjust
        val adjustRadius = externalEvent<Float>()
        val closeAdjusting = externalEvent<Unit>()
        val undoClicked = externalEvent<Unit>()
        val redoClicked = externalEvent<Unit>()

        val state: Signal<State> = Signal.fold(
            State(emptyList(), 0, emptyList(), emptyList(), null, 0f, 0f),
            on(canvasLeftClicked) { s, offset ->
                val clickedOnCircle = s.circles.any { dist(it, offset) < it.r }
                if (clickedOnCircle) s
                else {
                    val newCircle = Circle(s.nextId, offset.x, offset.y, 25f)
                    s.copy(
                        circles = s.circles + newCircle,
                        nextId = s.nextId + 1,
                        undoStack = s.undoStack + listOf(s.circles),
                        redoStack = emptyList()
                    )
                }
            },
            on(startAdjusting) { s, id ->
                val circle = s.circles.find { it.id == id }
                if (circle == null) s
                else s.copy(adjustingId = id, adjustingStartR = circle.r, liveR = circle.r)
            },
            on(adjustRadius) { s, r -> s.copy(liveR = r) },
            on(closeAdjusting) { s, _ ->
                if (s.adjustingId == null) s
                else {
                    val updatedCircles = s.circles.map {
                        if (it.id == s.adjustingId) it.copy(r = s.liveR) else it
                    }
                    val didChange = s.liveR != s.adjustingStartR
                    s.copy(
                        circles = updatedCircles,
                        adjustingId = null,
                        undoStack = if (didChange) s.undoStack + listOf(s.circles) else s.undoStack,
                        redoStack = if (didChange) emptyList() else s.redoStack
                    )
                }
            },
            on(undoClicked) { s, _ ->
                if (s.undoStack.isEmpty()) s
                else s.copy(
                    circles = s.undoStack.last(),
                    undoStack = s.undoStack.dropLast(1),
                    redoStack = s.redoStack + listOf(s.circles)
                )
            },
            on(redoClicked) { s, _ ->
                if (s.redoStack.isEmpty()) s
                else s.copy(
                    circles = s.redoStack.last(),
                    redoStack = s.redoStack.dropLast(1),
                    undoStack = s.undoStack + listOf(s.circles)
                )
            }
        )

        // Display: show live radius for the circle being adjusted
        val displayCircles: Signal<List<Circle>> = state.map { s ->
            if (s.adjustingId == null) s.circles
            else s.circles.map { if (it.id == s.adjustingId) it.copy(r = s.liveR) else it }
        }

        // Hovered circle: nearest circle within its radius from mouse pointer
        val hoveredCircleId: Signal<Option<Int>> = mousePosition.combineWith(displayCircles) { pos, circles ->
            if (pos.x.isNaN()) None
            else circles
                .filter { dist(it, pos) < it.r }
                .minByOrNull { dist(it, pos) }
                ?.id.toOption()
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    fun View() = YafrlCompose {
        val vm = remember { ViewModel(timeline) }
        val state by remember { vm.state.composeState(timeline) }
        val displayCircles by remember { vm.displayCircles.composeState(timeline) }
        val hoveredCircleId by remember { vm.hoveredCircleId.composeState(timeline) }

        Column(modifier = Modifier.padding(8.dp)) {
            Row(modifier = Modifier.padding(bottom = 8.dp)) {
                Button(
                    onClick = { vm.undoClicked.send(Unit) },
                    enabled = state.undoStack.isNotEmpty() && state.adjustingId == null,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("Undo")
                }
                Button(
                    onClick = { vm.redoClicked.send(Unit) },
                    enabled = state.redoStack.isNotEmpty() && state.adjustingId == null
                ) {
                    Text("Redo")
                }
            }

            ContextMenuArea(items = {
                val id = hoveredCircleId.getOrNull()
                if (id != null) {
                    listOf(ContextMenuItem("Adjust diameter..") {
                        vm.startAdjusting.send(id)
                    })
                } else {
                    emptyList()
                }
            }) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .border(BorderStroke(1.dp, Color.Black))
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    when (event.type) {
                                        PointerEventType.Move, PointerEventType.Enter -> {
                                            vm.mousePosition.value = event.changes.first().position
                                        }
                                        PointerEventType.Exit -> {
                                            vm.mousePosition.value = Offset(Float.NaN, Float.NaN)
                                        }
                                        PointerEventType.Press -> {
                                            if (event.button == PointerButton.Primary) {
                                                vm.canvasLeftClicked.send(event.changes.first().position)
                                            }
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        }
                ) {
                    for (circle in displayCircles) {
                        val isHovered = circle.id == hoveredCircleId.getOrNull()
                        drawCircle(
                            color = if (isHovered) Color.Gray else Color.Transparent,
                            radius = circle.r,
                            center = Offset(circle.cx, circle.cy)
                        )
                        drawCircle(
                            color = Color.Black,
                            radius = circle.r,
                            center = Offset(circle.cx, circle.cy),
                            style = Stroke(width = 1.5f)
                        )
                    }
                }
            }
        }

        // Separate window for diameter adjustment
        if (state.adjustingId != null) {
            Window(
                onCloseRequest = { vm.closeAdjusting.send(Unit) },
                title = "Adjust Diameter",
                state = rememberWindowState(width = 320.dp, height = 160.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Adjust diameter of selected circle:")
                    Slider(
                        value = state.liveR,
                        onValueChange = { vm.adjustRadius.send(it) },
                        valueRange = 5f..150f,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    Text("Diameter: ${"%.0f".format(state.liveR * 2)}px")
                }
            }
        }
    }
}

fun main() {
    application {
        val windowState = rememberWindowState(width = 520.dp, height = 540.dp)
        Window(onCloseRequest = ::exitApplication, state = windowState, title = "Circle Drawer") {
            CircleDrawer.View()
        }
    }
}
