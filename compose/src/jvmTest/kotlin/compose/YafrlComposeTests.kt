package compose

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.sintrastes.yafrl.internal.Timeline
import io.github.sintrastes.yafrl.interop.YafrlCompose
import androidx.compose.material.Text
import androidx.compose.runtime.*
import io.github.sintrastes.yafrl.bindingState
import io.github.sintrastes.yafrl.interop.composeState
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class YafrlComposeTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `clicking pause should pause the clock`() {
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            YafrlCompose(
                timeTravelDebugger = true,
                showFPS = true
            ) {
                Text("Here comes the content!")

                val valueState = remember {
                    bindingState(0)
                }

                val value by remember {
                    valueState.composeState()
                }

                Text("Value: $value")
            }
        }

        // Ensure clock is active
        Timeline.currentTimeline().clock

        val pausedState = Timeline.currentTimeline().pausedState

        // Verify initial state
        assertEquals(false, pausedState.value)

        composeTestRule.mainClock.advanceTimeBy(500)

        // Simulate button click to pause clock
        composeTestRule
            .onNodeWithContentDescription("Pause button")
            .performClick()

        println("After click")

        // Verify paused state
        assertEquals(true, pausedState.value)

        // Simulate button click to resume clock
        //composeTestRule.onNodeWithText("Un-pause").performClick()

        // Verify resumed state
        //assertEquals(false, pausedState.value)
    }
}