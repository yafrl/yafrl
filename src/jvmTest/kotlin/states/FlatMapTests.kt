package states

import io.github.sintrastes.yafrl.flatten
import io.github.sintrastes.yafrl.internal.Timeline
import io.github.sintrastes.yafrl.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals

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

        println("Updating use1")
        use1.value = false

        assertEquals(1, flatmapped.value)

        state2.value = 3

        assertEquals(3, flatmapped.value)
    }
}