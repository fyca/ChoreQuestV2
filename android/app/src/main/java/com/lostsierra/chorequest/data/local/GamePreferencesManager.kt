package com.lostsierra.chorequest.data.local

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
        private const val KEY_MEMORY_MATCH_BEST_TIME = "memory_match_best_time"
        private const val KEY_MEMORY_MATCH_BEST_MOVES = "memory_match_best_moves"
        private const val KEY_MEMORY_MATCH_DIFFICULTY = "memory_match_difficulty"
        private const val KEY_ROCK_PAPER_SCISSORS_HIGH_SCORE = "rock_paper_scissors_high_score"
        private const val KEY_ROCK_PAPER_SCISSORS_DIFFICULTY = "rock_paper_scissors_difficulty"
        private const val KEY_JIGSAW_PUZZLE_BEST_TIME = "jigsaw_puzzle_best_time"
        private const val KEY_JIGSAW_PUZZLE_DIFFICULTY = "jigsaw_puzzle_difficulty"
        private const val KEY_JIGSAW_PUZZLE_SAVED_STATE = "jigsaw_puzzle_saved_state"
        private const val KEY_JIGSAW_PUZZLE_HAS_SAVED_GAME = "jigsaw_puzzle_has_saved_game"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_TIC_TAC_TOE_FLIP_MODE = "tic_tac_toe_flip_mode" // "single" or "entire"
        private const val KEY_TIC_TAC_TOE_WIN_CONDITION = "tic_tac_toe_win_condition" // 3, 4, or 5
        private const val KEY_SNAKE_GAME_HIGH_SCORE = "snake_game_high_score"
        private const val KEY_SNAKE_GAME_DIFFICULTY = "snake_game_difficulty"
        private const val KEY_SNAKE_GAME_SAVED_STATE = "snake_game_saved_state"
        private const val KEY_SNAKE_GAME_HAS_SAVED_GAME = "snake_game_has_saved_game"
        private const val KEY_BREAKOUT_GAME_HIGH_SCORE = "breakout_game_high_score"
        private const val KEY_BREAKOUT_GAME_DIFFICULTY = "breakout_game_difficulty"
        private const val KEY_BREAKOUT_GAME_SAVED_STATE = "breakout_game_saved_state"
        private const val KEY_BREAKOUT_GAME_HAS_SAVED_GAME = "breakout_game_has_saved_game"
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

    // Memory Match Best Time
    fun getMemoryMatchBestTime(): Int {
        return sharedPreferences.getInt(KEY_MEMORY_MATCH_BEST_TIME, Int.MAX_VALUE)
    }

    fun saveMemoryMatchBestTime(timeMs: Int) {
        sharedPreferences.edit().putInt(KEY_MEMORY_MATCH_BEST_TIME, timeMs).apply()
    }

    // Memory Match Best Moves
    fun getMemoryMatchBestMoves(): Int {
        return sharedPreferences.getInt(KEY_MEMORY_MATCH_BEST_MOVES, Int.MAX_VALUE)
    }

    fun saveMemoryMatchBestMoves(moves: Int) {
        sharedPreferences.edit().putInt(KEY_MEMORY_MATCH_BEST_MOVES, moves).apply()
    }

    // Memory Match Difficulty
    fun getMemoryMatchDifficulty(): String {
        return sharedPreferences.getString(KEY_MEMORY_MATCH_DIFFICULTY, "medium") ?: "medium"
    }

    fun saveMemoryMatchDifficulty(difficulty: String) {
        sharedPreferences.edit().putString(KEY_MEMORY_MATCH_DIFFICULTY, difficulty).apply()
    }

    // Rock Paper Scissors High Score
    fun getRockPaperScissorsHighScore(): Int {
        return sharedPreferences.getInt(KEY_ROCK_PAPER_SCISSORS_HIGH_SCORE, 0)
    }

    fun saveRockPaperScissorsHighScore(score: Int) {
        sharedPreferences.edit().putInt(KEY_ROCK_PAPER_SCISSORS_HIGH_SCORE, score).apply()
    }

    // Rock Paper Scissors Difficulty
    fun getRockPaperScissorsDifficulty(): String {
        return sharedPreferences.getString(KEY_ROCK_PAPER_SCISSORS_DIFFICULTY, "medium") ?: "medium"
    }

    fun saveRockPaperScissorsDifficulty(difficulty: String) {
        sharedPreferences.edit().putString(KEY_ROCK_PAPER_SCISSORS_DIFFICULTY, difficulty).apply()
    }

    // Jigsaw Puzzle Best Time
    fun getJigsawPuzzleBestTime(): Int {
        return sharedPreferences.getInt(KEY_JIGSAW_PUZZLE_BEST_TIME, Int.MAX_VALUE)
    }

    fun saveJigsawPuzzleBestTime(timeMs: Int) {
        sharedPreferences.edit().putInt(KEY_JIGSAW_PUZZLE_BEST_TIME, timeMs).apply()
    }

    // Jigsaw Puzzle Difficulty
    fun getJigsawPuzzleDifficulty(): String {
        return sharedPreferences.getString(KEY_JIGSAW_PUZZLE_DIFFICULTY, "medium") ?: "medium"
    }

    fun saveJigsawPuzzleDifficulty(difficulty: String) {
        sharedPreferences.edit().putString(KEY_JIGSAW_PUZZLE_DIFFICULTY, difficulty).apply()
    }

    // Jigsaw Puzzle Saved Game State
    fun hasSavedJigsawPuzzle(): Boolean {
        return sharedPreferences.getBoolean(KEY_JIGSAW_PUZZLE_HAS_SAVED_GAME, false)
    }

    fun saveJigsawPuzzleState(savedStateJson: String) {
        android.util.Log.d("GamePreferencesManager", "saveJigsawPuzzleState() called - state length: ${savedStateJson.length}")
        sharedPreferences.edit()
            .putString(KEY_JIGSAW_PUZZLE_SAVED_STATE, savedStateJson)
            .putBoolean(KEY_JIGSAW_PUZZLE_HAS_SAVED_GAME, true)
            .apply()
        android.util.Log.d("GamePreferencesManager", "saveJigsawPuzzleState() completed - hasSavedGame: ${hasSavedJigsawPuzzle()}")
    }

    fun getJigsawPuzzleSavedState(): String? {
        return if (hasSavedJigsawPuzzle()) {
            sharedPreferences.getString(KEY_JIGSAW_PUZZLE_SAVED_STATE, null)
        } else {
            null
        }
    }

    fun clearJigsawPuzzleSavedState() {
        sharedPreferences.edit()
            .remove(KEY_JIGSAW_PUZZLE_SAVED_STATE)
            .putBoolean(KEY_JIGSAW_PUZZLE_HAS_SAVED_GAME, false)
            .apply()
    }

    // Sound Effects
    fun isSoundEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_SOUND_ENABLED, true)
    }

    fun setSoundEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
    }

    // Tic-Tac-Toe Flip Mode (only for FLIP difficulty)
    fun getTicTacToeFlipMode(): String {
        return sharedPreferences.getString(KEY_TIC_TAC_TOE_FLIP_MODE, "entire") ?: "entire"
    }

    fun saveTicTacToeFlipMode(flipMode: String) {
        sharedPreferences.edit().putString(KEY_TIC_TAC_TOE_FLIP_MODE, flipMode).apply()
    }

    // Tic-Tac-Toe Win Condition (only for hard/flip difficulties)
    fun getTicTacToeWinCondition(): Int {
        return sharedPreferences.getInt(KEY_TIC_TAC_TOE_WIN_CONDITION, 0) // 0 means use boardSize
    }

    fun saveTicTacToeWinCondition(winCondition: Int) {
        sharedPreferences.edit().putInt(KEY_TIC_TAC_TOE_WIN_CONDITION, winCondition).apply()
    }

    // Snake Game High Score
    fun getSnakeGameHighScore(): Int {
        return sharedPreferences.getInt(KEY_SNAKE_GAME_HIGH_SCORE, 0)
    }

    fun saveSnakeGameHighScore(score: Int) {
        sharedPreferences.edit().putInt(KEY_SNAKE_GAME_HIGH_SCORE, score).apply()
    }

    // Snake Game Difficulty
    fun getSnakeGameDifficulty(): String {
        return sharedPreferences.getString(KEY_SNAKE_GAME_DIFFICULTY, "medium") ?: "medium"
    }

    fun saveSnakeGameDifficulty(difficulty: String) {
        sharedPreferences.edit().putString(KEY_SNAKE_GAME_DIFFICULTY, difficulty).apply()
    }

    // Snake Game Saved Game State
    fun hasSavedSnakeGame(): Boolean {
        return sharedPreferences.getBoolean(KEY_SNAKE_GAME_HAS_SAVED_GAME, false)
    }

    fun saveSnakeGameState(savedStateJson: String) {
        android.util.Log.d("GamePreferencesManager", "saveSnakeGameState() called - state length: ${savedStateJson.length}")
        sharedPreferences.edit()
            .putString(KEY_SNAKE_GAME_SAVED_STATE, savedStateJson)
            .putBoolean(KEY_SNAKE_GAME_HAS_SAVED_GAME, true)
            .apply()
        android.util.Log.d("GamePreferencesManager", "saveSnakeGameState() completed - hasSavedGame: ${hasSavedSnakeGame()}")
    }

    fun getSnakeGameSavedState(): String? {
        return if (hasSavedSnakeGame()) {
            sharedPreferences.getString(KEY_SNAKE_GAME_SAVED_STATE, null)
        } else {
            null
        }
    }

    fun clearSnakeGameSavedState() {
        sharedPreferences.edit()
            .remove(KEY_SNAKE_GAME_SAVED_STATE)
            .putBoolean(KEY_SNAKE_GAME_HAS_SAVED_GAME, false)
            .apply()
    }

    // Breakout Game High Score
    fun getBreakoutGameHighScore(): Int {
        return sharedPreferences.getInt(KEY_BREAKOUT_GAME_HIGH_SCORE, 0)
    }

    fun saveBreakoutGameHighScore(score: Int) {
        sharedPreferences.edit().putInt(KEY_BREAKOUT_GAME_HIGH_SCORE, score).apply()
    }

    // Breakout Game Difficulty
    fun getBreakoutGameDifficulty(): String {
        return sharedPreferences.getString(KEY_BREAKOUT_GAME_DIFFICULTY, "medium") ?: "medium"
    }

    fun saveBreakoutGameDifficulty(difficulty: String) {
        sharedPreferences.edit().putString(KEY_BREAKOUT_GAME_DIFFICULTY, difficulty).apply()
    }

    // Breakout Game Saved Game State
    fun hasSavedBreakoutGame(): Boolean {
        return sharedPreferences.getBoolean(KEY_BREAKOUT_GAME_HAS_SAVED_GAME, false)
    }

    fun saveBreakoutGameState(savedStateJson: String) {
        android.util.Log.d("GamePreferencesManager", "saveBreakoutGameState() called - state length: ${savedStateJson.length}")
        sharedPreferences.edit()
            .putString(KEY_BREAKOUT_GAME_SAVED_STATE, savedStateJson)
            .putBoolean(KEY_BREAKOUT_GAME_HAS_SAVED_GAME, true)
            .apply()
        android.util.Log.d("GamePreferencesManager", "saveBreakoutGameState() completed - hasSavedGame: ${hasSavedBreakoutGame()}")
    }

    fun getBreakoutGameSavedState(): String? {
        return if (hasSavedBreakoutGame()) {
            sharedPreferences.getString(KEY_BREAKOUT_GAME_SAVED_STATE, null)
        } else {
            null
        }
    }

    fun clearBreakoutGameSavedState() {
        sharedPreferences.edit()
            .remove(KEY_BREAKOUT_GAME_SAVED_STATE)
            .putBoolean(KEY_BREAKOUT_GAME_HAS_SAVED_GAME, false)
            .apply()
    }
}
