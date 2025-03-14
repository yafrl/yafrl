package states

import io.github.sintrastes.yafrl.*
import io.github.sintrastes.yafrl.annotations.FragileYafrlAPI
import io.github.sintrastes.yafrl.internal.Timeline
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.experimental.ExperimentalNativeApi
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapUpdatingTests {
    @BeforeTest
    fun `init timeline`() {
        Timeline.initializeTimeline(
            CoroutineScope(Dispatchers.Default)
        )
    }

    @Test
    fun `Build node`() {
        val node = mutableStateOf(0)

        assertEquals(0, node.value)
    }

    @Test
    fun `Build held node`() {
        val updates = broadcastEvent<Int>()

        val state = State.hold(0, updates)

        assertEquals(0, state.value)

        updates.send(1)

        assertEquals(1, state.value)
    }

    @Test
    fun `Build map node`() {
        val node = mutableStateOf(0)

        val mapped = node
            .map { it + 2 }

        assertEquals(2, mapped.value)
    }

    @Test
    fun `Map node updates immediately`() {
        val node = mutableStateOf(0)

        val mapped = node
            .map { it + 2 }

        node.value = 3

        assertEquals(5, mapped.value)
    }

    @Test
    @OptIn(ExperimentalNativeApi::class)
    fun `Map does not update unless queried`() {
        var mapEvaluated = false

        val node = mutableStateOf(0)

        node.map {
            mapEvaluated = true
            it + 2
        }

        node.value = 3

        assertTrue(!mapEvaluated)
    }

    @OptIn(FragileYafrlAPI::class, ExperimentalNativeApi::class)
    @Test
    fun `Map updates if listened to`() {
        runTest {
            var mapEvaluated = false

            val node = mutableStateOf(0)

            val mapped = node.map {
                mapEvaluated = true
                it + 2
            }

            mapped.collectAsync { value ->
                println("Collecting $value")
            }

            node.value = 3

            assertTrue(mapEvaluated)
        }
    }
}