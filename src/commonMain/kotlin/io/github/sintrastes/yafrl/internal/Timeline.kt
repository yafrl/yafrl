package io.github.sintrastes.yafrl.internal

import io.github.sintrastes.yafrl.EventState
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Simple implementation of a push-pull FRP system using a graph
 *  of nodes.
 *
 * Changes are propagated by marking child nodes as dirty when a parent value
 *  changes.
 *
 * Dirty values are recomputed lazily upon request.
 **/
class Timeline(
    internal val scope: CoroutineScope
) : SynchronizedObject() {
    private var latestID = -1

    val onNextFrameListeners = mutableListOf<() -> Unit>()

    private fun newID(): NodeID {
        latestID++

        return NodeID(latestID)
    }

    internal fun <A> createNode(
        value: Lazy<A>,
        onUpdate: (Node<A>) -> A = { it -> it.rawValue },
        onNextFrame: ((Node<A>) -> Unit)? = null
    ): Node<A> {
        val id = newID()

        var newNode: Node<A>? = null
        newNode = Node(
            value,
            id,
            { onUpdate(newNode!!) },
            onNextFrame
        )

        nodes[id] = newNode

        return newNode
    }

    internal fun <A, B> createMappedNode(
        parent: Node<A>,
        f: (A) -> B,
        initialValue: Lazy<B> = lazy { f(fetchNodeValue(parent) as A) },
        onNextFrame: ((Node<B>) -> Unit)? = null
    ): Node<B> {
        val mapNodeID = newID()

        var mappedNode: Node<B>? = null
        mappedNode = Node(
            initialValue = initialValue,
            id = mapNodeID,
            recompute = {
                val parentValue = fetchNodeValue(parent) as A

                val result = f(parentValue)

                result
            },
            onNextFrame = onNextFrame
        )

        nodes[mapNodeID] = mappedNode

        children[parent.id]
            ?.add(mapNodeID)
            ?: run { children[parent.id] = mutableListOf(mapNodeID) }

        return mappedNode
    }

    internal fun <A, B> createFoldNode(
        initialValue: A,
        eventNode: Node<EventState<B>>,
        reducer: (A, B) -> A
    ): Node<A> {
        val foldNodeID = newID()

        var currentValue = initialValue

        val foldNode = Node(
            lazy { initialValue },
            foldNodeID,
            {
                val event = fetchNodeValue(eventNode) as EventState<B>
                if (event is EventState.Fired) {
                    currentValue = reducer(currentValue, event.event)
                }

                currentValue
            }
        )

        nodes[foldNodeID] = foldNode

        children[eventNode.id]
            ?.add(foldNodeID)
            ?: run { children[eventNode.id] = mutableListOf(foldNodeID) }

        return foldNode
    }

    internal fun <A> createCombinedNode(
        parentNodes: List<Node<Any?>>,
        combine: (List<Any?>) -> A,
        onNextFrame: ((Node<A>) -> Unit)? = null
    ): Node<A> {
        val combinedNodeID = newID()

        val initialValue = lazy { combine(parentNodes.map { it.rawValue }) }

        val combinedNode = Node(
            initialValue,
            combinedNodeID,
            {
                combine(parentNodes.map { fetchNodeValue(it) as A })
            },
            onNextFrame
        )

        nodes[combinedNodeID] = combinedNode

        for (parentNode in parentNodes) {
            children[parentNode.id]
                ?.add(combinedNodeID)
                ?: run { children[parentNode.id] = mutableListOf(combinedNodeID) }
        }

        return combinedNode
    }

    private val nodes: MutableMap<NodeID, Node<Any?>> = mutableMapOf()

    // Basically we are building up a doubly-lined DAG here:

    // Map from a node ID to it's set of child nodes
    private val children: MutableMap<NodeID, MutableList<NodeID>> = mutableMapOf()

    // Note: This is the entrypoint for a new "frame" in the timeline.
    internal fun updateNodeValue(
        node: Node<Any?>,
        newValue: Any?
    ) = synchronized(this) {
        for (listener in onNextFrameListeners) {
            listener()
        }

        onNextFrameListeners.clear()

        node.rawValue = newValue

        println("Updating sync listeners: ${node.syncOnValueChangedListeners.size}")
        for (listener in node.syncOnValueChangedListeners) {
            listener.invoke(newValue)
        }

        scope.launch {
            for (listener in node.onValueChangedListeners) {
                listener.emit(newValue)
            }
        }

        updateChildNodes(node)

        if (node.onNextFrame != null) {
            onNextFrameListeners.add { node.onNextFrame!!.invoke(node) }
        }
    }

    private fun updateChildNodes(
        node: Node<Any?>
    ) {
        val childNodes = children[node.id] ?: listOf()

        for (childID in childNodes) {
            val child = nodes[childID]!!

            if (child.onValueChangedListeners.size == 0 &&
                child.syncOnValueChangedListeners.size == 0) {
                // If not listening, we can mark the node dirty
                child.dirty = true
            } else {
                // Otherwise, we are forced to calculate the node's value
                child.rawValue = child.recompute()

                // As well as invoking any listeners on the child.
                for (listener in child.syncOnValueChangedListeners) {
                    listener.invoke(child.rawValue)
                }

                scope.launch {
                    for (listener in child.onValueChangedListeners) {
                        listener.emit(child.rawValue)
                    }
                }
            }

            updateChildNodes(child)
        }
    }

    internal fun fetchNodeValue(
        node: Node<Any?>
    ): Any? = synchronized(this) {
        if (!node.dirty) {
            return node.rawValue
        } else {
            node.rawValue = node.recompute()
        }

        return node.rawValue
    }

    companion object {
        private var _timeline: Timeline? = null

        fun initializeTimeline(scope: CoroutineScope) {
            _timeline = Timeline(scope)
        }

        fun currentTimeline(): Timeline {
            return _timeline
                ?: error("Timeline must be initialized with Timeline.initializeTimeline().")
        }
    }
}

fun <A> Node<A>.current(): A {
    val graph = Timeline.currentTimeline()

    return graph.fetchNodeValue(this) as A
}