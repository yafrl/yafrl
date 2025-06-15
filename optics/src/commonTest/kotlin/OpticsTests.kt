import arrow.optics.Lens
import arrow.optics.optics
import io.github.sintrastes.yafrl.bindingState
import io.github.sintrastes.yafrl.State
import io.github.sintrastes.yafrl.broadcastEvent
import io.github.sintrastes.yafrl.internal.Timeline
import io.github.sintrastes.yafrl.optics.embed
import io.github.sintrastes.yafrl.optics.focus
import io.kotest.core.spec.style.FunSpec
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals

@optics
sealed interface CombinedEvent {
    companion object
}

data object Event1 : CombinedEvent

data object Event2 : CombinedEvent

class OpticsTests : FunSpec({
    beforeTest {
        Timeline.initializeTimeline()
    }

    test("Test embedding events") {
        val event1 = broadcastEvent<Event1>()

        val embedded = event1
            .embed(arrow.optics.Prism.instanceOf())

        val collected = State.fold(listOf<Event1>(), embedded) { xs, x ->
            xs + listOf(x)
        }

        assertEquals(listOf(), collected.value)

        event1.send(Event1)

        assertEquals(listOf(Event1), collected.value)
    }

    // Currently broken, bidirectional binding causes stack overflow.
    test("Test focusing states") {
        val state = bindingState(0 to 0, typeOf<Pair<Int, Int>>())

        val focused = state
            .focus(Lens.pairFirst())

        state.value = 1 to 2

        // Test setting same value again, make sure it doesn't overflow
        state.value = 1 to 2

        assertEquals(1, focused.value)

        focused.value = 3

        // Test setting same value again, make sure it doesn't overflow
        focused.value = 3

        assertEquals(3 to 2, state.value)

        // Test binding the other way around
        state.value = 4 to 5

        assertEquals(4, focused.value)
    }

    test("Test focusing immutable states") {
        val state = bindingState(0 to 0)

        val immutableState: State<Pair<Int, Int>> = state

        val focused = immutableState
            .focus(Lens.pairFirst())

        state.value = 1 to 2

        assertEquals(1, focused.value)
    }
})