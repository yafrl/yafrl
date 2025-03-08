package io.github.sintrastes.yafrl.internal

import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Simple implementation of a push-pull FRP system using a graph
 *  of nodes.
 *
 * Changes are propagated by marking child nodes as dirty when a parent value
 *  changes.
 *
 * Dirty values are recomputed lazily upon request.
 **/
class Timeline {
    var latestID = -1

    private fun newID(): NodeID {
        latestID++

        return NodeID(latestID)
    }

    internal fun <A> createNode(value: Lazy<A>): Node<A> {
        val id = newID()

        val newNode = Node(
            value,
            id,
            { value.value }
        )

        nodes[id] = newNode

        return newNode
    }

    internal fun <A, B> createMappedNode(parent: Node<A>, f: (A) -> B): Node<B> {
        val mapNodeID = newID()

        val initialValue = lazy { f(fetchNodeValue(parent) as A) }

        val mappedNode = Node(
            initialValue,
            mapNodeID,
            {
                f(fetchNodeValue(parent) as A)
            }
        )

        nodes[mapNodeID] = mappedNode

        children[parent.id]
            ?.add(mapNodeID)
            ?: run { children[parent.id] = mutableListOf(mapNodeID) }

        return mappedNode
    }

    private val nodes: MutableMap<NodeID, Node<Any?>> = mutableMapOf()

    // Basically we are building up a doubly-lined DAG here:

    // Map from a node ID to it's set of child nodes
    private val children: MutableMap<NodeID, MutableList<NodeID>> = mutableMapOf()

    internal suspend fun updateNodeValue(
        node: Node<Any?>,
        newValue: Any?
    ) {
        node.rawValue = newValue

        for (listener in node.onValueChangedListeners) {
            listener.emit(newValue)
        }

        updateChildNodes(node)
    }

    private fun updateChildNodes(
        node: Node<Any?>
    ) {
        println("Marking children of ${node.id} dirty")
        val childNodes = children[node.id] ?: listOf()

        for (childID in childNodes) {
            val child = nodes[childID]!!

            if (child.onValueChangedListeners.size == 0) {
                // If not listeneing, we can mark the node dirty
                child.dirty = true
            } else {
                // Otherwise, we are forced to calculate the node's value
                child.rawValue = child.recompute()
            }

            updateChildNodes(child)
        }
    }

    internal fun fetchNodeValue(
        node: Node<Any?>
    ): Any? {
        if (!node.dirty) {
            return node.rawValue
        } else {
            node.rawValue = node.recompute()
        }

        return node.rawValue
    }
}

private data class NodeGraphID(val graph: Timeline) : AbstractCoroutineContextElement(NodeGraphID) {
    companion object Key : CoroutineContext.Key<NodeGraphID>
}

internal val CoroutineContext.nodeGraph: Timeline?
    get() = this[NodeGraphID]?.graph

/**
 * Constructs a [CoroutineContext] which is associated with a single
 *  [Timeline].
 **/
fun newTimeline(): CoroutineContext {
    val nodeGraph = Timeline()

    return NodeGraphID(nodeGraph)
}

suspend fun <A> Node<A>.current(): A {
    val graph = currentCoroutineContext().nodeGraph!!

    return graph.fetchNodeValue(this) as A
}