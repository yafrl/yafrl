import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
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

        runBlocking {
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

        runBlocking {
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

        runBlocking {
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
    fun `You have to put a delay to get this to work`() {
        val scope = CoroutineScope(Dispatchers.Default)

        runBlocking {
            val flow = MutableStateFlow(0)

            val mapped = flow
                .map { it + 2 }
                .stateIn(
                    scope,
                    SharingStarted.Eagerly,
                    flow.value + 2
                )

            flow.value = 3

            // Disgusting
            delay(5)

            val result = mapped.value

            assertEquals(5, result)
        }
    }
}