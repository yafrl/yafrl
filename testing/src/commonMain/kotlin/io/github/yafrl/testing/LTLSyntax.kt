package io.github.yafrl.testing

import io.github.sintrastes.yafrl.BroadcastEvent
import io.github.sintrastes.yafrl.EventState
import io.github.sintrastes.yafrl.annotations.FragileYafrlAPI

/**
 * Syntax for building up LTL propositions.
 *
 * @param W - The type of the "world" -- i.e. the data we are testing the
 *  temporal proposition on.
 **/
interface LTLSyntax<W> {
    // Basic boolean operations
    infix fun LTL<W>.and(other: LTL<W>): LTL<W>
    infix fun LTL<W>.or(other: LTL<W>): LTL<W>
    infix fun LTL<W>.implies(other: LTL<W>): LTL<W>
    operator fun LTL<W>.not(): LTL<W>

    fun condition(cond: ConditionScope<W>.() -> Boolean): LTL<W>

    // Temporal constructs
    fun always(cond: LTL<W>): LTL<W>
    fun eventually(cond: LTL<W>): LTL<W>
    fun immediately(cond: LTL<W>): LTL<W>

    companion object {
        fun <W> build(syntax: LTLSyntax<W>.() -> LTL<W>): LTL<W> {
            val syntax = object: LTLSyntax<W> {
                override fun LTL<W>.and(other: LTL<W>): LTL<W> {
                    return LTL.And(this, other)
                }

                override fun LTL<W>.or(other: LTL<W>): LTL<W> {
                    return LTL.Or(this, other)
                }

                override fun LTL<W>.implies(other: LTL<W>): LTL<W> {
                    return LTL.Or(LTL.Not(this), other)
                }

                override fun LTL<W>.not(): LTL<W> {
                    return LTL.Not(this)
                }

                override fun condition(cond: ConditionScope<W>.() -> Boolean): LTL<W> {
                    return LTL.Condition(cond)
                }

                override fun always(cond: LTL<W>): LTL<W> {
                    return LTL.Until(cond, LTL.Condition { false })
                }

                override fun eventually(cond: LTL<W>): LTL<W> {
                    return LTL.Eventually(cond)
                }

                override fun immediately(cond: LTL<W>): LTL<W> {
                    return LTL.Next(cond)
                }
            }

            return syntax.syntax()
        }
    }
}

data class ConditionScope<W>(val world: (Int) -> W, val time: Int) {
    val current: W by lazy { world(time) }

    val previous: W? by lazy {
        if (time > 0) {
            world(time - 1)
        } else {
            null
        }
    }
}

/** A proposition of linear temporal logic of statements about the world [W]. */
sealed class LTL<W> {
    /**
     * Evaluate the given LTL proposition with respect to the given [data]
     *  at a specific [time].
     **/
    abstract fun evaluateAtTime(
        world: (Int) -> W,
        time: Int,
        maxTraceLength: Int
    ): LTLResult

    companion object {
        fun <W> evaluate(
            proposition: LTLSyntax<W>.() -> LTL<W>,
            sequence: Iterator<W>,
            maxTraceLength: Int
        ): LTLResult {
            val cachedWorlds = mutableMapOf<Int, W>(0 to sequence.next())

            var latestWorld = 0

            val world = { time: Int ->
                if (!cachedWorlds.containsKey(time)) {
                    for (i in 0 .. time - latestWorld) {
                        latestWorld++
                        cachedWorlds[latestWorld] = sequence.next()
                    }
                }

                cachedWorlds[time]!!
            }

            return evaluate(proposition, world, maxTraceLength)
        }

        fun <W> evaluate(
            proposition: LTLSyntax<W>.() -> LTL<W>,
            world: (Int) -> W,
            maxTraceLength: Int
        ): LTLResult {
            val prop = LTLSyntax.build(proposition)

            return prop.evaluateAtTime(world, 0, maxTraceLength)
        }
    }

    data class And<W>(val x: LTL<W>, val y: LTL<W>): LTL<W>() {
        override fun evaluateAtTime(world: (Int) -> W, time: Int, maxTraceLength: Int): LTLResult {
            return x.evaluateAtTime(world, time, maxTraceLength) and
                    y.evaluateAtTime(world, time, maxTraceLength)
        }
    }

    data class Or<W>(val x: LTL<W>, val y: LTL<W>): LTL<W>() {
        override fun evaluateAtTime(world: (Int) -> W, time: Int, maxTraceLength: Int): LTLResult {
            return x.evaluateAtTime(world, time, maxTraceLength) or
                y.evaluateAtTime(world, time, maxTraceLength)
        }
    }

    data class Not<W>(val cond: LTL<W>): LTL<W>() {
        override fun evaluateAtTime(world: (Int) -> W, time: Int, maxTraceLength: Int): LTLResult {
            return !cond.evaluateAtTime(world, time, maxTraceLength)
        }
    }

    data class Condition<W>(val cond: ConditionScope<W>.() -> Boolean): LTL<W>() {
        override fun evaluateAtTime(world: (Int) -> W, time: Int, maxTraceLength: Int): LTLResult {
            return if (ConditionScope(world, time).cond()) LTLResult.True else LTLResult.False
        }
    }

    data class Next<W>(val cond: LTL<W>): LTL<W>() {
        override fun evaluateAtTime(world: (Int) -> W, time: Int, maxTraceLength: Int): LTLResult {
            return cond.evaluateAtTime(world, time + 1, maxTraceLength)
        }
    }

    data class Eventually<W>(val cond: LTL<W>): LTL<W>() {
        override fun evaluateAtTime(
            world: (Int) -> W,
            time: Int,
            maxTraceLength: Int
        ): LTLResult {
            // Couldn't find an example within the limit.
            if (time > maxTraceLength) return LTLResult.Indeterminate

            if (cond.evaluateAtTime(world, time, maxTraceLength) == LTLResult.True) {
                return LTLResult.True
            } else {
                return evaluateAtTime(world, time + 1, maxTraceLength)
            }
        }
    }

    data class Until<W>(val x: LTL<W>, val y: LTL<W>): LTL<W>() {
        override fun evaluateAtTime(world: (Int) -> W, time: Int, maxTraceLength: Int): LTLResult {
            // Couldn't invalidate proposition, return indeterminate.
            if (time > maxTraceLength) return LTLResult.Indeterminate

            val yHolds = y.evaluateAtTime(world, time, maxTraceLength)

            // The second arg releases us of any responsibilities on x.
            if (yHolds == LTLResult.True) {
                return LTLResult.True
            } else {
                val xHolds = x.evaluateAtTime(world, time, maxTraceLength)

                return when (xHolds) {
                    // Could be true, keep evaluating
                    LTLResult.True -> evaluateAtTime(world, time + 1, maxTraceLength)
                    // Can't get any better than these results, return immediately.
                    LTLResult.False -> LTLResult.False
                    LTLResult.Indeterminate -> LTLResult.Indeterminate
                }
            }
        }
    }
}

/**
 * Results to use for an LTL proposition that has been
 *  evaluated for a finite segment of observations.
 *
 * Essentially a ternary logic with indeterminate truth value
 *  for propositions that require more evaluations in order to
 *  verify.
 **/
enum class LTLResult {
    True, False, Indeterminate;

    // Evaluate by truth table for ternary logic.
    infix fun and(other: LTLResult): LTLResult = when(this) {
        True -> when(other) {
            True -> True
            False -> False
            Indeterminate -> Indeterminate
        }
        False -> when(other) {
            True -> False
            False -> False
            Indeterminate -> False
        }
        Indeterminate -> when(other) {
            True -> Indeterminate
            False -> False
            Indeterminate -> Indeterminate
        }
    }

    // Evaluate by truth table for ternary logic
    infix fun or(other: LTLResult): LTLResult = when(this) {
        True -> when(other) {
            True -> True
            False -> True
            Indeterminate -> True
        }
        False -> when(other) {
            True -> True
            False -> False
            Indeterminate -> Indeterminate
        }
        Indeterminate -> when(other) {
            True -> True
            False -> Indeterminate
            Indeterminate -> Indeterminate
        }
    }

    // Evaluate by truth table for ternary logic
    operator fun not(): LTLResult = when(this) {
        True -> False
        False -> True
        Indeterminate -> Indeterminate
    }

    // Use material implication for now. Not sure if this is what we want.
    infix fun implies(other: LTLResult): LTLResult = not() or other
}