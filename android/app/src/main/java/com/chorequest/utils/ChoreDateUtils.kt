package com.chorequest.utils

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
