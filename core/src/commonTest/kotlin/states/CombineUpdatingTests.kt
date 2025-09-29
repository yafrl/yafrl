package states

import io.github.yafrl.Signal
import io.github.yafrl.timeline.Timeline
import io.github.yafrl.runYafrl
import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.experimental.ExperimentalNativeApi
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CombineUpdatingTests : FunSpec({
    test("Combined state updates when parents update") {
        runYafrl {
            val x = externalSignal(0)

            val y = externalSignal(0)

            val sum = x.combineWith(y) { x, y -> x + y }

            assertEquals(0, sum.currentValue())

            x.value = 1

            assertEquals(1, sum.currentValue())

            y.value = 1

            assertEquals(2, sum.currentValue())
        }
    }

    @OptIn(ExperimentalNativeApi::class)
    test("Combined does not update unless queried") {
        runYafrl {
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
    }

    test("Combined state 3-arg updates when parents update") {
        runYafrl {
            val x = externalSignal(0)

            val y = externalSignal(0)

            val z = externalSignal(0)

            val sum = x.combineWith(y, z) { x, y, z -> x + y + z }

            assertEquals(0, sum.currentValue())

            x.value = 1

            assertEquals(1, sum.currentValue())

            y.value = 1

            assertEquals(2, sum.currentValue())

            z.value = 1

            assertEquals(3, sum.currentValue())
        }
    }

    test("Combined state 4-arg updates when parents update") {
        runYafrl {
            val x = externalSignal(0)

            val y = externalSignal(0)

            val z = externalSignal(0)

            val w = externalSignal(0)

            val sum = x.combineWith(y, z, w) { x, y, z, w -> x + y + z + w }

            assertEquals(0, sum.currentValue())

            x.value = 1

            assertEquals(1, sum.currentValue())

            y.value = 1

            assertEquals(2, sum.currentValue())

            z.value = 1

            assertEquals(3, sum.currentValue())

            w.value = 1

            assertEquals(4, sum.currentValue())
        }
    }

    test("Combined state 5-arg updates when parents update") {
        runYafrl {
            val x = externalSignal(0)

            val y = externalSignal(0)

            val z = externalSignal(0)

            val w = externalSignal(0)

            val q = externalSignal(0)

            val sum = x.combineWith(y, z, w, q) { x, y, z, w, q -> x + y + z + w + q }

            assertEquals(0, sum.currentValue())

            x.value = 1

            assertEquals(1, sum.currentValue())

            y.value = 1

            assertEquals(2, sum.currentValue())

            z.value = 1

            assertEquals(3, sum.currentValue())

            w.value = 1

            assertEquals(4, sum.currentValue())

            q.value = 1

            assertEquals(5, sum.currentValue())
        }
    }

    test("Combine all updates when parents update") {
        runYafrl {
            val x = externalSignal(0)

            val y = externalSignal(0)

            val z = externalSignal(0)

            val w = externalSignal(0)

            val q = externalSignal(0)

            val r = externalSignal(0)

            val sum = Signal.combineAll(x, y, z, w, q, r)
                .map { it.sum() }

            assertEquals(0, sum.currentValue())

            x.value = 1

            assertEquals(1, sum.currentValue())

            y.value = 1

            assertEquals(2, sum.currentValue())

            z.value = 1

            assertEquals(3, sum.currentValue())

            w.value = 1

            assertEquals(4, sum.currentValue())

            q.value = 1

            assertEquals(5, sum.currentValue())

            r.value = 1

            assertEquals(6, sum.currentValue())
        }
    }

    test("sequenceState combines all states into list") {
        runYafrl {
            val x = externalSignal(0)
            val y = externalSignal(1)

            val sequenced = listOf(x, y)
                .sequenceState()

            assertEquals(listOf(0, 1), sequenced.currentValue())

            x.value = 1
            assertEquals(listOf(1, 1), sequenced.currentValue())

            y.value = 2
            assertEquals(listOf(1, 2), sequenced.currentValue())
        }
    }
})