package com.lostsierra.chorequest.presentation.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostsierra.chorequest.data.local.GamePreferencesManager
import com.lostsierra.chorequest.data.local.SessionManager
import com.lostsierra.chorequest.data.repository.UserRepository
import com.lostsierra.chorequest.data.remote.ChoreQuestApi
import com.lostsierra.chorequest.domain.models.User
import com.lostsierra.chorequest.utils.SoundManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import com.google.gson.JsonObject
import android.util.Log
import java.util.UUID
import javax.inject.Inject

enum class GameMode {
    AI, FAMILY_USER, LOCAL_PLAYER, REMOTE_PLAY
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
    val opponentUserId: String? = null, // For FAMILY_USER or REMOTE_PLAY mode
    val opponentName: String? = null, // For FAMILY_USER, LOCAL_PLAYER, or REMOTE_PLAY mode
    val player1Name: String = "You", // Player X name
    val player2Name: String = "AI", // Player O name
    val winCondition: Int = 0, // 0 = use boardSize, 3-5 = custom win condition (only for hard/flip)
    val remoteGameId: String? = null, // For REMOTE_PLAY mode - unique game ID
    val isWaitingForOpponent: Boolean = false, // For REMOTE_PLAY mode - true when waiting for opponent's move
    val isMyTurn: Boolean = true, // For REMOTE_PLAY mode - true when it's current user's turn
    val lastSyncTime: Long = 0L, // Timestamp of last sync for polling
    val availableGames: List<RemoteGameState> = emptyList(), // In-progress games available to resume
    val showGameSelectionDialog: Boolean = false, // Show dialog to select existing game or start new
    val pendingOpponentUserId: String? = null, // Opponent ID pending game selection
    val pendingOpponentName: String? = null // Opponent name pending game selection
)

// Data model for remote game state
data class RemoteGameState(
    val gameId: String,
    val player1Id: String, // Player X
    val player1Name: String,
    val player2Id: String, // Player O
    val player2Name: String,
    val boardSize: Int,
    val board: List<String?>, // "X", "O", or null
    val currentPlayer: String, // "X" or "O"
    val playerXScore: Int,
    val playerOScore: Int,
    val difficulty: String,
    val winCondition: Int,
    val isGameOver: Boolean,
    val winner: String?, // "X", "O", or null (null = draw or ongoing)
    val createdAt: Long,
    val lastUpdated: Long
)

data class TicTacToeGamesData(
    val games: Map<String, RemoteGameState> = emptyMap()
)

@HiltViewModel
class TicTacToeViewModel @Inject constructor(
    private val gamePreferencesManager: GamePreferencesManager,
    private val soundManager: SoundManager,
    val userRepository: UserRepository,
    val sessionManager: SessionManager,
    private val api: ChoreQuestApi,
    private val gson: Gson,
    private val driveApiService: com.lostsierra.chorequest.data.drive.DriveApiService,
    private val tokenManager: com.lostsierra.chorequest.data.drive.TokenManager
) : ViewModel() {
    
    companion object {
        private const val TAG = "TicTacToeViewModel"
        private const val POLL_INTERVAL_MS = 3000L // Poll every 3 seconds (currently disabled - manual refresh only)
    }
    
    override fun onCleared() {
        super.onCleared()
        // Stop any polling when ViewModel is cleared
        stopPolling()
    }

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
        
        // Handle remote play mode
        if (currentState.gameMode == GameMode.REMOTE_PLAY) {
            if (!currentState.isMyTurn || currentState.isWaitingForOpponent) {
                return // Not your turn
            }
            val session = sessionManager.loadSession() ?: return
            val currentUserId = session.userId
            // Determine which player we are
            val isPlayerX = currentState.opponentUserId?.let { currentUserId < it } ?: true
            val playerToMove = if (isPlayerX) Player.X else Player.O
            
            // Make the move remotely
            makeRemoteMove(index, playerToMove)
            soundManager.playSound(SoundManager.SoundType.CLICK)
            return
        }
        
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
            GameMode.REMOTE_PLAY -> currentState.opponentName ?: "Opponent"
        }
        
        _uiState.value = currentState.copy(
            gameMode = gameMode,
            player1Name = player1Name,
            player2Name = player2Name,
            isAITurn = false,
            isWaitingForOpponent = false,
            isMyTurn = true,
            remoteGameId = null
        )
        newGame() // Start new game with new mode
    }

    fun setOpponent(opponentUserId: String?, opponentName: String?) {
        val currentState = _uiState.value
        val player2Name = when (currentState.gameMode) {
            GameMode.AI -> "AI"
            GameMode.FAMILY_USER -> opponentName ?: "Opponent"
            GameMode.LOCAL_PLAYER -> opponentName ?: "Player 2"
            GameMode.REMOTE_PLAY -> opponentName ?: "Opponent"
        }
        
        if (currentState.gameMode == GameMode.REMOTE_PLAY && opponentUserId != null && opponentName != null) {
            // Check for existing games with this opponent
            viewModelScope.launch {
                val existingGames = getInProgressGamesWithOpponent(opponentUserId)
                if (existingGames.isNotEmpty()) {
                    // Show dialog to select existing game or start new one
                    _uiState.value = currentState.copy(
                        availableGames = existingGames,
                        showGameSelectionDialog = true,
                        pendingOpponentUserId = opponentUserId,
                        pendingOpponentName = opponentName
                    )
                } else {
                    // No existing games, start new one
                    startRemoteGame(opponentUserId, opponentName)
                }
            }
        } else {
            _uiState.value = currentState.copy(
                opponentUserId = opponentUserId,
                opponentName = opponentName,
                player2Name = player2Name
            )
            newGame() // Start new game with new opponent
        }
    }
    
    fun resumeRemoteGame(gameId: String) {
        viewModelScope.launch {
            loadRemoteGameState(gameId)
            // Clear the selection dialog state after loading
            val updatedState = _uiState.value
            _uiState.value = updatedState.copy(
                showGameSelectionDialog = false,
                availableGames = emptyList(),
                pendingOpponentUserId = null,
                pendingOpponentName = null
            )
        }
    }
    
    fun startNewRemoteGame() {
        val currentState = _uiState.value
        val opponentUserId = currentState.pendingOpponentUserId
        val opponentName = currentState.pendingOpponentName
        
        if (opponentUserId != null && opponentName != null) {
            startRemoteGame(opponentUserId, opponentName)
            _uiState.value = currentState.copy(
                showGameSelectionDialog = false,
                availableGames = emptyList(),
                pendingOpponentUserId = null,
                pendingOpponentName = null
            )
        }
    }
    
    fun dismissGameSelectionDialog() {
        val currentState = _uiState.value
        _uiState.value = currentState.copy(
            showGameSelectionDialog = false,
            availableGames = emptyList(),
            pendingOpponentUserId = null,
            pendingOpponentName = null
        )
    }
    
    private suspend fun getInProgressGamesWithOpponent(opponentUserId: String): List<RemoteGameState> {
        return try {
            val session = sessionManager.loadSession() ?: return emptyList()
            val currentUserId = session.userId
            
            // Try direct Drive API first
            val accessToken = tokenManager.getValidAccessToken()
            val folderId = session.driveWorkbookLink
            
            val gamesData = if (accessToken != null && folderId != null) {
                try {
                    Log.d(TAG, "Using direct Drive API to load in-progress games")
                    val fileName = "tic_tac_toe_games.json"
                    
                    val fileId = driveApiService.findFileByName(accessToken, folderId, fileName)
                    if (fileId != null) {
                        val jsonContent = driveApiService.readFileContent(accessToken, fileId)
                        gson.fromJson(jsonContent, TicTacToeGamesData::class.java)
                            ?: TicTacToeGamesData()
                    } else {
                        TicTacToeGamesData()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error using direct Drive API, falling back to Apps Script", e)
                    // Fallback below
                    null
                }
            } else {
                null
            } ?: run {
                // Fallback to Apps Script
                Log.d(TAG, "Falling back to Apps Script to load in-progress games")
                val response = api.getData(
                    path = "data",
                    action = "get",
                    type = "tic_tac_toe_games",
                    familyId = session.familyId
                )
                
                if (response.isSuccessful && response.body()?.success == true) {
                    val dataJson = response.body()?.data as? Map<*, *>
                    if (dataJson != null && dataJson.containsKey("games")) {
                        val gamesMap = dataJson["games"] as? Map<*, *> ?: emptyMap<String, RemoteGameState>()
                        val games = gamesMap.mapValues { (_, value) ->
                            gson.fromJson(gson.toJson(value), RemoteGameState::class.java)
                        } as Map<String, RemoteGameState>
                        TicTacToeGamesData(games = games)
                    } else {
                        TicTacToeGamesData()
                    }
                } else {
                    TicTacToeGamesData()
                }
            }
            
            // Filter games where current user is player1 or player2, opponent is the other player, and game is not over
            gamesData.games.values.filter { game ->
                val isPlayerInGame = game.player1Id == currentUserId || game.player2Id == currentUserId
                val isOpponentInGame = game.player1Id == opponentUserId || game.player2Id == opponentUserId
                val isNotOver = !game.isGameOver
                isPlayerInGame && isOpponentInGame && isNotOver
            }.sortedByDescending { it.lastUpdated } // Most recent first
        } catch (e: Exception) {
            Log.e(TAG, "Error loading in-progress games", e)
            emptyList()
        }
    }

    private fun updateHighScore(newScore: Int) {
        val currentHighScore = _uiState.value.highScore
        if (newScore > currentHighScore) {
            gamePreferencesManager.saveTicTacToeHighScore(newScore)
            _uiState.value = _uiState.value.copy(highScore = newScore)
        }
    }

    // Remote play methods
    fun startRemoteGame(opponentUserId: String, opponentName: String) {
        val session = sessionManager.loadSession() ?: return
        val currentUserId = session.userId
        
        val gameId = UUID.randomUUID().toString()
        val currentState = _uiState.value
        
        // Determine which player is X and which is O (alphabetically by user ID)
        val isPlayerX = currentUserId < opponentUserId
        val player1Id = if (isPlayerX) currentUserId else opponentUserId
        val player1Name = if (isPlayerX) "You" else opponentName
        val player2Id = if (isPlayerX) opponentUserId else currentUserId
        val player2Name = if (isPlayerX) opponentName else "You"
        
        val boardSize = getBoardSize(currentState.difficulty)
        val remoteGameState = RemoteGameState(
            gameId = gameId,
            player1Id = player1Id,
            player1Name = player1Name,
            player2Id = player2Id,
            player2Name = player2Name,
            boardSize = boardSize,
            board = List(boardSize * boardSize) { null },
            currentPlayer = "X", // X always starts
            playerXScore = 0,
            playerOScore = 0,
            difficulty = currentState.difficulty,
            winCondition = if (currentState.winCondition > 0) currentState.winCondition else boardSize,
            isGameOver = false,
            winner = null,
            createdAt = System.currentTimeMillis(),
            lastUpdated = System.currentTimeMillis()
        )
        
        _uiState.value = currentState.copy(
            gameMode = GameMode.REMOTE_PLAY,
            opponentUserId = opponentUserId,
            opponentName = opponentName,
            remoteGameId = gameId,
            isMyTurn = isPlayerX, // X starts, so if we're X, it's our turn
            isWaitingForOpponent = !isPlayerX,
            player1Name = player1Name,
            player2Name = player2Name,
            gameState = GameState(
                boardSize = boardSize,
                board = arrayOfNulls(boardSize * boardSize),
                currentPlayer = Player.X,
                playerXScore = 0,
                playerOScore = 0
            ),
            lastSyncTime = System.currentTimeMillis()
        )
        
        saveRemoteGameState(remoteGameState)
        
        // Don't start automatic polling - user must click refresh to check for opponent moves
    }

    fun refreshRemoteGame() {
        val currentState = _uiState.value
        val gameId = currentState.remoteGameId ?: return
        
        viewModelScope.launch {
            try {
                loadRemoteGameState(gameId)
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing remote game", e)
            }
        }
    }

    private fun saveRemoteGameState(remoteGameState: RemoteGameState) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val session = sessionManager.loadSession() ?: return@launch
                
                // Try direct Drive API first
                val accessToken = tokenManager.getValidAccessToken()
                val folderId = session.driveWorkbookLink
                
                if (accessToken != null && folderId != null) {
                    try {
                        Log.d(TAG, "Using direct Drive API to save remote game state")
                        val fileName = "tic_tac_toe_games.json"
                        
                        // Read existing games
                        val fileId = driveApiService.findFileByName(accessToken, folderId, fileName)
                        val gamesData = if (fileId != null) {
                            val jsonContent = driveApiService.readFileContent(accessToken, fileId)
                            gson.fromJson(jsonContent, TicTacToeGamesData::class.java)
                                ?: TicTacToeGamesData()
                        } else {
                            TicTacToeGamesData()
                        }
                        
                        // Update or add the game
                        val updatedGames = gamesData.games.toMutableMap()
                        updatedGames[remoteGameState.gameId] = remoteGameState.copy(
                            lastUpdated = System.currentTimeMillis()
                        )
                        
                        val updatedData = TicTacToeGamesData(games = updatedGames)
                        val jsonContent = gson.toJson(updatedData)
                        
                        // Write back to Drive
                        driveApiService.writeFileContent(accessToken, folderId, fileName, jsonContent)
                        
                        Log.d(TAG, "Remote game state saved successfully via Drive API")
                        _uiState.value = _uiState.value.copy(lastSyncTime = System.currentTimeMillis())
                        return@launch
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Error using direct Drive API, falling back to Apps Script", e)
                    }
                } else {
                    Log.d(TAG, "No access token or folder ID available, using Apps Script")
                }
                
                // Fallback to Apps Script
                Log.d(TAG, "Falling back to Apps Script to save remote game state")
                val response = api.getData(
                    path = "data",
                    action = "get",
                    type = "tic_tac_toe_games",
                    familyId = session.familyId
                )
                
                val gamesData = if (response.isSuccessful && response.body()?.success == true) {
                    val dataJson = response.body()?.data as? Map<*, *>
                    if (dataJson != null && dataJson.containsKey("games")) {
                        val gamesMap = dataJson["games"] as? Map<*, *> ?: emptyMap<String, RemoteGameState>()
                        val games = gamesMap.mapValues { (_, value) ->
                            gson.fromJson(gson.toJson(value), RemoteGameState::class.java)
                        } as Map<String, RemoteGameState>
                        TicTacToeGamesData(games = games)
                    } else {
                        TicTacToeGamesData()
                    }
                } else {
                    TicTacToeGamesData()
                }
                
                // Update or add the game
                val updatedGames = gamesData.games.toMutableMap()
                updatedGames[remoteGameState.gameId] = remoteGameState.copy(
                    lastUpdated = System.currentTimeMillis()
                )
                
                val updatedData = TicTacToeGamesData(games = updatedGames)
                val saveResponse = api.saveData(
                    path = "data",
                    action = "save",
                    type = "tic_tac_toe_games",
                    data = gson.toJsonTree(updatedData).asJsonObject.apply {
                        addProperty("userId", session.userId)
                    }
                )
                
                if (saveResponse.isSuccessful) {
                    Log.d(TAG, "Remote game state saved successfully via Apps Script")
                    _uiState.value = _uiState.value.copy(lastSyncTime = System.currentTimeMillis())
                } else {
                    Log.e(TAG, "Failed to save remote game state: ${saveResponse.message()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving remote game state", e)
            }
        }
    }

    private var isLoadingGameState = false
    
    private suspend fun loadRemoteGameState(gameId: String) {
        // Prevent concurrent loads
        if (isLoadingGameState) {
            Log.d(TAG, "Already loading game state, skipping duplicate call")
            return
        }
        
        isLoadingGameState = true
        try {
            val session = sessionManager.loadSession() ?: return
            
            // Try direct Drive API first
            val accessToken = tokenManager.getValidAccessToken()
            val folderId = session.driveWorkbookLink
            
            if (accessToken != null && folderId != null) {
                try {
                    Log.d(TAG, "Using direct Drive API to load remote game state")
                    val fileName = "tic_tac_toe_games.json"
                    
                    val fileId = driveApiService.findFileByName(accessToken, folderId, fileName)
                    if (fileId != null) {
                        val jsonContent = driveApiService.readFileContent(accessToken, fileId)
                        val gamesData = gson.fromJson(jsonContent, TicTacToeGamesData::class.java)
                            ?: TicTacToeGamesData()
                        
                        val remoteGameState = gamesData.games[gameId]
                        if (remoteGameState != null) {
                            applyRemoteGameState(remoteGameState)
                            Log.d(TAG, "Remote game state loaded successfully via Drive API")
                            return
                        } else {
                            Log.w(TAG, "Game $gameId not found in games data")
                        }
                    } else {
                        Log.w(TAG, "tic_tac_toe_games.json not found in Drive")
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error using direct Drive API, falling back to Apps Script", e)
                }
            } else {
                Log.d(TAG, "No access token or folder ID available, using Apps Script")
            }
            
            // Fallback to Apps Script
            Log.d(TAG, "Falling back to Apps Script to load remote game state")
            val response = api.getData(
                path = "data",
                action = "get",
                type = "tic_tac_toe_games",
                familyId = session.familyId
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                val dataJson = response.body()?.data as? Map<*, *>
                if (dataJson != null && dataJson.containsKey("games")) {
                    val gamesMap = dataJson["games"] as? Map<*, *> ?: emptyMap<String, RemoteGameState>()
                    val gameJson = gamesMap[gameId] ?: return
                    
                    val remoteGameState = gson.fromJson(gson.toJson(gameJson), RemoteGameState::class.java)
                    applyRemoteGameState(remoteGameState)
                    Log.d(TAG, "Remote game state loaded successfully via Apps Script")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading remote game state", e)
        } finally {
            isLoadingGameState = false
        }
    }

    private fun applyRemoteGameState(remoteGameState: RemoteGameState) {
        val session = sessionManager.loadSession() ?: return
        val currentUserId = session.userId
        
        val isPlayerX = remoteGameState.player1Id == currentUserId
        val myPlayer = if (isPlayerX) "X" else "O"
        val isMyTurn = remoteGameState.currentPlayer == myPlayer && !remoteGameState.isGameOver
        
        // Convert board from List<String?> to Array<Player?>
        val board = Array(remoteGameState.boardSize * remoteGameState.boardSize) { index ->
            when (remoteGameState.board.getOrNull(index)) {
                "X" -> Player.X
                "O" -> Player.O
                else -> null
            }
        }
        
        Log.d(TAG, "Applying remote game state - gameId: ${remoteGameState.gameId}, boardSize: ${remoteGameState.boardSize}, moves: ${board.count { it != null }}, board: ${board.contentToString()}")
        
        val gameState = GameState(
            boardSize = remoteGameState.boardSize,
            board = board,
            currentPlayer = if (remoteGameState.currentPlayer == "X") Player.X else Player.O,
            playerXScore = remoteGameState.playerXScore,
            playerOScore = remoteGameState.playerOScore
        )
        
        val effectiveWinCondition = if (remoteGameState.winCondition > 0 && remoteGameState.winCondition <= remoteGameState.boardSize) 
            remoteGameState.winCondition 
        else 
            remoteGameState.boardSize
        
        val winner = when (remoteGameState.winner) {
            "X" -> Player.X
            "O" -> Player.O
            else -> null
        }
        
        val showWinDialog = remoteGameState.isGameOver && winner != null
        val showDrawDialog = remoteGameState.isGameOver && winner == null
        
        // Determine opponent info
        val opponentUserId = if (isPlayerX) remoteGameState.player2Id else remoteGameState.player1Id
        val opponentName = if (isPlayerX) remoteGameState.player2Name else remoteGameState.player1Name
        
        _uiState.value = _uiState.value.copy(
            gameMode = GameMode.REMOTE_PLAY,
            remoteGameId = remoteGameState.gameId,
            opponentUserId = opponentUserId,
            opponentName = opponentName,
            gameState = gameState,
            isMyTurn = isMyTurn,
            isWaitingForOpponent = !isMyTurn && !remoteGameState.isGameOver,
            showWinDialog = showWinDialog,
            showDrawDialog = showDrawDialog,
            showCelebration = showWinDialog && winner == (if (isPlayerX) Player.X else Player.O),
            difficulty = remoteGameState.difficulty,
            winCondition = remoteGameState.winCondition,
            player1Name = remoteGameState.player1Name,
            player2Name = remoteGameState.player2Name,
            lastSyncTime = System.currentTimeMillis()
        )
        
        // Stop any automatic polling - user must manually refresh
        stopPolling()
    }

    private var pollingJob: kotlinx.coroutines.Job? = null
    
    // Polling is disabled - user must manually refresh
    // Keeping function for potential future use
    @Suppress("UNUSED")
    private fun startPollingForOpponentMove() {
        // Polling disabled - user must manually refresh
    }
    
    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun makeRemoteMove(index: Int, player: Player) {
        val currentState = _uiState.value
        val gameId = currentState.remoteGameId ?: return
        val session = sessionManager.loadSession() ?: return
        
        // Make the move locally first
        val effectiveWinCondition = if (currentState.winCondition > 0 && currentState.winCondition <= currentState.gameState.boardSize) 
            currentState.winCondition 
        else 
            currentState.gameState.boardSize
        
        val newState = currentState.gameState.makeMove(index, player, skipWinnerCheck = false, winCondition = effectiveWinCondition)
        val winner = newState.checkWinner(winCondition = effectiveWinCondition)
        val isGameOver = winner != null || newState.isBoardFull()
        
        // Convert to remote game state
        val board = newState.board.map { p -> when (p) {
            Player.X -> "X"
            Player.O -> "O"
            null -> null
        }}
        
        // Determine player IDs (alphabetically, same as in startRemoteGame)
        val currentUserId = session.userId
        val opponentUserId = currentState.opponentUserId ?: return
        val isPlayerX = currentUserId < opponentUserId
        val player1Id = if (isPlayerX) currentUserId else opponentUserId
        val player2Id = if (isPlayerX) opponentUserId else currentUserId
        
        val remoteGameState = RemoteGameState(
            gameId = gameId,
            player1Id = player1Id,
            player1Name = currentState.player1Name,
            player2Id = player2Id,
            player2Name = currentState.player2Name,
            boardSize = newState.boardSize,
            board = board,
            currentPlayer = if (newState.currentPlayer == Player.X) "X" else "O",
            playerXScore = newState.playerXScore,
            playerOScore = newState.playerOScore,
            difficulty = currentState.difficulty,
            winCondition = effectiveWinCondition,
            isGameOver = isGameOver,
            winner = when (winner) {
                Player.X -> "X"
                Player.O -> "O"
                null -> null
            },
            createdAt = currentState.lastSyncTime,
            lastUpdated = System.currentTimeMillis()
        )
        
        saveRemoteGameState(remoteGameState)
        
        // Update local state
        _uiState.value = currentState.copy(
            gameState = newState,
            isMyTurn = false,
            isWaitingForOpponent = !isGameOver,
            showWinDialog = isGameOver && winner != null,
            showDrawDialog = isGameOver && winner == null,
            showCelebration = isGameOver && winner == Player.X
        )
        
        // Don't start automatic polling - user must click refresh to check for opponent moves
    }
}
