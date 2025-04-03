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
import io.github.sintrastes.yafrl.interop.composeState
import io.github.sintrastes.yafrl.interop.YafrlCompose
import io.github.sintrastes.yafrl.vector.Float2
import io.github.sintrastes.yafrl.vector.VectorSpace
import ui.Physics.Entity
import ui.Physics.collisionSummation
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import kotlin.math.roundToInt

val tileSize = 42.dp

val width = 2000f.dp

val height = 800f.dp

@Composable
fun size() = with(LocalDensity.current) { Size(width.toPx(), height.toPx()) }

object Physics {
    /** Generic render-able entity. */
    data class Entity(
        val position: Offset,
        val size: Size,
        val render: DrawScope.() -> Unit
    )

    /** Utility to check an entity at [tentativePosition] with size [entitySize] would collide with
     * an entity [other]. */
    fun collides(tentativePosition: Float2, entitySize: Size, other: Entity): Boolean {
        val minX1 = tentativePosition.x
        val minY1 = tentativePosition.y
        val maxX1 = tentativePosition.x + entitySize.width
        val maxY1 = tentativePosition.y + entitySize.height

        val minX2 = other.position.x
        val minY2 = other.position.y
        val maxX2 = other.position.x + other.size.width
        val maxY2 = other.position.y + other.size.height

        return minX1 < maxX2 && maxX1 > minX2 &&
                minY1 < maxY2 && maxY1 > minY2
    }

    /**
     * Vector sum that clips the total summation if it would lead to a collision.
     **/
    fun collisionSummation(entitySize: Size, entities: Behavior<List<Entity>>): (Float2, Float2) -> Float2 = with (VectorSpace.float2()) {
        return { accumulatedPosition, newVelocity ->
            val tentativePosition = accumulatedPosition + newVelocity

            val others = entities.value // Get current state of other entities

            var clippedPosition = tentativePosition
            for (other in others) {
                if (collides(tentativePosition, entitySize, other)) {
                    clippedPosition = clipPosition(tentativePosition, entitySize, newVelocity, other)
                }
            }

            clippedPosition
        }
    }

    /**
     * Utility to calculate the clipped position of an entity that is going to collide with
     *  an [other] entity.
     **/
    fun clipPosition(
        tentativePosition: Float2,
        entitySize: Size, // Size of the moving entity
        velocity: Float2,
        other: Entity,
    ): Float2 {
        var clippedPosition = tentativePosition

        // Horizontal clipping
        if (velocity.x > 0) { // Moving right
            // If the right side of our entity overshoots other's left side...
            if (tentativePosition.x + entitySize.width > other.position.x && tentativePosition.x < other.position.x) {
                // ...clip so that the right edge aligns with other's left edge.
                clippedPosition = clippedPosition.copy(x = other.position.x - entitySize.width)
            }
        } else if (velocity.x < 0) { // Moving left
            // If the left side overshoots other's right side...
            if (tentativePosition.x < other.position.x + other.size.width && tentativePosition.x + entitySize.width > other.position.x + other.size.width) {
                // ...clip so that the left edge aligns with other's right edge.
                clippedPosition = clippedPosition.copy(x = other.position.x + other.size.width)
            }
        }

        // Vertical clipping
        if (velocity.y > 0) { // Moving down
            // If the bottom overshoots other's top...
            if (tentativePosition.y + entitySize.height > other.position.y && tentativePosition.y < other.position.y) {
                // ...clip so that the bottom edge aligns with other's top edge.
                clippedPosition = clippedPosition.copy(y = other.position.y - entitySize.height)
            }
        } else if (velocity.y < 0) { // Moving up
            // If the top overshoots other's bottom...
            if (tentativePosition.y < other.position.y + other.size.height && tentativePosition.y + entitySize.height > other.position.y + other.size.height) {
                // ...clip so that the top edge aligns with other's bottom edge.
                clippedPosition = clippedPosition.copy(y = other.position.y + other.size.height)
            }
        }

        return clippedPosition
    }
}

/** Implementation of a small platformer game. */
object PlatformerComponent {
    class ViewModel(
        private val maxSize: Size,
        private val tileHeight: Float,
        private val tileset: ImageBitmap,
        private val player: ImageBitmap
    ) {
        val clicked = broadcastEvent<Offset>("clicked")

        val clicks = State.fold(listOf<State<Entity>>(), clicked) { clicked, click ->
            clicked + entity(
                click,
                accelerating(Float2(0f, 220f), Float2(0f, 350f)),
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
        fun accelerating(v: Float2, dv: Float2) = const(v) + integral(const(dv))

        fun entities() = State.combineAll(
            entity(
                Offset(maxSize.width / 2, 0f),
                accelerating(Float2(0f, 420f), Float2(0f, 350f)),
            ),
            entity(
                Offset(maxSize.width / 3, 100f),
                accelerating(Float2(0f, 430f), Float2(0f, 350f)),
            ),
            entity(
                Offset(2 * maxSize.width / 3, 50f),
                accelerating(Float2(0f, 440f), Float2(0f, 350f)),
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
                size = Size(tileHeight, tileHeight),
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
            speed: State<Float2>
        ): State<Entity> {
            val size = Size(4 * 50f, 4 * 36f)

            val startVector = Float2(start.x, start.y)

            val position = (speed.integrateWith(startVector, collisionSummation(size, tiles)))
                .map { Offset(it.x, it.y) }

            return position.map { position ->
                Entity(position, size) {
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
        //timeTravelDebugger = true
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
            ViewModel(size, tileHeight, tileset, player)
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
