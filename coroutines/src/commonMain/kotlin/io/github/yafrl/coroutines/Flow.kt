package io.github.yafrl.coroutines

import io.github.yafrl.BindingSignal
import io.github.yafrl.BroadcastEvent
import io.github.yafrl.Event
import io.github.yafrl.Signal
import io.github.yafrl.annotations.FragileYafrlAPI
import io.github.yafrl.externalEvent
import io.github.yafrl.timeline.Timeline
import io.github.yafrl.externalSignal
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
    val event = externalEvent<A>()
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
 * Construct a [Signal] at the edge of the FRP network
 *  with the same values and update behavior of the passed [StateFlow].
 **/
@OptIn(FragileYafrlAPI::class)
inline fun <reified A> StateFlow<A>.asState(): Signal<A> {
    val state = externalSignal(value)
    val scope = Timeline.currentTimeline().scope
    bindStateFlowToState(scope, this, state)

    return state
}

// Note: Created to fix bug with test coverage
@FragileYafrlAPI
fun <A> bindStateFlowToState(scope: CoroutineScope, flow: StateFlow<A>, state: BindingSignal<A>) {
    scope.launch {
        // Since state flows replay the current state, drop the first value
        // so we only emit on updates.
        flow.drop(1).collect { updatedValue ->
            state.value = updatedValue
        }
    }
}