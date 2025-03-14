package negative_tests

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

/**
 * Examples showing why we're developing this library in the first place.
 *
 * In other words -- these tests showcase unexpected and / or annoying
 *  default behavior we want to avoid in `yafrl`.
 *
 * ```
 * "Task failed successfully."
 *    - Windows XP
 * ```
 **/
class NegativeResults {
    @Test
    fun `Mapping state flow fails`() {
        val scope = CoroutineScope(Dispatchers.Default)

        runTest {
            val flow = MutableStateFlow(0)

            val mapped = flow
                .map { it + 2 }
                .stateIn(scope)

            flow.value = 3

            assertNotSame(5, mapped.value)
        }
    }

    @Test
    fun `Mapping state flow with 3-arg stateIn fails`() {
        val scope = CoroutineScope(Dispatchers.Default)

        runTest {
            val flow = MutableStateFlow(0)

            val mapped = flow
                .map { it + 2 }
                .stateIn(
                    scope,
                    SharingStarted.Eagerly,
                    flow.value + 2
                )

            flow.value = 3

            val result = mapped.value

            assertNotSame(5, result)
        }
    }

    @Test
    fun `Mapping state flow with 3-arg stateIn fails even with test scope`() {
        val scope = TestScope()

        runTest {
            val flow = MutableStateFlow(0)

            val mapped = flow
                .map { it + 2 }
                .stateIn(
                    scope,
                    SharingStarted.Eagerly,
                    flow.value + 2
                )

            flow.value = 3

            val result = mapped.value

            assertNotSame(5, result)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `You have to put a delay to get this to work`() {
        val scope = CoroutineScope(Dispatchers.Default)

        runTest {
            val flow = MutableStateFlow(0)

            val mapped = flow
                .map { it + 2 }
                .stateIn(
                    scope,
                    SharingStarted.Eagerly,
                    flow.value + 2
                )

            flow.value = 3

            // Have to put in some kind of pause to get this to work
            // If not using runTest this would be a `delay`.
            advanceUntilIdle()

            val result = mapped.value

            assertEquals(5, result)
        }
    }

    // Example from https://qfpl.io/posts/reflex/basics/events/
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `Does not handle simultaneous events`() {
        val clicks = MutableSharedFlow<Unit>()

        val ones = clicks.scan(0) { x, _ -> x + 1 }

        val hundreds = ones.map { it * 100 }

        val sum = ones.combine(hundreds) { x, y -> x + y }

        val scope = CoroutineScope(Dispatchers.Default)

        runTest {
            // Count the number of times that sum emits
            var sumEmitted = 0

            scope.launch {
                sum.collect {
                    sumEmitted++
                }
            }

            // Count the number of clicks
            var clicksEmitted = 0

            // Simulate clicking the button a few times
            scope.launch {
                clicks.emit(Unit)
                clicksEmitted++
            }

            // Wait for a decent number of clicks
            advanceTimeBy(50)

            //
            // Naively, we'd expect these to be the same -- but because
            // kotlinx.coroutine's Flows by default work fully asynchronously,
            // this causes more events to be emitted than we'd expect -- with
            // "glitch" states where the states are not in sync.
            //
            // yafrl with its synchronous timeline implementation does not have
            // this problem.
            //
            assertNotSame(sumEmitted, clicksEmitted)
        }
    }
}