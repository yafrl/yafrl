package states

import io.github.sintrastes.yafrl.internal.Timeline
import io.github.sintrastes.yafrl.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CombineUpdatingTests {
    @BeforeTest
    fun `init timeline`() {
        Timeline.initializeTimeline(
            CoroutineScope(Dispatchers.Default)
        )
    }

    @Test
    fun `Combined state updates when parents update`() {
        val x = mutableStateOf(0)

        val y = mutableStateOf(0)

        val sum = x.combineWith(y) { x, y -> x + y }

        assertEquals(0, sum.current())

        x.value = 1

        assertEquals(1, sum.current())

        y.value = 1

        assertEquals(2, sum.current())
    }

    @Test
    fun `Combined does not update unless queried`() {
        val x = mutableStateOf(0)

        val y = mutableStateOf(0)

        var evaluated = false

        x.combineWith(y) { x, y ->
            evaluated = true
            x + y
        }

        x.value = 1

        assert(!evaluated)
    }

    @Test
    fun `Combined state (3-arg) updates when parents update`() {
        val x = mutableStateOf(0)

        val y = mutableStateOf(0)

        val z = mutableStateOf(0)

        val sum = x.combineWith(y, z) { x, y, z -> x + y + z }

        assertEquals(0, sum.current())

        x.value = 1

        assertEquals(1, sum.current())

        y.value = 1

        assertEquals(2, sum.current())

        z.value = 1

        assertEquals(3, sum.current())
    }

    @Test
    fun `Combined state (4-arg) updates when parents update`() {
        val x = mutableStateOf(0)

        val y = mutableStateOf(0)

        val z = mutableStateOf(0)

        val w = mutableStateOf(0)

        val sum = x.combineWith(y, z, w) { x, y, z, w -> x + y + z + w }

        assertEquals(0, sum.current())

        x.value = 1

        assertEquals(1, sum.current())

        y.value = 1

        assertEquals(2, sum.current())

        z.value = 1

        assertEquals(3, sum.current())

        w.value = 1

        assertEquals(4, sum.current())
    }

    @Test
    fun `Combined state (5-arg) updates when parents update`() {
        val x = mutableStateOf(0)

        val y = mutableStateOf(0)

        val z = mutableStateOf(0)

        val w = mutableStateOf(0)

        val q = mutableStateOf(0)

        val sum = x.combineWith(y, z, w, q) { x, y, z, w, q -> x + y + z + w + q }

        assertEquals(0, sum.current())

        x.value = 1

        assertEquals(1, sum.current())

        y.value = 1

        assertEquals(2, sum.current())

        z.value = 1

        assertEquals(3, sum.current())

        w.value = 1

        assertEquals(4, sum.current())

        q.value = 1

        assertEquals(5, sum.current())
    }
}