package debugging

import io.github.sintrastes.yafrl.EventState.Fired
import io.github.sintrastes.yafrl.broadcastEvent
import io.github.sintrastes.yafrl.internal.NodeID
import io.github.sintrastes.yafrl.internal.Timeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals

class EventLoggingWorks {
    @Test
    fun `test event logging`() {
        Timeline.initializeTimeline(
            scope = CoroutineScope(Dispatchers.Default),
            timeTravel = true
        )

        val externalEvent = broadcastEvent<Int>()

        val mapped = externalEvent
            .map { it + 2 }

        externalEvent.send(1)

        externalEvent.send(2)

        assertEquals(
            listOf(
                Timeline.ExternalEvent(NodeID(0), Fired(1)),
                Timeline.ExternalEvent(NodeID(0), Fired(2))
            ),
            Timeline.currentTimeline().eventTrace.toList()
        )
    }
}