package states

import io.github.sintrastes.yafrl.*
import io.github.sintrastes.yafrl.vector.Float2
import io.github.sintrastes.yafrl.vector.Float3
import io.kotest.core.spec.style.FunSpec
import kotlin.test.assertEquals

class StateSummationTests: FunSpec({
    test("Test summing float states") {
        runYafrl {
            val state1 = externalSignal(1f)
            val state2 = externalSignal(2f)

            val summed = state1 + state2

            assertEquals(3f, summed.currentValue())
        }
    }

    test("Test summing float2 states") {
        runYafrl {
            val state1 = externalSignal(Float2(1f, 1f))
            val state2 = externalSignal(Float2(2f, 2f))

            val summed = state1 + state2

            assertEquals(Float2(3f, 3f), summed.currentValue())
        }
    }

    test("Test summing float3 states") {
        runYafrl {
            val state1 = externalSignal(Float3(1f, 1f, 1f))
            val state2 = externalSignal(Float3(2f, 2f, 2f))

            val summed = state1 + state2

            assertEquals(Float3(3f, 3f, 3f), summed.currentValue())
        }
    }
})