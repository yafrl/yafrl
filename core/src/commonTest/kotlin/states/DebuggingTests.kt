package states

import io.github.sintrastes.yafrl.externalSignal
import io.github.sintrastes.yafrl.internal.Timeline
import io.kotest.core.spec.style.FunSpec
import kotlin.test.assertEquals

class DebuggingTests : FunSpec({
    test("Setting label updates string display") {
        Timeline.initializeTimeline()

        val state = externalSignal(0)
            .labeled("state")

        assertEquals("State(state)", state.toString())
    }
})