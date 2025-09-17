package io.github.yafrl.timeline.graph

import io.github.yafrl.behaviors.Behavior
import io.github.yafrl.timeline.BehaviorID
import io.github.yafrl.timeline.Node
import io.github.yafrl.timeline.NodeID
import io.github.yafrl.timeline.debugging.ExternalBehavior

/**
 * Simple graph interface used to represent the graph of nodes in a
 *  [io.github.yafrl.timeline.Timeline]
 **/
interface Graph {
    /** Get the list of current nodes in the graph. */
    fun getCurrentNodes(): Collection<Node<*>>

    fun getExternalBehaviors(): Map<BehaviorID, ExternalBehavior>

    /** Get the current map of nodes in the graph. */
    fun getCurrentNodeMap(): Map<NodeID, Node<*>>

    /** Get the current list of the child map in the graph. */
    fun getCurrentChildMap(): Map<NodeID, Collection<NodeID>>

    /** Reset the state of the child map to match that of the passed map. */
    fun resetChildren(map: Map<NodeID, Collection<NodeID>>)

    /** Get the node (if any) corresponding to the given id. */
    fun getNode(id: NodeID): Node<*>?

    /** Adds a node to the graph. */
    fun addNode(node: Node<*>)

    fun addBehavior(behavior: ExternalBehavior)

    /** Adds a parent-child edge to the graph. */
    fun addChild(parent: NodeID, child: NodeID)

    fun removeChild(parent: NodeID, child: NodeID)

    fun addChild(behavior: BehaviorID, child: NodeID)

    fun removeChild(behavior: BehaviorID, child: NodeID)

    fun getBehaviorParentsOf(id: NodeID): List<BehaviorID>

    /** Get the list of all child nodes of a node in the graph. */
    fun getChildrenOf(id: NodeID): List<NodeID>
}