package io.github.sintrastes.yafrl.behaviors

import io.github.sintrastes.yafrl.annotations.ExperimentalYafrlAPI
import io.github.sintrastes.yafrl.vector.Float2
import io.github.sintrastes.yafrl.vector.Float3
import io.github.sintrastes.yafrl.vector.VectorSpace
import kotlin.jvm.JvmName

///
/// This file contains convenient extension methods on behaviors that
///  offer piecewise operations for common numeric and boolean functions.
///

@JvmName("plusDouble")
operator fun Behavior<Double>.plus(other: Behavior<Double>): Behavior<Double> = addBehavior(other)

@JvmName("plusFloat")
operator fun Behavior<Float>.plus(other: Behavior<Float>): Behavior<Float> = addBehavior(other)

@JvmName("plusInt")
operator fun Behavior<Int>.plus(other: Behavior<Int>): Behavior<Int> = addBehavior(other)

@JvmName("plusFloat2")
operator fun Behavior<Float2>.plus(other: Behavior<Float2>): Behavior<Float2> = addBehavior(other)

@JvmName("plusFloat3")
operator fun Behavior<Float3>.plus(other: Behavior<Float3>): Behavior<Float3> = addBehavior(other)

@OptIn(ExperimentalYafrlAPI::class)
internal inline fun <reified A> Behavior<A>.addBehavior(other: Behavior<A>): Behavior<A> =
    with(VectorSpace.instance<A>()) {
        when (this@addBehavior) {
            is Behavior.Polynomial<A> -> {
                when (other) {
                    is Behavior.Polynomial<A> -> {
                        var coefficients = coefficients
                        var otherCoefficients = other.coefficients

                        if (other.coefficients.size > coefficients.size) {
                            val difference = other.coefficients.size - coefficients.size

                            coefficients += (0 until difference).map { zero }
                        }

                        if (coefficients.size > other.coefficients.size) {
                            val difference = coefficients.size - other.coefficients.size

                            otherCoefficients += (0 until difference).map { zero }
                        }

                        Behavior.Polynomial(
                            vectorSpace,
                            coefficients.zip(otherCoefficients) { x, y -> x + y }
                        )
                    }

                    else -> Behavior.Sum(this@with, this@addBehavior, other)
                }
            }

            is Behavior.Impulse<*, *> -> {
                Behavior.Sum(this@with, this@addBehavior, other)
            }

            else -> when (other) {
                is Behavior.Impulse<*, *> -> {
                    Behavior.Sum(this@with, this@addBehavior, other)
                }

                else -> Behavior.Continuous(
                    lazy { this@with },
                    { time -> this@addBehavior.sampleValue(time) + other.sampleValue(time) }
                )
            }
        }
    }