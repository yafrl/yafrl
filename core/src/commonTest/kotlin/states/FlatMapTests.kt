package states

import io.github.sintrastes.yafrl.Event
import io.github.sintrastes.yafrl.State
import io.github.sintrastes.yafrl.broadcastEvent
import io.github.sintrastes.yafrl.internal.Timeline
import io.github.sintrastes.yafrl.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class FlatMapTests {
    @Test
    fun `flat map switches between states properly`() {
        Timeline.initializeTimeline(
            CoroutineScope(Dispatchers.Default)
        )

        val state1 = mutableStateOf(0)

        val state2 = mutableStateOf(1)

        val use1 = mutableStateOf(true)

        val flatmapped = use1.flatMap { use1 ->
            if (use1) {
                state1
            } else {
                state2
            }
        }

        assertEquals(0, flatmapped.value)

        state1.value = 2

        assertEquals(2, flatmapped.value)

        use1.value = false

        assertEquals(1, flatmapped.value)

        state2.value = 3

        assertEquals(3, flatmapped.value)
    }

    @Test
    fun `flat map switches between states for complex nodes`() {
        Timeline.initializeTimeline(
            CoroutineScope(Dispatchers.Default)
        )

        val modifier = mutableStateOf<Float>(0f)

        val deltaTime = broadcastEvent<Duration>()

        fun position(speed: State<Float>, deltaTime: Event<Duration>): State<Float> = speed.flatMap { speed ->
            State.fold(0f, deltaTime) { state, time ->
                state + speed * time.inWholeMilliseconds / 1000f
            }
        }

        fun accelerating(v: Float, dv: Float): State<Float> = State.fold(v, deltaTime) { v, dt ->
            v + dv * dt.inWholeMilliseconds / 1000f
        }

        fun state(deltaTime: Event<Duration>) = modifier.flatMap { modifier ->
            position(
                accelerating(1f, modifier),
                deltaTime
            )
        }

        val position = state(deltaTime)

        assertEquals(0f, position.value)

        deltaTime.send(1.0.seconds)

        assertEquals(1f, position.value)

        modifier.value = 2f

        deltaTime.send(1.0.seconds)

        assertEquals(3f, position.value)
    }
}