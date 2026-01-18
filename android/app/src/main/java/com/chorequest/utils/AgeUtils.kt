package com.chorequest.utils

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Utility functions for age calculation and age-based recommendations
 */
object AgeUtils {
    
    /**
     * Calculate age from a birthdate string (ISO 8601 format: YYYY-MM-DD)
     */
    fun calculateAge(birthdateString: String?): Int? {
        if (birthdateString == null) return null
        
        return try {
            val birthdate = LocalDate.parse(birthdateString, DateTimeFormatter.ISO_DATE)
            val today = LocalDate.now()
            ChronoUnit.YEARS.between(birthdate, today).toInt()
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get age group category for a given age
     */
    fun getAgeGroup(age: Int?): AgeGroup? {
        if (age == null) return null
        
        return when {
            age < 3 -> AgeGroup.TODDLER
            age < 5 -> AgeGroup.PRESCHOOL
            age < 8 -> AgeGroup.EARLY_ELEMENTARY
            age < 11 -> AgeGroup.LATE_ELEMENTARY
            age < 14 -> AgeGroup.MIDDLE_SCHOOL
            else -> AgeGroup.TEEN
        }
    }
}

enum class AgeGroup {
    TODDLER,        // 0-2 years
    PRESCHOOL,      // 3-4 years
    EARLY_ELEMENTARY, // 5-7 years
    LATE_ELEMENTARY,  // 8-10 years
    MIDDLE_SCHOOL,   // 11-13 years
    TEEN             // 14+ years
}
