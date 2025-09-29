package io.github.yafrl.testing

import io.github.yafrl.Signal
import io.github.yafrl.runYafrl
import io.github.yafrl.sample
import io.github.yafrl.timeline.Timeline
import io.github.yafrl.timeline.TimelineScope

/**
 * Attempts to find the minimal list of actions that reproduces a test failure using recursive
 *  list shrinking on the actions.
 *
 * Note: Should probably be bounded by max shrinks.
 *
 * Note: Should probably use recursive list shrinking native to kotest if that ever makes it into
 *  kotest natively.
 **/
fun <W> shrinkActions(
    setupState: TimelineScope.() -> Signal<W>,
    actions: List<StateSpaceAction>,
    actionsPassed: Boolean = true,
    test: (List<W>) -> Boolean,
): List<StateSpaceAction>? {
    // Adapted from my fork of kotest to work with the current (un-forked) version of Kotest.
    val shrinks = when {
        actions.isEmpty() -> emptyList()
        actions.size == 1 -> listOfNotNull<List<StateSpaceAction>>(
            actions.first().shrink()
        )
        else -> {
            val removals = listOf(
                // just the last element
                actions.takeLast(1),
                // Try removing the first half.
                actions.drop(actions.size / 2)
                // Try removing individual steps at all indices
            ) + actions.indices.map { i ->
                actions.toMutableList()
                    .also { it.removeAt(i) }
            }

            removals.filter { it.size >= 1 } + actions.flatMapIndexed { i, item ->
                // For each index of the list, we can try shrinking any of the arguments
                // In all of the possible ways it can be shrunk.
                item.shrink().map { shrunkItem ->
                    val result = actions.toMutableList()

                    result.removeAt(i)

                    result.add(i, shrunkItem)

                    result
                }
            }
        }
    }

    var anyFailed = false

    for (shrink in shrinks) {
        val states = mutableListOf<W>()

        runYafrl {
            val signal = setupState()

            states += sample { signal.currentValue() }

            for (action in shrink) {
                action.performAction(timeline)
                states += sample { signal.currentValue() }
            }
        }

        println("Testing shrunk actions ${shrink.map { it.value.value }}")
        println("  (with states: ${states})")

        val testPassed = try {
            test(states)
        } catch (_: IndexOutOfBoundsException) {
            // Skip -- trace is too small to reproduce.
            continue
        }

        // Test didn't pass, so we can still try to shrink it further
        if (!testPassed) {
            anyFailed = true

            // Recurse to see if we can get an even smaller result.
            val result = shrinkActions(setupState, shrink, testPassed, test)

            if (result == null) {
                continue
            }

            return result
        }
    }

    val allPassed = !anyFailed

    if (allPassed && !actionsPassed) {
        // If every possible shrink failed, we have found the minimal list of actions
        return actions
    } else {
        // Otherwise, we have failed.
        return null
    }
}