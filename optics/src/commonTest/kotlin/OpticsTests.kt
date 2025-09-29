import arrow.optics.Lens
import arrow.optics.optics
import io.github.yafrl.Signal
import io.github.yafrl.optics.embed
import io.github.yafrl.optics.focus
import io.github.yafrl.runYafrl
import io.kotest.core.spec.style.FunSpec
import kotlin.reflect.typeOf
import kotlin.test.assertEquals

@optics
sealed interface CombinedEvent {
    companion object
}

data object Event1 : CombinedEvent

data object Event2 : CombinedEvent

class OpticsTests : FunSpec({
    test("Test embedding events") {
        runYafrl {
            val event1 = externalEvent<Event1>()

            val embedded = event1
                .embed(timeline, arrow.optics.Prism.instanceOf())

            val collected = Signal.fold(listOf<Event1>(), embedded) { xs, x ->
                xs + listOf(x)
            }

            assertEquals(listOf(), collected.currentValue())

            event1.send(Event1)

            assertEquals(listOf(Event1), collected.currentValue())
        }
    }

    // Currently broken, bidirectional binding causes stack overflow.
    test("Test focusing states") {
        runYafrl {
            val state = externalSignal(0 to 0, typeOf<Pair<Int, Int>>())

            val focused = state
                .focus(timeline, Lens.pairFirst())

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
    }

    test("Test focusing immutable states") {
        runYafrl {
            val state = externalSignal(0 to 0)

            val immutableState: Signal<Pair<Int, Int>> = state

            val focused = immutableState
                .focus(timeline, Lens.pairFirst())

            state.value = 1 to 2

            assertEquals(1, focused.currentValue())
        }
    }
})