package io.github.yafrl.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

interface StateFlowBuilder {
    fun <A> StateFlow<A>.bind(): A
}

/**
 * Monadic builder syntax used for building up state flows.
 *
 * Compare with [io.github.yafrl.signal].
 **/
fun <A> stateFlow(
    scope: CoroutineScope,
    body: StateFlowBuilder.() -> A
): StateFlow<A> {
    val recompute = MutableSharedFlow<Unit>()

    val registrationScope = object: StateFlowBuilder {
        override fun <A> StateFlow<A>.bind(): A {
            val updates = drop(1)

            scope.launch {
                updates.collect {
                    recompute.emit(Unit)
                }
            }

            return value
        }
    }

    val initialValue = registrationScope.body()

    val result = MutableStateFlow(initialValue)

    val recomputeScope = object: StateFlowBuilder {
        override fun <A> StateFlow<A>.bind(): A {
            return value
        }
    }

    scope.launch {
        recompute.collect {
            result.value = recomputeScope.body()
        }
    }

    return result
}