package io.github.sintrastes.yafrl

import io.github.sintrastes.yafrl.annotations.FragileYafrlAPI
import io.github.sintrastes.yafrl.internal.Node
import io.github.sintrastes.yafrl.internal.Timeline

interface StateScope {
    fun <A> State<A>.bind(): A
}

@OptIn(FragileYafrlAPI::class)
fun <A> state(body: StateScope.() -> A): State<A> {
    val parentNodes = mutableListOf<Node<Any?>>()

    // Run the body once to determine parent nodes, and get initial value
    val initialScope = object: StateScope {
        override fun <A> State<A>.bind(): A {
            parentNodes += node
            return value
        }
    }

    initialScope.body()

    // When recomputing, just return the latest values of all of the states.
    val recomputeScope = object: StateScope {
        override fun <A> State<A>.bind(): A {
            return value
        }
    }

    val timeline = Timeline.currentTimeline()

    return State(
        timeline.createCombinedNode(
            parentNodes = parentNodes.toList(),
            combine = {
                recomputeScope.body()
            }
        )
    )
}