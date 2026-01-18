package com.chorequest.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GamePreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "chorequest_games",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_TIC_TAC_TOE_HIGH_SCORE = "tic_tac_toe_high_score"
        private const val KEY_TIC_TAC_TOE_DIFFICULTY = "tic_tac_toe_difficulty"
        private const val KEY_CHORE_QUIZ_HIGH_SCORE = "chore_quiz_high_score"
        private const val KEY_CHORE_QUIZ_DIFFICULTY = "chore_quiz_difficulty"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
    }

    // Tic-Tac-Toe High Score
    fun getTicTacToeHighScore(): Int {
        return sharedPreferences.getInt(KEY_TIC_TAC_TOE_HIGH_SCORE, 0)
    }

    fun saveTicTacToeHighScore(score: Int) {
        sharedPreferences.edit().putInt(KEY_TIC_TAC_TOE_HIGH_SCORE, score).apply()
    }

    // Tic-Tac-Toe Difficulty
    fun getTicTacToeDifficulty(): String {
        return sharedPreferences.getString(KEY_TIC_TAC_TOE_DIFFICULTY, "medium") ?: "medium"
    }

    fun saveTicTacToeDifficulty(difficulty: String) {
        sharedPreferences.edit().putString(KEY_TIC_TAC_TOE_DIFFICULTY, difficulty).apply()
    }

    // Chore Quiz High Score
    fun getChoreQuizHighScore(): Int {
        return sharedPreferences.getInt(KEY_CHORE_QUIZ_HIGH_SCORE, 0)
    }

    fun saveChoreQuizHighScore(score: Int) {
        sharedPreferences.edit().putInt(KEY_CHORE_QUIZ_HIGH_SCORE, score).apply()
    }

    // Chore Quiz Difficulty
    fun getChoreQuizDifficulty(): String {
        return sharedPreferences.getString(KEY_CHORE_QUIZ_DIFFICULTY, "medium") ?: "medium"
    }

    fun saveChoreQuizDifficulty(difficulty: String) {
        sharedPreferences.edit().putString(KEY_CHORE_QUIZ_DIFFICULTY, difficulty).apply()
    }

    // Sound Effects
    fun isSoundEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_SOUND_ENABLED, true)
    }

    fun setSoundEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
    }
}
