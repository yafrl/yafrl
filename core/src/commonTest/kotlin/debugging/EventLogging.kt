package debugging

import io.github.yafrl.EventState.Fired
import io.github.yafrl.annotations.FragileYafrlAPI
import io.github.yafrl.externalEvent
import io.github.yafrl.timeline.debugging.EventLogger
import io.github.yafrl.timeline.NodeID
import io.github.yafrl.timeline.Timeline
import io.github.yafrl.timeline.debugging.ExternalEvent
import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.assertEquals

@OptIn(FragileYafrlAPI::class)
class EventLogging : FunSpec({
    test("test event logging") {
        Timeline.initializeTimeline(
            scope = CoroutineScope(Dispatchers.Default),
            eventLogger = EventLogger.InMemory(),
            timeTravel = true
        )

        val externalEvent = externalEvent<Int>()

        val mapped = externalEvent
            .map { it + 2 }

        externalEvent.send(1)

        externalEvent.send(2)

        assertEquals(
            listOf(
                ExternalEvent(NodeID(0), Fired(1)),
                ExternalEvent(NodeID(0), Fired(2))
            ),
            Timeline.currentTimeline().reportEvents().toList()
        )
    }
})