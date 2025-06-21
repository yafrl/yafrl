package io.github.yafrl.vector

/** A two-dimensional vector in Cartesian coordinates using [Float] values. */
data class Float2(val x: Float, val y: Float) {
    companion object {
        val zero = Float2(0f, 0f)

        val x = Float2(1f, 0f)

        val y = Float2(0f, 1f)
    }
}

/** A three-dimensional vector in Cartesian coordinates using [Float] values. */
data class Float3(val x: Float, val y: Float, val z: Float) {
    companion object {
        val zero = Float3(0f, 0f, 0f)

        val x = Float3(1f, 0f, 0f)

        val y = Float3(0f, 1f, 0f)

        val z = Float3(0f, 0f, 1f)
    }
}

/** A two-dimensional vector in Cartesian coordinates using [Double] values. */
data class Double2(val x: Double, val y: Double)

/** A three-dimensional vector in Cartesian coordinates using [Double] values. */
data class Double3(val x: Double, val y: Double, val z: Double)