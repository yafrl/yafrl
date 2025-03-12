package io.github.sintrastes.yafrl.interop

import androidx.compose.runtime.mutableStateOf
import io.github.sintrastes.yafrl.FragileYafrlAPI
import io.github.sintrastes.yafrl.State
import io.github.sintrastes.yafrl.internal.Timeline
import kotlinx.coroutines.launch

/**
 * Utility function to assist with using a [io.github.sintrastes.yafrl.State]
 *  in Jetpack Compose, by converting it to a [androidx.compose.runtime.State].
 **/
@OptIn(FragileYafrlAPI::class)
fun <A> State<A>.composeState(): androidx.compose.runtime.State<A> {
    val state = mutableStateOf(value = value)

    val scope = Timeline.currentTimeline().scope

    scope.launch {
        collectAsync { updatedState ->
            state.value = updatedState
        }
    }

    return state
}