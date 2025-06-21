package io.github.yafrl

import io.github.yafrl.annotations.FragileYafrlAPI
import io.github.yafrl.timeline.Node
import io.github.yafrl.timeline.Timeline
import io.github.yafrl.timeline.current

interface SignalScope {
    fun <A> Signal<A>.bind(): A
}

/**
 * Monadic builder syntax for [Signal]s, inspired by those formerly used by
 *  [arrow-kt](https://old.arrow-kt.io/docs/patterns/monad_comprehensions/).
 *
 * [signal] introduces a scope allowing for the use of the [SignalScope.bind] method.
 *  This works similarly to [Signal.value], but calls to bind within the block are
 *  recomputed whenever any of the [Signal]s that bind was called on have updated
 *  values.
 *
 * In effect then, [signal] gives us a nicer syntax for both [Signal.flatMap] as well as
 *  the various [Signal.combineWith]s, depending on how it is used.
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
 * which is especially convenient for [Signal.combineWith] calls with many parameters.
 *
 * For usages where the result of one [Signal] is bound and used in the rest of the block such as the following:
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
 * In general, the [signal] builder lets you treat the bound results of a [Signal] as regular values,
 *  and have them automatically react to changes in input values the way you'd expect, but with a
 *  convenient sequential syntax -- letting you combine [Signal]s in complex ways without
 *  having to deal with the large lambdas of [Signal.combineWith] or the nested callbacks needed for certain
 *  uses of [Signal.flatMap].
 **/
@OptIn(FragileYafrlAPI::class)
fun <A> signal(body: SignalScope.() -> A): Signal<A> {
    val parentNodes = mutableListOf<Node<Any?>>()

    // Run the body once to determine parent nodes, and get initial value
    val initialScope = object: SignalScope {
        override fun <A> Signal<A>.bind(): A {
            parentNodes += node
            return node.current()
        }
    }

    initialScope.body()

    // When recomputing, just return the latest values of all of the states.
    val recomputeScope = object: SignalScope {
        override fun <A> Signal<A>.bind(): A {
            return node.current()
        }
    }

    val timeline = Timeline.currentTimeline()

    return Signal(
        timeline.createCombinedNode(
            parentNodes = parentNodes.toList(),
            combine = {
                recomputeScope.body()
            }
        )
    )
}