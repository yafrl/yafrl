package io.github.sintrastes.yafrl

import io.github.sintrastes.yafrl.internal.Node
import io.github.sintrastes.yafrl.internal.current
import io.github.sintrastes.yafrl.internal.nodeGraph
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow

/**
 * A flow can be thought of as a combination of a [Behavior] and an
 *  [Event].
 *
 * Like a [Behavior], a [State] has a [current] value which can be
 *  sampled at any time.
 *
 * Like an [Event], a [State] will automatically influence derived [State]s
 *  when the underlying state changes -- in other words, it is _reactive_.
 *
 * Following our graphical analogies for [Event] and [Behavior], a [State]
 *  can be thought of as a stepwise function.
 *
 * ```
 * ^
 * |                       ********
 * |          ****
 * |  ********                     ********
 * |              *********
 * ---------------------------------------->
 * ```
 *
 * [State]s are incredibly useful for representing the state of
 *  various components in an application which need to be consumed
 *  in responsive user interfaces.
 *
 * They are very similar to [StateFlow]s in this sense -- only better.
 **/
open class State<A> internal constructor(
    internal val node: Node<A>
): Flow<A>, Behavior<A> {
    override suspend fun collect(collector: FlowCollector<A>) {
        node.collect(collector)
    }

    override suspend fun current(): A {
        return node.current()
    }

    suspend fun <B> map(f: (A) -> B): State<B> {
        val graph = currentCoroutineContext().nodeGraph!!

        return State(graph.createMappedNode(node, f))
    }


}

/**
 * Variant of [State] that can be [setTo] a new value.
 *
 * Constructed with the [mutableStateOf] function.
 **/
class MutableState<A> internal constructor(
    node: Node<A>
): State<A>(node) {
    suspend infix fun setTo(updatedValue: A) {
        val graph = currentCoroutineContext().nodeGraph!!

        graph.updateNodeValue(node, updatedValue)
    }
}

suspend fun <A> mutableStateOf(value: A): MutableState<A> {
    val graph = currentCoroutineContext().nodeGraph!!

    return MutableState(graph.createNode(lazy { value }))
}