package states

import io.github.sintrastes.yafrl.*
import io.github.sintrastes.yafrl.annotations.FragileYafrlAPI
import io.github.sintrastes.yafrl.internal.Timeline
import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.experimental.ExperimentalNativeApi
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapUpdatingTests: FunSpec({
    beforeTest {
        Timeline.initializeTimeline(
            CoroutineScope(Dispatchers.Default)
        )
    }

    test("Build node") {
        val node = bindingState(0)

        assertEquals(0, node.value)
    }

    test("Build held node") {
        val updates = broadcastEvent<Int>()

        val state = State.hold(0, updates)

        assertEquals(0, state.value)

        updates.send(1)

        assertEquals(1, state.value)
    }

    test("Build map node") {
        val node = bindingState(0)

        val mapped = node
            .map { it + 2 }

        assertEquals(2, mapped.value)
    }

    test("Map node updates immediately") {
        val node = bindingState(0)

        val mapped = node
            .map { it + 2 }

        node.value = 3

        assertEquals(5, mapped.value)
    }

    @OptIn(ExperimentalNativeApi::class)
    test("Map does not update unless queried") {
        var mapEvaluated = false

        val node = bindingState(0)

        node.map {
            mapEvaluated = true
            it + 2
        }

        node.value = 3

        assertTrue(!mapEvaluated)
    }

    @OptIn(FragileYafrlAPI::class, ExperimentalNativeApi::class)
    test("Map updates if listened to") {
        runTest {
            var mapEvaluated = false

            val node = bindingState(0)

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
})