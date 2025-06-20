package io.github.sintrastes.yafrl.coroutines

import io.github.sintrastes.yafrl.BindingState
import io.github.sintrastes.yafrl.BroadcastEvent
import io.github.sintrastes.yafrl.Event
import io.github.sintrastes.yafrl.State
import io.github.sintrastes.yafrl.annotations.FragileYafrlAPI
import io.github.sintrastes.yafrl.broadcastEvent
import io.github.sintrastes.yafrl.internal.Timeline
import io.github.sintrastes.yafrl.bindingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Construct an [Event] at the edge of the FRP network by
 *  emitting a frame whenever the passed [Flow] updates.
 **/
@OptIn(FragileYafrlAPI::class)
inline fun <reified A> Flow<A>.asEvent(): Event<A> {
    val event = broadcastEvent<A>()
    val scope = Timeline.currentTimeline().scope
    bindFlowToEvent(scope, this , event)

    return event
}

// Note: Created to fix bug with test coverage.
@FragileYafrlAPI
fun <A> bindFlowToEvent(scope: CoroutineScope, flow: Flow<A>, event: BroadcastEvent<A>) {
    scope.launch {
        flow.collect { value ->
            event.send(value)
        }
    }
}

/**
 * Construct a [State] at the edge of the FRP network
 *  with the same values and update behavior of the passed [StateFlow].
 **/
@OptIn(FragileYafrlAPI::class)
inline fun <reified A> StateFlow<A>.asState(): State<A> {
    val state = bindingState(value)
    val scope = Timeline.currentTimeline().scope
    bindStateFlowToState(scope, this, state)

    return state
}

// Note: Created to fix bug with test coverage
@FragileYafrlAPI
fun <A> bindStateFlowToState(scope: CoroutineScope, flow: StateFlow<A>, state: BindingState<A>) {
    scope.launch {
        // Since state flows replay the current state, drop the first value
        // so we only emit on updates.
        flow.drop(1).collect { updatedValue ->
            state.value = updatedValue
        }
    }
}