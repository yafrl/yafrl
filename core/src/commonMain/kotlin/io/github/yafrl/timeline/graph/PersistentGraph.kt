package io.github.yafrl.timeline.graph

import io.github.yafrl.annotations.FragileYafrlAPI
import io.github.yafrl.timeline.BehaviorID
import io.github.yafrl.timeline.Node
import io.github.yafrl.timeline.NodeID
import io.github.yafrl.timeline.debugging.ExternalBehavior
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/**
 * Graph implemented using persistent collections.
 *
 * Should be somewhat efficient if making extensive use of time-travel debugging,
 *  otherwise [MutableGraph] should be a better option.
 **/
class PersistentGraph : Graph {
    private var nodes: PersistentMap<NodeID, Node<Any?>> = persistentMapOf()

    // Map from a node ID to it's set of child nodes
    private var children: PersistentMap<NodeID, PersistentList<NodeID>> = persistentMapOf()

    private var _behaviorParents = persistentMapOf<NodeID, PersistentList<BehaviorID>>()

    private var _externalBehaviors = persistentMapOf<BehaviorID, ExternalBehavior>()

    override fun getCurrentNodes(): Collection<Node<*>> {
        // No conversion needed because underling data structure is persistent.
        return nodes.values
    }

    override fun getExternalBehaviors(): Map<BehaviorID, ExternalBehavior> {
        return _externalBehaviors
    }

    override fun addChild(behavior: BehaviorID, child: NodeID) {
        var parentList = _behaviorParents.get(child)

        if (parentList == null) {
            parentList = persistentListOf(behavior)
        } else {
            parentList = parentList.add(behavior)
        }

        _behaviorParents = _behaviorParents.put(child, parentList)
    }

    override fun removeChild(behavior: BehaviorID, child: NodeID) {
        val parentList = _behaviorParents.get(child)

        if (parentList != null) {
            _behaviorParents = _behaviorParents.put(child, parentList.remove(behavior))
        }
    }

    override fun getCurrentNodeMap(): Map<NodeID, Node<*>> {
        // No conversion needed because underlying data structure is persistent.
        return nodes
    }

    override fun getCurrentChildMap(): Map<NodeID, Collection<NodeID>> {
        // No conversion needed because the underlying data structure is persistent.
        return children
    }

    override fun resetChildren(map: Map<NodeID, Collection<NodeID>>) {
        // Bit of a hack -- don't have to copy if we know it is already a persistent map,
        // which it will be based on how the time-travel debugger works (saving previous
        // states from this same interface).
        children = map as PersistentMap<NodeID, PersistentList<NodeID>>
    }

    override fun getNode(id: NodeID): Node<*>? {
        return nodes[id]
    }

    @OptIn(FragileYafrlAPI::class)
    override fun addNode(node: Node<*>) {
        nodes = nodes.put(node.id, node)
    }

    override fun addBehavior(behavior: ExternalBehavior) {
        _externalBehaviors = _externalBehaviors.put(behavior.behavior.id, behavior)
    }

    @OptIn(FragileYafrlAPI::class)
    override fun addChild(
        parent: NodeID,
        child: NodeID
    ) {
        val newChildren = children[parent]

        children = if (newChildren != null) {
            children.put(parent, newChildren.add(child))
        } else {
            children.put(parent, persistentListOf(child))
        }
    }

    override fun removeChild(parent: NodeID, child: NodeID) {
        val newChildren = children[parent]

        if (newChildren != null) {
            children = children.put(parent, newChildren.remove(child))
        }
    }

    override fun getBehaviorParentsOf(id: NodeID): List<BehaviorID> {
        return _behaviorParents[id] ?: listOf()
    }

    override fun getChildrenOf(id: NodeID): List<NodeID> {
        return children[id] ?: listOf()
    }
}