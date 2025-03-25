package events

import io.github.sintrastes.yafrl.*
import io.github.sintrastes.yafrl.EventState
import io.github.sintrastes.yafrl.annotations.FragileYafrlAPI
import io.github.sintrastes.yafrl.internal.Timeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class EventUpdatingTests {
    @BeforeTest
    fun `init timeline`() {
        Timeline.initializeTimeline(
            CoroutineScope(Dispatchers.Default)
        )
    }

    @Test
    fun `Event updates immediately`() {
        val timeline = Timeline.currentTimeline()

        val events = broadcastEvent<Int>()

        events.send(1)

        assertEquals(
            EventState.Fired(1),
            timeline.fetchNodeValue(events.node)
        )
    }

    @OptIn(FragileYafrlAPI::class)
    @Test
    fun `Mapped event updates if collected`() {
        runTest {
            val timeline = Timeline.currentTimeline()

            val events = broadcastEvent<Int>()

            val mappedEvents = events
                .map { it + 2 }

            mappedEvents.collect {
                // no-op
            }

            events.send(1)

            assertEquals(
                EventState.Fired(3),
                timeline.fetchNodeValue(mappedEvents.node)
            )
        }
    }

    @Test
    fun `Mapped event is lazy`() {
        val events = broadcastEvent<Int>()

        val mappedEvents = events
            .map { it + 2 }

        events.send(1)

        assertEquals(
            EventState.None,
            mappedEvents.node.rawValue
        )
    }

    @Test
    fun `filtered event does not emit on filtered elements`() {
        val events = broadcastEvent<Int>()

        // Should only let event events through.
        val filtered = events
            .filter { it % 2 == 0 }

        events.send(1)

        assertEquals(EventState.None, filtered.node.rawValue)
    }

    @Test
    fun `Event should not be fired on next tick`() {
        val event1 = broadcastEvent<Unit>()

        val event2 = broadcastEvent<Unit>()

        // Emit an event
        event1.send(Unit)

        // In this frame, we should see the event has fired.
        assertEquals(EventState.Fired(Unit), event1.node.rawValue)

        // Simulate a new frame by emitting a second event
        event2.send(Unit)

        // The first event should no longer be "fired" in this frame.
        assertEquals(EventState.None, event1.node.rawValue)
    }


    @Test
    fun `Default merge handling is Leftmost`() {
        val graph = Timeline.currentTimeline()

        val count = broadcastEvent<Int>()

        val fizz = count
            .filter { it % 3 == 0 }
            .map { "fizz" }

        val buzz = count
            .filter { it % 5 == 0 }
            .map { "buzz" }

        val fizzbuzz = Event.merged(fizz, buzz)

        count.send(3)
        assertEquals(
            EventState.Fired("fizz"),
            graph.fetchNodeValue(fizzbuzz.node)
        )

        count.send(5)
        assertEquals(
            EventState.Fired("buzz"),
            graph.fetchNodeValue(fizzbuzz.node)
        )

        count.send(15)
        assertEquals(
            EventState.Fired("fizz"),
            graph.fetchNodeValue(fizzbuzz.node)
        )
    }

    @Test
    fun `Custom merge works for fizzbuzz`() {
        val graph = Timeline.currentTimeline()

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

        val fizzbuzz = Event.mergedWith(
            append,
            fizz,
            buzz
        )

        count.send(3)
        assertEquals(
            EventState.Fired("fizz"),
            graph.fetchNodeValue(fizzbuzz.node)
        )

        count.send(5)
        assertEquals(
            EventState.Fired("buzz"),
            graph.fetchNodeValue(fizzbuzz.node)
        )

        count.send(15)
        assertEquals(
            EventState.Fired("fizzbuzz"),
            graph.fetchNodeValue(fizzbuzz.node)
        )
    }

    @Test
    fun `Event does not fire if gated`() {
        val clicks = broadcastEvent<Unit>()

        val enabled = bindingState<Boolean>(true)

        val count = clicks.gate(!enabled).scan(0) { count, _click ->
            count + 1
        }

        clicks.send(Unit)

        assertEquals(1, count.value)

        enabled.value = false

        clicks.send(Unit)

        assertEquals(1, count.value)

        enabled.value = true

        clicks.send(Unit)

        assertEquals(2, count.value)
    }

    @Test
    fun `Window works as intended`() {
        val event = broadcastEvent<Int>()

        val windowed = State.hold(listOf(), event.window(3))

        event.send(1)
        event.send(2)
        event.send(3)

        assertEquals(listOf(1, 2, 3), windowed.value)

        event.send(4)

        assertEquals(listOf(2, 3, 4), windowed.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `Debounce only emits last event`() {
        val event = broadcastEvent<Int>()

        val debounced = event.debounced(100.milliseconds).hold(0)

         runTest {
             event.send(1)
             event.send(2)
             event.send(3)

             withContext(Dispatchers.Default) { delay(110.milliseconds) }
             advanceUntilIdle()

             assertEquals(3, debounced.value)
         }
    }

    @Test
    fun `Tick emits events at specified intervals`() {
        runTest {
            val ticks = Event
                .tick(50.milliseconds)
                .scan(0) { ticks, _ -> ticks + 1 }

            withContext(Dispatchers.Default) {
                repeat(10) {
                    delay(50.milliseconds)

                    // TODO: This should not be required. Laziness bug.
                    ticks.value
                }

                delay(10.milliseconds)

                assertEquals(10, ticks.value)
            }
        }
    }
}