package io.github.yafrl.timeline.logging

import io.github.yafrl.timeline.debugging.ExternalEvent
import io.github.yafrl.timeline.debugging.ExternalEventSerializer
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readLine
import kotlinx.io.writeString

/**
 * Interface allowing for custom implementations of yafrl's event logging
 *  capability to be injected into a [Timeline].
 *
 * yafrl provides an [InMemory] logger by default, but if desired a custom implementation
 *  can be used (e.x. to provide console logging, serialization of events, etc...)
 **/
interface EventLogger {
    /**
     * Return the list of all currently logged events in-order.
     *
     * Note: Depending on implementation, may or may not be the
     *  total log of all events since the beginnning of the timeline.
     **/
    fun reportEvents(): List<ExternalEvent>

    /**
     * Send an event to the log.
     **/
    fun logEvent(event: ExternalEvent)

    /** The default event logger -- does not log anything. */
    object Disabled: EventLogger {
        override fun reportEvents() = listOf<ExternalEvent>()
        override fun logEvent(event: ExternalEvent) { }
    }

    /** A simple in-memory event logger. */
    class InMemory: EventLogger {
        private val eventLog = mutableListOf<ExternalEvent>()

        override fun reportEvents(): List<ExternalEvent> {
            return eventLog.toList()
        }

        override fun logEvent(event: ExternalEvent) {
            eventLog += event
        }
    }

    /**
     * A simple event logger that logs serialized event traces to the specified file.
     *
     * NOTE: This requires that all events / state updates in your program are serializable,
     *  with a serializer that has been registered for polymorphic serialization in Any within
     *  the serializers module of the passed [format], and will throw an [AssertionError] at runtime
     *  if this is not the case.
     **/
    class File(private val path: String, private val format: StringFormat): EventLogger {
        private val loggerOutput = SystemFileSystem.sink(Path(path)).buffered()

        override fun reportEvents(): List<ExternalEvent> {
            val loggerInput = SystemFileSystem
                .source(Path(path))
                .buffered()

            var eventLog = mutableListOf<ExternalEvent>()

            var line: String? = null
            while (run { line = loggerInput.readLine(); line != null }) {
                println("Reading line: $line")
                val decoded = format.decodeFromString(ExternalEventSerializer, line!!)
                eventLog.add(decoded)
            }

            return eventLog.toList()
        }

        @OptIn(ExperimentalSerializationApi::class)
        override fun logEvent(event: ExternalEvent) {
            // Assert the user has registered the relevant polymorphic serializer.
            // And if not, fail early with an assertion.
            event.externalAction.value?.let { value ->
                if (format.serializersModule.getPolymorphic(Any::class, value) == null) {
                    throw AssertionError("Polymorphic serializer in Any was not registered for ${value::class}")
                }
            }

            val encoded = format.encodeToString(ExternalEventSerializer, event)

            println("Encoded: $encoded")

            loggerOutput.writeString(encoded + "\n")
            loggerOutput.flush()
        }
    }
}