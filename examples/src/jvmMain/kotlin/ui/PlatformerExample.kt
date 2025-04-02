package ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.sintrastes.yafrl.Behavior.Companion.integral
import io.github.sintrastes.yafrl.*
import io.github.sintrastes.yafrl.State.Companion.const
import io.github.sintrastes.yafrl.internal.Timeline
import io.github.sintrastes.yafrl.interop.composeState
import io.github.sintrastes.yafrl.interop.YafrlCompose
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.Duration

val tileSize = 42.dp

val width = 2000f.dp

val height = 800f.dp

@Composable
fun size() = with(LocalDensity.current) { Size(width.toPx(), height.toPx()) }

object PlatformerComponent {
    data class Entity(
        val position: Offset,
        val size: Float,
        val render: DrawScope.() -> Unit
    )

    class ViewModel(
        private val maxSize: Size,
        private val deltaTime: Event<Duration>,
        private val tileHeight: Float,
        private val tileset: ImageBitmap,
        private val player: ImageBitmap
    ) {
        val clicked = broadcastEvent<Offset>("clicked")

        val clicks = State.fold(listOf<State<Entity>>(), clicked) { clicked, click ->
            clicked + entity(
                click,
                accelerating(220f, 350f),
            )
        }

        val spawned = clicks.flatMap { clicks ->
            clicks
                .sequenceState()
        }

        val tiles = const(
            listOf<Entity>(
                *(10..16).map {
                    tile(IntOffset(it, 15))
                }.toTypedArray(),

                *(25..31).map {
                    tile(IntOffset(it, 25))
                }.toTypedArray(),

                *(0..120).map { x ->
                    tile(IntOffset(x, 36))
                }.toTypedArray()
            )
        )

        /**
         * Creates a speed [v] that is accelerating by [dv]
         **/
        fun accelerating(v: Float, dv: Float) = const(v) + integral(const(dv))

        fun entities() = State.combineAll(
            entity(
                Offset(maxSize.width / 2, 0f),
                accelerating(420f, 350f),
            ),
            entity(
                Offset(maxSize.width / 3, 100f),
                accelerating(430f, 350f),
            ),
            entity(
                Offset(2 * maxSize.width / 3, 50f),
                accelerating(440f, 350f),
            )
        ).combineWith(spawned) { initial, spawned ->
            initial + spawned
        }.combineWith(tiles) { entities, tiles ->
            tiles + entities
        }

        /** Creates a tile */
        fun tile(
            position: IntOffset
        ): Entity {
            val targetPosition = Offset(position.x * tileSize.value, position.y * tileSize.value)
            return Entity(
                position = targetPosition,
                size = tileHeight,
                render = {
                    drawImage(
                        image = tileset,
                        srcSize = IntSize(16, 16),
                        dstSize = IntSize(tileSize.roundToPx(), tileSize.roundToPx()),
                        srcOffset = IntOffset(5 * 16, 2 * 16),
                        dstOffset = IntOffset(
                            targetPosition.x.toInt(),
                            targetPosition.y.toInt()
                        ),
                        filterQuality = FilterQuality.None
                    )
                }
            )
        }

        /** Creates a simple entity. */
        fun entity(
            start: Offset,
            speed: State<Float>
        ): State<Entity> {
            val height = 4 * 37f

            val position = State.fold(start, deltaTime) { position, dt ->
                val dt = dt.inWholeMilliseconds / 1000.0f

                Offset(
                    position.x,
                    min(
                        position.y + dt * speed.value,
                        maxSize.height - height
                    )
                )
            }

            return position.map { position ->
                Entity(position, height) {
                    drawImage(
                        image = player,
                        srcSize = IntSize(50, 36),
                        dstSize = IntSize(4 * 50, 4 * 36),
                        srcOffset = IntOffset(0, 0),
                        dstOffset = IntOffset(
                            position.x.toInt(),
                            position.y.toInt()
                        ),
                        filterQuality = FilterQuality.None
                    )
                }
            }
        }

    }

    @Composable
    fun view() = YafrlCompose(
        showFPS = true,
        timeTravelDebugger = true
    ) {
        val tileset = remember {
            useResource("tileset.png", ::loadImageBitmap)
        }

        val player = remember {
            useResource("adventurer-idle-00.png", ::loadImageBitmap)
        }

        val background = remember {
            useResource("background.png", ::loadImageBitmap)
        }

        // Setup a global event handler.
        remember {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher { event ->
                if (event.id == KeyEvent.KEY_PRESSED && event.keyCode == KeyEvent.VK_SPACE) {
                    true
                } else {
                    false
                }
            }
        }

        val textMeasurer = rememberTextMeasurer()
        val textStyle = remember { TextStyle(fontSize = 26.sp, color = Color.White) }

        val size = size()

        val tileHeight = with(LocalDensity.current) { tileSize.toPx() }

        val viewModel = remember {
            ViewModel(size, Timeline.currentTimeline().clock, tileHeight, tileset, player)
        }

        val entities by remember {
            viewModel
                .entities()
                .composeState()
        }

        Canvas(
            modifier = Modifier
                .width(size.width.dp)
                .height(size.height.dp)
                .border(Dp.Hairline, Color.Black)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Press) {
                                viewModel.clicked.send(event.changes.first().position)
                            }
                        }
                    }
                },
        ) {
            drawImage(
                image = background,
                srcSize = IntSize(320, 180),
                dstSize = IntSize(
                    size.width.roundToInt(),
                    size.height.roundToInt()
                ),
                filterQuality = FilterQuality.None
            )

            for (entity in entities) {
                entity.render(this)
            }

            drawText(
                textMeasurer = textMeasurer,
                style = textStyle,
                text = "Entities created: ${entities.size}",
                topLeft = Offset(6f, 0f)
            )
        }
    }
}

fun main(args: Array<String>) {
    // Open a window with the view.
    application {
        val state = rememberWindowState(
            width = width,
            height = height + 100f.dp
        )

        Window(
            onCloseRequest = ::exitApplication,
            resizable = true,
            state = state,
            title = "Drawing"
        ) {
            PlatformerComponent.view()
        }
    }
}
