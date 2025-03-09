package io.github.sintrastes.yafrl

/**
 * Strategy used to combine simultaneously occurring events within a frame
 *  when merging multiple [Event]s.
 *
 * Default behavior for this is [Leftmost].
 **/
fun interface MergeStrategy<A> {
    fun mergeEvents(events: List<A>): A?

    /**
     * Default [MergeStrategy] -- choosing the leftmost event that was passed to
     * [Event.mergedWith].
     **/
    class Leftmost<A> : MergeStrategy<A> {
        override fun mergeEvents(events: List<A>): A? {
            return events.firstOrNull()
        }
    }
}