package event_logging

import io.github.yafrl.Signal
import io.github.yafrl.externalEvent
import io.github.yafrl.externalSignal
import io.github.yafrl.sample
import io.github.yafrl.signal
import io.github.yafrl.timeline.Timeline
import io.github.yafrl.timeline.logging.EventLogger
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import java.io.File

class EventLoggingTests : FunSpec({
    test("Events write to log") {
        val testFile = File.createTempFile("test", "")

        val format = Json {
            useArrayPolymorphism = true

            serializersModule = SerializersModule {
                polymorphic(Any::class, String::class, String.serializer())
            }
        }

        val logger = EventLogger.File(testFile.absolutePath, format)

        Timeline.initializeTimeline(
            eventLogger = logger
        )

        val event = externalEvent<String>()

        val state = externalSignal("test")

        val accumulated = Signal.fold("", event) { x, y -> x + y }

        val derived = signal {
            state.bind() + accumulated.bind()
        }

        sample { derived.currentValue() } shouldBe "test"

        event.send("!")

        sample { derived.currentValue() } shouldBe "test!"

        state.value = "foo"

        sample { derived.currentValue() } shouldBe "foo!"

        // Ensure that external events are serialized properly.
        logger.reportEvents().toString() shouldBe "[" +
                "ExternalEvent(" +
                    "behaviorsSampled={}, externalAction=FireEvent(id=node#0, value=!)" +
                "), " +
                "ExternalEvent(" +
                    "behaviorsSampled={}, externalAction=UpdateValue(id=node#1, value=foo)" +
                ")]"

        testFile.deleteOnExit()
    }
})