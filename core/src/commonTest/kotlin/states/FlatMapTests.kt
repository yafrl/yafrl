package states

import io.github.sintrastes.yafrl.Signal.Companion.const
import io.github.sintrastes.yafrl.internal.Timeline
import io.github.sintrastes.yafrl.externalSignal
import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.assertEquals

class FlatMapTests: FunSpec({
    test("flat map switches between states properly") {
        Timeline.initializeTimeline(
            CoroutineScope(Dispatchers.Default)
        )

        val state1 = externalSignal(0)

        val state2 = externalSignal(1)

        val use1 = externalSignal(true)

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

    test("flat map updates when either state updates") {
        Timeline.initializeTimeline(
            CoroutineScope(Dispatchers.Default)
        )

        val state1 = externalSignal(0)

        val state2 = externalSignal(0)

        val flatmapped = state1.flatMap { x ->
            state2.flatMap { y ->
                const(x + y)
            }
        }

        assertEquals(0, flatmapped.value)

        state1.value = 2

        assertEquals(2, flatmapped.value)

        state2.value = 3

        assertEquals(5, flatmapped.value)
    }
})