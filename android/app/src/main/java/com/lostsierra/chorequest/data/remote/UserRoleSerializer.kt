package com.lostsierra.chorequest.data.remote

import com.lostsierra.chorequest.domain.models.UserRole
import com.google.gson.*
import java.lang.reflect.Type

/**
 * Combined TypeAdapter for UserRole enum
 * Serializes: Converts uppercase PARENT/CHILD enum values to lowercase "parent"/"child" for API/Drive JSON
 * Deserializes: Converts lowercase "parent"/"child" from API to uppercase PARENT/CHILD enum values
 */
class UserRoleTypeAdapter : JsonSerializer<UserRole>, JsonDeserializer<UserRole> {
    override fun serialize(
        src: UserRole?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        return JsonPrimitive(src?.name?.lowercase() ?: "child")
    }

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
