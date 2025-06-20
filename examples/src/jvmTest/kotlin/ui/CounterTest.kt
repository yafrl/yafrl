package ui

import io.github.sintrastes.yafrl.externalEvent
import io.github.sintrastes.yafrl.internal.Timeline
import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.assertEquals

class CounterTest : FunSpec({
    test("Counter resets state after two events with timetravel debugger") {
        Timeline.initializeTimeline(
            CoroutineScope(Dispatchers.Default),
            debug = true,
            timeTravel = true,
            // Note: Shouldn't need non-lazy here. This is a bug.
            lazy = false
        )

        val clicks = externalEvent<Unit>()

        val viewModel = CounterComponent.ViewModel(clicks)

        assertEquals(0, viewModel.count.value)

        clicks.send(Unit)

        clicks.send(Unit)

        assertEquals(2, viewModel.count.value)

        Timeline.currentTimeline()
            .rollbackState()

        assertEquals(1, viewModel.count.value)
    }

    test("Counter resets state with timetravel debugger") {
        Timeline.initializeTimeline(
            CoroutineScope(Dispatchers.Default),
            debug = true,
            timeTravel = true,
            // Note: Shouldn't need non-lazy here. This is a bug.
            lazy = false
        )

        val clicks = externalEvent<Unit>()

        val viewModel = CounterComponent.ViewModel(clicks)

        assertEquals(0, viewModel.count.value)

        clicks.send(Unit)

        assertEquals(1, viewModel.count.value)

        Timeline.currentTimeline()
            .rollbackState()

        assertEquals(0, viewModel.count.value)
    }
})