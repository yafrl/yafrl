package states

import io.github.yafrl.runYafrl
import io.kotest.core.spec.style.FunSpec
import kotlin.test.assertEquals

class DebuggingTests : FunSpec({
    test("Setting label updates string display") {
        runYafrl {
            val state = externalSignal(0)
                .labeled("state")

            assertEquals("Signal(state)", state.toString())
        }
    }
})