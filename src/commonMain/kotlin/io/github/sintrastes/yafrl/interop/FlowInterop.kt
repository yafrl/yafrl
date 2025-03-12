package io.github.sintrastes.yafrl.interop

import io.github.sintrastes.yafrl.Event
import io.github.sintrastes.yafrl.State
import io.github.sintrastes.yafrl.broadcastEvent
import io.github.sintrastes.yafrl.internal.Timeline
import io.github.sintrastes.yafrl.mutableStateOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Construct an [Event] at the edge of the FRP network by
 *  emitting a frame whenever the passed [Flow] updates.
 **/
fun <A> Flow<A>.asEvent(): Event<A> {
    val event = broadcastEvent<A>()

    val scope = Timeline.currentTimeline().scope

    scope.launch {
        collect { value ->
            event.send(value)
        }
    }

    return event
}

/**
 * Construct a [State] at the edge of the FRP network
 *  with the same values and update behavior of the passed [StateFlow].
 **/
fun <A> StateFlow<A>.asState(): State<A> {
    val state = mutableStateOf(value)

    val scope = Timeline.currentTimeline().scope

    scope.launch {
        // Since state flows replay the current state, drop the first value
        // so we only emit on updates.
        drop(1).collect { updatedValue ->
            state.value = updatedValue
        }
    }

    return state
}