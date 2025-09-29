package ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import arrow.core.NonEmptyList
import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import arrow.core.toNonEmptyListOrNull
import io.github.yafrl.*
import io.github.yafrl.behaviors.*
import io.github.yafrl.compose.YafrlCompose
import io.github.yafrl.compose.composeState
import io.github.yafrl.timeline.Timeline
import io.github.yafrl.timeline.TimelineScope
import io.github.yafrl.vector.Float2
import kotlin.math.pow
import kotlin.math.sqrt

object PolygonExample {
    @JvmInline
    value class VertexID(val value: Int) {
        companion object {
            private var latestID = -1

            fun new() = run {
                latestID++
                VertexID(latestID)
            }
        }
    }

    @JvmInline
    value class PolygonID(val value: Int) {
        companion object {
            private var latestID = -1

            fun new() = run {
                latestID++
                PolygonID(latestID)
            }
        }
    }

    /** Handle to a vertex of a polygon currently being edited. */
    data class VertexHandle(
        private val id: VertexID,
        val position: Float2,
        val dragging: Boolean,
        val beginDrag: BroadcastEvent<Unit>,
        val endDrag: BroadcastEvent<Unit>,
        val delete: BroadcastEvent<Unit>
    )

    data class Polygon(
        val id: PolygonID,
        val positions: NonEmptyList<Float2>,
        val edit: BroadcastEvent<Unit>,
        val delete: BroadcastEvent<Unit>
    )

    // threshold for “close to first point”
    val delta = 10f

    fun distance(a: Float2, b: Float2) = sqrt(
        (a.x - b.x).pow(2) + (a.y - b.y).pow(2)
    )

    class ViewModel(
        timeline: Timeline,
        val createPolygonButton: BroadcastEvent<Unit> = timeline.timelineScope
            .externalEvent<Unit>("create_polygon_click"),
        val clicks: BroadcastEvent<Unit> = timeline.timelineScope
            .externalEvent<Unit>("click"),
        val mousePosition: BindingSignal<Float2> = timeline.timelineScope
            .externalSignal<Float2>(Float2(0f, 0f), "mouse_position")
    ) : TimelineScope(timeline) {
        val editingPolygon = Signal.fold<Option<PolygonID>, _>(None, createPolygonButton) { editing, _ ->
            if (editing.isSome()) editing else Some(PolygonID.new())
        }

        // A vertex is added if we click while editing a polygon.
        val addVertex: Event<Float2> = clicks
            .gate(editingPolygon.asBehavior().map { it.isNone() })
            // A vertex is added at the current mouse position.
            .tag(mousePosition.asBehavior())

        private val vertexData = Signal.fold(
            initial = listOf<Pair<VertexID, Float2>>(),
            on(addVertex) { handles, _ ->
                val newPosition = mousePosition.currentValue()

                if (handles.isEmpty() || distance(newPosition, handles.first().second) > delta) {
                    handles + listOf(VertexID.new() to newPosition)
                } else {
                    // Completed the polygon, so clear the active vertices.
                    listOf()
                }
            }
        )

        val vertices: Signal<List<VertexHandle>> = vertexData.flatMap { handleData ->
            handleData.map { (id, initialPosition) ->
                val beginDrag = externalEvent<Unit>("begin_drag")
                val endDrag = externalEvent<Unit>("end_drag")
                val delete = externalEvent<Unit>("delete")

                Signal.fold<Option<VertexHandle>>(
                    initial = Some(
                        VertexHandle(
                            id,
                            initialPosition,
                            false,
                            beginDrag,
                            endDrag,
                            delete
                        )
                    ),
                    on(delete) { _, _ ->
                        None
                    },
                    on(mousePosition.updated()) { handle, newPosition ->
                        handle.map {
                            if (it.dragging) it.copy(position = newPosition) else it
                        }
                    },
                    on(beginDrag) { handle, _ ->
                        handle.map { it.copy(dragging = true) }
                    },
                    on(endDrag) { handle, _ ->
                        handle.map { it.copy(dragging = false) }
                    }
                )
            }
                .sequenceState()
                .map { it.mapNotNull { it.getOrNull() } }
        }

        // A polygon is added whenever the added point closes a new polygon.
        val addPolygon: Event<Polygon> = addVertex
            .filter { vertexToAdd ->
                val currentVertices = sample { vertices.currentValue() }

                println("Checking if adding polygon, ${vertexToAdd}, ${currentVertices.firstOrNull()?.position}")

                currentVertices.size >= 3 &&
                        distance(vertexToAdd, currentVertices.first().position) < delta
            }
            .map { vertexToAdd ->
                val edit = externalEvent<Unit>()
                val delete = externalEvent<Unit>()

                println("Adding polygon")

                Polygon(
                    PolygonID.new(),
                    (vertices.currentValue().map { it.position } + listOf(vertexToAdd))
                        .toNonEmptyListOrNull()!!,
                    edit,
                    delete
                )
            }

        val polygons: Signal<List<Polygon>> = Signal.fold(
            listOf(),
            on(addPolygon) { polygons, newPolygon ->
                println("Adding polygon with ${newPolygon.positions.size} points")
                polygons + listOf(newPolygon)
            }
        )
    }

    @Composable
    fun view() = YafrlCompose {
        val viewModel = remember { ViewModel(timeline) }

        val polygons by remember { viewModel.polygons.composeState(timeline) }
        val vertices by remember { viewModel.vertices.composeState(timeline) }
        val mousePosition by remember { viewModel.mousePosition.composeState(timeline) }
        // TODO: Laziness bug. This has to be observed
        val editingPolygon by remember { viewModel.editingPolygon.composeState(timeline) }

        Column {
            Button(
                content = { Text("Create New Polygon") },
                onClick = { viewModel.createPolygonButton.send(Unit) }
            )

            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while(true) {
                                val event = awaitPointerEvent()

                                if (event.type == PointerEventType.Move) {
                                    val position = event.changes.first().position
                                    viewModel.mousePosition.value = Float2(
                                        position.x, position.y
                                    )
                                }

                                if (event.type == PointerEventType.Press) {
                                    viewModel.clicks.send(Unit)
                                }
                            }
                        }

                    }
                    .border(BorderStroke(1.dp, Color.Black))
            ) {
                // Draw completed polygons
                for (polygon in polygons) {
                    drawPath(
                        path = androidx.compose.ui.graphics.Path().apply {
                            polygon.positions.mapIndexed { i, point ->
                                if (i == 0) {
                                    moveTo(point.x, point.y)
                                } else {
                                    lineTo(point.x, point.y)
                                }
                            }
                        },
                        color = Color.Black
                    )
                }

                // Draw currently editing polygon
                if (vertices.isNotEmpty()) {
                    drawPath(
                        path = androidx.compose.ui.graphics.Path().apply {
                            vertices.mapIndexed { i, vertex ->
                                if (i == 0) {
                                    moveTo(vertex.position.x, vertex.position.y)
                                } else {
                                    lineTo(vertex.position.x, vertex.position.y)
                                }
                            }
                        },
                        color = Color.Black,
                        style = Stroke(3f)
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
            PolygonExample.view()
        }
    }
}