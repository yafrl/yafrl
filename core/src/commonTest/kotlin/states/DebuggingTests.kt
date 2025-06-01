package states

import io.github.sintrastes.yafrl.bindingState
import io.github.sintrastes.yafrl.internal.Timeline
import io.kotest.core.spec.style.FunSpec
import kotlin.test.assertEquals

class DebuggingTests : FunSpec({
    test("Setting label updates string display") {
        Timeline.initializeTimeline()

        val state = bindingState(0)
            .labeled("state")

        assertEquals("State(state)", state.toString())
    }
})