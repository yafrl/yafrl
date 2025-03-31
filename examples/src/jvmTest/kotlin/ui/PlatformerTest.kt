package ui

import androidx.compose.ui.geometry.Size
import io.github.sintrastes.yafrl.broadcastEvent
import io.github.sintrastes.yafrl.internal.Timeline
import io.kotest.core.spec.style.FunSpec
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class PlatformerTest : FunSpec({
    xtest("Test view model") {
        Timeline.initializeTimeline(
            CoroutineScope(Dispatchers.Default)
        )

        val deltaTime = broadcastEvent<Duration>()

        val viewModel = PlatformerComponent.ViewModel(
            Size(100f, 100f),
            deltaTime,
            35f,
            mockk(),
            mockk()
        )

        viewModel.entities()

        deltaTime.send(1.0.seconds)

        assertTrue(
            Timeline.currentTimeline().scope.isActive
        )
    }
})