package com.lostsierra.chorequest.data.remote

import com.lostsierra.chorequest.domain.models.UserRole
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

/**
 * Custom deserializer for UserRole enum
 * Converts lowercase "parent"/"child" from API to uppercase PARENT/CHILD enum values
 */
class UserRoleDeserializer : JsonDeserializer<UserRole> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): UserRole {
        val roleString = json?.asString?.lowercase() ?: throw IllegalArgumentException("Role cannot be null")
        return when (roleString) {
            "parent" -> UserRole.PARENT
            "child" -> UserRole.CHILD
            "system" -> UserRole.SYSTEM
            else -> throw IllegalArgumentException("Unknown role: $roleString")
        }
    }
}
