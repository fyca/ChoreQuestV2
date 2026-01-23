package com.lostsierra.chorequest.data.remote

import com.lostsierra.chorequest.domain.models.ActivityActionType
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

/**
 * Custom deserializer for ActivityActionType enum.
 * Converts lowercase snake_case strings from API (e.g. "chore_created") to enum values.
 * Maps backend aliases that don't match enum names (e.g. "chore_updated" -> CHORE_EDITED).
 */
class ActivityActionTypeDeserializer : JsonDeserializer<ActivityActionType> {
    companion object {
        /** Backend actionType strings that map to different enum names */
        private val ALIASES = mapOf(
            "chore_updated" to ActivityActionType.CHORE_EDITED,
            "reward_updated" to ActivityActionType.REWARD_EDITED
        )
    }

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): ActivityActionType {
        val raw = json?.asString ?: throw JsonParseException("ActivityActionType cannot be null")
        ALIASES[raw]?.let { return it }
        val normalized = raw.uppercase()
        return try {
            ActivityActionType.valueOf(normalized)
        } catch (e: IllegalArgumentException) {
            throw JsonParseException("Unknown ActivityActionType: $raw", e)
        }
    }
}
