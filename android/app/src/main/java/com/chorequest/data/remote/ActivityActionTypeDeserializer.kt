package com.chorequest.data.remote

import com.chorequest.domain.models.ActivityActionType
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

/**
 * Custom deserializer for ActivityActionType enum
 * Converts lowercase snake_case strings (e.g. "chore_created") from API to uppercase enum values (CHORE_CREATED)
 */
class ActivityActionTypeDeserializer : JsonDeserializer<ActivityActionType> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): ActivityActionType {
        val raw = json?.asString ?: throw JsonParseException("ActivityActionType cannot be null")
        val normalized = raw.uppercase()
        return try {
            ActivityActionType.valueOf(normalized)
        } catch (e: IllegalArgumentException) {
            throw JsonParseException("Unknown ActivityActionType: $raw", e)
        }
    }
}
