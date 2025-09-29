package debugging

import io.github.yafrl.annotations.FragileYafrlAPI
import io.github.yafrl.runYafrl
import io.github.yafrl.timeline.logging.EventLogger
import io.github.yafrl.timeline.NodeID
import io.github.yafrl.timeline.debugging.ExternalAction
import io.github.yafrl.timeline.debugging.ExternalEvent
import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.assertEquals

@OptIn(FragileYafrlAPI::class)
class EventLogging : FunSpec({
    test("test event logging") {
        runYafrl(
            scope = CoroutineScope(Dispatchers.Default),
            eventLogger = EventLogger.InMemory(),
            timeTravel = true
        ) {

            val externalEvent = externalEvent<Int>()

            val mapped = externalEvent
                .map { it + 2 }

            externalEvent.send(1)

            externalEvent.send(2)

            assertEquals(
                listOf(
                    ExternalEvent(mapOf(), ExternalAction.FireEvent(NodeID(0), 1)),
                    ExternalEvent(mapOf(), ExternalAction.FireEvent(NodeID(0), 2))
                ),
                timeline.reportEvents().toList()
            )
        }
    }
})