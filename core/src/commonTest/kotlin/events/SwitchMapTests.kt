package events

import io.github.yafrl.EventState
import io.github.yafrl.Signal
import io.github.yafrl.annotations.FragileYafrlAPI
import io.github.yafrl.runYafrl
import io.kotest.core.spec.style.FunSpec
import kotlin.test.assertEquals

@OptIn(FragileYafrlAPI::class)
class SwitchMapTests : FunSpec({
    test("switchMap forwards events from initially active source") {
        runYafrl {
            val use1 = externalSignal(true)
            val event1 = externalEvent<Int>("event1")
            val event2 = externalEvent<Int>("event2")

            val switched = use1.switchMap { b -> if (b) event1 else event2 }

            val count = switched.scan(0) { n, _ -> n + 1 }

            event1.send(42)

            assertEquals(1, count.currentValue())
        }
    }

    test("switchMap does not forward events from inactive source") {
        runYafrl {
            val use1 = externalSignal(true)
            val event1 = externalEvent<Int>("event1")
            val event2 = externalEvent<Int>("event2")

            val switched = use1.switchMap { b -> if (b) event1 else event2 }

            val count = switched.scan(0) { n, _ -> n + 1 }

            event2.send(42)

            assertEquals(0, count.currentValue())
        }
    }

    test("switchMap switches to new source after outer signal changes") {
        runYafrl {
            val use1 = externalSignal(true)
            val event1 = externalEvent<Int>("event1")
            val event2 = externalEvent<Int>("event2")

            val switched = use1.switchMap { b -> if (b) event1 else event2 }

            val count = switched.scan(0) { n, _ -> n + 1 }

            event1.send(1)
            assertEquals(1, count.currentValue())

            use1.value = false

            // After switch: event1 should no longer fire through, event2 should
            event1.send(2)
            assertEquals(1, count.currentValue())

            event2.send(3)
            assertEquals(2, count.currentValue())
        }
    }

    test("switchMap stops forwarding old source after switching") {
        runYafrl {
            val use1 = externalSignal(true)
            val event1 = externalEvent<Unit>("event1")
            val event2 = externalEvent<Unit>("event2")

            val switched = use1.switchMap { b -> if (b) event1 else event2 }

            val count = switched.scan(0) { n, _ -> n + 1 }

            use1.value = false

            event1.send(Unit)

            // event1 is now inactive; switch output should not have fired
            assertEquals(0, count.currentValue())
        }
    }

    test("switchMap graph tracks active source via addChild / removeChild") {
        runYafrl {
            val use1 = externalSignal(true)
            val event1 = externalEvent<Unit>("event1")
            val event2 = externalEvent<Unit>("event2")

            val switched = use1.switchMap { b -> if (b) event1 else event2 }

            // Consume switched so the result node exists in the graph
            switched.scan(0) { n, _ -> n + 1 }

            // Initially event1 is active (has children), event2 is not
            val event1ChildrenBefore = timeline.graph.getChildrenOf(event1.node.id)
            val event2ChildrenBefore = timeline.graph.getChildrenOf(event2.node.id)
            assertEquals(true, event1ChildrenBefore.isNotEmpty(), "event1 should be active initially")
            assertEquals(true, event2ChildrenBefore.isEmpty(), "event2 should be inactive initially")

            use1.value = false

            // After switching: event2 is now active, event1 is not
            val event1ChildrenAfter = timeline.graph.getChildrenOf(event1.node.id)
            val event2ChildrenAfter = timeline.graph.getChildrenOf(event2.node.id)
            assertEquals(true, event1ChildrenAfter.isEmpty(), "event1 should be inactive after switch")
            assertEquals(true, event2ChildrenAfter.isNotEmpty(), "event2 should be active after switch")
        }
    }
})
