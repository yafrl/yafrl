package io.github.sintrastes.yafrl.timeline.debugging

import io.github.sintrastes.yafrl.annotations.FragileYafrlAPI
import io.github.sintrastes.yafrl.timeline.NodeID
import io.github.sintrastes.yafrl.timeline.Timeline
import kotlinx.atomicfu.locks.synchronized
import kotlin.time.measureTime

/**
 * Simple implementation of a time-travel debugger that takes snapshots of the
 * states at each frame in order to be able to restore the state of the timeline.
 **/
class SnapshotDebugger(
    private val timeline: Timeline
) : TimeTravelDebugger {
    /**
     * Data structure to hold on to a "snapshot" of the current state of the
     *  timeline so that previous states can be restored.
     **/
    data class GraphState(
        val nodeValues: Map<NodeID, Any?>,
        val children: Map<NodeID, Collection<NodeID>>
    )

    // Indexed by frame number.
    val previousStates: MutableMap<Long, GraphState> = mutableMapOf()

    @OptIn(FragileYafrlAPI::class)
    override fun persistState() {
        val nodes = timeline.graph.getCurrentNodeMap()
        val children = timeline.graph.getCurrentChildMap()

        if (timeline.debugLogging) {
            println("Persisting state in frame ${timeline.latestFrame}, ${nodes.size} nodes")
        }
        previousStates[timeline.latestFrame] = GraphState(
            nodes
                .mapValues { it.value.rawValue },
            children
        )
    }

    @OptIn(FragileYafrlAPI::class)
    override fun resetState(frame: Long) = synchronized(timeline) {
        val time = measureTime {
            if (timeline.debugLogging) {
                println("Resetting to frame ${frame}, event was: " +
                        "${timeline.eventLogger.reportEvents().getOrNull(frame.toInt())}")
            }

            val nodeValues = previousStates[frame]
                ?.nodeValues ?: run {
                if (timeline.debugLogging) {
                    println("No previous state found for frame ${frame}")
                }
                return@synchronized
            }

            val nodes = timeline.graph.getCurrentNodes()

            nodes.forEach { node ->
                if (node.label == "__internal_paused") return@forEach

                val resetValue = nodeValues[node.id]

                if (resetValue != null) {
                    timeline.updateNodeValue(node, resetValue)
                    node.onRollback?.invoke(node, frame)
                }
            }

            timeline.graph.resetChildren(previousStates[frame]!!.children)
            timeline.latestFrame = frame
        }

        if (timeline.debugLogging) println("Reset state in ${time}")
    }

    override fun rollbackState() { resetState(timeline.latestFrame - 1) }

    override fun nextState() { resetState(timeline.latestFrame + 1) }
}