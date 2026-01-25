package com.lostsierra.chorequest.presentation.games

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostsierra.chorequest.data.local.GamePreferencesManager
import com.lostsierra.chorequest.utils.SoundManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class SnakeGameViewModel @Inject constructor(
    private val preferencesManager: GamePreferencesManager,
    private val soundManager: SoundManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SnakeGameUiState())
    val uiState: StateFlow<SnakeGameUiState> = _uiState.asStateFlow()

    private var gameLoopJob: Job? = null
    private var lastMoveTime = 0L

    companion object {
        const val GRID_SIZE = 20 // 20x20 grid
        const val INITIAL_SNAKE_LENGTH = 3
        
        // Speed settings (milliseconds between moves)
        const val SPEED_EASY = 300L
        const val SPEED_MEDIUM = 200L
        const val SPEED_HARD = 120L
    }

    init {
        loadPreferences()
        // Try to load saved game state
        loadSavedGame()
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            val highScore = preferencesManager.getSnakeGameHighScore()
            val difficulty = preferencesManager.getSnakeGameDifficulty()
            _uiState.value = _uiState.value.copy(
                highScore = highScore,
                difficulty = difficulty
            )
        }
    }

    fun startNewGame() {
        gameLoopJob?.cancel()
        // Clear saved game when starting new
        preferencesManager.clearSnakeGameSavedState()
        
        val snake = mutableListOf<Pair<Int, Int>>()
        // Start snake in the middle, facing right
        for (i in 0 until INITIAL_SNAKE_LENGTH) {
            snake.add(Pair(GRID_SIZE / 2 - i, GRID_SIZE / 2))
        }
        
        val food = generateFood(snake)
        
        _uiState.value = _uiState.value.copy(
            snake = snake,
            food = food,
            direction = Direction.RIGHT,
            nextDirection = Direction.RIGHT,
            score = 0,
            isGameOver = false,
            isPaused = false,
            isGameStarted = false,
            hasSavedGame = false
        )
        
        lastMoveTime = System.currentTimeMillis()
    }

    fun startGame() {
        if (_uiState.value.isGameStarted || _uiState.value.isGameOver) return
        
        _uiState.value = _uiState.value.copy(isGameStarted = true, isPaused = false)
        startGameLoop()
    }

    fun pauseGame() {
        if (!_uiState.value.isGameStarted || _uiState.value.isGameOver) return
        gameLoopJob?.cancel()
        _uiState.value = _uiState.value.copy(isPaused = true)
        // Save game state when pausing
        saveGameState()
    }

    fun resumeGame() {
        if (!_uiState.value.isGameStarted || _uiState.value.isGameOver || !_uiState.value.isPaused) return
        _uiState.value = _uiState.value.copy(isPaused = false)
        startGameLoop()
    }

    private fun startGameLoop() {
        gameLoopJob?.cancel()
        gameLoopJob = viewModelScope.launch {
            while (!_uiState.value.isGameOver && !_uiState.value.isPaused) {
                val currentState = _uiState.value
                if (!currentState.isGameStarted) break
                
                val speed = getSpeedForDifficulty(currentState.difficulty)
                delay(speed)
                
                if (!currentState.isPaused && !currentState.isGameOver) {
                    moveSnake()
                }
            }
        }
    }

    fun changeDirection(newDirection: Direction) {
        val currentState = _uiState.value
        if (!currentState.isGameStarted || currentState.isPaused || currentState.isGameOver) return
        
        // Prevent reversing into itself
        val currentDirection = currentState.direction
        if (newDirection.isOpposite(currentDirection)) {
            return
        }
        
        // Queue the direction change for the next move
        _uiState.value = currentState.copy(nextDirection = newDirection)
        
        // If game hasn't started yet, start it
        if (!currentState.isGameStarted) {
            startGame()
        }
    }

    private fun moveSnake() {
        val currentState = _uiState.value
        val snake = currentState.snake.toMutableList()
        val direction = currentState.nextDirection
        
        // Calculate new head position
        val head = snake.first()
        val newHead = when (direction) {
            Direction.UP -> Pair(head.first, head.second - 1)
            Direction.DOWN -> Pair(head.first, head.second + 1)
            Direction.LEFT -> Pair(head.first - 1, head.second)
            Direction.RIGHT -> Pair(head.first + 1, head.second)
        }
        
        // Check wall collision
        if (newHead.first < 0 || newHead.first >= GRID_SIZE ||
            newHead.second < 0 || newHead.second >= GRID_SIZE) {
            gameOver()
            return
        }
        
        // Check self collision
        if (snake.contains(newHead)) {
            gameOver()
            return
        }
        
        // Add new head
        snake.add(0, newHead)
        
        // Check if food eaten
        val food = currentState.food
        val score = currentState.score
        val newFood = if (newHead == food) {
            soundManager.playSound(SoundManager.SoundType.WIN) // Use win sound for eating
            // Don't remove tail, snake grows
            generateFood(snake)
        } else {
            // Remove tail
            snake.removeAt(snake.size - 1)
            food
        }
        
        val newScore = if (newHead == food) score + 10 else score
        
        // Update state with new direction, snake position, food, and score all at once
        _uiState.value = currentState.copy(
            direction = direction,
            nextDirection = direction, // Keep nextDirection in sync
            snake = snake,
            food = newFood,
            score = newScore
        )
        
        // Update high score if needed
        if (newScore > currentState.highScore) {
            preferencesManager.saveSnakeGameHighScore(newScore)
            _uiState.value = _uiState.value.copy(highScore = newScore)
        }
    }

    private fun generateFood(snake: List<Pair<Int, Int>>): Pair<Int, Int> {
        val availablePositions = mutableListOf<Pair<Int, Int>>()
        for (x in 0 until GRID_SIZE) {
            for (y in 0 until GRID_SIZE) {
                val pos = Pair(x, y)
                if (!snake.contains(pos)) {
                    availablePositions.add(pos)
                }
            }
        }
        return availablePositions.random()
    }

    private fun gameOver() {
        gameLoopJob?.cancel()
        soundManager.playSound(SoundManager.SoundType.LOSE)
        _uiState.value = _uiState.value.copy(
            isGameOver = true,
            isPaused = false,
            showGameOverDialog = true
        )
    }

    fun dismissGameOverDialog() {
        _uiState.value = _uiState.value.copy(showGameOverDialog = false)
        // Clear saved game when game is over
        preferencesManager.clearSnakeGameSavedState()
        _uiState.value = _uiState.value.copy(hasSavedGame = false)
    }

    fun setDifficulty(difficulty: String) {
        preferencesManager.saveSnakeGameDifficulty(difficulty)
        _uiState.value = _uiState.value.copy(difficulty = difficulty)
    }

    fun setSoundEnabled(enabled: Boolean) {
        preferencesManager.setSoundEnabled(enabled)
        _uiState.value = _uiState.value.copy(soundEnabled = enabled)
    }

    private fun getSpeedForDifficulty(difficulty: String): Long {
        return when (difficulty) {
            "easy" -> SPEED_EASY
            "hard" -> SPEED_HARD
            else -> SPEED_MEDIUM
        }
    }

    private fun saveGameState(state: SnakeGameUiState? = null) {
        val currentState = state ?: _uiState.value
        Log.d("SnakeGame", "saveGameState() called - isGameOver=${currentState.isGameOver}, isGameStarted=${currentState.isGameStarted}, snake.size=${currentState.snake.size}")
        
        // Don't save if game is complete or not started
        if (currentState.isGameOver) {
            Log.d("SnakeGame", "Skipping save - game is over")
            return
        }
        if (!currentState.isGameStarted) {
            Log.d("SnakeGame", "Skipping save - game not started")
            return
        }
        if (currentState.snake.isEmpty()) {
            Log.d("SnakeGame", "Skipping save - no snake")
            return
        }
        
        Log.d("SnakeGame", "Saving game state - score=${currentState.score}, snake.size=${currentState.snake.size}")
        
        viewModelScope.launch {
            try {
                val savedState = JSONObject().apply {
                    put("score", currentState.score)
                    put("difficulty", currentState.difficulty)
                    put("direction", currentState.direction.name)
                    put("nextDirection", currentState.nextDirection.name)
                    put("isPaused", currentState.isPaused)
                    put("isGameStarted", currentState.isGameStarted)
                    
                    // Save snake positions
                    val snakeArray = JSONArray()
                    currentState.snake.forEach { segment ->
                        val segmentJson = JSONArray().apply {
                            put(segment.first)
                            put(segment.second)
                        }
                        snakeArray.put(segmentJson)
                    }
                    put("snake", snakeArray)
                    
                    // Save food position
                    val foodJson = JSONArray().apply {
                        put(currentState.food.first)
                        put(currentState.food.second)
                    }
                    put("food", foodJson)
                }
                
                preferencesManager.saveSnakeGameState(savedState.toString())
                _uiState.value = currentState.copy(hasSavedGame = true)
                Log.d("SnakeGame", "Game state saved successfully")
            } catch (e: Exception) {
                Log.e("SnakeGame", "Error saving game state", e)
            }
        }
    }
    
    private fun loadSavedGame() {
        val savedStateJson = preferencesManager.getSnakeGameSavedState() ?: return
        
        viewModelScope.launch {
            try {
                val savedState = JSONObject(savedStateJson)
                val score = savedState.getInt("score")
                val difficulty = savedState.getString("difficulty")
                val direction = Direction.valueOf(savedState.getString("direction"))
                val nextDirection = Direction.valueOf(savedState.getString("nextDirection"))
                val isPaused = savedState.getBoolean("isPaused")
                val isGameStarted = savedState.getBoolean("isGameStarted")
                
                // Load snake positions
                val snakeArray = savedState.getJSONArray("snake")
                val snake = mutableListOf<Pair<Int, Int>>()
                for (i in 0 until snakeArray.length()) {
                    val segmentArray = snakeArray.getJSONArray(i)
                    snake.add(Pair(segmentArray.getInt(0), segmentArray.getInt(1)))
                }
                
                // Load food position
                val foodArray = savedState.getJSONArray("food")
                val food = Pair(foodArray.getInt(0), foodArray.getInt(1))
                
                _uiState.value = _uiState.value.copy(
                    snake = snake,
                    food = food,
                    score = score,
                    difficulty = difficulty,
                    direction = direction,
                    nextDirection = nextDirection,
                    isPaused = isPaused,
                    isGameStarted = isGameStarted,
                    hasSavedGame = true
                )
                
                // Resume game loop if game was started and not paused
                if (isGameStarted && !isPaused && !_uiState.value.isGameOver) {
                    startGameLoop()
                }
                
                Log.d("SnakeGame", "Game state loaded successfully - score=$score, snake.size=${snake.size}")
            } catch (e: Exception) {
                Log.e("SnakeGame", "Error loading saved game", e)
                // Clear corrupted save
                preferencesManager.clearSnakeGameSavedState()
            }
        }
    }
    
    fun saveGameStateOnPause() {
        // Public method to be called when app goes to background
        Log.d("SnakeGame", "saveGameStateOnPause() called")
        saveGameState()
    }

    override fun onCleared() {
        super.onCleared()
        gameLoopJob?.cancel()
        // Save game state when ViewModel is cleared (e.g., app closing, navigation away)
        Log.d("SnakeGame", "ViewModel onCleared() - saving game state")
        saveGameState()
    }
}

data class SnakeGameUiState(
    val snake: List<Pair<Int, Int>> = emptyList(),
    val food: Pair<Int, Int> = Pair(-1, -1),
    val direction: Direction = Direction.RIGHT,
    val nextDirection: Direction = Direction.RIGHT,
    val score: Int = 0,
    val highScore: Int = 0,
    val isGameOver: Boolean = false,
    val isPaused: Boolean = false,
    val isGameStarted: Boolean = false,
    val showGameOverDialog: Boolean = false,
    val difficulty: String = "medium",
    val soundEnabled: Boolean = true,
    val hasSavedGame: Boolean = false
)

enum class Direction {
    UP, DOWN, LEFT, RIGHT;
    
    fun isOpposite(other: Direction): Boolean {
        return when (this) {
            UP -> other == DOWN
            DOWN -> other == UP
            LEFT -> other == RIGHT
            RIGHT -> other == LEFT
        }
    }
}
