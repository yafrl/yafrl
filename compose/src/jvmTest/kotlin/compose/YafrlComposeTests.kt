package compose

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.yafrl.timeline.Timeline
import io.github.yafrl.compose.YafrlCompose
import androidx.compose.material.Text
import androidx.compose.runtime.*
import io.github.yafrl.compose.composeState
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.Test
import kotlin.test.assertEquals

// Note: Have to run with JUnit4, not currently working in kotest or JUnit5.
@RunWith(JUnit4::class)
class YafrlComposeTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `clicking pause should pause the clock`() {
        println("In test")
        composeTestRule.mainClock.autoAdvance = false

        lateinit var _timeline: Timeline

        println("Setting content")
        composeTestRule.setContent {
            YafrlCompose(
                timeTravelDebugger = true,
                showFPS = true
            ) {
                _timeline = timeline

                Text("Here comes the content!")

                val valueState = remember {
                    externalSignal(0)
                }

                val value by remember {
                    valueState.composeState(timeline)
                }

                Text("Value: $value")
            }
        }
        println("Got content")

        // Ensure clock is active
        _timeline.clock

        val pausedState = _timeline.pausedState

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
        composeTestRule.onNodeWithText("Un-pause").performClick()

        // Verify resumed state
        assertEquals(false, pausedState.value)
    }
}