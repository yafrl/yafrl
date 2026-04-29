package io.github.yafrl.testing

import io.github.yafrl.Signal
import io.github.yafrl.annotations.FragileYafrlAPI
import io.github.yafrl.runYafrl
import io.github.yafrl.sample
import io.github.yafrl.timeline.Timeline
import io.github.yafrl.timeline.TimelineScope
import io.github.yafrl.timeline.BehaviorID
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.Sample
import kotlin.time.Duration

/**
 * Attempts to find the minimal list of actions that reproduces a test failure using recursive
 *  list shrinking on the actions.
 *
 * Note: Should probably be bounded by max shrinks.
 *
 * Note: Should probably use recursive list shrinking native to kotest if that ever makes it into
 *  kotest natively.
 **/
@OptIn(FragileYafrlAPI::class)
fun <W> shrinkActions(
    setupState: TimelineScope.() -> Signal<W>,
    actions: List<StateSpaceAction>,
    randomizeBehaviors: Boolean = false,
    randomSource: RandomSource = RandomSource.default(),
    clockGenerator: Arb<Duration> = fpsClockGenerator(),
    actionsPassed: Boolean = true,
    test: (List<W>) -> Boolean,
): List<StateSpaceAction>? {
    // Adapted from my fork of kotest to work with the current (un-forked) version of Kotest.
    val shrinks = when {
        actions.isEmpty() -> emptyList()
        actions.size == 1 -> {
            // Try removing the single action entirely, then try each individually shrunk
            // variant (smaller event value or smaller behavior value) as its own candidate.
            listOf(emptyList<StateSpaceAction>()) + actions.first().shrink().map { shrunkAction ->
                listOf(shrunkAction)
            }
        }
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
            var currentBehaviorValues: Map<BehaviorID, Sample<Any?>> = emptyMap()
            if (randomizeBehaviors) {
                // Fall back to fresh draws for behaviors not present in the recorded map
                // (e.g. a behavior that was inactive during the original failing run).
                val fallback = arbBehaviorMockProvider(randomSource, clockGenerator)
                timeline.behaviorMockProvider = { id, type ->
                    currentBehaviorValues[id]?.value ?: fallback(id, type)
                }
            }

            val signal = setupState()

            states += sample { signal.currentValue() }

            for (action in shrink) {
                currentBehaviorValues = action.behaviorValues
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
            val result = shrinkActions(setupState, shrink, randomizeBehaviors, randomSource, clockGenerator, testPassed, test)

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