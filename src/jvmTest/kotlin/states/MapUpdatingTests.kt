package states

import io.github.sintrastes.yafrl.*
import io.github.sintrastes.yafrl.internal.Timeline
import kotlinx.coroutines.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

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

        assert(node.value == 0)
    }

    @Test
    fun `Build map node`() {
        val node = mutableStateOf(0)

        val mapped = node
            .map { it + 2 }

        assert(mapped.value == 2)
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
    fun `Map does not update unless queried`() {
        var mapEvaluated = false

        val node = mutableStateOf(0)

        node.map {
            mapEvaluated = true
            it + 2
        }

        node.value = 3

        assert(!mapEvaluated)
    }

    @OptIn(FragileYafrlAPI::class)
    @Test
    fun `Map updates if listened to`() {
        runBlocking {
            var mapEvaluated = false

            val node = mutableStateOf(0)

            val mapped = node.map {
                mapEvaluated = true
                it + 2
            }

            mapped.collect { value ->
                println("Collecting $value")
            }

            node.value = 3

            assert(mapEvaluated)
        }
    }
}