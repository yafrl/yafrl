package io.github.yafrl.testing

/** Syntax for building up LTL propositions. */
interface LTLAlgebra<T> {
    // Basic boolean operations
    infix fun T.and(other: T): T
    infix fun T.or(other: T): T
    infix fun T.implies(other: T): T
    operator fun T.not(): T

    // Temporal constructs
    fun always(cond: T): T
    fun eventually(cond: T): T
}

/**
 * Results to use for an LTL proposition that has been
 *  evaluated for a finite segment of observations
 **/
enum class LTLResult {
    True, False, Indeterminate;
}