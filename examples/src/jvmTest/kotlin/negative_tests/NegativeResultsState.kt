package negative_tests

import androidx.compose.runtime.*
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

/**
 * More negative test results showing that even [molecule](https://github.com/cashapp/molecule/)
 *  and [androidx.compose.runtime.State] does not solve the issues with [kotlinx.coroutines.flow.StateFlow] that we are
 *  trying to solve in this library.
 **/
class NegativeResultsState : FunSpec({
    xtest("Molecule has the same behavior") {
        val scope = CoroutineScope(Dispatchers.Default)

        val flow = MutableStateFlow(0)

        val mapped = scope.launchMolecule(RecompositionMode.Immediate) {
            val state by flow.collectAsState(flow.value)

            state + 2
        }

        flow.value = 3

        assertNotSame(5, mapped.value)
    }

    xtest("Molecule needs delay to pass") {
        val scope = CoroutineScope(Dispatchers.Default)

        val flow = MutableStateFlow(0)

        val mapped = scope.launchMolecule(RecompositionMode.Immediate) {
            val state by flow.collectAsState(flow.value)

            state + 2
        }

        runTest {
            flow.value = 3

            with(Dispatchers.Default) { delay(35) }

            assertEquals(5, mapped.value)
        }
    }
})