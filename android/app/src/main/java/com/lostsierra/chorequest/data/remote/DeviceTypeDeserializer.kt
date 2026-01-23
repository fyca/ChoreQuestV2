package com.lostsierra.chorequest.data.remote

import com.lostsierra.chorequest.domain.models.DeviceType
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

/**
 * Custom deserializer for DeviceType enum.
 * Converts lowercase strings from API (e.g. "unknown", "android") to enum values.
 */
class DeviceTypeDeserializer : JsonDeserializer<DeviceType> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): DeviceType {
        val raw = json?.asString ?: return DeviceType.UNKNOWN
        return try {
            DeviceType.valueOf(raw.uppercase())
        } catch (e: IllegalArgumentException) {
            DeviceType.UNKNOWN
        }
    }
}
