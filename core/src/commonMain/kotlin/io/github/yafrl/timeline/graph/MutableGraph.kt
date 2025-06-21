package io.github.yafrl.timeline.graph

import io.github.yafrl.annotations.FragileYafrlAPI
import io.github.yafrl.timeline.Node
import io.github.yafrl.timeline.NodeID

/**
 * An efficient graph implementation using mutable adjacency lists.
 */
class MutableGraph: Graph {
    val nodes: MutableMap<NodeID, Node<*>> = mutableMapOf()

    val children: MutableMap<NodeID, MutableList<NodeID>> = mutableMapOf()

    override fun getCurrentNodes(): List<Node<*>> {
        // `toList` to get a persistent copy of the nodes.
        return nodes.values.toList()
    }

    override fun getCurrentNodeMap(): Map<NodeID, Node<*>> {
        // `toMap` to get a persistent copy.
        return nodes.toMap()
    }

    override fun getCurrentChildMap(): Map<NodeID, Collection<NodeID>> {
        // Need to get a nested persistent copy.
        return children.mapValues {
            it.value.toList()
        }
    }

    override fun resetChildren(map: Map<NodeID, Collection<NodeID>>) {
        children.clear()

        val newMap = map.mapValues { it.value.toMutableList() }

        children.putAll(newMap)
    }

    override fun getNode(id: NodeID): Node<*>? {
        return nodes[id]
    }

    @OptIn(FragileYafrlAPI::class)
    override fun addNode(node: Node<*>) {
        nodes[node.id] = node
    }

    override fun addChild(
        parent: NodeID,
        child: NodeID
    ) {
        var childNodes = children[parent]

        if (childNodes == null) {
            children[parent] = mutableListOf<NodeID>()
            childNodes = children[parent]
        }

        childNodes!!.add(child)
    }

    override fun getChildrenOf(id: NodeID): List<NodeID> {
        // `toList` to get a persistent copy.
        return children[id]?.toList() ?: listOf()
    }
}