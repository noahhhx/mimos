package io.github.noahhhx.mimos.client

import io.github.noahhhx.mimos.api.models.PanelActionsBlock
import io.github.noahhhx.mimos.api.models.PanelHighlightBlock
import io.github.noahhhx.mimos.api.models.PanelTextBlock
import io.github.noahhhx.mimos.api.models.PanelWheelBlock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * One block of a plugin's week panel (ADR-0017). The one API model not generated (ADR-0029): a
 * newer server may send a block type this app does not know, which reads as [Unknown] instead of
 * failing the whole panel. Each known block is the generated model for its schema.
 */
@Serializable(with = PanelBlockSerializer::class)
sealed interface PanelBlock {
    data class Text(val block: PanelTextBlock) : PanelBlock

    data class Highlight(val block: PanelHighlightBlock) : PanelBlock

    data class Wheel(val block: PanelWheelBlock) : PanelBlock

    data class Actions(val block: PanelActionsBlock) : PanelBlock

    data class Unknown(val json: JsonObject) : PanelBlock
}

internal object PanelBlockSerializer : KSerializer<PanelBlock> {
    override val descriptor = JsonObject.serializer().descriptor

    override fun deserialize(decoder: Decoder): PanelBlock {
        val input =
            decoder as? JsonDecoder ?: throw SerializationException("PanelBlock reads JSON only")
        val json = input.decodeJsonElement().jsonObject
        val format = input.json
        return when ((json["type"] as? JsonPrimitive)?.contentOrNull) {
            "text" ->
                PanelBlock.Text(format.decodeFromJsonElement(PanelTextBlock.serializer(), json))
            "highlight" ->
                PanelBlock.Highlight(
                    format.decodeFromJsonElement(PanelHighlightBlock.serializer(), json)
                )
            "wheel" ->
                PanelBlock.Wheel(format.decodeFromJsonElement(PanelWheelBlock.serializer(), json))
            "actions" ->
                PanelBlock.Actions(
                    format.decodeFromJsonElement(PanelActionsBlock.serializer(), json)
                )
            else -> PanelBlock.Unknown(json)
        }
    }

    override fun serialize(encoder: Encoder, value: PanelBlock) =
        when (value) {
            is PanelBlock.Text ->
                encoder.encodeSerializableValue(PanelTextBlock.serializer(), value.block)
            is PanelBlock.Highlight ->
                encoder.encodeSerializableValue(PanelHighlightBlock.serializer(), value.block)
            is PanelBlock.Wheel ->
                encoder.encodeSerializableValue(PanelWheelBlock.serializer(), value.block)
            is PanelBlock.Actions ->
                encoder.encodeSerializableValue(PanelActionsBlock.serializer(), value.block)
            is PanelBlock.Unknown ->
                encoder.encodeSerializableValue(JsonObject.serializer(), value.json)
        }
}
