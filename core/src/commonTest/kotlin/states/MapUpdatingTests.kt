package states

import io.github.yafrl.*
import io.github.yafrl.annotations.FragileYafrlAPI
import io.github.yafrl.timeline.Timeline
import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.experimental.ExperimentalNativeApi
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapUpdatingTests: FunSpec({
    beforeTest {
        Timeline.initializeTimeline(
            CoroutineScope(Dispatchers.Default)
        )
    }

    test("Build node") {
        val node = externalSignal(0)

        assertEquals(0, node.value)
    }

    test("Build held node") {
        runYafrl {
            val updates = externalEvent<Int>()

            val state = Signal.hold(0, updates)

            assertEquals(0, state.currentValue())

            updates.send(1)

            assertEquals(1, state.currentValue())
        }
    }

    test("Build map node") {
        runYafrl {
            val node = externalSignal(0)

            val mapped = node
                .map { it + 2 }

            assertEquals(2, mapped.currentValue())
        }
    }

    test("Map node updates immediately") {
        runYafrl {
            val node = externalSignal(0)

            val mapped = node
                .map { it + 2 }

            node.value = 3

            assertEquals(5, mapped.currentValue())
        }
    }

    @OptIn(ExperimentalNativeApi::class)
    test("Map does not update unless queried") {
        var mapEvaluated = false

        val node = externalSignal(0)

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

            val node = externalSignal(0)

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