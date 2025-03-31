package behaviors

import io.github.sintrastes.yafrl.Behavior.Companion.integral
import io.github.sintrastes.yafrl.BroadcastEvent
import io.github.sintrastes.yafrl.State
import io.github.sintrastes.yafrl.State.Companion.const
import io.github.sintrastes.yafrl.broadcastEvent
import io.github.sintrastes.yafrl.internal.Timeline
import io.github.sintrastes.yafrl.bindingState
import io.github.sintrastes.yafrl.plus
import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.assertEquals
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

        fun position(speed: State<Float>): State<Float> = integral(speed)

        fun accelerating(v: Float, dv: State<Float>): State<Float> =
            const(v) + integral(dv)

        val position = position(
            accelerating(1f, modifier)
        )

        assertEquals(0f, position.value)

        deltaTime.send(1.0.seconds)

        assertEquals(1f, position.value)

        modifier.value = 1f

        deltaTime.send(1.0.seconds)

        assertEquals(3f, position.value)
    }
})
