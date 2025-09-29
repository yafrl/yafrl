package states

import io.github.yafrl.Signal
import io.github.yafrl.runYafrl
import io.kotest.core.spec.style.FunSpec
import kotlin.test.assertEquals

class FlatMapTests: FunSpec({
    test("flat map switches between states properly") {
        runYafrl {
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

            assertEquals(0, flatmapped.currentValue())

            state1.value = 2

            assertEquals(2, flatmapped.currentValue())

            use1.value = false

            assertEquals(1, flatmapped.currentValue())

            state2.value = 3

            assertEquals(3, flatmapped.currentValue())
        }
    }

    test("flat map updates when either state updates") {
        runYafrl {
            val state1 = externalSignal(0)

            val state2 = externalSignal(0)

            val flatmapped = state1.flatMap { x ->
                state2.flatMap { y ->
                    Signal.const(x + y)
                }
            }

            assertEquals(0, flatmapped.currentValue())

            state1.value = 2

            assertEquals(2, flatmapped.currentValue())

            state2.value = 3

            assertEquals(5, flatmapped.currentValue())
        }
    }
})