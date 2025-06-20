package io.github.sintrastes.yafrl

import io.github.sintrastes.yafrl.annotations.FragileYafrlAPI
import io.github.sintrastes.yafrl.internal.Node
import io.github.sintrastes.yafrl.internal.Timeline

interface StateScope {
    fun <A> State<A>.bind(): A
}

/**
 * Monadic builder syntax for state, inspired by those formerly used by
 *  [arrow-kt](https://old.arrow-kt.io/docs/patterns/monad_comprehensions/).
 *
 * [state] introduces a scope allowing for the use of the [StateScope.bind] method.
 *  This works similarly to [State.value], but calls to bind within the block are
 *  recomputed whenever any of the [State]s that bind was called on have updated
 *  values.
 *
 * In effect then, [state] gives us a nicer syntax for both [State.flatMap] as well as
 *  the various [State.combineWith]s, depending on how it is used.
 *
 * For example, the following
 *
 * ```kotlin
 * foo.combineWith(bar) { foo, bar -> foo + bar }
 * ```
 *
 * is equivalent to:
 *
 * ```kotlin
 * state { foo.bind() + bar.bind() }
 * ```
 *
 * which is especially convenient for [State.combineWith] calls with many parameters.
 *
 * For usages where the result of one [State] is bound and used in the rest of the block such as the following:
 *
 * ```kotlin
 * state {
 *     val x = xState.bind()
 *     val y = yState.bind()
 *     if (x - y == 0) {
 *         zState.bind()
 *     } else {
 *         wState.bind()
 *     }
 * }
 * ```
 *
 * the result is equivalent to:
 *
 * ```kotlin
 * xState.flatMap { x ->
 *     yState.flatMap { y ->
 *         if (x - y == 0) {
 *             zState
 *         } else {
 *             wState
 *         }
 *     }
 * }
 * ```
 *
 * In general, the [state] builder lets you treat the bound results of a [State] as regular values,
 *  and have them automatically react to changes in input values the way you'd expect, but with a
 *  convenient sequential syntax -- letting you combine [State]s in complex ways without
 *  having to deal with the large lambdas of [State.combineWith] or the nested callbacks needed for certain
 *  uses of [State.flatMap].
 **/
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