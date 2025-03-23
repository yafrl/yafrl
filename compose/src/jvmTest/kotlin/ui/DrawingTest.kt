import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import io.github.sintrastes.yafrl.interop.YafrlCompose
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object DrawingComponent {
    data class Entity(
        val position: Offset,
        val color: Color,
        val size: Float
    )

    class ViewModel(
        private val maxSize: Size,
        private val deltaTime: Event<Duration>,
        private val spacePressed: Event<Unit>,
        private val clicked: Event<Offset>
    ) {
        val spacePressedTimes = State.fold(0, spacePressed) { times, _ -> times + 1 }

        val newBallColor = spacePressedTimes.map { spacePressedTimes ->
            if (spacePressedTimes % 2 == 0) {
                Color.Red
            } else {
                Color.Blue
            }
        }

        val clicks = State.fold(listOf<State<Entity>>(), clicked) { clicked, click ->
            clicked + listOf(
                ball(
                    click,
                    accelerating(120f, 350f),
                    35f,
                    newBallColor
                )
            )
        }

        val spawned = clicks.flatMap { clicks ->
            clicks
                .sequenceState()
        }

        /**
         * Creates a speed [v] that is accelerating by [dv]
         **/
        fun accelerating(v: Float, dv: Float) = const(v) + integral(const(dv))

        fun entities() = State.combineAll(
            ball(
                Offset(maxSize.width / 2, 0f),
                accelerating(50f, 150f),
                35f,
                const(Color.Red),
            ),
            ball(
                Offset(maxSize.width / 3, 100f),
                accelerating(120f, 150f),
                20f,
                const(Color.Blue)
            ),
            ball(
                Offset(2 * maxSize.width / 3, 50f),
                accelerating(130f, 150f),
                30f,
                const(Color.Green)
            )
        ).combineWith(spawned) { initial, spawned ->
            initial + spawned
        }

        /** Creates a simple ball entity. */
        fun ball(
            start: Offset,
            speed: State<Float>,
            height: Float,
            color: State<Color>,
        ): State<Entity> {
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

            return position.combineWith(color) { position, color ->
                Entity(position, color, height)
            }
        }
    }

    val size = Size(1000f, 944f)

    @Composable
    fun view() = YafrlCompose(
        timeTravelDebugger = true
    ) {
        val spacePressed = remember {
            broadcastEvent<Unit>("space_pressed")
        }

        val clicked = remember {
            broadcastEvent<Offset>("clicked")
        }

        // Setup a global event handler.
        remember {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher { event ->
                if (event.id == KeyEvent.KEY_PRESSED && event.keyCode == KeyEvent.VK_SPACE) {
                    spacePressed.send(Unit)
                    true
                } else {
                    false
                }
            }
        }

        val textMeasurer = rememberTextMeasurer()
        val textStyle = remember { TextStyle(fontSize = 18.sp, color = Color.Black) }

        val entities by remember {
            ViewModel(size, Timeline.currentTimeline().clock, spacePressed, clicked)
                .entities()
                .composeState()
        }

        Canvas(
            modifier = Modifier
                .size(500.dp)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Press) {
                                clicked.send(event.changes.first().position)
                            }
                        }
                    }
                },
        ) {
            drawText(
                textMeasurer = textMeasurer,
                style = textStyle,
                text = "Entities created: ${entities.size}",
                topLeft = Offset(6f, 0f)
            )

            for (entity in entities) {
                drawCircle(
                    color = entity.color,
                    radius = entity.size,
                    center = entity.position
                )
            }
        }
    }
}

class DrawingTest {
    @Test
    fun `Test view model`() {
        Timeline.initializeTimeline(
            CoroutineScope(Dispatchers.Default)
        )

        val deltaTime = broadcastEvent<Duration>()

        val spacePressed = broadcastEvent<Unit>()

        val viewModel = DrawingComponent.ViewModel(
            Size(100f, 100f),
            deltaTime,
            spacePressed,
            broadcastEvent()
        )

        viewModel.entities()

        deltaTime.send(1.0.seconds)

        assertTrue(
            Timeline.currentTimeline().scope.isActive
        )
    }

    // Disabled by default
    // @Test
    fun `run drawing example`() {
        // Open a window with the view.
        application {
            val state = rememberWindowState(
                width = 500.dp,
                height = 600.dp
            )

            Window(
                onCloseRequest = ::exitApplication,
                resizable = false,
                state = state,
                title = "Drawing"
            ) {
                DrawingComponent.view()
            }
        }
    }
}