
import io.github.sintrastes.yafrl.internal.Timeline
import io.github.sintrastes.yafrl.interop.asEvent
import io.github.sintrastes.yafrl.interop.asState
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.assertEquals

class CoroutineIntegrationTests: FunSpec({
    @OptIn(ExperimentalCoroutinesApi::class)
    test("External events are received") {
        Timeline.initializeTimeline(CoroutineScope(Dispatchers.Default))

        runTest {
            val flow = MutableSharedFlow<Unit>()

            val event = flow.asEvent()

            val events = event.scan(listOf<Unit>()) { xs, x -> xs + x }

            flow.emit(Unit)

            withContext(Dispatchers.Default) { delay(25) }

            events.value.size shouldBe 1
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    test("External state changes are received") {
        Timeline.initializeTimeline(CoroutineScope(Dispatchers.Default))

        runTest {
            val stateFlow = MutableStateFlow(0)

            val state = stateFlow.asState()

            assertEquals(0, state.value)

            stateFlow.value = 1

            withContext(Dispatchers.Default) { delay(25) }

            assertEquals(1, state.value)
        }
    }
})