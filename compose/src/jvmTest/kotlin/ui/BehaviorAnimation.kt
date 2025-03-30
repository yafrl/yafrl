package ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.ButtonColors
import androidx.compose.material.ButtonDefaults.buttonColors
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.sintrastes.yafrl.Behavior
import io.github.sintrastes.yafrl.Behavior.Companion.integral
import io.github.sintrastes.yafrl.State.Companion.const
import io.github.sintrastes.yafrl.interop.YafrlCompose
import io.github.sintrastes.yafrl.interop.composeState
import io.github.sintrastes.yafrl.vector.Float2
import io.github.sintrastes.yafrl.vector.VectorSpace
import java.lang.Math.toDegrees
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.test.Test


/**
 * Creates a [Float2] vector rotating counter-clockwise at the specified angular [velocity]
 *  rotating about (0, 0).
 **/
fun angular(velocity: Behavior<Float>) = integral(velocity).map { angle ->
    Float2(cos(angle), sin(angle))
}

/**
 * Converts a position in the complex plane into a Compose [Color],
 *  where phase represents hue, and magnitude (up to 255f) represents
 *  luminance.
 **/
fun toColor(position: Float2): Color {
    val (x, y) = position

    // Compute angle in radians and map to [0, 360] degrees for hue
    val hue = ((toDegrees(atan2(y, x).toDouble()) + 360) % 360).toFloat()

    // Compute magnitude and cap at 255
    val magnitude = min(hypot(x, y), 255f)

    // Normalize magnitude to [0, 1] for luminance
    val luminance = magnitude / 255f

    // Convert HSV to Color
    return Color.hsv(hue, 1f, luminance)
}

class BehaviorAnimation {

    fun buttonColor() =
        angular(velocity = const(1.5f))
        .map { with(VectorSpace.float2()) { 225f * it } }
        .map(::toColor)
        .sampleState()

    // @Test
    fun `run behavior animation exmaple`() {
        // Open a window with the view.
        application {
            val state = rememberWindowState(
                width = 330.dp,
                height = 270.dp
            )

            Window(
                onCloseRequest = ::exitApplication,
                state = state,
                title = "Behavior Animation"
            ) {
                YafrlCompose {
                    val color by remember {
                        buttonColor().composeState()
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        Button(
                            content = { Text("Trippy!") },
                            onClick = { },
                            colors = buttonColors(
                                backgroundColor = color
                            )
                        )
                    }
                }
            }
        }
    }
}