package io.github.yafrl.testing

import io.github.yafrl.Signal
import io.github.yafrl.timeline.TimelineScope
import io.github.yafrl.timeline.debugging.ExternalActionSerializer
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
    setupState: TimelineScope.() -> Signal<W>,
    proposition: LTLSyntax<W>.() -> LTL<W>,
    serializedTrace: String,
    format: StringFormat
): Result<List<StateSpaceAction>> {
    val actions = serializedTrace
        .split("\n")
        .map { format.decodeFromString(ExternalActionSerializer, it) }
        .map { StateSpaceAction.fromExternalAction(it) }

    val shrunkActions = shrinkActions(
        setupState = setupState,
        actions = actions,
        test = { actions ->
            val result = LTL.evaluate(
                proposition,
                actions.asIterable().iterator(),
                actions.size
            )

            result >= LTLResult.PresumablyTrue
        }
    )
        ?: return Result.failure(Exception("Could not shrink actions"))

    return Result.success(shrunkActions)
}