package io.github.yafrl.testing

import io.github.yafrl.Signal
import kotlinx.serialization.StringFormat

/**
 * Given a state space built via [setupState], and a LTL [proposition] to validate,
 *  attempts to produce a minimal set of actions causing [proposition] to fail from
 *  the [serializedTrace] (for example, extracted from a bug report).
 *
 * Similar to [testPropositionHoldsFor] except uses a pre-existing [serializedTrace]
 *  of actions which have been reported to cause the issue.
 *
 * Useful for deriving hard-coded minimally reproducible examples that [testPropositionHoldsFor]
 *  would be unlikely to manually generate.
 **/
fun <W> findMinimalReproducingTrace(
    setupState: () -> Signal<W>,
    proposition: LTLSyntax<W>.() -> LTL<W>,
    serializedTrace: String,
    format: StringFormat
): Result<List<StateSpaceAction>> {
    TODO()
}