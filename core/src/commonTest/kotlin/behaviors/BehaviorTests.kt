package behaviors

import io.github.sintrastes.yafrl.Behavior
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

class BehaviorTests {
    @Test
    fun `Mapped behavior works as intended`() {
        val behavior = Behavior.continuous { time ->
            val seconds = time.inWholeMilliseconds / 1000f

            sin(seconds)
        }

        val mapped = behavior.map { it * 2 }

        val value = behavior.value
        val mappedValue = mapped.value

        val difference = abs(value * 2 - mappedValue)

        assertTrue("Difference between mapped and unmapped value was $difference") {
            difference < 0.1f
        }
    }
}