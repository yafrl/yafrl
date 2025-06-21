package io.github.yafrl.timeline.graph

import io.github.yafrl.timeline.Node
import io.github.yafrl.timeline.NodeID

/**
 * Simple graph interface used to represent the graph of nodes in a
 *  [io.github.yafrl.timeline.Timeline]
 **/
interface Graph {
    /** Get the list of current nodes in the graph. */
    fun getCurrentNodes(): Collection<Node<*>>

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

    /** Adds a parent-child edge to the graph. */
    fun addChild(parent: NodeID, child: NodeID)

    /** Get the list of all child nodes of a node in the graph. */
    fun getChildrenOf(id: NodeID): List<NodeID>
}