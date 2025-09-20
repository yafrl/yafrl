package io.github.yafrl.timeline

import io.github.yafrl.annotations.FragileYafrlAPI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.jvm.JvmInline

/**
 * A node is the basic abstraction used in yafrl.
 *
 * It represents a node in the state graph of reactive values in the application,
 *  and can represent either an event, or a reactive behavior.
 **/
open class Node<out A> internal constructor(
    private val initialValue: Lazy<A>,
    @property:FragileYafrlAPI
    val id: NodeID,
    internal val recompute: () -> A,
    internal val onNextFrame: ((Node<@UnsafeVariance A>) -> Unit)? = null,
    internal val onRollback: ((node: Node<@UnsafeVariance A>, frame: Long) -> Unit)? = null,
    internal var label: String = id.toString(),
) : Flow<A> {
    override fun toString(): String {
        return label
    }

    internal val onValueChangedListeners: MutableList<FlowCollector<@UnsafeVariance A>> = mutableListOf()

    internal val syncOnValueChangedListeners: MutableList<(@UnsafeVariance A) -> Unit> = mutableListOf()

    internal var _rawValue: @UnsafeVariance A? = null

    @property:FragileYafrlAPI
    var rawValue: @UnsafeVariance A
        get() {
            if (_rawValue == null) _rawValue = initialValue.value
            return _rawValue!!
        }
        set(value) {
            _rawValue = value
        }

    internal var dirty = false

    override suspend fun collect(collector: FlowCollector<A>) {
        onValueChangedListeners += collector
    }

    fun collectSync(collector: (A) -> Unit) {
        syncOnValueChangedListeners += collector
    }

    fun unregisterSync(collector: (A) -> Unit) {
        syncOnValueChangedListeners -= collector
    }
}

/** Uniquely identifies nodes in a particular [Timeline]. */
@JvmInline
value class NodeID internal constructor(internal val rawValue: Int) {
    override fun toString(): String {
        return "node#${rawValue}"
    }
}

// Custom serializer due to kotlinJs internal compiler error
object NodeIDSerializer : KSerializer<NodeID> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("NodeId", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: NodeID) {
        encoder.encodeInt(value.rawValue)
    }

    override fun deserialize(decoder: Decoder): NodeID {
        return NodeID(decoder.decodeInt())
    }
}

/** Uniquely identifies sampled behaviors in a particular [Timeline]. */
@JvmInline
value class BehaviorID internal constructor(internal val rawValue: Int) {
    override fun toString(): String {
        return "behavior#${rawValue}"
    }
}

// Custom serializer due to kotlinJs internal compiler error
object BehaviorIDSerializer : KSerializer<BehaviorID> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("BehaviorId", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: BehaviorID) {
        encoder.encodeInt(value.rawValue)
    }

    override fun deserialize(decoder: Decoder): BehaviorID {
        return BehaviorID(decoder.decodeInt())
    }
}
