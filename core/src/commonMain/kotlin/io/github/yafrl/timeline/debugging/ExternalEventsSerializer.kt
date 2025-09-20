package io.github.yafrl.timeline.debugging

import io.github.yafrl.timeline.BehaviorID
import io.github.yafrl.timeline.BehaviorIDSerializer
import io.github.yafrl.timeline.NodeID
import io.github.yafrl.timeline.NodeIDSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule

// ----------------- helpers for contextual fallbacks -----------------
private fun SerializersModule.behaviorIdSer(): KSerializer<BehaviorID> =
    getContextual(BehaviorID::class) ?: BehaviorIDSerializer

private fun SerializersModule.nodeIdSer(): KSerializer<NodeID> =
    getContextual(NodeID::class) ?: NodeIDSerializer

// ----------------- Tagged Any? (format-agnostic) -----------------
// Encodes as: { kind: "...", one matching field }
// kinds: null|string|boolean|int|long|float|double|object
object TaggedAnySerializer : KSerializer<Any?> {
    private enum class Kind { null_, string, boolean, int, long, float, double, object_ }

    private val polyAny = PolymorphicSerializer(Any::class)

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("TaggedAny") {
            element("kind", PrimitiveSerialDescriptor("kind", PrimitiveKind.STRING))
            element("string", PrimitiveSerialDescriptor("string", PrimitiveKind.STRING), isOptional = true)
            element("boolean", PrimitiveSerialDescriptor("boolean", PrimitiveKind.BOOLEAN), isOptional = true)
            element("int", PrimitiveSerialDescriptor("int", PrimitiveKind.INT), isOptional = true)
            element("long", PrimitiveSerialDescriptor("long", PrimitiveKind.LONG), isOptional = true)
            element("float", PrimitiveSerialDescriptor("float", PrimitiveKind.FLOAT), isOptional = true)
            element("double", PrimitiveSerialDescriptor("double", PrimitiveKind.DOUBLE), isOptional = true)
            element("object", polyAny.descriptor, isOptional = true)
        }

    override fun serialize(encoder: Encoder, value: Any?) {
        val c = encoder.beginStructure(descriptor)
        when (value) {
            null -> {
                c.encodeStringElement(descriptor, 0, Kind.null_.name.removeSuffix("_"))
            }
            is String -> {
                c.encodeStringElement(descriptor, 0, Kind.string.name)
                c.encodeStringElement(descriptor, 1, value)
            }
            is Boolean -> {
                c.encodeStringElement(descriptor, 0, Kind.boolean.name)
                c.encodeBooleanElement(descriptor, 2, value)
            }
            is Int -> {
                c.encodeStringElement(descriptor, 0, Kind.int.name)
                c.encodeIntElement(descriptor, 3, value)
            }
            is Long -> {
                c.encodeStringElement(descriptor, 0, Kind.long.name)
                c.encodeLongElement(descriptor, 4, value)
            }
            is Float -> {
                c.encodeStringElement(descriptor, 0, Kind.float.name)
                c.encodeFloatElement(descriptor, 5, value)
            }
            is Double -> {
                c.encodeStringElement(descriptor, 0, Kind.double.name)
                c.encodeDoubleElement(descriptor, 6, value)
            }
            else -> {
                c.encodeStringElement(descriptor, 0, "object")
                c.encodeSerializableElement(descriptor, 7, polyAny, value)
            }
        }
        c.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): Any? {
        val c = decoder.beginStructure(descriptor)

        var kind: String? = null
        var str: String? = null
        var bool: Boolean? = null
        var i: Int? = null
        var l: Long? = null
        var f: Float? = null
        var d: Double? = null
        var obj: Any? = null

        loop@ while (true) {
            when (val ix = c.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                0 -> kind = c.decodeStringElement(descriptor, 0)
                1 -> str = c.decodeStringElement(descriptor, 1)
                2 -> bool = c.decodeBooleanElement(descriptor, 2)
                3 -> i = c.decodeIntElement(descriptor, 3)
                4 -> l = c.decodeLongElement(descriptor, 4)
                5 -> f = c.decodeFloatElement(descriptor, 5)
                6 -> d = c.decodeDoubleElement(descriptor, 6)
                7 -> obj = c.decodeSerializableElement(descriptor, 7, polyAny)
                else -> throw SerializationException("Unknown index: $ix")
            }
        }
        c.endStructure(descriptor)

        return when (kind) {
            "null"    -> null
            "string"  -> str ?: throw SerializationException("Missing 'string' value")
            "boolean" -> bool ?: throw SerializationException("Missing 'boolean' value")
            "int"     -> i ?: throw SerializationException("Missing 'int' value")
            "long"    -> l ?: throw SerializationException("Missing 'long' value")
            "float"   -> f ?: throw SerializationException("Missing 'float' value")
            "double"  -> d ?: throw SerializationException("Missing 'double' value")
            "object"  -> obj ?: throw SerializationException("Missing 'object' value")
            else -> throw SerializationException("Missing or unknown 'kind': $kind")
        }
    }
}


// ----------------- ExternalAction -----------------
object ExternalActionSerializer : KSerializer<ExternalAction> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("ExternalAction") {
            element("type", PrimitiveSerialDescriptor("type", PrimitiveKind.STRING))
            element("id", NodeIDSerializer.descriptor)
            element("value", TaggedAnySerializer.descriptor)
        }

    override fun serialize(encoder: Encoder, value: ExternalAction) {
        val nodeSer = encoder.serializersModule.nodeIdSer()
        val c = encoder.beginStructure(descriptor)
        when (value) {
            is ExternalAction.FireEvent -> c.encodeStringElement(descriptor, 0, "FireEvent")
            is ExternalAction.UpdateValue -> c.encodeStringElement(descriptor, 0, "UpdateValue")
        }
        c.encodeSerializableElement(descriptor, 1, nodeSer, value.id)
        val v = when (value) {
            is ExternalAction.FireEvent -> value.value
            is ExternalAction.UpdateValue -> value.value
        }
        c.encodeSerializableElement(descriptor, 2, TaggedAnySerializer, v)
        c.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): ExternalAction {
        val nodeSer = decoder.serializersModule.nodeIdSer()
        val c = decoder.beginStructure(descriptor)

        var type: String? = null
        var id: NodeID? = null
        var value: Any? = null

        loop@ while (true) {
            when (val ix = c.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                0 -> type = c.decodeStringElement(descriptor, 0)
                1 -> id = c.decodeSerializableElement(descriptor, 1, nodeSer)
                2 -> value = c.decodeSerializableElement(descriptor, 2, TaggedAnySerializer)
                else -> throw SerializationException("Unknown index $ix")
            }
        }
        c.endStructure(descriptor)

        val nid = id ?: throw SerializationException("Missing 'id'")
        return when (type) {
            "FireEvent" -> ExternalAction.FireEvent(nid, value)
            "UpdateValue" -> ExternalAction.UpdateValue(nid, value)
            else -> throw SerializationException("Unknown or missing 'type': $type")
        }
    }
}

// ----------------- ExternalEvent -----------------
object ExternalEventSerializer : KSerializer<ExternalEvent> {
    // descriptor built with concrete serializers so schema is stable
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("ExternalEvent") {
            val mapDesc = MapSerializer(BehaviorIDSerializer, TaggedAnySerializer).descriptor
            element("behaviorsSampled", mapDesc)
            element("externalAction", ExternalActionSerializer.descriptor)
        }

    override fun serialize(encoder: Encoder, value: ExternalEvent) {
        val mapSer = MapSerializer(encoder.serializersModule.behaviorIdSer(), TaggedAnySerializer)
        val c = encoder.beginStructure(descriptor)
        c.encodeSerializableElement(descriptor, 0, mapSer, value.behaviorsSampled)
        c.encodeSerializableElement(descriptor, 1, ExternalActionSerializer, value.externalAction)
        c.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): ExternalEvent {
        val mapSer = MapSerializer(decoder.serializersModule.behaviorIdSer(), TaggedAnySerializer)
        val c = decoder.beginStructure(descriptor)

        var behaviors: Map<BehaviorID, Any?>? = null
        var action: ExternalAction? = null

        loop@ while (true) {
            when (val ix = c.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                0 -> behaviors = c.decodeSerializableElement(descriptor, 0, mapSer)
                1 -> action = c.decodeSerializableElement(descriptor, 1, ExternalActionSerializer)
                else -> throw SerializationException("Unknown index $ix")
            }
        }
        c.endStructure(descriptor)

        return ExternalEvent(
            behaviorsSampled = behaviors ?: emptyMap(),
            externalAction = action ?: throw SerializationException("Missing 'externalAction'")
        )
    }
}