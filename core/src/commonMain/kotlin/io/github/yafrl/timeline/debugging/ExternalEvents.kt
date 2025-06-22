package io.github.yafrl.timeline.debugging

import io.github.yafrl.behaviors.Behavior
import io.github.yafrl.behaviors.Behavior.Companion
import io.github.yafrl.behaviors.Behavior.Companion.sampled
import io.github.yafrl.timeline.BehaviorID
import io.github.yafrl.timeline.Node
import io.github.yafrl.timeline.NodeID
import io.github.yafrl.timeline.Timeline
import kotlin.reflect.KType

/**
 * An external event is a snapshot of some external operation that has been made
 *  to update the state of an [ExternalNode].
 **/
data class ExternalEvent(
    val behaviorsSampled: Map<BehaviorID, Any?>,
    val id: NodeID,
    val value: Any?
)

/**
 * An external node is a node whose behavior is controlled externally to the
 *  timeline graph (e.x. input from a user interface, update from a databse, etc...)
 *
 * [ExternalNode] helps us keep track of all external nodes that have been created
 *  as inputs to a [Timeline] and their types so that inputs to external nodes
 *  can be randomly generated when running tests for a yafrl program.
 **/
data class ExternalNode(
    val type: KType,
    val node: Node<*>
)

/**
 * An external behavior is a non-deterministic [Behavior.sampled] behavior
 * that we need to track in the timeline for reproducibility.
 *
 * See also [ExternalNode].
 **/
data class ExternalBehavior(
    val type: KType,
    val behavior: Behavior.Sampled<*>
)

