package io.github.yafrl

import io.github.yafrl.timeline.Timeline
import kotlinx.benchmark.*

@State(Scope.Benchmark)
class YafrlBenchmark {
    @Benchmark
    fun benchmark_mapping_node(): Int {
        Timeline.initializeTimeline()

        val input = externalEvent<Int>()

        val mapped = Signal.hold(0, input.map { it * 2 })

        input.send(15)

        return sample { mapped.currentValue() }
    }

    @Benchmark
    fun benchmark_combining_node_initial_value() {
        Timeline.initializeTimeline()

        val input = externalSignal<Int>(0)

        val inner = (0 .. 100).map { i ->
            input.map { it + i }
        }

        val result = Signal
            .combineAll(*inner.toTypedArray())
            .map { it.sum() }

        sample { result.currentValue() }
    }
}