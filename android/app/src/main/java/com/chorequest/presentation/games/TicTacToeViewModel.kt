package com.chorequest.presentation.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chorequest.data.local.GamePreferencesManager
import com.chorequest.data.local.SessionManager
import com.chorequest.data.repository.UserRepository
import com.chorequest.domain.models.User
import com.chorequest.utils.SoundManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class GameMode {
    AI, FAMILY_USER, LOCAL_PLAYER
}

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
    val columnsToFlip: Set<Int> = emptySet(),
    val flipMode: String = "entire", // "single" or "entire"
    val gameMode: GameMode = GameMode.AI,
    val opponentUserId: String? = null, // For FAMILY_USER mode
    val opponentName: String? = null, // For FAMILY_USER or LOCAL_PLAYER mode
    val player1Name: String = "You", // Player X name
    val player2Name: String = "AI", // Player O name
    val winCondition: Int = 0 // 0 = use boardSize, 3-5 = custom win condition (only for hard/flip)
)

@HiltViewModel
class TicTacToeViewModel @Inject constructor(
    private val gamePreferencesManager: GamePreferencesManager,
    private val soundManager: SoundManager,
    val userRepository: UserRepository,
    val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        TicTacToeUiState(
            gameState = GameState(
                boardSize = getBoardSize(gamePreferencesManager.getTicTacToeDifficulty())
            ),
            difficulty = gamePreferencesManager.getTicTacToeDifficulty(),
            highScore = gamePreferencesManager.getTicTacToeHighScore(),
            soundEnabled = gamePreferencesManager.isSoundEnabled(),
            flipMode = gamePreferencesManager.getTicTacToeFlipMode(),
            winCondition = gamePreferencesManager.getTicTacToeWinCondition()
        )
    )
    val uiState: StateFlow<TicTacToeUiState> = _uiState.asStateFlow()

    init {
        // Handle AI moves (only when game mode is AI)
        viewModelScope.launch {
            while (true) {
                val currentState = _uiState.value
                // Don't process AI turn if columns are flipping, game is over, or not in AI mode
                val effectiveWinCondition = if (currentState.winCondition > 0 && currentState.winCondition <= currentState.gameState.boardSize) 
                    currentState.winCondition 
                else 
                    currentState.gameState.boardSize
                if (currentState.isAITurn && 
                    currentState.gameMode == GameMode.AI &&
                    !currentState.gameState.isGameOver(effectiveWinCondition) && 
                    !currentState.isFlippingColumns) {
                    delay(500) // Small delay for better UX
                    
                    val difficulty = when (currentState.difficulty) {
                        "easy" -> Difficulty.EASY
                        "medium" -> Difficulty.MEDIUM
                        "hard" -> Difficulty.HARD
                        "flip" -> Difficulty.FLIP
                        else -> Difficulty.MEDIUM
                    }
                    
                        // Calculate AI move on background thread to avoid blocking UI
                        val effectiveWinCondition = if (currentState.winCondition > 0 && currentState.winCondition <= currentState.gameState.boardSize) 
                            currentState.winCondition 
                        else 
                            currentState.gameState.boardSize
                        // Block center during first two moves in hard/flip modes
                        val blockCenter = currentState.difficulty == "hard" || currentState.difficulty == "flip"
                        val aiMove = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                            findBestMove(
                                currentState.gameState.board.copyOf(), // Copy to avoid mutation
                                currentState.gameState.boardSize,
                                difficulty,
                                effectiveWinCondition,
                                blockCenter
                            )
                        }
                    
                    if (aiMove != -1) {
                        // Skip winner check in makeMove if flip mode (we'll check after flipping)
                        val skipWinnerCheck = currentState.difficulty == "flip"
                        var newState = currentState.gameState.makeMove(aiMove, Player.O, skipWinnerCheck = skipWinnerCheck)
                        soundManager.playSound(SoundManager.SoundType.MOVE)
                        
                        // If flip mode, flip columns after AI move
                        if (currentState.difficulty == "flip") {
                            // Calculate which column was played in
                            val columnIndex = aiMove % newState.boardSize
                            // Determine which columns to show flipping animation for
                            val columnsToFlip = if (currentState.flipMode == "single") {
                                setOf(columnIndex)
                            } else {
                                (0 until newState.boardSize).toSet()
                            }
                            
                            _uiState.value = currentState.copy(
                                gameState = newState,
                                isAITurn = false,
                                isFlippingColumns = true,
                                columnsToFlip = columnsToFlip
                            )
                            
                            // Wait for flip animation, then flip and check winner
                            delay(800) // Animation duration
                            
                            // Re-read state to ensure we have the latest
                            val stateBeforeFlip = _uiState.value
                            // Only proceed if we're still in flipping state (prevent race conditions)
                            if (!stateBeforeFlip.isFlippingColumns || stateBeforeFlip.gameState.boardSize != newState.boardSize) {
                                return@launch
                            }
                            
                            // Flip the current game state based on flip mode
                            val flippedState = stateBeforeFlip.gameState.flipColumns(columnIndex, stateBeforeFlip.flipMode)
                            
                            // Immediately update the UI with the flipped board (before checking winner)
                            _uiState.value = stateBeforeFlip.copy(
                                gameState = flippedState,
                                isFlippingColumns = false,
                                columnsToFlip = emptySet()
                            )
                            
                            // Now check for win or draw after flip
                            val effectiveWinCondition = if (stateBeforeFlip.winCondition > 0 && stateBeforeFlip.winCondition <= flippedState.boardSize) 
                                stateBeforeFlip.winCondition 
                            else 
                                flippedState.boardSize
                            val winner = flippedState.checkWinner(winCondition = effectiveWinCondition)
                            
                            // Check if both players have winning combinations (simultaneous win = draw)
                            val isSimultaneousWin = flippedState.hasSimultaneousWin(effectiveWinCondition)
                            
                            val finalState = if (winner == Player.O && !isSimultaneousWin) {
                                // Update score with winner
                                flippedState.copy(playerOScore = stateBeforeFlip.gameState.playerOScore + 1)
                            } else if (winner == Player.X && !isSimultaneousWin) {
                                flippedState.copy(playerXScore = stateBeforeFlip.gameState.playerXScore + 1)
                            } else {
                                flippedState.copy(
                                    playerXScore = stateBeforeFlip.gameState.playerXScore,
                                    playerOScore = stateBeforeFlip.gameState.playerOScore
                                )
                            }
                            
                            // Get the latest state after the flip update
                            val latestState = _uiState.value
                            
                            // Check for win or draw after flip and update UI accordingly
                            if (isSimultaneousWin) {
                                // Both players have winning combinations = draw
                                soundManager.playSound(SoundManager.SoundType.DRAW)
                                _uiState.value = latestState.copy(
                                    gameState = finalState,
                                    showDrawDialog = true,
                                    showWinDialog = false,
                                    isAITurn = false
                                )
                            } else if (winner == Player.O) {
                                soundManager.playSound(SoundManager.SoundType.LOSE)
                                _uiState.value = latestState.copy(
                                    gameState = finalState,
                                    showWinDialog = true,
                                    showDrawDialog = false // Ensure draw dialog is cleared
                                )
                            } else if (finalState.isBoardFull()) {
                                soundManager.playSound(SoundManager.SoundType.DRAW)
                                _uiState.value = latestState.copy(
                                    gameState = finalState,
                                    showDrawDialog = true,
                                    showWinDialog = false // Ensure win dialog is cleared
                                )
                            } else {
                                _uiState.value = latestState.copy(
                                    gameState = finalState,
                                    showWinDialog = false, // Ensure dialogs are cleared
                                    showDrawDialog = false
                                )
                            }
                        } else {
                            _uiState.value = currentState.copy(
                                gameState = newState,
                                isAITurn = false
                            )
                            
                            // Check for game end after AI move
                            checkGameEnd(newState)
                        }
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
        
        // Determine which player should move
        val playerToMove = if (currentState.gameMode == GameMode.AI) {
            // In AI mode, only Player.X (human) can click
            if (currentState.isAITurn) return // It's AI's turn, don't allow clicks
            Player.X
        } else {
            // In human vs human mode, check whose turn it is
            if (currentState.isAITurn) {
                // It's Player.O's turn (opponent)
                Player.O
            } else {
                // It's Player.X's turn (current user)
                Player.X
            }
        }
        
        // Don't allow moves if cell is occupied, game is over, or columns are flipping
        val effectiveWinCondition = if (currentState.winCondition > 0 && currentState.winCondition <= currentState.gameState.boardSize) 
            currentState.winCondition 
        else 
            currentState.gameState.boardSize
        
        // Block center during first two moves in hard/flip modes
        val blockCenter = currentState.difficulty == "hard" || currentState.difficulty == "flip"
        if (blockCenter) {
            val moveCount = currentState.gameState.board.count { it != null }
            if (moveCount < 2) { // First two moves of the game
                val center = currentState.gameState.boardSize / 2
                val centerIndex = center * currentState.gameState.boardSize + center
                if (index == centerIndex) {
                    return // Block center move
                }
            }
        }
        
        if (currentState.gameState.board[index] != null || 
            currentState.gameState.isGameOver(effectiveWinCondition) ||
            currentState.isFlippingColumns) {
            return
        }

        // Skip winner check in makeMove if flip mode (we'll check after flipping)
        val skipWinnerCheck = currentState.difficulty == "flip"
        // blockCenter is already declared above
        var newState = currentState.gameState.makeMove(index, playerToMove, skipWinnerCheck = skipWinnerCheck, winCondition = effectiveWinCondition, blockCenter = blockCenter)
        soundManager.playSound(SoundManager.SoundType.CLICK)
        
        // If flip mode, flip columns after player move
        if (currentState.difficulty == "flip") {
            // Calculate which column was played in
            val columnIndex = index % newState.boardSize
            // Determine which columns to show flipping animation for
            val columnsToFlip = if (currentState.flipMode == "single") {
                setOf(columnIndex)
            } else {
                (0 until newState.boardSize).toSet()
            }
            
            _uiState.value = currentState.copy(
                gameState = newState,
                isFlippingColumns = true,
                columnsToFlip = columnsToFlip
            )
            
            // Wait for flip animation, then flip and check winner
            viewModelScope.launch {
                delay(800) // Animation duration
                
                // Re-read state to ensure we have the latest
                val stateBeforeFlip = _uiState.value
                // Only proceed if we're still in flipping state (prevent race conditions)
                if (!stateBeforeFlip.isFlippingColumns || stateBeforeFlip.gameState.boardSize != newState.boardSize) {
                    return@launch
                }
                
                // Flip the current game state based on flip mode
                val flippedState = stateBeforeFlip.gameState.flipColumns(columnIndex, stateBeforeFlip.flipMode)
                
                // Immediately update the UI with the flipped board (before checking winner)
                _uiState.value = stateBeforeFlip.copy(
                    gameState = flippedState,
                    isFlippingColumns = false,
                    columnsToFlip = emptySet()
                )
                
                // Now check for win or draw after flip
                val effectiveWinCondition = if (stateBeforeFlip.winCondition > 0 && stateBeforeFlip.winCondition <= flippedState.boardSize) 
                    stateBeforeFlip.winCondition 
                else 
                    flippedState.boardSize
                val winner = flippedState.checkWinner(winCondition = effectiveWinCondition)
                
                // Check if both players have winning combinations (simultaneous win = draw)
                val isSimultaneousWin = flippedState.hasSimultaneousWin(effectiveWinCondition)
                
                val updatedState = if (winner == Player.X && !isSimultaneousWin) {
                    // Update score with winner
                    flippedState.copy(playerXScore = stateBeforeFlip.gameState.playerXScore + 1)
                } else if (winner == Player.O && !isSimultaneousWin) {
                    flippedState.copy(playerOScore = stateBeforeFlip.gameState.playerOScore + 1)
                } else {
                    flippedState.copy(
                        playerXScore = stateBeforeFlip.gameState.playerXScore,
                        playerOScore = stateBeforeFlip.gameState.playerOScore
                    )
                }
                
                // Get the latest state after the flip update
                val latestState = _uiState.value
                
                val winnerPlayer = if (winner == Player.X) Player.X else if (winner == Player.O) Player.O else null
                
                if (isSimultaneousWin) {
                    // Both players have winning combinations = draw
                    soundManager.playSound(SoundManager.SoundType.DRAW)
                    _uiState.value = latestState.copy(
                        gameState = updatedState,
                        showDrawDialog = true,
                        isAITurn = false
                    )
                } else if (winnerPlayer != null) {
                    soundManager.playSound(if (winnerPlayer == Player.X) SoundManager.SoundType.WIN else SoundManager.SoundType.LOSE)
                    _uiState.value = latestState.copy(
                        gameState = updatedState,
                        showWinDialog = true,
                        showCelebration = winnerPlayer == Player.X,
                        isAITurn = false
                    )
                    if (winnerPlayer == Player.X) {
                        updateHighScore(updatedState.playerXScore)
                    }
                } else if (updatedState.isBoardFull()) {
                    soundManager.playSound(SoundManager.SoundType.DRAW)
                    _uiState.value = latestState.copy(
                        gameState = updatedState,
                        showDrawDialog = true,
                        isAITurn = false
                    )
                } else {
                    // Opponent's turn (AI or human) - ensure flipping is complete
                    if (currentState.gameMode == GameMode.AI) {
                        _uiState.value = latestState.copy(
                            gameState = updatedState,
                            isAITurn = true
                        )
                    } else {
                        // Human vs human - switch to opponent's turn
                        _uiState.value = latestState.copy(
                            gameState = updatedState,
                            isAITurn = !latestState.isAITurn // Toggle turn for human vs human
                        )
                    }
                }
            }
        } else {
            _uiState.value = currentState.copy(gameState = newState)
            
            // Check for win or draw
            val effectiveWinCondition = if (currentState.winCondition > 0 && currentState.winCondition <= newState.boardSize) 
                currentState.winCondition 
            else 
                newState.boardSize
            val winner = newState.checkWinner(winCondition = effectiveWinCondition)
            
            // Check if both players have winning combinations (simultaneous win = draw)
            val isSimultaneousWin = newState.hasSimultaneousWin(effectiveWinCondition)
            
            val winnerPlayer = if (winner == Player.X) Player.X else if (winner == Player.O) Player.O else null
            
            if (isSimultaneousWin) {
                // Both players have winning combinations = draw
                soundManager.playSound(SoundManager.SoundType.DRAW)
                _uiState.value = currentState.copy(
                    gameState = newState,
                    showDrawDialog = true
                )
            } else if (winnerPlayer != null) {
                soundManager.playSound(if (winnerPlayer == Player.X) SoundManager.SoundType.WIN else SoundManager.SoundType.LOSE)
                _uiState.value = currentState.copy(
                    gameState = newState,
                    showWinDialog = true,
                    showCelebration = winnerPlayer == Player.X
                )
                if (winnerPlayer == Player.X) {
                    updateHighScore(newState.playerXScore)
                }
            } else if (newState.isBoardFull()) {
                soundManager.playSound(SoundManager.SoundType.DRAW)
                _uiState.value = currentState.copy(
                    gameState = newState,
                    showDrawDialog = true
                )
            } else {
                // Opponent's turn (AI or human)
                if (currentState.gameMode == GameMode.AI) {
                    _uiState.value = currentState.copy(
                        gameState = newState,
                        isAITurn = true
                    )
                } else {
                    // Human vs human - switch to opponent's turn
                    _uiState.value = currentState.copy(
                        gameState = newState,
                        isAITurn = !currentState.isAITurn // Toggle turn for human vs human
                    )
                }
            }
        }
    }

    private fun checkGameEnd(gameState: GameState) {
        val currentState = _uiState.value
        val effectiveWinCondition = if (currentState.winCondition > 0 && currentState.winCondition <= gameState.boardSize) 
            currentState.winCondition 
        else 
            gameState.boardSize
        val winner = gameState.checkWinner(winCondition = effectiveWinCondition)
        
        // Check if both players have winning combinations (simultaneous win = draw)
        val isSimultaneousWin = gameState.hasSimultaneousWin(effectiveWinCondition)
        
        if (isSimultaneousWin) {
            // Both players have winning combinations = draw
            soundManager.playSound(SoundManager.SoundType.DRAW)
            _uiState.value = currentState.copy(
                gameState = gameState,
                showDrawDialog = true,
                showWinDialog = false // Ensure win dialog is cleared
            )
        } else if (winner == Player.O) {
            soundManager.playSound(SoundManager.SoundType.LOSE)
            _uiState.value = currentState.copy(
                gameState = gameState,
                showWinDialog = true,
                showDrawDialog = false // Ensure draw dialog is cleared
            )
        } else if (gameState.isBoardFull()) {
            soundManager.playSound(SoundManager.SoundType.DRAW)
            _uiState.value = currentState.copy(
                gameState = gameState,
                showDrawDialog = true,
                showWinDialog = false // Ensure win dialog is cleared
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
        val currentState = _uiState.value
        _uiState.value = currentState.copy(
            showWinDialog = false,
            showCelebration = false // Also clear celebration when dismissing
        )
    }

    fun dismissDrawDialog() {
        val currentState = _uiState.value
        _uiState.value = currentState.copy(showDrawDialog = false)
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

    fun setFlipMode(flipMode: String) {
        gamePreferencesManager.saveTicTacToeFlipMode(flipMode)
        _uiState.value = _uiState.value.copy(flipMode = flipMode)
    }

    fun setWinCondition(winCondition: Int) {
        gamePreferencesManager.saveTicTacToeWinCondition(winCondition)
        _uiState.value = _uiState.value.copy(winCondition = winCondition)
        newGame() // Start new game with new win condition
    }

    fun setGameMode(gameMode: GameMode) {
        val currentState = _uiState.value
        val player1Name = "You"
        val player2Name = when (gameMode) {
            GameMode.AI -> "AI"
            GameMode.FAMILY_USER -> currentState.opponentName ?: "Opponent"
            GameMode.LOCAL_PLAYER -> currentState.opponentName ?: "Player 2"
        }
        
        _uiState.value = currentState.copy(
            gameMode = gameMode,
            player1Name = player1Name,
            player2Name = player2Name,
            isAITurn = false
        )
        newGame() // Start new game with new mode
    }

    fun setOpponent(opponentUserId: String?, opponentName: String?) {
        val currentState = _uiState.value
        val player2Name = when (currentState.gameMode) {
            GameMode.AI -> "AI"
            GameMode.FAMILY_USER -> opponentName ?: "Opponent"
            GameMode.LOCAL_PLAYER -> opponentName ?: "Player 2"
        }
        
        _uiState.value = currentState.copy(
            opponentUserId = opponentUserId,
            opponentName = opponentName,
            player2Name = player2Name
        )
        newGame() // Start new game with new opponent
    }

    private fun updateHighScore(newScore: Int) {
        val currentHighScore = _uiState.value.highScore
        if (newScore > currentHighScore) {
            gamePreferencesManager.saveTicTacToeHighScore(newScore)
            _uiState.value = _uiState.value.copy(highScore = newScore)
        }
    }
}
