package com.chorequest.utils

import com.chorequest.domain.models.RecurringFrequency
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Utility functions for chore date calculations and formatting
 */
object ChoreDateUtils {
    
    /**
     * Calculate time remaining until chore expiration
     * @param dueDateString The due date in ISO format (YYYY-MM-DD) or full ISO datetime string
     * @return TimeRemaining object with formatted string and remaining time, or null if invalid/expired
     */
    fun calculateTimeRemaining(dueDateString: String?): TimeRemaining? {
        if (dueDateString == null || dueDateString.isBlank()) return null
        
        return try {
            // Parse the due date - handle both YYYY-MM-DD and full ISO datetime strings
            val dueDate = if (dueDateString.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) {
                // YYYY-MM-DD format - treat as end of day
                LocalDate.parse(dueDateString, DateTimeFormatter.ISO_DATE)
                    .atTime(23, 59, 59)
            } else {
                // Try parsing as ISO datetime
                try {
                    LocalDateTime.parse(dueDateString, DateTimeFormatter.ISO_DATE_TIME)
                } catch (e: Exception) {
                    // Try parsing as ISO date with timezone
                    try {
                        java.time.ZonedDateTime.parse(dueDateString).toLocalDateTime()
                    } catch (e2: Exception) {
                        // If all parsing fails, return null
                        return null
                    }
                }
            }
            
            val now = LocalDateTime.now()
            
            // If already expired, return null
            if (dueDate.isBefore(now) || dueDate.isEqual(now)) {
                return null
            }
            
            // Calculate differences
            val days = ChronoUnit.DAYS.between(now, dueDate)
            val hours = ChronoUnit.HOURS.between(now, dueDate)
            val minutes = ChronoUnit.MINUTES.between(now, dueDate)
            
            // Format the remaining time
            val formatted = when {
                days > 7 -> "${days} days"
                days > 0 -> {
                    val remainingHours = hours % 24
                    if (remainingHours > 0) {
                        "$days day${if (days > 1) "s" else ""}, $remainingHours hour${if (remainingHours > 1) "s" else ""}"
                    } else {
                        "$days day${if (days > 1) "s" else ""}"
                    }
                }
                hours > 0 -> {
                    val remainingMinutes = minutes % 60
                    if (remainingMinutes > 0) {
                        "$hours hour${if (hours > 1) "s" else ""}, $remainingMinutes min${if (remainingMinutes > 1) "s" else ""}"
                    } else {
                        "$hours hour${if (hours > 1) "s" else ""}"
                    }
                }
                minutes > 0 -> "$minutes min${if (minutes > 1) "s" else ""}"
                else -> "Less than a minute"
            }
            
            TimeRemaining(
                formatted = formatted,
                days = days,
                hours = hours,
                minutes = minutes,
                isUrgent = days == 0L && hours < 24L,
                isVeryUrgent = days == 0L && hours < 6L
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Infer frequency from cycleId format
     * Monthly: "YYYY-MM", Weekly: "YYYY-W##", Daily: "YYYY-MM-DD"
     */
    fun inferFrequencyFromCycleId(cycleId: String?): RecurringFrequency? {
        if (cycleId == null) return null
        return when {
            cycleId.matches(Regex("^\\d{4}-\\d{2}$")) -> RecurringFrequency.MONTHLY // YYYY-MM
            cycleId.matches(Regex("^\\d{4}-W\\d{2}$")) -> RecurringFrequency.WEEKLY // YYYY-W##
            cycleId.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$")) -> RecurringFrequency.DAILY // YYYY-MM-DD
            else -> null
        }
    }
    
    /**
     * Calculate expiration progress percentage based on chore frequency
     * @param dueDateString The due date in ISO format (YYYY-MM-DD) or full ISO datetime string
     * @param frequency The recurring frequency (DAILY, WEEKLY, MONTHLY) or null for one-time chores
     * @param cycleId Optional cycleId to infer frequency if frequency is null
     * @return Progress value between 0.0f (just started) and 1.0f (expired), or null if invalid
     */
    fun calculateExpirationProgress(dueDateString: String?, frequency: RecurringFrequency?, cycleId: String? = null): Float? {
        if (dueDateString == null || dueDateString.isBlank()) return null
        
        // If frequency is null but cycleId exists, try to infer frequency from cycleId
        val actualFrequency = frequency ?: inferFrequencyFromCycleId(cycleId)
        
        return try {
            // Parse the due date
            val dueDate = if (dueDateString.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) {
                // YYYY-MM-DD format - treat as end of day
                LocalDate.parse(dueDateString, DateTimeFormatter.ISO_DATE)
                    .atTime(23, 59, 59)
            } else {
                // Try parsing as ISO datetime
                try {
                    LocalDateTime.parse(dueDateString, DateTimeFormatter.ISO_DATE_TIME)
                } catch (e: Exception) {
                    // Try parsing as ISO date with timezone
                    try {
                        java.time.ZonedDateTime.parse(dueDateString).toLocalDateTime()
                    } catch (e2: Exception) {
                        return null
                    }
                }
            }
            
            val now = LocalDateTime.now()
            
            // If already expired, return 1.0f (100%)
            if (dueDate.isBefore(now) || dueDate.isEqual(now)) {
                return 1.0f
            }
            
            // Calculate start date based on frequency
            val startDate = when (actualFrequency) {
                RecurringFrequency.DAILY -> {
                    // Start is the beginning of the due date's day (00:00:00)
                    val dueLocalDate = dueDate.toLocalDate()
                    dueLocalDate.atStartOfDay()
                }
                RecurringFrequency.WEEKLY -> {
                    // Start is the beginning of the week (Monday 00:00:00)
                    // Calculate days to subtract to get to Monday (dayOfWeek: 1=Monday, 7=Sunday)
                    val dueLocalDate = dueDate.toLocalDate()
                    val dayOfWeek = dueLocalDate.dayOfWeek.value // 1=Monday, 7=Sunday
                    val daysToSubtract = (dayOfWeek - 1).toLong() // Days to go back to Monday
                    dueLocalDate.minusDays(daysToSubtract).atStartOfDay()
                }
                RecurringFrequency.MONTHLY -> {
                    // Start is the first day of the month (00:00:00)
                    // Note: For monthly chores, the due date might be a specific day (e.g., 15th)
                    // but we still calculate progress from the start of the month
                    val dueLocalDate = dueDate.toLocalDate()
                    LocalDate.of(dueLocalDate.year, dueLocalDate.month, 1)
                        .atStartOfDay()
                }
                null -> {
                    // For one-time chores, use 7 days before due date as the start
                    dueDate.minusDays(7)
                }
            }
            
            // Ensure start date is not in the future
            val actualStartDate = if (startDate.isAfter(now)) now else startDate
            
            // Calculate total duration and elapsed time
            val totalDuration = ChronoUnit.MILLIS.between(actualStartDate, dueDate)
            val elapsedTime = ChronoUnit.MILLIS.between(actualStartDate, now)
            
            if (totalDuration <= 0) return 1.0f
            
            // Calculate progress (0.0 = just started, 1.0 = expired)
            val progress = (elapsedTime.toFloat() / totalDuration.toFloat()).coerceIn(0.0f, 1.0f)
            
            progress
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Data class representing time remaining until expiration
 */
data class TimeRemaining(
    val formatted: String,
    val days: Long,
    val hours: Long,
    val minutes: Long,
    val isUrgent: Boolean,      // Less than 24 hours remaining
    val isVeryUrgent: Boolean   // Less than 6 hours remaining
)
