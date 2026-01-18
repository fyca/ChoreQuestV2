package com.chorequest.data.local

import androidx.room.TypeConverter
import com.chorequest.domain.models.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    // List<String> conversions
    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        if (value == null) return null
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType)
    }

    // Subtask list conversions
    @TypeConverter
    fun fromSubtaskList(value: List<Subtask>?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toSubtaskList(value: String?): List<Subtask>? {
        if (value == null) return null
        val listType = object : TypeToken<List<Subtask>>() {}.type
        return gson.fromJson(value, listType)
    }

    // Device list conversions
    @TypeConverter
    fun fromDeviceList(value: List<Device>?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toDeviceList(value: String?): List<Device>? {
        if (value == null) return null
        val listType = object : TypeToken<List<Device>>() {}.type
        return gson.fromJson(value, listType)
    }

    // RecurringSchedule conversions
    @TypeConverter
    fun fromRecurringSchedule(value: RecurringSchedule?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toRecurringSchedule(value: String?): RecurringSchedule? {
        if (value == null) return null
        return gson.fromJson(value, RecurringSchedule::class.java)
    }

    // UserSettings conversions
    @TypeConverter
    fun fromUserSettings(value: UserSettings?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toUserSettings(value: String?): UserSettings? {
        if (value == null) return null
        return gson.fromJson(value, UserSettings::class.java)
    }

    // UserStats conversions
    @TypeConverter
    fun fromUserStats(value: UserStats?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toUserStats(value: String?): UserStats? {
        if (value == null) return null
        return gson.fromJson(value, UserStats::class.java)
    }

    // ActivityDetails conversions
    @TypeConverter
    fun fromActivityDetails(value: ActivityDetails?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toActivityDetails(value: String?): ActivityDetails? {
        if (value == null) return null
        return gson.fromJson(value, ActivityDetails::class.java)
    }

    // ActivityMetadata conversions
    @TypeConverter
    fun fromActivityMetadata(value: ActivityMetadata?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toActivityMetadata(value: String?): ActivityMetadata? {
        if (value == null) return null
        return gson.fromJson(value, ActivityMetadata::class.java)
    }

    // Enum conversions
    @TypeConverter
    fun fromUserRole(value: UserRole): String {
        return value.name
    }

    @TypeConverter
    fun toUserRole(value: String): UserRole {
        return UserRole.valueOf(value)
    }

    @TypeConverter
    fun fromChoreStatus(value: ChoreStatus): String {
        return value.name
    }

    @TypeConverter
    fun toChoreStatus(value: String): ChoreStatus {
        return ChoreStatus.valueOf(value)
    }

    @TypeConverter
    fun fromActivityActionType(value: ActivityActionType): String {
        return value.name
    }

    @TypeConverter
    fun toActivityActionType(value: String): ActivityActionType {
        return ActivityActionType.valueOf(value)
    }

    @TypeConverter
    fun fromTransactionType(value: TransactionType): String {
        return value.name
    }

    @TypeConverter
    fun toTransactionType(value: String): TransactionType {
        return TransactionType.valueOf(value)
    }

    @TypeConverter
    fun fromRewardRedemptionStatus(value: RewardRedemptionStatus): String {
        return value.name
    }

    @TypeConverter
    fun toRewardRedemptionStatus(value: String): RewardRedemptionStatus {
        return RewardRedemptionStatus.valueOf(value)
    }
}
