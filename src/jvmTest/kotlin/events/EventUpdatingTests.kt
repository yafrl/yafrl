package events

import io.github.sintrastes.yafrl.Event
import io.github.sintrastes.yafrl.EventState
import io.github.sintrastes.yafrl.MergeStrategy
import io.github.sintrastes.yafrl.broadcastEvent
import io.github.sintrastes.yafrl.internal.newTimeline
import io.github.sintrastes.yafrl.internal.nodeGraph
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.Test

class EventUpdatingTests {
    @Test
    fun `Event updates immediately`() {
        runBlocking(newTimeline()) {
            val events = broadcastEvent<Int>()

            var latestEvent: Int? = null

            events.collect {
                latestEvent = it
            }

            events.emit(1)

            assert(latestEvent == 1)
        }
    }

    @Test
    fun `Mapped event updates if collected`() {
        runBlocking(newTimeline()) {
            val events = broadcastEvent<Int>()

            val mappedEvents = events
                .map { it + 2 }

            var latestEvent: Int? = null

            mappedEvents.collect {
                latestEvent = it
            }

            events.emit(1)

            assertEquals(3, latestEvent)
        }
    }

    @Test
    fun `Mapped event is lazy`() {
        runBlocking(newTimeline()) {
            val events = broadcastEvent<Int>()

            val mappedEvents = events
                .map { it + 2 }

            events.emit(1)

            assertEquals(
                EventState.None,
                mappedEvents.node.rawValue
            )
        }
    }

    @Test
    fun `filtered event does not emit on filtered elements`() {
        runBlocking(newTimeline()) {
            val events = broadcastEvent<Int>()

            // Should only let event events through.
            val filtered = events
                .filter { it % 2 == 0 }

            events.emit(1)

            assertEquals(EventState.None, filtered.node.rawValue)
        }
    }

    @Test
    fun `Event should not be fired on next tick`() {
        runBlocking(newTimeline()) {
            val event1 = broadcastEvent<Unit>()

            val event2 = broadcastEvent<Unit>()

            // Emit an event
            event1.emit(Unit)

            // In this frame, we should see the event has fired.
            assertEquals(EventState.Fired(Unit), event1.node.rawValue)

            // Simulate a new frame by emitting a second event
            event2.emit(Unit)

            // The first event should no longer be "fired" in this frame.
            assertEquals(EventState.None, event1.node.rawValue)
        }
    }


    @Test
    fun `Default merge handling is Leftmost`() {
        runBlocking(newTimeline()) {
            val graph = currentCoroutineContext().nodeGraph!!

            val count = broadcastEvent<Int>()

            val fizz = count
                .filter { it % 3 == 0 }
                .map { "fizz" }

            val buzz = count
                .filter { it % 5 == 0 }
                .map { "buzz" }

            val fizzbuzz = Event.merged(fizz, buzz)

            count.emit(3)
            assertEquals(
                EventState.Fired("fizz"),
                graph.fetchNodeValue(fizzbuzz.node)
            )

            count.emit(5)
            assertEquals(
                EventState.Fired("buzz"),
                graph.fetchNodeValue(fizzbuzz.node)
            )

            count.emit(15)
            assertEquals(
                EventState.Fired("fizz"),
                graph.fetchNodeValue(fizzbuzz.node)
            )
        }
    }

    @Test
    fun `Custom merge works for fizzbuzz`() {
        runBlocking(newTimeline()) {
            val graph = currentCoroutineContext().nodeGraph!!

            val count = broadcastEvent<Int>()

            val fizz = count
                .filter { it % 3 == 0 }
                .map { "fizz" }

            val buzz = count
                .filter { it % 5 == 0 }
                .map { "buzz" }

            val append = MergeStrategy { values ->
                values.joinToString("") { it }
            }

            val fizzbuzz = Event.mergedWith(append,
                fizz,
                buzz
            )

            count.emit(3)
            assertEquals(
                EventState.Fired("fizz"),
                graph.fetchNodeValue(fizzbuzz.node)
            )

            count.emit(5)
            assertEquals(
                EventState.Fired("buzz"),
                graph.fetchNodeValue(fizzbuzz.node)
            )

            count.emit(15)
            assertEquals(
                EventState.Fired("fizzbuzz"),
                graph.fetchNodeValue(fizzbuzz.node)
            )
        }
    }
}