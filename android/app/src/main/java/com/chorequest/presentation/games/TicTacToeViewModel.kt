package com.chorequest.presentation.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chorequest.data.local.GamePreferencesManager
import com.chorequest.utils.SoundManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class TicTacToeUiState(
    val gameState: GameState = GameState(),
    val isAITurn: Boolean = false,
    val showWinDialog: Boolean = false,
    val showDrawDialog: Boolean = false,
    val showCelebration: Boolean = false,
    val difficulty: String = "medium",
    val highScore: Int = 0,
    val soundEnabled: Boolean = true,
    val isFlippingColumns: Boolean = false,
    val columnsToFlip: Set<Int> = emptySet()
)

@HiltViewModel
class TicTacToeViewModel @Inject constructor(
    private val gamePreferencesManager: GamePreferencesManager,
    private val soundManager: SoundManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        TicTacToeUiState(
            gameState = GameState(
                boardSize = getBoardSize(gamePreferencesManager.getTicTacToeDifficulty())
            ),
            difficulty = gamePreferencesManager.getTicTacToeDifficulty(),
            highScore = gamePreferencesManager.getTicTacToeHighScore(),
            soundEnabled = gamePreferencesManager.isSoundEnabled()
        )
    )
    val uiState: StateFlow<TicTacToeUiState> = _uiState.asStateFlow()

    init {
        // Handle AI moves
        viewModelScope.launch {
            while (true) {
                val currentState = _uiState.value
                if (currentState.isAITurn && !currentState.gameState.isGameOver()) {
                    delay(500) // Small delay for better UX
                    
                    val difficulty = when (currentState.difficulty) {
                        "easy" -> Difficulty.EASY
                        "medium" -> Difficulty.MEDIUM
                        "hard" -> Difficulty.HARD
                        "hard-flip" -> Difficulty.HARD_FLIP
                        else -> Difficulty.MEDIUM
                    }
                    
                    // Calculate AI move on background thread to avoid blocking UI
                    val aiMove = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        findBestMove(
                            currentState.gameState.board.copyOf(), // Copy to avoid mutation
                            currentState.gameState.boardSize,
                            difficulty
                        )
                    }
                    
                    if (aiMove != -1) {
                        var newState = currentState.gameState.makeMove(aiMove, Player.O)
                        soundManager.playSound(SoundManager.SoundType.MOVE)
                        
                        // If hard-flip mode, flip columns after AI move
                        if (currentState.difficulty == "hard-flip") {
                            val columnsToFlip = (0 until newState.boardSize).toSet()
                            _uiState.value = currentState.copy(
                                gameState = newState,
                                isAITurn = false,
                                isFlippingColumns = true,
                                columnsToFlip = columnsToFlip
                            )
                            
                            // Wait for animation, then flip
                            delay(800) // Animation duration
                            newState = newState.flipColumnsRandomly()
                            val updatedState = _uiState.value
                            _uiState.value = updatedState.copy(
                                gameState = newState,
                                isAITurn = false,
                                isFlippingColumns = false,
                                columnsToFlip = emptySet()
                            )
                        } else {
                            _uiState.value = currentState.copy(
                                gameState = newState,
                                isAITurn = false
                            )
                        }
                        
                        // Check for game end after AI move
                        checkGameEnd(newState)
                    } else {
                        _uiState.value = currentState.copy(isAITurn = false)
                    }
                }
                delay(100) // Check every 100ms
            }
        }
    }

    fun onCellClick(index: Int) {
        val currentState = _uiState.value
        if (currentState.isAITurn || 
            currentState.gameState.board[index] != null || 
            currentState.gameState.isGameOver()) {
            return
        }

        var newState = currentState.gameState.makeMove(index, Player.X)
        soundManager.playSound(SoundManager.SoundType.CLICK)
        
        // If hard-flip mode, flip columns after player move
        if (currentState.difficulty == "hard-flip") {
            val columnsToFlip = (0 until newState.boardSize).toSet()
            _uiState.value = currentState.copy(
                gameState = newState,
                isFlippingColumns = true,
                columnsToFlip = columnsToFlip
            )
            
            // Wait for animation, then flip
            viewModelScope.launch {
                delay(800) // Animation duration
                newState = newState.flipColumnsRandomly()
                val latestState = _uiState.value
                
                // Check for win or draw after flip
                val winner = newState.checkWinner()
                if (winner == Player.X) {
                    soundManager.playSound(SoundManager.SoundType.WIN)
                    _uiState.value = latestState.copy(
                        gameState = newState,
                        showWinDialog = true,
                        showCelebration = true,
                        isFlippingColumns = false,
                        columnsToFlip = emptySet()
                    )
                    updateHighScore(newState.playerXScore)
                } else if (newState.isBoardFull()) {
                    soundManager.playSound(SoundManager.SoundType.DRAW)
                    _uiState.value = latestState.copy(
                        gameState = newState,
                        showDrawDialog = true,
                        isFlippingColumns = false,
                        columnsToFlip = emptySet()
                    )
                } else {
                    // AI's turn
                    _uiState.value = latestState.copy(
                        gameState = newState,
                        isAITurn = true,
                        isFlippingColumns = false,
                        columnsToFlip = emptySet()
                    )
                }
            }
        } else {
            _uiState.value = currentState.copy(gameState = newState)
            
            // Check for win or draw
            val winner = newState.checkWinner()
            if (winner == Player.X) {
                soundManager.playSound(SoundManager.SoundType.WIN)
                _uiState.value = currentState.copy(
                    gameState = newState,
                    showWinDialog = true,
                    showCelebration = true
                )
                updateHighScore(newState.playerXScore)
            } else if (newState.isBoardFull()) {
                soundManager.playSound(SoundManager.SoundType.DRAW)
                _uiState.value = currentState.copy(
                    gameState = newState,
                    showDrawDialog = true
                )
            } else {
                // AI's turn
                _uiState.value = currentState.copy(
                    gameState = newState,
                    isAITurn = true
                )
            }
        }
    }

    private fun checkGameEnd(gameState: GameState) {
        val winner = gameState.checkWinner()
        val currentState = _uiState.value
        
        if (winner == Player.O) {
            soundManager.playSound(SoundManager.SoundType.LOSE)
            _uiState.value = currentState.copy(
                showWinDialog = true
            )
        } else if (gameState.isBoardFull()) {
            soundManager.playSound(SoundManager.SoundType.DRAW)
            _uiState.value = currentState.copy(
                showDrawDialog = true
            )
        }
    }

    fun newGame() {
        val currentState = _uiState.value
        val boardSize = getBoardSize(currentState.difficulty)
        _uiState.value = currentState.copy(
            gameState = GameState(
                board = arrayOfNulls(boardSize * boardSize),
                boardSize = boardSize,
                playerXScore = currentState.gameState.playerXScore,
                playerOScore = currentState.gameState.playerOScore
            ),
            showWinDialog = false,
            showDrawDialog = false,
            isAITurn = false,
            isFlippingColumns = false,
            columnsToFlip = emptySet()
        )
    }

    fun resetScore() {
        val currentState = _uiState.value
        val boardSize = currentState.gameState.boardSize
        _uiState.value = currentState.copy(
            gameState = GameState(
                board = arrayOfNulls(boardSize * boardSize),
                boardSize = boardSize
            ),
            showWinDialog = false,
            showDrawDialog = false,
            isAITurn = false,
            isFlippingColumns = false,
            columnsToFlip = emptySet()
        )
    }

    fun dismissWinDialog() {
        _uiState.value = _uiState.value.copy(showWinDialog = false)
    }

    fun dismissDrawDialog() {
        _uiState.value = _uiState.value.copy(showDrawDialog = false)
    }

    fun onCelebrationComplete() {
        _uiState.value = _uiState.value.copy(showCelebration = false)
    }

    fun setDifficulty(difficulty: String) {
        gamePreferencesManager.saveTicTacToeDifficulty(difficulty)
        val currentState = _uiState.value
        val boardSize = getBoardSize(difficulty)
        _uiState.value = currentState.copy(
            difficulty = difficulty,
            gameState = GameState(
                board = arrayOfNulls(boardSize * boardSize),
                boardSize = boardSize,
                playerXScore = currentState.gameState.playerXScore,
                playerOScore = currentState.gameState.playerOScore
            ),
            showWinDialog = false,
            showDrawDialog = false,
            isAITurn = false,
            isFlippingColumns = false,
            columnsToFlip = emptySet()
        )
    }

    fun setSoundEnabled(enabled: Boolean) {
        gamePreferencesManager.setSoundEnabled(enabled)
        _uiState.value = _uiState.value.copy(soundEnabled = enabled)
    }

    private fun updateHighScore(newScore: Int) {
        val currentHighScore = _uiState.value.highScore
        if (newScore > currentHighScore) {
            gamePreferencesManager.saveTicTacToeHighScore(newScore)
            _uiState.value = _uiState.value.copy(highScore = newScore)
        }
    }
}
