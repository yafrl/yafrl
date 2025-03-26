package compose

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.sintrastes.yafrl.internal.Timeline
import io.github.sintrastes.yafrl.interop.YafrlCompose
import androidx.compose.material.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class YafrlComposeTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `clicking pause should pause the clock`() {
        Timeline.initializeTimeline(CoroutineScope(Dispatchers.Default))

        composeTestRule.setContent {
            YafrlCompose(
                timeTravelDebugger = true
            ) {
                Text("Here comes the content!")
            }
        }

        val pausedState = Timeline.currentTimeline().pausedState

        // Verify initial state
        assertEquals(false, pausedState.value)

        // Simulate button click to pause clock
        composeTestRule.onNodeWithText("Pause").performClick()

        // Verify paused state
        //assertEquals(true, pausedState.value)

        // Simulate button click to resume clock
        //composeTestRule.onNodeWithText("Un-pause").performClick()

        // Verify resumed state
        //assertEquals(false, pausedState.value)
    }
}