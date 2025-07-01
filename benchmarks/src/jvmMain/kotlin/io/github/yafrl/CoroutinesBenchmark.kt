package io.github.yafrl

import kotlinx.benchmark.Scope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.State

@State(Scope.Benchmark)
class CoroutinesBenchmark {
    @Benchmark
    fun benchmark_mapping_shared_flow(): Int {
        val scope = CoroutineScope(Dispatchers.Default)

        val input = MutableSharedFlow<Int>()

        val mapped = input
            .map { it * 2 }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = 0
            )

        scope.launch {
            input.emit(15)
        }

        var result = mapped.value
        var iterations = 0
        // After 5 milliseconds just give up because sometimes MutableSharedFlow
        //  likes to deadlock...
        while (result == 0 && iterations < 5) {
            result = mapped.value
            iterations++
            runBlocking { delay(1L) }
        }

        scope.cancel()
        return result
    }

    @Benchmark
    fun benchmark_combining_state_flows_initial_value() {
        val scope = CoroutineScope(Dispatchers.Default)
        val input = MutableStateFlow<Int>(0)

        val inner = (0 .. 100).map { i ->
            input
                .map { it + i }
                .stateIn(scope, SharingStarted.Eagerly, input.value + i)
        }

        val result = combine(*inner.toTypedArray()) { it.sum() }
            .stateIn(scope, SharingStarted.Eagerly, inner.sumOf { it.value })

        result.value
            .also { scope.cancel() }
    }
}