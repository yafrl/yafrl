package io.github.yafrl.timeline.graph

/** Simple graph interface used to represent the graph of nodes in a [io.github.yafrl.timeline.Timeline] */
interface Graph<ID, N> {
    /** Get the list of current nodes in the graph. */
    fun getCurrentNodes(): Collection<N>

    /** Get the current map of nodes in the graph. */
    fun getCurrentNodeMap(): Map<ID, N>

    /** Get the current list of the child map in the graph. */
    fun getCurrentChildMap(): Map<ID, Collection<ID>>

    /** Reset the state of the child map to match that of the passed map. */
    fun resetChildren(map: Map<ID, Collection<ID>>)

    /** Get the node (if any) corresponding to the given id. */
    fun getNode(id: ID): N?

    /** Adds a node to the graph. */
    fun addNode(node: N)

    /** Adds a parent-child edge to the graph. */
    fun addChild(parent: ID, child: ID)

    /** Get the list of all child nodes of a node in the graph. */
    fun getChildrenOf(id: ID): List<ID>
}