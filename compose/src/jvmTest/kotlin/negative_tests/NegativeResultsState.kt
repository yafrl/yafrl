package negative_tests

import androidx.compose.runtime.*
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

/**
 * More negative test results showing that even [molecule](https://github.com/cashapp/molecule/)
 *  and [androidx.compose.runtime.State] does not solve the issues with [StateFlow] that we are
 *  trying to solve in this library.
 **/
class NegativeResultsState {
    @Test
    fun `Molecule has the same behavior`() {
        val scope = CoroutineScope(Dispatchers.Default)

        val flow = MutableStateFlow(0)

        val mapped = scope.launchMolecule(RecompositionMode.Immediate) {
            val state by flow.collectAsState(flow.value)

            state + 2
        }

        flow.value = 3

        assertNotSame(5, mapped.value)
    }

    @Test
    fun `Molecule needs delay to pass`() {
        val scope = CoroutineScope(Dispatchers.Default)

        val flow = MutableStateFlow(0)

        val mapped = scope.launchMolecule(RecompositionMode.Immediate) {
            val state by flow.collectAsState(flow.value)

            state + 2
        }

        runBlocking {
            flow.value = 3

            delay(35)

            assertEquals(5, mapped.value)
        }
    }
}