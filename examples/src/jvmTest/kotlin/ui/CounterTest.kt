package ui

import io.github.yafrl.annotations.FragileYafrlAPI
import io.github.yafrl.runYafrl
import io.github.yafrl.timeline.Timeline
import io.github.yafrl.timeline.current
import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.assertEquals

@OptIn(FragileYafrlAPI::class)
class CounterTest : FunSpec({
    test("Counter resets state after two events with timetravel debugger") {
        runYafrl(
            scope = CoroutineScope(Dispatchers.Default),
            timeTravel = true,
            // Note: Shouldn't need non-lazy here. This is a bug.
            lazy = false
        ) {

            val clicks = externalEvent<Unit>()

            val viewModel = CounterComponent.ViewModel(this, clicks)

            assertEquals(0, viewModel.count.node.current(timeline))

            clicks.send(Unit)

            clicks.send(Unit)

            assertEquals(2, viewModel.count.node.current(timeline))

            timeline
                .timeTravel.rollbackState()

            assertEquals(1, viewModel.count.node.current(timeline))
        }
    }

    test("Counter resets state with timetravel debugger") {
        runYafrl(
            scope = CoroutineScope(Dispatchers.Default),
            timeTravel = true,
            // Note: Shouldn't need non-lazy here. This is a bug.
            lazy = false
        ) {

            val clicks = externalEvent<Unit>()

            val viewModel = CounterComponent.ViewModel(this, clicks)

            assertEquals(0, viewModel.count.node.current(timeline))

            clicks.send(Unit)

            assertEquals(1, viewModel.count.node.current(timeline))

            timeline
                .timeTravel.rollbackState()

            assertEquals(0, viewModel.count.node.current(timeline))
        }
    }
})