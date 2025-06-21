package io.github.yafrl.timeline.debugging

/**
 * Interface for a time-travel debugger -- that is, a capability allowing the state of a
 * [Timeline] to be reset to an arbitrary state in the past, from which execution can be resumed.
 **/
interface TimeTravelDebugger {
    /** Persist the state of the current frame. */
    fun persistState()

    /** Reset the state to the given frame. */
    fun resetState(frame: Long)

    /** Rollback to the previous frame.*/
    fun rollbackState()

    /** Reset to the next frame. */
    fun nextState()

    /** Trivial no-op implementation of [TimeTravelDebugger]. */
    object Disabled : TimeTravelDebugger {
        override fun persistState() { }
        override fun resetState(frame: Long) { }
        override fun rollbackState() { }
        override fun nextState() { }
    }
}