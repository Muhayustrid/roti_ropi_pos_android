package com.rotiropi.pos_erpnext.data.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

abstract class UnsupportedEnumSerializer<T : Enum<T>>(
    private val values: Map<String, T>,
    private val unsupported: T
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UnsupportedEnum", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): T = values[decoder.decodeString()] ?: unsupported
    override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeString(values.entries.firstOrNull { it.value == value }?.key ?: "unsupported")
    }
}
