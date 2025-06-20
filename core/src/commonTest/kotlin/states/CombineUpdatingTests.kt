package states

import io.github.sintrastes.yafrl.Signal.Companion.combineAll
import io.github.sintrastes.yafrl.internal.Timeline
import io.github.sintrastes.yafrl.externalSignal
import io.github.sintrastes.yafrl.sequenceState
import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.experimental.ExperimentalNativeApi
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CombineUpdatingTests : FunSpec({
    beforeTest {
        Timeline.initializeTimeline(
            CoroutineScope(Dispatchers.Default)
        )
    }

    test("Combined state updates when parents update") {
        val x = externalSignal(0)

        val y = externalSignal(0)

        val sum = x.combineWith(y) { x, y -> x + y }

        assertEquals(0, sum.value)

        x.value = 1

        assertEquals(1, sum.value)

        y.value = 1

        assertEquals(2, sum.value)
    }

    @OptIn(ExperimentalNativeApi::class)
    test("Combined does not update unless queried") {
        val x = externalSignal(0)

        val y = externalSignal(0)

        var evaluated = false

        x.combineWith(y) { x, y ->
            evaluated = true
            x + y
        }

        x.value = 1

        assertTrue(!evaluated)
    }

    test("Combined state 3-arg updates when parents update") {
        val x = externalSignal(0)

        val y = externalSignal(0)

        val z = externalSignal(0)

        val sum = x.combineWith(y, z) { x, y, z -> x + y + z }

        assertEquals(0, sum.value)

        x.value = 1

        assertEquals(1, sum.value)

        y.value = 1

        assertEquals(2, sum.value)

        z.value = 1

        assertEquals(3, sum.value)
    }

    test("Combined state 4-arg updates when parents update") {
        val x = externalSignal(0)

        val y = externalSignal(0)

        val z = externalSignal(0)

        val w = externalSignal(0)

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

    test("Combined state 5-arg updates when parents update") {
        val x = externalSignal(0)

        val y = externalSignal(0)

        val z = externalSignal(0)

        val w = externalSignal(0)

        val q = externalSignal(0)

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

    test("Combine all updates when parents update") {
        val x = externalSignal(0)

        val y = externalSignal(0)

        val z = externalSignal(0)

        val w = externalSignal(0)

        val q = externalSignal(0)

        val r = externalSignal(0)

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

    test("sequenceState combines all states into list") {
        val x = externalSignal(0)
        val y = externalSignal(1)

        val sequenced = listOf(x, y)
            .sequenceState()

        assertEquals(listOf(0, 1), sequenced.value)

        x.value = 1
        assertEquals(listOf(1, 1), sequenced.value)

        y.value = 2
        assertEquals(listOf(1, 2), sequenced.value)
    }
})