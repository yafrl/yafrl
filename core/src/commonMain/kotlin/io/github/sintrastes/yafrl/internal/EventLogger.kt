package io.github.sintrastes.yafrl.internal

import io.github.sintrastes.yafrl.internal.Timeline.ExternalEvent

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
}