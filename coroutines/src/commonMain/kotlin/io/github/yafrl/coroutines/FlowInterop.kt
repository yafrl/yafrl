package io.github.yafrl.coroutines

import io.github.yafrl.BindingSignal
import io.github.yafrl.BroadcastEvent
import io.github.yafrl.Event
import io.github.yafrl.EventState
import io.github.yafrl.Signal
import io.github.yafrl.annotations.FragileYafrlAPI
import io.github.yafrl.timeline.Timeline
import io.github.yafrl.sample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Construct an [Event] at the edge of the FRP network by
 *  emitting a frame whenever the passed [Flow] updates.
 **/
@OptIn(FragileYafrlAPI::class)
inline fun <reified A> Flow<A>.asEvent(timeline: Timeline): Event<A> {
    return with(timeline.timelineScope) {
        val event = externalEvent<A>()
        val scope = timeline.scope
        bindFlowToEvent(scope, this@asEvent, event)

        event
    }
}

/**
 * Construct a new [Flow] that updates whenever the [Event] fires
 *  in yafrl's state graph.
 *
 * Note: Events will be emitted to the flow using the timeline's builtin
 *  coroutine scope.
 **/
@OptIn(FragileYafrlAPI::class)
fun <A> Event<A>.asFlow(timeline: Timeline): Flow<A> = with(timeline.timelineScope) {
    val result = MutableSharedFlow<A>()

    timeline.scope.launch {
        node.collect { newEvent ->
            if (newEvent is EventState.Fired<A>) result.emit(newEvent.event)
        }
    }

    result
}

/** @suppress -- Internal: Created to fix bug with test coverage. */
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
inline fun <reified A> StateFlow<A>.asSignal(timeline: Timeline): Signal<A> = with(timeline.timelineScope) {
    val state = externalSignal(value)
    val scope = timeline.scope
    bindStateFlowToState(scope, this@asSignal, state)

    state
}

/**
 * Build a new [StateFlow] whose state matches that of the supplied [Signal].
 *
 * Useful for interop with pre-existing APIs using state flow.
 **/
@OptIn(FragileYafrlAPI::class)
fun <A> Signal<A>.asStateFlow(timeline: Timeline): StateFlow<A> = with(timeline.timelineScope) {
    sample {
        val result = MutableStateFlow(
            currentValue()
        )

        node.collectSync { newValue ->
            result.value = newValue
        }

        result
    }
}

/** @suppress Internal -- Created to fix bug with test coverage */
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