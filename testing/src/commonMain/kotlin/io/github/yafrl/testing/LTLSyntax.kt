package io.github.yafrl.testing

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlin.test.assertTrue

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

    fun condition(cond: ConditionScope<W>.() -> Boolean): ReadOnlyProperty<Any?, LTL<W>>

    // Temporal constructs
    fun always(cond: LTL<W>): LTL<W>
    fun eventually(cond: LTL<W>): LTL<W>
    fun next(cond: LTL<W>): LTL<W>

    infix fun LTL<W>.until(cond: LTL<W>): LTL<W>
    infix fun LTL<W>.releases(by: LTL<W>): LTL<W>

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

                private fun String.toSnakeCase(): String {
                    // insert underscore between lower–upper, and between upper–upperLower
                    val step1 = replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
                    val step2 = step1.replace(Regex("([A-Z])([A-Z][a-z])"), "$1_$2")
                    return step2.lowercase()
                }

                override fun condition(cond: ConditionScope<W>.() -> Boolean): ReadOnlyProperty<Any?, LTL<W>> {
                    return object: ReadOnlyProperty<Any?, LTL<W>> {
                        override fun getValue(
                            thisRef: Any?,
                            property: KProperty<*>
                        ): LTL<W> {
                            return LTL.Condition(property.name.toSnakeCase(), cond)
                        }
                    }
                }

                override fun always(cond: LTL<W>): LTL<W> {
                    return LTL.Until(cond, LTL.False())
                }

                override fun eventually(cond: LTL<W>): LTL<W> {
                    return LTL.Eventually(cond)
                }

                override fun next(cond: LTL<W>): LTL<W> {
                    return LTL.Next(cond)
                }

                override fun LTL<W>.until(cond: LTL<W>): LTL<W> {
                    return LTL.Until(this, cond)
                }

                override fun LTL<W>.releases(by: LTL<W>): LTL<W> {
                    return LTL.Release(this, by)
                }
            }

            return syntax.syntax()
        }
    }
}

data class ConditionScope<W>(val world: (Int) -> W, val time: Int, val maxTraceLength: Int) {
    fun LTL<W>.holds(): Boolean {
        return evaluateAtTime(world, time, maxTraceLength) == LTLResult.True
    }

    val current: W by lazy { world(time) }

    val previous: W? by lazy {
        if (time > 0) {
            world(time - 1)
        } else {
            null
        }
    }

    val next: W by lazy {
        world(time + 1)
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
                    repeat(time - latestWorld) {
                        latestWorld++
                        cachedWorlds[latestWorld] = sequence.next()
                    }
                }

                cachedWorlds[time]
                    ?: throw IndexOutOfBoundsException("Tried to access $time but only ${cachedWorlds.size} have been cached.")
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

    class False<W>() : LTL<W>() {
        override fun evaluateAtTime(
            world: (Int) -> W,
            time: Int,
            maxTraceLength: Int
        ): LTLResult {
            return LTLResult.False
        }
    }

    data class And<W>(val x: LTL<W>, val y: LTL<W>): LTL<W>() {
        override fun evaluateAtTime(world: (Int) -> W, time: Int, maxTraceLength: Int): LTLResult {
            return x.evaluateAtTime(world, time, maxTraceLength) and
                    y.evaluateAtTime(world, time, maxTraceLength)
        }

        override fun toString() = "($x and $y)"
    }

    data class Or<W>(val x: LTL<W>, val y: LTL<W>): LTL<W>() {
        override fun evaluateAtTime(world: (Int) -> W, time: Int, maxTraceLength: Int): LTLResult {
            return x.evaluateAtTime(world, time, maxTraceLength) or
                y.evaluateAtTime(world, time, maxTraceLength)
        }

        override fun toString() = "($x or $y)"
    }

    data class Not<W>(val cond: LTL<W>): LTL<W>() {
        override fun evaluateAtTime(world: (Int) -> W, time: Int, maxTraceLength: Int): LTLResult {
            return !cond.evaluateAtTime(world, time, maxTraceLength)
        }

        override fun toString() = "!$cond"
    }

    data class Condition<W>(val name: String?, val cond: ConditionScope<W>.() -> Boolean): LTL<W>() {
        override fun evaluateAtTime(world: (Int) -> W, time: Int, maxTraceLength: Int): LTLResult {
            // Conditions can fail if trying to access an out of bound index
            val condition = try {
                ConditionScope(world, time, maxTraceLength).cond()
            } catch (e: NoSuchElementException) {
                // If this happens, the expression is presumably false
                return LTLResult.PresumablyFalse
            }

            return if (condition) LTLResult.True else LTLResult.False
        }

        override fun toString() = name ?: "[unnamed_condition]"
    }

    data class Next<W>(val cond: LTL<W>): LTL<W>() {
        override fun evaluateAtTime(world: (Int) -> W, time: Int, maxTraceLength: Int): LTLResult {
            return cond.evaluateAtTime(world, time + 1, maxTraceLength)
        }

        override fun toString() = "next($cond)"
    }

    data class Eventually<W>(val cond: LTL<W>): LTL<W>() {
        override fun evaluateAtTime(
            world: (Int) -> W,
            time: Int,
            maxTraceLength: Int
        ): LTLResult {
            // Couldn't find an example within the limit.
            if (time > maxTraceLength) return LTLResult.PresumablyFalse

            if (cond.evaluateAtTime(world, time, maxTraceLength) == LTLResult.True) {
                return LTLResult.True
            } else {
                return evaluateAtTime(world, time + 1, maxTraceLength)
            }
        }

        override fun toString() = "eventually($cond)"
    }

    // x has to hold at least until y becomes true,
    // which must hold at the current or a future position.
    data class Until<W>(val x: LTL<W>, val y: LTL<W>): LTL<W>() {
        override fun evaluateAtTime(world: (Int) -> W, time: Int, maxTraceLength: Int): LTLResult {
            val yHolds = y.evaluateAtTime(world, time, maxTraceLength)

            // The second arg releases us of any responsibilities on x.
            if (yHolds == LTLResult.True) {
                return LTLResult.True
            }

            val xHolds = x.evaluateAtTime(world, time, maxTraceLength)

            // Couldn't invalidate proposition
            if (time >= maxTraceLength - 1) {
                if (xHolds >= LTLResult.PresumablyTrue) {
                    return LTLResult.PresumablyTrue
                } else {
                    return LTLResult.PresumablyFalse
                }
            }

            return when {
                // Could be true, keep evaluating
                xHolds > LTLResult.PresumablyFalse -> evaluateAtTime(world, time + 1, maxTraceLength)
                // Can't get any better than these results, return immediately.
                else -> {
//                        println("Failed evaluating $x within until at time $time")
                    LTLResult.False
                }
            }
        }

        override fun toString() = "($x until $y)"
    }

    //
    // y holds until (and including) the point where x becomes true (x "releases" y).
    // If x never becomes true, then y must hold forever
    //
    data class Release<W>(val x: LTL<W>, val y: LTL<W>): LTL<W>() {
        override fun evaluateAtTime(world: (Int) -> W, time: Int, maxTraceLength: Int): LTLResult {
            val yHolds = y.evaluateAtTime(world, time, maxTraceLength)

            val xHolds = x.evaluateAtTime(world, time, maxTraceLength)

            if (time >= maxTraceLength - 1) {
                return (yHolds or xHolds) and LTLResult.PresumablyTrue
            }

            // The first arg releases us of any responsibilities on x.
            if (xHolds >= LTLResult.PresumablyTrue) {
                return xHolds
            } else {
                // If the first arg is not true, we must evaluate the second arg.
                return when (yHolds) {
                    LTLResult.False -> {
                        // println("Failed evaluating $x within (... releases $y) at time $time")
                        LTLResult.False
                    }
                    // Could be true, keep evaluating
                    else -> evaluateAtTime(world, time + 1, maxTraceLength)
                }
            }
        }

        override fun toString() = "($x releases $y)"
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
    False, PresumablyFalse, PresumablyTrue, True;

    // Evaluate by truth table for ternary logic.
    infix fun and(other: LTLResult): LTLResult = when(this) {
        True -> other

        PresumablyTrue -> when(other) {
            True -> PresumablyTrue
            PresumablyTrue -> PresumablyTrue
            PresumablyFalse -> PresumablyFalse
            False -> False
        }
        PresumablyFalse -> when(other) {
            True -> PresumablyFalse
            PresumablyTrue -> PresumablyFalse
            PresumablyFalse -> PresumablyFalse
            False -> False
        }
        False -> False
    }

    // Evaluate by truth table for ternary logic
    infix fun or(other: LTLResult): LTLResult = when(this) {
        True -> True
        PresumablyTrue -> when(other) {
            True -> True
            PresumablyTrue -> PresumablyTrue
            PresumablyFalse -> PresumablyTrue
            False -> PresumablyTrue
        }
        PresumablyFalse -> when(other) {
            True -> True
            PresumablyTrue -> PresumablyTrue
            PresumablyFalse -> PresumablyFalse
            False -> PresumablyFalse
        }
        False -> other
    }

    // Evaluate by truth table for ternary logic
    operator fun not(): LTLResult = when(this) {
        True -> False
        PresumablyTrue -> PresumablyFalse
        PresumablyFalse -> PresumablyTrue
        False -> True
    }

    // Use material implication for now. Not sure if this is what we want.
    infix fun implies(other: LTLResult): LTLResult = not() or other
}