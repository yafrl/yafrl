package io.github.sintrastes.yafrl.vector

interface Algebra<A>: VectorSpace<A> {
    operator fun A.times(other: A): A

    companion object {
        inline fun <reified T> instance(): Algebra<T> {
            return when (T::class) {
                Double::class -> ScalarSpace.double() as Algebra<T>
                Float::class -> ScalarSpace.float() as Algebra<T>
                else -> throw IllegalArgumentException()
            }
        }
    }
}