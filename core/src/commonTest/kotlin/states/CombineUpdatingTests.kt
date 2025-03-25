package states

import io.github.sintrastes.yafrl.State.Companion.combineAll
import io.github.sintrastes.yafrl.internal.Timeline
import io.github.sintrastes.yafrl.bindingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.experimental.ExperimentalNativeApi
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CombineUpdatingTests {
    @BeforeTest
    fun `init timeline`() {
        Timeline.initializeTimeline(
            CoroutineScope(Dispatchers.Default)
        )
    }

    @Test
    fun `Combined state updates when parents update`() {
        val x = bindingState(0)

        val y = bindingState(0)

        val sum = x.combineWith(y) { x, y -> x + y }

        assertEquals(0, sum.value)

        x.value = 1

        assertEquals(1, sum.value)

        y.value = 1

        assertEquals(2, sum.value)
    }

    @Test
    @OptIn(ExperimentalNativeApi::class)
    fun `Combined does not update unless queried`() {
        val x = bindingState(0)

        val y = bindingState(0)

        var evaluated = false

        x.combineWith(y) { x, y ->
            evaluated = true
            x + y
        }

        x.value = 1

        assertTrue(!evaluated)
    }

    @Test
    fun `Combined state 3-arg updates when parents update`() {
        val x = bindingState(0)

        val y = bindingState(0)

        val z = bindingState(0)

        val sum = x.combineWith(y, z) { x, y, z -> x + y + z }

        assertEquals(0, sum.value)

        x.value = 1

        assertEquals(1, sum.value)

        y.value = 1

        assertEquals(2, sum.value)

        z.value = 1

        assertEquals(3, sum.value)
    }

    @Test
    fun `Combined state 4-arg updates when parents update`() {
        val x = bindingState(0)

        val y = bindingState(0)

        val z = bindingState(0)

        val w = bindingState(0)

        val sum = x.combineWith(y, z, w) { x, y, z, w -> x + y + z + w }

        assertEquals(0, sum.value)

        x.value = 1

        assertEquals(1, sum.value)

        y.value = 1

        assertEquals(2, sum.value)

        z.value = 1

        assertEquals(3, sum.value)

        w.value = 1

        assertEquals(4, sum.value)
    }

    @Test
    fun `Combined state 5-arg updates when parents update`() {
        val x = bindingState(0)

        val y = bindingState(0)

        val z = bindingState(0)

        val w = bindingState(0)

        val q = bindingState(0)

        val sum = x.combineWith(y, z, w, q) { x, y, z, w, q -> x + y + z + w + q }

        assertEquals(0, sum.value)

        x.value = 1

        assertEquals(1, sum.value)

        y.value = 1

        assertEquals(2, sum.value)

        z.value = 1

        assertEquals(3, sum.value)

        w.value = 1

        assertEquals(4, sum.value)

        q.value = 1

        assertEquals(5, sum.value)
    }

    @Test
    fun `Combine all updates when parents update`() {
        val x = bindingState(0)

        val y = bindingState(0)

        val z = bindingState(0)

        val w = bindingState(0)

        val q = bindingState(0)

        val r = bindingState(0)

        val sum = combineAll(x, y, z, w, q, r)
            .map { it.sum() }

        assertEquals(0, sum.value)

        x.value = 1

        assertEquals(1, sum.value)

        y.value = 1

        assertEquals(2, sum.value)

        z.value = 1

        assertEquals(3, sum.value)

        w.value = 1

        assertEquals(4, sum.value)

        q.value = 1

        assertEquals(5, sum.value)

        r.value = 1

        assertEquals(6, sum.value)
    }
}