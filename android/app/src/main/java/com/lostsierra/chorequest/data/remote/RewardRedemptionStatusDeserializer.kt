package com.lostsierra.chorequest.data.remote

import com.lostsierra.chorequest.domain.models.RewardRedemptionStatus
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

/**
 * Custom deserializer for RewardRedemptionStatus enum.
 * Converts lowercase strings from API (e.g. "pending", "approved") to enum values.
 */
class RewardRedemptionStatusDeserializer : JsonDeserializer<RewardRedemptionStatus> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): RewardRedemptionStatus {
        val raw = json?.asString ?: return RewardRedemptionStatus.PENDING
        return try {
            RewardRedemptionStatus.valueOf(raw.uppercase())
        } catch (e: IllegalArgumentException) {
            RewardRedemptionStatus.PENDING
        }
    }
}
