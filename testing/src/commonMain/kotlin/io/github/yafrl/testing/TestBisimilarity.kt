package io.github.yafrl.testing

import io.github.yafrl.Signal

/**
 * Given two signals constructed with [setupState1] and [setupState2],
 *  test whether or not they represent behaviorally equivalent signals --
 *  that is, bisimilar when viewed as state machines.
 **/
fun <W> testBisimilarSignals(
    setupState1: () -> Signal<W>,
    setupState2: () -> Signal<W>
) {
    val state1 = setupState1()

    val state2 = setupState2()

    // TODO: For this to work well, we probably have to have
    //  the notion of separate timelines.
}