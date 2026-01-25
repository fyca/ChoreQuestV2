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
import kotlin.math.abs

@HiltViewModel
class BreakoutGameViewModel @Inject constructor(
    private val preferencesManager: GamePreferencesManager,
    private val soundManager: SoundManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(BreakoutGameUiState())
    val uiState: StateFlow<BreakoutGameUiState> = _uiState.asStateFlow()

    private var gameLoopJob: Job? = null
    private var lastUpdateTime = 0L

    companion object {
        const val BOARD_WIDTH = 10 // Grid cells wide
        const val BOARD_HEIGHT = 20 // Grid cells tall (including paddle area)
        const val PADDLE_Y = BOARD_HEIGHT - 4.5f // Paddle is well above bottom for clear visibility
        const val PADDLE_WIDTH = 3 // Paddle spans 3 cells
        const val BALL_START_Y = BOARD_HEIGHT - 6 // Ball starts above paddle
        
        // Brick layout
        const val BRICK_ROWS = 5
        const val BRICK_COLS = BOARD_WIDTH
        const val BRICK_START_Y = 1 // Bricks start at row 1
        
        // Speed settings (milliseconds per frame) - Slower initial speeds
        const val SPEED_EASY_BASE = 70L
        const val SPEED_MEDIUM_BASE = 50L
        const val SPEED_HARD_BASE = 35L
        
        // Ball speed (cells per update) - Slower initial speeds
        const val BALL_SPEED_EASY_BASE = 0.35f
        const val BALL_SPEED_MEDIUM_BASE = 0.5f
        const val BALL_SPEED_HARD_BASE = 0.7f
        
        // Speed increase per level (percentage)
        const val SPEED_INCREASE_PER_LEVEL = 0.05f // 5% faster per level
        const val MAX_SPEED_MULTIPLIER = 2.0f // Maximum 2x speed
    }

    init {
        loadPreferences()
        // Try to load saved game state
        loadSavedGame()
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            val highScore = preferencesManager.getBreakoutGameHighScore()
            val difficulty = preferencesManager.getBreakoutGameDifficulty()
            _uiState.value = _uiState.value.copy(
                highScore = highScore,
                difficulty = difficulty
            )
        }
    }

    fun startNewGame() {
        gameLoopJob?.cancel()
        // Clear saved game when starting new
        preferencesManager.clearBreakoutGameSavedState()
        
        // Initialize bricks
        val bricks = mutableListOf<Brick>()
        for (row in 0 until BRICK_ROWS) {
            for (col in 0 until BRICK_COLS) {
                bricks.add(
                    Brick(
                        x = col.toFloat(),
                        y = (BRICK_START_Y + row).toFloat(),
                        color = getBrickColor(row)
                    )
                )
            }
        }
        
        // Initialize paddle in center
        val paddleX = (BOARD_WIDTH - PADDLE_WIDTH) / 2f
        
        // Initialize ball above paddle
        val ballX = BOARD_WIDTH / 2f
        val ballY = BALL_START_Y.toFloat()
        
        _uiState.value = _uiState.value.copy(
            bricks = bricks,
            bricksToRemove = emptyList(),
            paddleX = paddleX,
            ballX = ballX,
            ballY = ballY,
            ballVelocityX = getBallSpeedForDifficulty(1) * (if (kotlin.random.Random.nextBoolean()) 1f else -1f),
            ballVelocityY = -getBallSpeedForDifficulty(1),
            score = 0,
            lives = 3,
            level = 1,
            isGameOver = false,
            isPaused = false,
            isGameStarted = false,
            hasSavedGame = false,
            showWinDialog = false,
            showLevelCompleteDialog = false
        )
        
        lastUpdateTime = System.currentTimeMillis()
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
                
                val speed = getSpeedForDifficulty(currentState.difficulty, currentState.level)
                delay(speed)
                
                if (!currentState.isPaused && !currentState.isGameOver) {
                    updateGame()
                }
            }
        }
    }

    fun movePaddleLeft() {
        val currentState = _uiState.value
        if (!currentState.isGameStarted || currentState.isPaused || currentState.isGameOver) return
        
        val newX = (currentState.paddleX - 1f).coerceAtLeast(0f)
        _uiState.value = currentState.copy(paddleX = newX)
        
        // If game hasn't started yet, start it
        if (!currentState.isGameStarted) {
            startGame()
        }
    }

    fun movePaddleRight() {
        val currentState = _uiState.value
        if (!currentState.isGameStarted || currentState.isPaused || currentState.isGameOver) return
        
        val maxX = BOARD_WIDTH - PADDLE_WIDTH
        val newX = (currentState.paddleX + 1f).coerceAtMost(maxX.toFloat())
        _uiState.value = currentState.copy(paddleX = newX)
        
        // If game hasn't started yet, start it
        if (!currentState.isGameStarted) {
            startGame()
        }
    }

    fun setPaddlePosition(x: Float) {
        val currentState = _uiState.value
        if (!currentState.isGameStarted || currentState.isPaused || currentState.isGameOver) return
        
        val maxX = BOARD_WIDTH - PADDLE_WIDTH
        val clampedX = x.coerceIn(0f, maxX.toFloat())
        _uiState.value = currentState.copy(paddleX = clampedX)
    }

    private fun updateGame() {
        val currentState = _uiState.value
        var ballX = currentState.ballX
        var ballY = currentState.ballY
        var ballVelocityX = currentState.ballVelocityX
        var ballVelocityY = currentState.ballVelocityY
        var bricks = currentState.bricks.toMutableList()
        var score = currentState.score
        var lives = currentState.lives
        
        // Store previous position for collision detection
        val prevBallX = ballX
        val prevBallY = ballY
        
        // Update ball position
        ballX += ballVelocityX
        ballY += ballVelocityY
        
        // Wall collisions (left and right)
        if (ballX <= 0 || ballX >= BOARD_WIDTH) {
            ballVelocityX = -ballVelocityX
            ballX = ballX.coerceIn(0f, BOARD_WIDTH.toFloat())
            soundManager.playSound(SoundManager.SoundType.MOVE)
        }
        
        // Top wall collision
        if (ballY <= 0) {
            ballVelocityY = -ballVelocityY
            ballY = 0f
            soundManager.playSound(SoundManager.SoundType.MOVE)
        }
        
        // Bottom wall collision (lose life)
        if (ballY >= BOARD_HEIGHT) {
            lives--
            soundManager.playSound(SoundManager.SoundType.LOSE)
            
            if (lives <= 0) {
                gameOver()
                return
            } else {
                // Reset ball position
                ballX = BOARD_WIDTH / 2f
                ballY = BALL_START_Y.toFloat()
                val currentLevel = currentState.level
                ballVelocityX = getBallSpeedForDifficulty(currentLevel) * (if (kotlin.random.Random.nextBoolean()) 1f else -1f)
                ballVelocityY = -getBallSpeedForDifficulty(currentLevel)
            }
        }
        
        // Paddle collision
        val paddleX = currentState.paddleX
        val paddleY = PADDLE_Y
        if (ballY >= paddleY - 0.5f && ballY <= paddleY + 0.5f &&
            ballX >= paddleX && ballX <= paddleX + PADDLE_WIDTH) {
            // Calculate hit position on paddle (0.0 to 1.0)
            val hitPosition = (ballX - paddleX) / PADDLE_WIDTH
            // Bounce angle based on hit position (-1.0 to 1.0, where 0.5 is center)
            val angle = (hitPosition - 0.5f) * 2f
            ballVelocityX = getBallSpeedForDifficulty() * angle
            ballVelocityY = -abs(ballVelocityY) // Always bounce up
            ballY = paddleY - 0.5f
            soundManager.playSound(SoundManager.SoundType.MOVE)
        }
        
        // Brick collisions - check ball's path to prevent passing through bricks
        var hitBrick: Brick? = null
        var hitSide: String? = null
        var collisionPointX: Float? = null
        var collisionPointY: Float? = null
        
        // Check collisions only with bricks not marked for removal
        val activeBricks = bricks.filter { !currentState.bricksToRemove.contains(it) }
        
        for (brick in activeBricks) {
            // Check if ball's path intersects with brick and find exact collision point
            val collision = findBallPathCollisionPoint(prevBallX, prevBallY, ballX, ballY, brick)
            if (collision != null) {
                hitBrick = brick
                hitSide = collision.first
                collisionPointX = collision.second
                collisionPointY = collision.third
                break // Only hit one brick at a time
            }
        }
        
        // Track bricks to remove (bricks that are hit but ball hasn't moved away yet)
        val newBricksToRemove = currentState.bricksToRemove.toMutableList()
        
        if (hitBrick != null && hitSide != null && collisionPointX != null && collisionPointY != null) {
            // Mark brick for removal (but keep it visible until ball moves away)
            if (!newBricksToRemove.contains(hitBrick)) {
                newBricksToRemove.add(hitBrick)
            }
            
            // Position ball at exact collision point, then move it just outside the brick
            if (hitSide == "horizontal") {
                // Hit from left or right - position ball just outside the brick edge
                ballX = if (ballVelocityX > 0) hitBrick.x - 0.2f else hitBrick.x + 1.2f
                ballY = collisionPointY
            } else {
                // Hit from top or bottom - position ball just outside the brick edge
                ballX = collisionPointX
                ballY = if (ballVelocityY > 0) hitBrick.y - 0.2f else hitBrick.y + 1.2f
            }
            
            // Bounce based on hit side
            if (hitSide == "horizontal") {
                ballVelocityX = -ballVelocityX
            } else {
                ballVelocityY = -ballVelocityY
            }
            
            score += 10
            soundManager.playSound(SoundManager.SoundType.WIN)
        }
        
        // Remove bricks that the ball has moved away from (at least 1.5 cells away)
        val bricksToActuallyRemove = mutableListOf<Brick>()
        for (brickToRemove in newBricksToRemove) {
            val brickCenterX = brickToRemove.x + 0.5f
            val brickCenterY = brickToRemove.y + 0.5f
            val distanceX = abs(ballX - brickCenterX)
            val distanceY = abs(ballY - brickCenterY)
            
            // Remove if ball is at least 1.5 cells away from brick center
            if (distanceX > 1.5f || distanceY > 1.5f) {
                bricksToActuallyRemove.add(brickToRemove)
            }
        }
        
        // Remove bricks that ball has moved away from
        bricks.removeAll(bricksToActuallyRemove)
        val remainingBricksToRemove = newBricksToRemove.filter { !bricksToActuallyRemove.contains(it) }
        
        // Check level complete condition
        if (bricks.isEmpty()) {
            advanceLevel()
            return
        }
        
        // Update state
        val newState = currentState.copy(
            ballX = ballX,
            ballY = ballY,
            ballVelocityX = ballVelocityX,
            ballVelocityY = ballVelocityY,
            bricks = bricks,
            bricksToRemove = remainingBricksToRemove,
            score = score,
            lives = lives
        )
        _uiState.value = newState
        
        // Update high score if needed
        if (score > currentState.highScore) {
            preferencesManager.saveBreakoutGameHighScore(score)
            _uiState.value = newState.copy(highScore = score)
        }
    }

    private fun getBrickColor(row: Int): Int {
        // Different colors for different rows
        return when (row % 5) {
            0 -> 0xFFFF4444.toInt() // Red
            1 -> 0xFFFF8800.toInt() // Orange
            2 -> 0xFFFFBB33.toInt() // Yellow
            3 -> 0xFF99CC00.toInt() // Green
            4 -> 0xFF33B5E5.toInt() // Blue
            else -> 0xFFFFFFFF.toInt() // White
        }
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

    private fun advanceLevel() {
        val currentState = _uiState.value
        val newLevel = currentState.level + 1
        
        // Increase score bonus for completing level
        val levelBonus = newLevel * 50
        val newScore = currentState.score + levelBonus
        
        soundManager.playSound(SoundManager.SoundType.WIN)
        
        // Generate new bricks for next level
        val bricks = mutableListOf<Brick>()
        for (row in 0 until BRICK_ROWS) {
            for (col in 0 until BRICK_COLS) {
                bricks.add(
                    Brick(
                        x = col.toFloat(),
                        y = (BRICK_START_Y + row).toFloat(),
                        color = getBrickColor(row)
                    )
                )
            }
        }
        
        // Reset paddle and ball positions
        val paddleX = (BOARD_WIDTH - PADDLE_WIDTH) / 2f
        val ballX = BOARD_WIDTH / 2f
        val ballY = BALL_START_Y.toFloat()
        
        // Calculate speeds based on level and difficulty
        val ballSpeed = getBallSpeedForDifficulty(newLevel)
        val ballVelocityX = ballSpeed * (if (kotlin.random.Random.nextBoolean()) 1f else -1f)
        val ballVelocityY = -ballSpeed
        
        // Pause game loop while showing level complete dialog
        gameLoopJob?.cancel()
        
        _uiState.value = currentState.copy(
            bricks = bricks,
            bricksToRemove = emptyList(),
            paddleX = paddleX,
            ballX = ballX,
            ballY = ballY,
            ballVelocityX = ballVelocityX,
            ballVelocityY = ballVelocityY,
            level = newLevel,
            score = newScore,
            isPaused = true,
            showLevelCompleteDialog = true
        )
        
        // Update high score if needed
        if (newScore > currentState.highScore) {
            preferencesManager.saveBreakoutGameHighScore(newScore)
            _uiState.value = _uiState.value.copy(highScore = newScore)
        }
    }
    
    private fun winGame() {
        gameLoopJob?.cancel()
        soundManager.playSound(SoundManager.SoundType.WIN)
        _uiState.value = _uiState.value.copy(
            isGameOver = true,
            isPaused = false,
            showWinDialog = true
        )
    }

    fun dismissGameOverDialog() {
        _uiState.value = _uiState.value.copy(showGameOverDialog = false)
        // Clear saved game when game is over
        preferencesManager.clearBreakoutGameSavedState()
        _uiState.value = _uiState.value.copy(hasSavedGame = false)
    }

    fun dismissWinDialog() {
        _uiState.value = _uiState.value.copy(showWinDialog = false)
        // Clear saved game when game is won
        preferencesManager.clearBreakoutGameSavedState()
        _uiState.value = _uiState.value.copy(hasSavedGame = false)
    }

    fun dismissLevelCompleteDialog() {
        _uiState.value = _uiState.value.copy(
            showLevelCompleteDialog = false,
            isPaused = false
        )
        // Resume game loop after level complete dialog is dismissed
        if (_uiState.value.isGameStarted && !_uiState.value.isGameOver) {
            startGameLoop()
        }
    }

    fun setDifficulty(difficulty: String) {
        preferencesManager.saveBreakoutGameDifficulty(difficulty)
        _uiState.value = _uiState.value.copy(difficulty = difficulty)
    }

    fun setSoundEnabled(enabled: Boolean) {
        preferencesManager.setSoundEnabled(enabled)
        _uiState.value = _uiState.value.copy(soundEnabled = enabled)
    }

    /**
     * Find the exact collision point between ball's path and brick
     * Returns Triple(side, collisionX, collisionY) or null if no collision
     */
    private fun findBallPathCollisionPoint(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        brick: Brick
    ): Triple<String, Float, Float>? {
        val brickLeft = brick.x
        val brickRight = brick.x + 1f
        val brickTop = brick.y
        val brickBottom = brick.y + 1f
        
        val dx = endX - startX
        val dy = endY - startY
        
        // Check intersection with each edge and find the earliest collision
        var earliestT = Float.MAX_VALUE
        var collisionSide: String? = null
        var collisionX: Float? = null
        var collisionY: Float? = null
        
        // Check left edge
        if (dx != 0f && startX < brickLeft && endX >= brickLeft) {
            val t = (brickLeft - startX) / dx
            if (t >= 0f && t < earliestT) {
                val y = startY + t * dy
                if (y >= brickTop && y <= brickBottom) {
                    earliestT = t
                    collisionSide = "horizontal"
                    collisionX = brickLeft
                    collisionY = y
                }
            }
        }
        
        // Check right edge
        if (dx != 0f && startX > brickRight && endX <= brickRight) {
            val t = (brickRight - startX) / dx
            if (t >= 0f && t < earliestT) {
                val y = startY + t * dy
                if (y >= brickTop && y <= brickBottom) {
                    earliestT = t
                    collisionSide = "horizontal"
                    collisionX = brickRight
                    collisionY = y
                }
            }
        }
        
        // Check top edge
        if (dy != 0f && startY < brickTop && endY >= brickTop) {
            val t = (brickTop - startY) / dy
            if (t >= 0f && t < earliestT) {
                val x = startX + t * dx
                if (x >= brickLeft && x <= brickRight) {
                    earliestT = t
                    collisionSide = "vertical"
                    collisionX = x
                    collisionY = brickTop
                }
            }
        }
        
        // Check bottom edge
        if (dy != 0f && startY > brickBottom && endY <= brickBottom) {
            val t = (brickBottom - startY) / dy
            if (t >= 0f && t < earliestT) {
                val x = startX + t * dx
                if (x >= brickLeft && x <= brickRight) {
                    earliestT = t
                    collisionSide = "vertical"
                    collisionX = x
                    collisionY = brickBottom
                }
            }
        }
        
        // Check if start point is inside brick (ball already inside)
        if (startX >= brickLeft && startX <= brickRight &&
            startY >= brickTop && startY <= brickBottom) {
            // Ball is already inside, determine exit side
            val brickCenterX = brick.x + 0.5f
            val brickCenterY = brick.y + 0.5f
            val distToLeft = abs(startX - brickLeft)
            val distToRight = abs(startX - brickRight)
            val distToTop = abs(startY - brickTop)
            val distToBottom = abs(startY - brickBottom)
            
            val minDist = minOf(distToLeft, distToRight, distToTop, distToBottom)
            when (minDist) {
                distToLeft -> return Triple("horizontal", brickLeft, startY)
                distToRight -> return Triple("horizontal", brickRight, startY)
                distToTop -> return Triple("vertical", startX, brickTop)
                distToBottom -> return Triple("vertical", startX, brickBottom)
            }
        }
        
        return if (collisionSide != null && collisionX != null && collisionY != null) {
            Triple(collisionSide, collisionX, collisionY)
        } else {
            null
        }
    }
    
    /**
     * Check if ball's movement path intersects with a brick
     * Uses line-segment vs rectangle collision detection
     */
    private fun checkBallPathCollision(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        brick: Brick
    ): Boolean {
        // Brick bounds
        val brickLeft = brick.x
        val brickRight = brick.x + 1f
        val brickTop = brick.y
        val brickBottom = brick.y + 1f
        
        // Check if line segment intersects with brick rectangle
        // Using separating axis theorem (SAT) for line-rectangle intersection
        
        // Check if line segment is completely outside brick bounds
        val minX = minOf(startX, endX)
        val maxX = maxOf(startX, endX)
        val minY = minOf(startY, endY)
        val maxY = maxOf(startY, endY)
        
        // Quick rejection test
        if (maxX < brickLeft || minX > brickRight || maxY < brickTop || minY > brickBottom) {
            return false
        }
        
        // Check if line segment intersects brick rectangle
        // Check each edge of the rectangle
        val dx = endX - startX
        val dy = endY - startY
        
        // Check intersection with left edge
        if (dx != 0f) {
            val t = (brickLeft - startX) / dx
            if (t >= 0f && t <= 1f) {
                val y = startY + t * dy
                if (y >= brickTop && y <= brickBottom) return true
            }
        }
        
        // Check intersection with right edge
        if (dx != 0f) {
            val t = (brickRight - startX) / dx
            if (t >= 0f && t <= 1f) {
                val y = startY + t * dy
                if (y >= brickTop && y <= brickBottom) return true
            }
        }
        
        // Check intersection with top edge
        if (dy != 0f) {
            val t = (brickTop - startY) / dy
            if (t >= 0f && t <= 1f) {
                val x = startX + t * dx
                if (x >= brickLeft && x <= brickRight) return true
            }
        }
        
        // Check intersection with bottom edge
        if (dy != 0f) {
            val t = (brickBottom - startY) / dy
            if (t >= 0f && t <= 1f) {
                val x = startX + t * dx
                if (x >= brickLeft && x <= brickRight) return true
            }
        }
        
        // Check if start point is inside brick
        if (startX >= brickLeft && startX <= brickRight &&
            startY >= brickTop && startY <= brickBottom) {
            return true
        }
        
        return false
    }

    private fun getSpeedForDifficulty(difficulty: String, level: Int = _uiState.value.level): Long {
        val baseSpeed = when (difficulty) {
            "easy" -> SPEED_EASY_BASE
            "hard" -> SPEED_HARD_BASE
            else -> SPEED_MEDIUM_BASE
        }
        
        // Calculate speed multiplier based on level (capped at MAX_SPEED_MULTIPLIER)
        val levelMultiplier = 1f + (level - 1) * SPEED_INCREASE_PER_LEVEL
        val speedMultiplier = levelMultiplier.coerceAtMost(MAX_SPEED_MULTIPLIER)
        
        // Faster update = lower delay, so divide by multiplier
        return (baseSpeed / speedMultiplier).toLong().coerceAtLeast(10L) // Minimum 10ms
    }

    private fun getBallSpeedForDifficulty(level: Int = _uiState.value.level): Float {
        val baseSpeed = when (_uiState.value.difficulty) {
            "easy" -> BALL_SPEED_EASY_BASE
            "hard" -> BALL_SPEED_HARD_BASE
            else -> BALL_SPEED_MEDIUM_BASE
        }
        
        // Calculate speed multiplier based on level (capped at MAX_SPEED_MULTIPLIER)
        val levelMultiplier = 1f + (level - 1) * SPEED_INCREASE_PER_LEVEL
        val speedMultiplier = levelMultiplier.coerceAtMost(MAX_SPEED_MULTIPLIER)
        
        return baseSpeed * speedMultiplier
    }

    private fun saveGameState(state: BreakoutGameUiState? = null) {
        val currentState = state ?: _uiState.value
        Log.d("BreakoutGame", "saveGameState() called - isGameOver=${currentState.isGameOver}, isGameStarted=${currentState.isGameStarted}")
        
        // Don't save if game is complete or not started
        if (currentState.isGameOver) {
            Log.d("BreakoutGame", "Skipping save - game is over")
            return
        }
        if (!currentState.isGameStarted) {
            Log.d("BreakoutGame", "Skipping save - game not started")
            return
        }
        if (currentState.bricks.isEmpty()) {
            Log.d("BreakoutGame", "Skipping save - game won")
            return
        }
        
        Log.d("BreakoutGame", "Saving game state - score=${currentState.score}, lives=${currentState.lives}, bricks=${currentState.bricks.size}")
        
        viewModelScope.launch {
            try {
                val savedState = JSONObject().apply {
                    put("score", currentState.score)
                    put("lives", currentState.lives)
                    put("level", currentState.level)
                    put("difficulty", currentState.difficulty)
                    put("paddleX", currentState.paddleX)
                    put("ballX", currentState.ballX)
                    put("ballY", currentState.ballY)
                    put("ballVelocityX", currentState.ballVelocityX.toDouble())
                    put("ballVelocityY", currentState.ballVelocityY.toDouble())
                    put("isPaused", currentState.isPaused)
                    put("isGameStarted", currentState.isGameStarted)
                    
                    // Save bricks
                    val bricksArray = JSONArray()
                    currentState.bricks.forEach { brick ->
                        val brickJson = JSONObject().apply {
                            put("x", brick.x.toDouble())
                            put("y", brick.y.toDouble())
                            put("color", brick.color)
                        }
                        bricksArray.put(brickJson)
                    }
                    put("bricks", bricksArray)
                }
                
                preferencesManager.saveBreakoutGameState(savedState.toString())
                _uiState.value = currentState.copy(hasSavedGame = true)
                Log.d("BreakoutGame", "Game state saved successfully")
            } catch (e: Exception) {
                Log.e("BreakoutGame", "Error saving game state", e)
            }
        }
    }
    
    private fun loadSavedGame() {
        val savedStateJson = preferencesManager.getBreakoutGameSavedState() ?: return
        
        viewModelScope.launch {
            try {
                val savedState = JSONObject(savedStateJson)
                val score = savedState.getInt("score")
                val lives = savedState.getInt("lives")
                val level = if (savedState.has("level")) savedState.getInt("level") else 1
                val difficulty = savedState.getString("difficulty")
                val paddleX = savedState.getDouble("paddleX").toFloat()
                val ballX = savedState.getDouble("ballX").toFloat()
                val ballY = savedState.getDouble("ballY").toFloat()
                val ballVelocityX = savedState.getDouble("ballVelocityX").toFloat()
                val ballVelocityY = savedState.getDouble("ballVelocityY").toFloat()
                val isPaused = savedState.getBoolean("isPaused")
                val isGameStarted = savedState.getBoolean("isGameStarted")
                
                // Load bricks
                val bricksArray = savedState.getJSONArray("bricks")
                val bricks = mutableListOf<Brick>()
                for (i in 0 until bricksArray.length()) {
                    val brickJson = bricksArray.getJSONObject(i)
                    bricks.add(
                        Brick(
                            x = brickJson.getDouble("x").toFloat(),
                            y = brickJson.getDouble("y").toFloat(),
                            color = brickJson.getInt("color")
                        )
                    )
                }
                
                _uiState.value = _uiState.value.copy(
                    bricks = bricks,
                    bricksToRemove = emptyList(), // Reset on load
                    paddleX = paddleX,
                    ballX = ballX,
                    ballY = ballY,
                    ballVelocityX = ballVelocityX,
                    ballVelocityY = ballVelocityY,
                    score = score,
                    lives = lives,
                    level = level,
                    difficulty = difficulty,
                    isPaused = isPaused,
                    isGameStarted = isGameStarted,
                    hasSavedGame = true
                )
                
                // Resume game loop if game was started and not paused
                if (isGameStarted && !isPaused && !_uiState.value.isGameOver) {
                    startGameLoop()
                }
                
                Log.d("BreakoutGame", "Game state loaded successfully - score=$score, lives=$lives, bricks=${bricks.size}")
            } catch (e: Exception) {
                Log.e("BreakoutGame", "Error loading saved game", e)
                // Clear corrupted save
                preferencesManager.clearBreakoutGameSavedState()
            }
        }
    }
    
    fun saveGameStateOnPause() {
        // Public method to be called when app goes to background
        Log.d("BreakoutGame", "saveGameStateOnPause() called")
        saveGameState()
    }

    override fun onCleared() {
        super.onCleared()
        gameLoopJob?.cancel()
        // Save game state when ViewModel is cleared (e.g., app closing, navigation away)
        Log.d("BreakoutGame", "ViewModel onCleared() - saving game state")
        saveGameState()
    }
}

data class BreakoutGameUiState(
    val bricks: List<Brick> = emptyList(),
    val bricksToRemove: List<Brick> = emptyList(), // Bricks hit but not yet removed (waiting for ball to move away)
    val paddleX: Float = 0f,
    val ballX: Float = 0f,
    val ballY: Float = 0f,
    val ballVelocityX: Float = 0f,
    val ballVelocityY: Float = 0f,
    val score: Int = 0,
    val lives: Int = 3,
    val level: Int = 1,
    val highScore: Int = 0,
    val isGameOver: Boolean = false,
    val isPaused: Boolean = false,
    val isGameStarted: Boolean = false,
    val showGameOverDialog: Boolean = false,
    val showWinDialog: Boolean = false,
    val showLevelCompleteDialog: Boolean = false,
    val difficulty: String = "medium",
    val soundEnabled: Boolean = true,
    val hasSavedGame: Boolean = false
)

data class Brick(
    val x: Float,
    val y: Float,
    val color: Int
)
