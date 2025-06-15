package vectors

import io.github.sintrastes.yafrl.behaviors.Behavior.Companion.const
import io.github.sintrastes.yafrl.*
import io.github.sintrastes.yafrl.behaviors.integrate
import io.github.sintrastes.yafrl.behaviors.plus
import io.github.sintrastes.yafrl.internal.Timeline
import io.github.sintrastes.yafrl.vector.Double2
import io.github.sintrastes.yafrl.vector.Double3
import io.github.sintrastes.yafrl.vector.Float2
import io.github.sintrastes.yafrl.vector.Float3
import io.github.sintrastes.yafrl.vector.ScalarSpace
import io.github.sintrastes.yafrl.vector.VectorSpace
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.numericDouble
import io.kotest.property.arbitrary.numericFloat
import io.kotest.property.checkAll

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class VectorTests : FunSpec({
    test("Gravity simulation works") {
        val clock by lazy { broadcastEvent<Duration>() }

        Timeline.initializeTimeline(
            initClock = {
                clock
            }
        )

        val gravityAcceleration = Float2(0f, -9.81f)

        val initialPosition = Float2(20f, 100f)

        val gravity = const(gravityAcceleration).integrate()

        val position = const(initialPosition) + gravity.integrate()

        assertEquals(
            Float2(20f, 100f),
            position.value
        )

        val samples = 100

        repeat(samples) {
            clock.send((1.0 / samples).seconds)
            position.value
        }

        assertTrue(
            "Value was: ${position.value.y}"
        ) {
            95.095f == position.value.y
        }
    }

    test("Gravity simulation works 3D") {
        val clock by lazy { broadcastEvent<Duration>() }

        Timeline.initializeTimeline(
            initClock = {
                clock
            }
        )

        val gravityAcceleration = Float3(0f, 0f, -9.81f)

        val initialPosition = Float3(20f, 30f, 100f)

        val gravity = const(gravityAcceleration).integrate()

        val position = const(initialPosition) + gravity.integrate()

        assertEquals(
            Float3(20f, 30f, 100f),
            position.value
        )

        val samples = 100

        repeat(samples) {
            clock.send((1.0 / samples).seconds)
            position.value
        }

        assertTrue(
            95.095f == position.value.z
        )
    }

    test("Float vector arithmetic works") {
        testVectorSpace(VectorSpace.float2(), Arb.float2())
    }

    test("3D float vector arithmetic works") {
        testVectorSpace(VectorSpace.float3(), Arb.float3())
    }

    test("Double vector arithmetic works") {
        testVectorSpace(VectorSpace.double2(), Arb.double2())
    }

    test("3D Double vector arithmetic works") {
        testVectorSpace(VectorSpace.double3(), Arb.double3())
    }

    test("Float scalar arithmetic works") {
        testVectorSpace(
            ScalarSpace.float(),
            Arb.numericFloat().filterNot { it == -0f }
        )
    }

    test("Double scalar arithmetic works") {
        testVectorSpace(
            ScalarSpace.double(),
            Arb.numericDouble().filterNot { it == -0.0 }
        )
    }

    test("Instances resolve properly") {
        shouldNotThrow<IllegalArgumentException> {
            VectorSpace.instance<Double>()
            VectorSpace.instance<Float>()
            VectorSpace.instance<Int>()
            VectorSpace.instance<Float2>()
            VectorSpace.instance<Float3>()
            VectorSpace.instance<Double2>()
            VectorSpace.instance<Double3>()
        }
    }

    test("Non-vector spaces do not resolve") {
        shouldThrow<IllegalArgumentException> {
            VectorSpace.instance<Unit>()
        }
    }
})

fun Arb.Companion.double3() = arbitrary {
    Double3(
        Arb.numericDouble().filterNot { it == -0.0 }.bind(),
        Arb.numericDouble().filterNot { it == -0.0 }.bind(),
        Arb.numericDouble().filterNot { it == -0.0 }.bind()
    )
}

fun Arb.Companion.double2() = arbitrary {
    Double2(
        Arb.numericDouble().filterNot { it == -0.0 }.bind(),
        Arb.numericDouble().filterNot { it == -0.0 }.bind()
    )
}

fun Arb.Companion.float3() = arbitrary {
    Float3(
        Arb.numericFloat().filterNot { it == -0f }.bind(),
        Arb.numericFloat().filterNot { it == -0f }.bind(),
        Arb.numericFloat().filterNot { it == -0f }.bind()
    )
}

fun Arb.Companion.float2() = arbitrary {
    Float2(
        Arb.numericFloat().filterNot { it == -0f }.bind(),
        Arb.numericFloat().filterNot { it == -0f }.bind()
    )
}

suspend fun <A> testVectorSpace(space: VectorSpace<A>, arb: Arb<A>) = with(space) {
    checkAll(arb, arb) { x, y ->
        x + y shouldBe y + x
    }

    checkAll(arb) { x ->
        x + zero shouldBe x

        zero + x shouldBe x

        x - x shouldBe zero

        x / 1 shouldBe x

        1 * x shouldBe x

        x * 1 shouldBe x
    }
}