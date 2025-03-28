import arrow.optics.Lens
import arrow.optics.optics
import io.github.sintrastes.yafrl.bindingState
import io.github.sintrastes.yafrl.State
import io.github.sintrastes.yafrl.broadcastEvent
import io.github.sintrastes.yafrl.optics.embed
import io.github.sintrastes.yafrl.optics.focus
import kotlin.test.Test
import kotlin.test.assertEquals

@optics
sealed interface CombinedEvent {
    companion object
}

data object Event1 : CombinedEvent

data object Event2 : CombinedEvent

class OpticsTests {

    @Test
    fun `Test embedding events`() {
        val event1 = broadcastEvent<Event1>()

        val embedded = event1
            .embed(CombinedEvent.event1)
    }

    @Test
    fun `Test focusing states`() {
        val state = bindingState(0 to 0)

        val focused = state
            .focus(Lens.pairFirst())

        state.value = 1 to 2

        assertEquals(1, focused.value)
    }

    @Test
    fun `Test focusing immutable states`() {
        val state = bindingState(0 to 0)

        val immutableState: State<Pair<Int, Int>> = state

        val focused = immutableState
            .focus(Lens.pairFirst())

        state.value = 1 to 2

        assertEquals(1, focused.value)
    }
}