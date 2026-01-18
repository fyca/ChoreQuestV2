package com.chorequest.presentation.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chorequest.data.local.GamePreferencesManager
import com.chorequest.utils.SoundManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TicTacToeUiState(
    val gameState: GameState = GameState(),
    val isAITurn: Boolean = false,
    val showWinDialog: Boolean = false,
    val showDrawDialog: Boolean = false,
    val showCelebration: Boolean = false,
    val difficulty: String = "medium",
    val highScore: Int = 0,
    val soundEnabled: Boolean = true
)

@HiltViewModel
class TicTacToeViewModel @Inject constructor(
    private val gamePreferencesManager: GamePreferencesManager,
    private val soundManager: SoundManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        TicTacToeUiState(
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
                        else -> Difficulty.MEDIUM
                    }
                    
                    val aiMove = findBestMove(currentState.gameState.board, difficulty)
                    if (aiMove != -1) {
                        val newState = currentState.gameState.makeMove(aiMove, Player.O)
                        soundManager.playSound(SoundManager.SoundType.MOVE)
                        
                        _uiState.value = currentState.copy(
                            gameState = newState,
                            isAITurn = false
                        )
                        
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

        val newState = currentState.gameState.makeMove(index, Player.X)
        soundManager.playSound(SoundManager.SoundType.CLICK)
        
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
        _uiState.value = currentState.copy(
            gameState = GameState(
                playerXScore = currentState.gameState.playerXScore,
                playerOScore = currentState.gameState.playerOScore
            ),
            showWinDialog = false,
            showDrawDialog = false,
            isAITurn = false
        )
    }

    fun resetScore() {
        val currentState = _uiState.value
        _uiState.value = currentState.copy(
            gameState = GameState(),
            showWinDialog = false,
            showDrawDialog = false,
            isAITurn = false
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
        _uiState.value = _uiState.value.copy(difficulty = difficulty)
        newGame() // Start new game with new difficulty
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
