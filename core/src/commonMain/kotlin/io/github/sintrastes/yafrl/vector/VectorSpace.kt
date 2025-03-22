package io.github.sintrastes.yafrl.vector

/**
 * Abstraction for a vector space over the real numbers.
 **/
interface VectorSpace<V> {
    val zero: V

    operator fun V.plus(other: V): V

    operator fun V.minus(other: V): V

    operator fun Number.times(vect: V): V

    operator fun V.times(value: Number): V

    operator fun V.div(value: Number): V

    companion object {
        inline fun <reified T> instance(): VectorSpace<T> {
            return when (T::class) {
                Double::class -> ScalarSpace.double() as VectorSpace<T>
                Float::class -> ScalarSpace.float() as VectorSpace<T>
                Float2::class -> float2() as VectorSpace<T>
                Float3::class -> float3() as VectorSpace<T>
                Double2::class -> double2() as VectorSpace<T>
                Double3::class -> double3() as VectorSpace<T>
                else -> throw IllegalArgumentException("")
            }
        }

        fun float2() = object: VectorSpace<Float2> {
            override val zero: Float2 = Float2(0f, 0f)

            override fun Float2.plus(other: Float2): Float2 {
                return Float2(x + other.x, y + other.y)
            }

            override fun Float2.minus(other: Float2): Float2 {
                return Float2(x - other.x, y - other.y)
            }

            override fun Number.times(vect: Float2): Float2 {
                return Float2(this.toFloat() * vect.x, this.toFloat() * vect.y)
            }

            override fun Float2.times(value: Number): Float2 {
                return Float2(x * value.toFloat(), y * value.toFloat())
            }

            override fun Float2.div(value: Number): Float2 {
                return Float2(x / value.toFloat(), y / value.toFloat())
            }
        }

        fun float3() = object: VectorSpace<Float3> {
            override val zero: Float3 = Float3(0f, 0f, 0f)

            override fun Float3.plus(other: Float3): Float3 {
                return Float3(x + other.x, y + other.y, z + other.z)
            }

            override fun Float3.minus(other: Float3): Float3 {
                return Float3(x - other.x, y - other.y, z - other.z)
            }

            override fun Number.times(vect: Float3): Float3 {
                return Float3(this.toFloat() * vect.x, this.toFloat() * vect.y, this.toFloat() * vect.z)
            }

            override fun Float3.times(value: Number): Float3 {
                return Float3(x * value.toFloat(), y * value.toFloat(), z * value.toFloat())
            }

            override fun Float3.div(value: Number): Float3 {
                return Float3(x / value.toFloat(), y / value.toFloat(), z / value.toFloat())
            }
        }

        fun double2() = object: VectorSpace<Double2> {
            override val zero: Double2 = Double2(0.0, 0.0)

            override fun Double2.plus(other: Double2): Double2 {
                return Double2(x + other.x, y + other.y)
            }

            override fun Double2.minus(other: Double2): Double2 {
                return Double2(x - other.x, y - other.y)
            }

            override fun Number.times(vect: Double2): Double2 {
                return Double2(this.toFloat() * vect.x, this.toFloat() * vect.y)
            }

            override fun Double2.times(value: Number): Double2 {
                return Double2(x * value.toFloat(), y * value.toFloat())
            }

            override fun Double2.div(value: Number): Double2 {
                return Double2(x / value.toFloat(), y / value.toFloat())
            }
        }

        fun double3() = object: VectorSpace<Double3> {
            override val zero: Double3 = Double3(0.0, 0.0, 0.0)

            override fun Double3.plus(other: Double3): Double3 {
                return Double3(x + other.x, y + other.y, z + other.z)
            }

            override fun Double3.minus(other: Double3): Double3 {
                return Double3(x - other.x, y - other.y, z - other.z)
            }

            override fun Number.times(vect: Double3): Double3 {
                return Double3(this.toFloat() * vect.x, this.toFloat() * vect.y, this.toFloat() * vect.z)
            }

            override fun Double3.times(value: Number): Double3 {
                return Double3(x * value.toFloat(), y * value.toFloat(), z * value.toFloat())
            }

            override fun Double3.div(value: Number): Double3 {
                return Double3(x / value.toFloat(), y / value.toFloat(), z / value.toFloat())
            }
        }
    }
}

object ScalarSpace {
    fun float() = object: VectorSpace<Float> {
        override val zero: Float = 0f

        override fun Float.plus(other: Float): Float {
            return this + other
        }

        override fun Float.minus(other: Float): Float {
            return this - other
        }

        override fun Number.times(vect: Float): Float {
            return this.toFloat() * vect
        }

        override fun Float.times(value: Number): Float {
            return value.toFloat() * this
        }

        override fun Float.div(value: Number): Float {
            return this / value.toFloat()
        }
    }

    fun double() = object: VectorSpace<Double> {
        override val zero: Double = 0.0

        override fun Double.plus(other: Double): Double {
            return this + other
        }

        override fun Double.minus(other: Double): Double {
            return this - other
        }

        override fun Number.times(vect: Double): Double {
            return this.toDouble() * vect
        }

        override fun Double.times(value: Number): Double {
            return value.toDouble() * this
        }

        override fun Double.div(value: Number): Double {
            return this / value.toDouble()
        }
    }
}