package com.chorequest.data.remote

import com.chorequest.domain.models.ChoreStatus
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

/**
 * Converts backend chore status strings (e.g. "pending") into [ChoreStatus] enum values.
 */
class ChoreStatusDeserializer : JsonDeserializer<ChoreStatus> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): ChoreStatus {
        val raw = json?.asString ?: return ChoreStatus.PENDING
        val normalized = raw.uppercase()
        return try {
            ChoreStatus.valueOf(normalized)
        } catch (e: IllegalArgumentException) {
            throw JsonParseException("Unknown ChoreStatus: $raw", e)
        }
    }
}

