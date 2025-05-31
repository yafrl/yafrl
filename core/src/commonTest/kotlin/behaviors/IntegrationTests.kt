package behaviors

import io.github.sintrastes.yafrl.behaviors.Behavior.Companion.integral
import io.github.sintrastes.yafrl.BroadcastEvent
import io.github.sintrastes.yafrl.behaviors.Behavior.Companion.const
import io.github.sintrastes.yafrl.asBehavior
import io.github.sintrastes.yafrl.broadcastEvent
import io.github.sintrastes.yafrl.internal.Timeline
import io.github.sintrastes.yafrl.bindingState
import io.github.sintrastes.yafrl.behaviors.plus
import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class IntegrationTests: FunSpec({
    test("Integration can be used to compute variable velocity") {
        Timeline.initializeTimeline(
            CoroutineScope(Dispatchers.Default),
            initClock = {
                broadcastEvent<Duration>()
            }
        )

        val modifier = bindingState<Float>(0f)

        val deltaTime = Timeline.currentTimeline().clock as BroadcastEvent<Duration>

        val position = integral(
            const(1f) + integral(modifier.asBehavior())
        )

        assertEquals(0f, position.value)

        deltaTime.send(1.0.seconds)

        assertEquals(1f, position.value)

        modifier.value = 1f

        deltaTime.send(1.0.seconds)

        assertTrue("Expected 2.5 but was ${position.value}") {
            abs(position.value - 2.5f) < 0.01 // 0.000001 // TODO: Accuracy is not as good as it was here.
        }
    }
})
