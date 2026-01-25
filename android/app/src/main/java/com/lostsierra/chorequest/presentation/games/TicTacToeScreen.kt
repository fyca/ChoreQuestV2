package com.lostsierra.chorequest.presentation.games

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Divider
import androidx.compose.material3.*
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lostsierra.chorequest.data.local.GamePreferencesManager
import com.lostsierra.chorequest.presentation.components.CelebrationAnimation
import com.lostsierra.chorequest.presentation.components.CelebrationStyle
import com.lostsierra.chorequest.presentation.components.ChoreQuestTopAppBar
import com.lostsierra.chorequest.utils.SoundManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext

/**
 * Tic-Tac-Toe game screen with difficulty levels, sound effects, high scores, and celebrations
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicTacToeScreen(
    onNavigateBack: () -> Unit,
    viewModel: TicTacToeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDifficultyDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showGameModeDialog by remember { mutableStateOf(false) }
    var showOpponentDialog by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    
    // Track if we've initialized to prevent multiple calls
    var hasInitialized by remember { mutableStateOf(false) }
    
    // Clear any existing remote game state and show active games dialog on screen open
    // Only auto-load if there's a pending game ID from a notification
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (hasInitialized) return@LaunchedEffect
        hasInitialized = true
        
        // Always clear any existing remote game state first to prevent auto-loading
        // This ensures we don't auto-load games from previous sessions
        viewModel.clearRemoteGameState()
        
        // Small delay to ensure state is cleared before checking pending game ID
        kotlinx.coroutines.delay(50)
        
        val gameId = viewModel.getPendingGameId()
        
        // If there's a pending game ID from notification, load it directly
        if (gameId != null) {
            viewModel.resumeRemoteGame(gameId)
            viewModel.clearPendingGameId()
        } else {
            // Otherwise, show the active games dialog
            viewModel.loadAllActiveGames()
        }
    }

    // Celebration animation
    if (uiState.showCelebration) {
        CelebrationAnimation(
            style = CelebrationStyle.FIREWORKS,
            pointsEarned = 0, // Games don't earn points, just for fun
            onAnimationComplete = {
                viewModel.onCelebrationComplete()
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                ChoreQuestTopAppBar(
                    title = "⭕ Tic-Tac-Toe",
                    onNavigateBack = onNavigateBack,
                    actions = {
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Game Mode and Difficulty Card
                GameModeAndDifficultyCard(
                    gameMode = uiState.gameMode,
                    opponentName = uiState.opponentName,
                    difficulty = uiState.difficulty,
                    highScore = uiState.highScore,
                    onGameModeClick = { showGameModeDialog = true },
                    onDifficultyClick = { showDifficultyDialog = true }
                )

                // Game status
                GameStatusCard(
                    currentPlayer = uiState.gameState.currentPlayer,
                    playerXScore = uiState.gameState.playerXScore,
                    playerOScore = uiState.gameState.playerOScore,
                    isAITurn = uiState.isAITurn,
                    player1Name = uiState.player1Name,
                    player2Name = uiState.player2Name,
                    gameMode = uiState.gameMode,
                    uiState = uiState
                )

                // Game board
                val blockCenter = uiState.difficulty == "hard" || uiState.difficulty == "flip"
                val moveCount = uiState.gameState.board.count { it != null }
                val isCenterBlocked = blockCenter && moveCount < 2
                GameBoard(
                    board = uiState.gameState.board,
                    boardSize = uiState.gameState.boardSize,
                    isFlippingColumns = uiState.isFlippingColumns,
                    columnsToFlip = uiState.columnsToFlip,
                    onCellClick = { index ->
                        viewModel.onCellClick(index)
                    },
                    isCenterBlocked = isCenterBlocked
                )

                // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (uiState.gameMode == GameMode.REMOTE_PLAY && uiState.isWaitingForOpponent) {
                    Button(
                        onClick = { viewModel.refreshRemoteGame() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Refresh")
                    }
                } else {
                    Button(
                        onClick = { viewModel.newGame() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("New Game")
                    }
                }

                Button(
                    onClick = { viewModel.resetScore() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset Score")
                }
            }
            }
        }
        
        // Win dialog - positioned at top
        if (uiState.showWinDialog) {
            // Use the correct win condition to determine winner
            val effectiveWinCondition = if (uiState.winCondition > 0 && uiState.winCondition <= uiState.gameState.boardSize) 
                uiState.winCondition 
            else 
                uiState.gameState.boardSize
            val winner = uiState.gameState.checkWinner(winCondition = effectiveWinCondition)
            val winnerName = if (winner == Player.X) uiState.player1Name else uiState.player2Name
            
            // Determine message based on game mode
            val message = when (uiState.gameMode) {
                GameMode.REMOTE_PLAY -> {
                    // In remote play, determine if current user won
                    // Get current user's name from session to compare with winner
                    val currentUserName = viewModel.sessionManager.loadSession()?.userName
                    val currentUserWon = when {
                        currentUserName == null -> uiState.showCelebration // Fallback to showCelebration if we can't get name
                        winner == Player.X -> currentUserName == uiState.player1Name
                        winner == Player.O -> currentUserName == uiState.player2Name
                        else -> false
                    }
                    
                    if (currentUserWon) {
                        "Great job! You won!"
                    } else {
                        "$winnerName won! Better luck next time!"
                    }
                }
                GameMode.AI -> {
                    if (winner == Player.X) 
                        "Great job! You won!" 
                    else 
                        "${uiState.player2Name} won! Better luck next time!"
                }
                GameMode.FAMILY_USER, GameMode.LOCAL_PLAYER -> {
                    if (winner == Player.X) 
                        "Great job! ${uiState.player1Name} won!" 
                    else 
                        "Great job! ${uiState.player2Name} won!"
                }
            }
            
            WinDrawDialog(
                title = "🎉 $winnerName Wins! 🎉",
                message = message,
                onDismiss = { 
                    viewModel.dismissWinDialog()
                    viewModel.newGame()
                },
                onPlayAgain = { viewModel.newGame() },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp) // Below the top bar
            )
        }

        // Draw dialog - positioned at top
        if (uiState.showDrawDialog) {
            WinDrawDialog(
                title = "🤝 It's a Draw!",
                message = "Good game! Try again!",
                onDismiss = { 
                    viewModel.dismissDrawDialog()
                    viewModel.newGame()
                },
                onPlayAgain = { viewModel.newGame() },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp) // Below the top bar
            )
        }
    }

    // Difficulty selection dialog
    if (showDifficultyDialog) {
        DifficultySelectionDialog(
            currentDifficulty = uiState.difficulty,
            onDifficultySelected = { difficulty ->
                viewModel.setDifficulty(difficulty)
                showDifficultyDialog = false
            },
            onDismiss = { showDifficultyDialog = false }
        )
    }

    // Settings dialog
    if (showSettingsDialog) {
        GameSettingsDialog(
            soundEnabled = uiState.soundEnabled,
            onSoundToggled = { enabled ->
                viewModel.setSoundEnabled(enabled)
            },
            flipMode = uiState.flipMode,
            onFlipModeChanged = { flipMode ->
                viewModel.setFlipMode(flipMode)
            },
            showFlipModeOption = uiState.difficulty == "flip",
            winCondition = uiState.winCondition,
            onWinConditionChanged = { winCondition ->
                viewModel.setWinCondition(winCondition)
            },
            showWinConditionOption = uiState.difficulty == "hard" || uiState.difficulty == "flip",
            boardSize = uiState.gameState.boardSize,
            onDismiss = { showSettingsDialog = false }
        )
    }

    // Game mode selection dialog
    if (showGameModeDialog) {
        GameModeSelectionDialog(
            currentGameMode = uiState.gameMode,
            onGameModeSelected = { gameMode ->
                viewModel.setGameMode(gameMode)
                showGameModeDialog = false
                if (gameMode == GameMode.FAMILY_USER || gameMode == GameMode.LOCAL_PLAYER || gameMode == GameMode.REMOTE_PLAY) {
                    showOpponentDialog = true
                }
            },
            onDismiss = { showGameModeDialog = false }
        )
    }

    // Opponent selection dialog
    if (showOpponentDialog) {
        OpponentSelectionDialog(
            gameMode = uiState.gameMode,
            currentOpponentId = uiState.opponentUserId,
            currentOpponentName = uiState.opponentName,
            onOpponentSelected = { userId, name ->
                viewModel.setOpponent(userId, name)
                showOpponentDialog = false
            },
            onDismiss = { showOpponentDialog = false },
            viewModel = viewModel
        )
    }

    // Game selection dialog (for remote play - shows existing games or option to start new)
    if (uiState.showGameSelectionDialog) {
        RemoteGameSelectionDialog(
            availableGames = uiState.availableGames,
            opponentName = uiState.pendingOpponentName ?: "Opponent",
            onGameSelected = { gameId ->
                viewModel.resumeRemoteGame(gameId)
            },
            onStartNewGame = {
                viewModel.startNewRemoteGame()
            },
            onDismiss = {
                viewModel.dismissGameSelectionDialog()
            },
            viewModel = viewModel
        )
    }
    
    // All active games dialog (shown when screen opens)
    if (uiState.showAllGamesDialog) {
        AllActiveGamesDialog(
            availableGames = uiState.availableGames,
            onGameSelected = { gameId ->
                viewModel.resumeRemoteGame(gameId)
                viewModel.dismissAllGamesDialog()
            },
            onStartNewGame = {
                viewModel.dismissAllGamesDialog()
                showGameModeDialog = true
            },
            onDismiss = {
                viewModel.dismissAllGamesDialog()
            },
            viewModel = viewModel
        )
    }
}

@Composable
private fun WinDrawDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onPlayAgain,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Play Again")
                }
                
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun GameModeAndDifficultyCard(
    gameMode: GameMode,
    opponentName: String?,
    difficulty: String,
    highScore: Int,
    onGameModeClick: () -> Unit,
    onDifficultyClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Game Mode",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(onClick = onGameModeClick)
                    ) {
                        Text(
                            text = when (gameMode) {
                                GameMode.AI -> "vs AI"
                                GameMode.FAMILY_USER -> opponentName?.let { "vs $it" } ?: "vs Family"
                                GameMode.LOCAL_PLAYER -> opponentName?.let { "vs $it" } ?: "vs Player 2"
                                GameMode.REMOTE_PLAY -> opponentName?.let { "vs $it (Remote)" } ?: "vs Opponent (Remote)"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "Change game mode",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "High Score",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = highScore.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = "Difficulty: ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onDifficultyClick)
                ) {
                    Text(
                        text = difficulty.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "Change difficulty",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DifficultySelectionDialog(
    currentDifficulty: String,
    onDifficultySelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Difficulty") },
        text = {
            Column {
                DifficultyOption(
                    label = "Easy",
                    description = "3x3 board - AI makes random moves - Easy to win!",
                    isSelected = currentDifficulty == "easy",
                    onClick = { onDifficultySelected("easy") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                DifficultyOption(
                    label = "Medium",
                    description = "4x4 board - AI makes some mistakes - Fair challenge",
                    isSelected = currentDifficulty == "medium",
                    onClick = { onDifficultySelected("medium") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                DifficultyOption(
                    label = "Hard",
                    description = "5x5 board - AI is unbeatable - Best you can do is draw!",
                    isSelected = currentDifficulty == "hard",
                    onClick = { onDifficultySelected("hard") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                DifficultyOption(
                    label = "FLIP",
                    description = "5x5 board - Columns flip randomly after each turn!",
                    isSelected = currentDifficulty == "flip",
                    onClick = { onDifficultySelected("flip") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DifficultyOption(
    label: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun GameSettingsDialog(
    soundEnabled: Boolean,
    onSoundToggled: (Boolean) -> Unit,
    flipMode: String,
    onFlipModeChanged: (String) -> Unit,
    showFlipModeOption: Boolean,
    winCondition: Int,
    onWinConditionChanged: (Int) -> Unit,
    showWinConditionOption: Boolean,
    boardSize: Int,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Game Settings") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sound Effects")
                    Switch(
                        checked = soundEnabled,
                        onCheckedChange = onSoundToggled
                    )
                }
                
                if (showFlipModeOption) {
                    Divider()
                    Column {
                        Text(
                            text = "Flip Mode",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FlipModeOption(
                                label = "Single Column",
                                description = "Only the played column flips",
                                isSelected = flipMode == "single",
                                onClick = { onFlipModeChanged("single") },
                                modifier = Modifier.weight(1f)
                            )
                            FlipModeOption(
                                label = "Entire Board",
                                description = "All columns flip randomly",
                                isSelected = flipMode == "entire",
                                onClick = { onFlipModeChanged("entire") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                
                if (showWinConditionOption) {
                    Divider()
                    Column {
                        Text(
                            text = "Win Condition",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = "How many in a row to win (default: $boardSize)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (i in 3..5) {
                                WinConditionOption(
                                    value = i,
                                    isSelected = winCondition == i,
                                    onClick = { onWinConditionChanged(i) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        if (winCondition == 0) {
                            Text(
                                text = "Using default: $boardSize in a row",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun FlipModeOption(
    label: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun WinConditionOption(
    value: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$value",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun GameStatusCard(
    currentPlayer: Player,
    playerXScore: Int,
    playerOScore: Int,
    isAITurn: Boolean,
    player1Name: String,
    player2Name: String,
    gameMode: GameMode,
    uiState: TicTacToeUiState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when {
                    gameMode == GameMode.REMOTE_PLAY -> {
                        val currentState = uiState
                        val opponentName = currentState.opponentName ?: player2Name
                        
                        // Determine which player's turn it is based on currentPlayer
                        val turnPlayerName = if (currentPlayer == Player.X) player1Name else player2Name
                        
                        when {
                            currentState.showWinDialog || currentState.showDrawDialog -> {
                                "Game Over"
                            }
                            currentState.isMyTurn -> {
                                "✅ Your turn!"
                            }
                            currentState.isWaitingForOpponent -> {
                                "⏳ Waiting for $opponentName's move..."
                            }
                            else -> {
                                // Show whose turn it is
                                "$turnPlayerName's turn!"
                            }
                        }
                    }
                    isAITurn && gameMode == GameMode.AI -> "🤖 AI is thinking..."
                    isAITurn -> "$player2Name's turn!"
                    else -> "$player1Name's turn!"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ScoreDisplay(label = "$player1Name (X)", score = playerXScore, isActive = currentPlayer == Player.X && !isAITurn)
                Text("VS", style = MaterialTheme.typography.bodyLarge)
                ScoreDisplay(label = "$player2Name (O)", score = playerOScore, isActive = currentPlayer == Player.O && isAITurn)
            }
        }
    }
}

@Composable
private fun ScoreDisplay(
    label: String,
    score: Int,
    isActive: Boolean
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ), label = "scoreScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.scale(scale)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = score.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun GameBoard(
    board: Array<Player?>,
    boardSize: Int,
    isFlippingColumns: Boolean,
    columnsToFlip: Set<Int>,
    onCellClick: (Int) -> Unit,
    isCenterBlocked: Boolean = false
) {
    // Adjust board size based on boardSize (3x3 = 300dp, 4x4 = 360dp, 5x5 = 420dp)
    val boardDimension = when (boardSize) {
        3 -> 300.dp
        4 -> 360.dp
        5 -> 420.dp
        else -> 300.dp
    }
    
    Card(
        modifier = Modifier.size(boardDimension),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(boardSize) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(boardSize) { col ->
                        val index = row * boardSize + col
                        val center = boardSize / 2
                        val centerIndex = center * boardSize + center
                        val isBlocked = isCenterBlocked && index == centerIndex
                        GameCell(
                            player = board.getOrNull(index),
                            onClick = { onCellClick(index) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            boardSize = boardSize,
                            isBlocked = isBlocked
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GameCell(
    player: Player?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    boardSize: Int = 3,
    isBlocked: Boolean = false
) {
    val scale by animateFloatAsState(
        targetValue = if (player != null) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ), label = "cellScale"
    )

    // Adjust font size based on board size
    val fontSize = when (boardSize) {
        3 -> 48.sp
        4 -> 36.sp
        5 -> 28.sp
        else -> 48.sp
    }

    Card(
        modifier = modifier
            .then(
                if (isBlocked) {
                    Modifier // Don't make it clickable if blocked
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            )
            .scale(scale),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isBlocked) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (player != null) 4.dp else if (isBlocked) 1.dp else 2.dp
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when (player) {
                Player.X -> {
                    Text(
                        text = "✕",
                        fontSize = fontSize,
                        color = Color(0xFF2196F3),
                        fontWeight = FontWeight.Bold
                    )
                }
                Player.O -> {
                    Text(
                        text = "○",
                        fontSize = fontSize,
                        color = Color(0xFFF44336),
                        fontWeight = FontWeight.Bold
                    )
                }
                null -> {
                    // Empty cell - show blocked indicator if blocked
                    if (isBlocked) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = "Blocked",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Locked",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    } else {
                        Text(
                            text = "",
                            fontSize = fontSize
                        )
                    }
                }
            }
        }
    }
}

// Game logic
enum class Player {
    X, O
}

enum class Difficulty {
    EASY, MEDIUM, HARD, FLIP
}

// Helper function to get board size from difficulty
fun getBoardSize(difficulty: Difficulty): Int {
    return when (difficulty) {
        Difficulty.EASY -> 3
        Difficulty.MEDIUM -> 4
        Difficulty.HARD -> 5
        Difficulty.FLIP -> 5
    }
}

fun getBoardSize(difficulty: String): Int {
    return when (difficulty.lowercase()) {
        "easy" -> 3
        "medium" -> 4
        "hard" -> 5
        "flip" -> 5
        else -> 3
    }
}

data class GameState(
    val boardSize: Int = 3,
    val board: Array<Player?> = arrayOfNulls(boardSize * boardSize),
    val currentPlayer: Player = Player.X,
    val playerXScore: Int = 0,
    val playerOScore: Int = 0
) {
    fun makeMove(index: Int, player: Player, skipWinnerCheck: Boolean = false, winCondition: Int = 0, blockCenter: Boolean = false): GameState {
        val boardSizeSquared = boardSize * boardSize
        if (board[index] != null || index < 0 || index >= boardSizeSquared) {
            return this
        }

        // Block center during first two moves if requested (for hard/flip modes)
        if (blockCenter) {
            val moveCount = board.count { it != null }
            if (moveCount < 2) { // First two moves of the game
                val center = boardSize / 2
                val centerIndex = center * boardSize + center
                if (index == centerIndex) {
                    return this // Block center move
                }
            }
        }

        val newBoard = board.copyOf()
        newBoard[index] = player

        // Only check winner if not skipping (for flip mode, we check after flipping)
        val effectiveWinCondition = if (winCondition > 0 && winCondition <= boardSize) winCondition else boardSize
        val winner = if (!skipWinnerCheck) checkWinner(newBoard, effectiveWinCondition) else null
        val newPlayerXScore = if (winner == Player.X) playerXScore + 1 else playerXScore
        val newPlayerOScore = if (winner == Player.O) playerOScore + 1 else playerOScore

        return copy(
            board = newBoard,
            currentPlayer = if (player == Player.X) Player.O else Player.X,
            playerXScore = newPlayerXScore,
            playerOScore = newPlayerOScore
        )
    }

    fun checkWinner(board: Array<Player?> = this.board, winCondition: Int = boardSize): Player? {
        val requiredInRow = if (winCondition > 0 && winCondition <= boardSize) winCondition else boardSize
        
        var xWins = false
        var oWins = false
        
        // Helper function to check if a sequence contains a winning line
        fun checkSequence(sequence: List<Player?>): Boolean {
            if (sequence.size < requiredInRow) return false
            for (start in 0..(sequence.size - requiredInRow)) {
                val first = sequence[start]
                if (first != null) {
                    var allMatch = true
                    for (offset in 1 until requiredInRow) {
                        if (sequence[start + offset] != first) {
                            allMatch = false
                            break
                        }
                    }
                    if (allMatch) {
                        if (first == Player.X) xWins = true
                        if (first == Player.O) oWins = true
                        return true
                    }
                }
            }
            return false
        }
        
        // Check rows
        for (row in 0 until boardSize) {
            val rowSequence = (0 until boardSize).map { board[row * boardSize + it] }
            checkSequence(rowSequence)
        }

        // Check columns
        for (col in 0 until boardSize) {
            val colSequence = (0 until boardSize).map { board[it * boardSize + col] }
            checkSequence(colSequence)
        }

        // Check main diagonals (top-left to bottom-right)
        for (rowStart in 0..(boardSize - requiredInRow)) {
            for (colStart in 0..(boardSize - requiredInRow)) {
                val diagSequence = (0 until requiredInRow).map {
                    val idx = (rowStart + it) * boardSize + (colStart + it)
                    board[idx]
                }
                checkSequence(diagSequence)
            }
        }

        // Check anti-diagonals (top-right to bottom-left)
        for (rowStart in 0..(boardSize - requiredInRow)) {
            for (colStart in (requiredInRow - 1) until boardSize) {
                val diagSequence = (0 until requiredInRow).map {
                    val idx = (rowStart + it) * boardSize + (colStart - it)
                    board[idx]
                }
                checkSequence(diagSequence)
            }
        }

        // If both players have winning combinations, it's a draw (return null)
        if (xWins && oWins) {
            return null // Both win = draw
        }
        
        // Return the winner if only one has a winning combination
        if (xWins) return Player.X
        if (oWins) return Player.O
        
        return null
    }
    
    // Helper function to check if both players have winning combinations
    fun hasSimultaneousWin(winCondition: Int = boardSize): Boolean {
        // Use the same logic as checkWinner but check for both players
        val requiredInRow = if (winCondition > 0 && winCondition <= boardSize) winCondition else boardSize
        
        var xWins = false
        var oWins = false
        
        // Helper function to check if a sequence contains a winning line
        fun checkSequence(sequence: List<Player?>): Boolean {
            if (sequence.size < requiredInRow) return false
            for (start in 0..(sequence.size - requiredInRow)) {
                val first = sequence[start]
                if (first != null) {
                    var allMatch = true
                    for (offset in 1 until requiredInRow) {
                        if (sequence[start + offset] != first) {
                            allMatch = false
                            break
                        }
                    }
                    if (allMatch) {
                        if (first == Player.X) xWins = true
                        if (first == Player.O) oWins = true
                        return true
                    }
                }
            }
            return false
        }
        
        // Check rows
        for (row in 0 until boardSize) {
            val rowSequence = (0 until boardSize).map { board[row * boardSize + it] }
            checkSequence(rowSequence)
        }

        // Check columns
        for (col in 0 until boardSize) {
            val colSequence = (0 until boardSize).map { board[it * boardSize + col] }
            checkSequence(colSequence)
        }

        // Check main diagonals (top-left to bottom-right)
        for (rowStart in 0..(boardSize - requiredInRow)) {
            for (colStart in 0..(boardSize - requiredInRow)) {
                val diagSequence = (0 until requiredInRow).map {
                    val idx = (rowStart + it) * boardSize + (colStart + it)
                    board[idx]
                }
                checkSequence(diagSequence)
            }
        }

        // Check anti-diagonals (top-right to bottom-left)
        for (rowStart in 0..(boardSize - requiredInRow)) {
            for (colStart in (requiredInRow - 1) until boardSize) {
                val diagSequence = (0 until requiredInRow).map {
                    val idx = (rowStart + it) * boardSize + (colStart - it)
                    board[idx]
                }
                checkSequence(diagSequence)
            }
        }
        
        return xWins && oWins
    }

    fun isBoardFull(): Boolean {
        return board.all { it != null }
    }

    fun isGameOver(winCondition: Int = 0): Boolean {
        val effectiveWinCondition = if (winCondition > 0 && winCondition <= boardSize) winCondition else boardSize
        return checkWinner(winCondition = effectiveWinCondition) != null || isBoardFull()
    }

    // Flip a column vertically (reverse the column - top becomes bottom)
    fun flipColumn(columnIndex: Int): GameState {
        if (columnIndex < 0 || columnIndex >= boardSize) return this
        
        val newBoard = board.copyOf()
        val column = mutableListOf<Player?>()
        
        // Extract column from top to bottom
        for (row in 0 until boardSize) {
            val index = row * boardSize + columnIndex
            column.add(newBoard[index])
        }
        
        // Reverse the column (flip vertically)
        column.reverse()
        
        // Put reversed column back
        for (row in 0 until boardSize) {
            val index = row * boardSize + columnIndex
            newBoard[index] = column[row]
        }
        
        return copy(board = newBoard)
    }

    // Flip columns based on mode
    fun flipColumns(columnIndex: Int, flipMode: String): GameState {
        return when (flipMode) {
            "single" -> {
                // Only flip the column that was played in
                val random = kotlin.random.Random
                val flipCount = random.nextInt(0, 4) // 0..3
                if (flipCount % 2 == 1) {
                    this.flipColumn(columnIndex)
                } else {
                    this
                }
            }
            "entire" -> {
                // Flip all columns randomly
                flipColumnsRandomly()
            }
            else -> flipColumnsRandomly()
        }
    }

    // Flip multiple columns randomly
    fun flipColumnsRandomly(): GameState {
        var newState = this
        val random = kotlin.random.Random
        
        // Flip each column a random number of times.
        // Since a vertical flip is its own inverse, only the parity matters:
        // - even flips => unchanged
        // - odd flips  => flipped
        for (col in 0 until boardSize) {
            val flipCount = random.nextInt(0, 4) // 0..3
            if (flipCount % 2 == 1) {
                newState = newState.flipColumn(col)
            }
        }
        
        return newState
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GameState

        if (!board.contentEquals(other.board)) return false
        if (boardSize != other.boardSize) return false
        if (currentPlayer != other.currentPlayer) return false
        if (playerXScore != other.playerXScore) return false
        if (playerOScore != other.playerOScore) return false

        return true
    }

    override fun hashCode(): Int {
        var result = board.contentHashCode()
        result = 31 * result + boardSize
        result = 31 * result + currentPlayer.hashCode()
        result = 31 * result + playerXScore
        result = 31 * result + playerOScore
        return result
    }
}

// AI logic with difficulty levels
fun findBestMove(board: Array<Player?>, boardSize: Int, difficulty: Difficulty, winCondition: Int = 0, blockCenter: Boolean = false): Int {
    val effectiveWinCondition = if (winCondition > 0 && winCondition <= boardSize) winCondition else boardSize
    return when (difficulty) {
        Difficulty.EASY -> findRandomMove(board)
        Difficulty.MEDIUM -> findMediumMove(board, boardSize, effectiveWinCondition, blockCenter)
        Difficulty.HARD -> findBestMoveHard(board, boardSize, effectiveWinCondition, blockCenter)
        Difficulty.FLIP -> findBestMoveHard(board, boardSize, effectiveWinCondition, blockCenter) // Same as hard, columns will flip after
    }
}

fun findRandomMove(board: Array<Player?>, blockCenter: Boolean = false, boardSize: Int = 3): Int {
    val moveCount = board.count { it != null }
    val isCenterBlocked = blockCenter && moveCount < 2
    val center = boardSize / 2
    val centerIndex = center * boardSize + center
    
    val availableMoves = board.indices.filter { 
        board[it] == null && !(isCenterBlocked && it == centerIndex)
    }
    return if (availableMoves.isNotEmpty()) {
        availableMoves.random()
    } else {
        -1
    }
}

fun findMediumMove(board: Array<Player?>, boardSize: Int, winCondition: Int = boardSize, blockCenter: Boolean = false): Int {
    // For larger boards, use heuristic-based approach
    if (boardSize > 3) {
        // 70% chance to make smart move, 30% chance to make random move
        return if (kotlin.random.Random.nextFloat() < 0.7f) {
            findHeuristicMove(board, boardSize, winCondition, blockCenter)
        } else {
            findRandomMove(board, blockCenter, boardSize)
        }
    }
    // For 3x3, use minimax with depth limit
    return if (kotlin.random.Random.nextFloat() < 0.7f) {
        findBestMoveHard(board, boardSize)
    } else {
        findRandomMove(board)
    }
}

fun findBestMoveHard(board: Array<Player?>, boardSize: Int, winCondition: Int = boardSize, blockCenter: Boolean = false): Int {
    val moveCount = board.count { it != null }
    val isCenterBlocked = blockCenter && moveCount < 2
    val center = boardSize / 2
    val centerIndex = center * boardSize + center
    
    // For 3x3 boards, use full minimax
    if (boardSize == 3) {
        var bestScore = Int.MIN_VALUE
        var bestMove = -1

        for (i in board.indices) {
            if (board[i] == null) {
                // Skip center if it's blocked
                if (isCenterBlocked && i == centerIndex) continue
                
                board[i] = Player.O
                val score = minimax(board, boardSize, 0, false, Int.MIN_VALUE, Int.MAX_VALUE, maxDepth = 9, winCondition = winCondition)
                board[i] = null

                if (score > bestScore) {
                    bestScore = score
                    bestMove = i
                }
            }
        }

        return bestMove
    } else {
        // For larger boards, use heuristic-based approach
        return findHeuristicMove(board, boardSize, winCondition, blockCenter)
    }
}

// Heuristic-based move finder for larger boards (4x4, 5x5)
fun findHeuristicMove(board: Array<Player?>, boardSize: Int, winCondition: Int = boardSize, blockCenter: Boolean = false): Int {
    val effectiveWinCondition = if (winCondition > 0 && winCondition <= boardSize) winCondition else boardSize
    
    // Helper to check if center is blocked (first two moves)
    val moveCount = board.count { it != null }
    val isCenterBlocked = blockCenter && moveCount < 2
    val center = boardSize / 2
    val centerIndex = center * boardSize + center
    
    // 1. Check for winning move (AI can win immediately)
    for (i in board.indices) {
        if (board[i] == null) {
            // Skip center if it's blocked
            if (isCenterBlocked && i == centerIndex) continue
            
            board[i] = Player.O
            val winner = GameState(boardSize = boardSize, board = board).checkWinner(winCondition = effectiveWinCondition)
            board[i] = null
            if (winner == Player.O) {
                return i
            }
        }
    }

    // 2. Check for blocking move (prevent player from winning immediately)
    for (i in board.indices) {
        if (board[i] == null) {
            // Skip center if it's blocked
            if (isCenterBlocked && i == centerIndex) continue
            
            board[i] = Player.X
            val winner = GameState(boardSize = boardSize, board = board).checkWinner(winCondition = effectiveWinCondition)
            board[i] = null
            if (winner == Player.X) {
                return i // Block immediate win
            }
        }
    }
    
    // 2b. Check for blocking moves that prevent player from getting very close to winning
    // (having winCondition - 1 pieces in a line)
    var bestBlockMove = -1
    var bestBlockScore = -1
    for (i in board.indices) {
        if (board[i] == null) {
            // Skip center if it's blocked
            if (isCenterBlocked && i == centerIndex) continue
            
            val blockScore = evaluateMove(board, boardSize, i, Player.X, effectiveWinCondition)
            // If opponent would have winCondition-1 pieces (score >= 100), we should block
            if (blockScore >= 100 && blockScore > bestBlockScore) {
                bestBlockScore = blockScore
                bestBlockMove = i
            }
        }
    }
    if (bestBlockMove != -1) {
        return bestBlockMove // Block opponent from getting very close to winning
    }

    // 3. Check for moves that create lines close to winning (winCondition - 1 pieces)
    var bestMove = -1
    var bestScore = -1
    
    for (i in board.indices) {
        if (board[i] == null) {
            // Skip center if it's blocked
            if (isCenterBlocked && i == centerIndex) continue
            
            // Score based on how close to winning this move gets us
            val score = evaluateMove(board, boardSize, i, Player.O, effectiveWinCondition)
            
            // Also check if this move blocks opponent from getting close to winning
            val blockingScore = evaluateMove(board, boardSize, i, Player.X, effectiveWinCondition)
            
            // Prioritize moves that get us closer to winning or block opponent
            val totalScore = score * 2 + blockingScore // Weight our own progress more
            
            if (totalScore > bestScore) {
                bestScore = totalScore
                bestMove = i
            }
        }
    }
    
    // 4. If we found a good move, use it
    if (bestMove != -1 && bestScore > 0) {
        return bestMove
    }

    // 5. Try to take center or strategic positions (only if not blocked)
    if (!isCenterBlocked && board.getOrNull(centerIndex) == null) {
        return centerIndex
    }

    // 6. Try corners
    val corners = listOf(
        0, // top-left
        boardSize - 1, // top-right
        (boardSize - 1) * boardSize, // bottom-left
        boardSize * boardSize - 1 // bottom-right
    )
    for (corner in corners) {
        if (corner < board.size && board[corner] == null) {
            return corner
        }
    }
    
    // 7. Fallback to random move
    return findRandomMove(board, blockCenter, boardSize)
}

// Evaluate how good a move is by counting potential lines, aware of win condition
fun evaluateMove(board: Array<Player?>, boardSize: Int, index: Int, player: Player, winCondition: Int = boardSize): Int {
    val row = index / boardSize
    val col = index % boardSize
    val effectiveWinCondition = if (winCondition > 0 && winCondition <= boardSize) winCondition else boardSize
    var score = 0
    
    // Helper function to count consecutive pieces in a sequence that could form a winning line
    fun countConsecutiveInSequence(sequence: List<Player?>): Int {
        var maxConsecutive = 0
        var currentConsecutive = 0
        
        for (cell in sequence) {
            if (cell == player) {
                currentConsecutive++
                maxConsecutive = maxOf(maxConsecutive, currentConsecutive)
            } else if (cell == null) {
                // Empty cell - can potentially extend the line, but reset consecutive count
                // Check if we can form a line through this empty cell
                currentConsecutive = 0
            } else {
                // Opponent's piece - reset
                currentConsecutive = 0
            }
        }
        
        // Score exponentially higher for lines closer to winning
        return when {
            maxConsecutive >= effectiveWinCondition -> 1000 // Already winning (shouldn't happen)
            maxConsecutive == effectiveWinCondition - 1 -> 100 // One away from winning - very valuable!
            maxConsecutive == effectiveWinCondition - 2 -> 20 // Two away - still very good
            maxConsecutive >= effectiveWinCondition - 3 -> 5 // Three away
            else -> maxConsecutive // Just count pieces for smaller lines
        }
    }
    
    // Check all possible sequences that pass through this position
    
    // Evaluate row
    val rowSequence = (0 until boardSize).map { board[row * boardSize + it] }
    score += countConsecutiveInSequence(rowSequence)
    
    // Evaluate column
    val colSequence = (0 until boardSize).map { board[it * boardSize + col] }
    score += countConsecutiveInSequence(colSequence)
    
    // Evaluate main diagonal (if on main diagonal)
    if (row == col) {
        val diagSequence = (0 until boardSize).map { board[it * boardSize + it] }
        score += countConsecutiveInSequence(diagSequence)
    }
    
    // Evaluate anti-diagonal (if on anti-diagonal)
    if (row + col == boardSize - 1) {
        val antiDiagSequence = (0 until boardSize).map { board[it * boardSize + (boardSize - 1 - it)] }
        score += countConsecutiveInSequence(antiDiagSequence)
    }
    
    // Check all diagonal sequences that could form a winning line through this position
    // This is important for larger boards where multiple diagonals can pass through a point
    for (diagOffset in -(effectiveWinCondition - 1)..(effectiveWinCondition - 1)) {
        // Check main diagonal variations
        val startRow = row - diagOffset
        val startCol = col - diagOffset
        if (startRow >= 0 && startCol >= 0 && 
            startRow + effectiveWinCondition <= boardSize && 
            startCol + effectiveWinCondition <= boardSize) {
            val diagSequence = (0 until effectiveWinCondition).map {
                val r = startRow + it
                val c = startCol + it
                board[r * boardSize + c]
            }
            val seqScore = countConsecutiveInSequence(diagSequence)
            if (seqScore >= 20) { // Only count significant sequences to avoid double counting
                score += seqScore / 2
            }
        }
        
        // Check anti-diagonal variations
        val antiStartRow = row - diagOffset
        val antiStartCol = col + diagOffset
        if (antiStartRow >= 0 && antiStartCol < boardSize && 
            antiStartRow + effectiveWinCondition <= boardSize && 
            antiStartCol - effectiveWinCondition >= -1) {
            val antiDiagSequence = (0 until effectiveWinCondition).map {
                val r = antiStartRow + it
                val c = antiStartCol - it
                if (c >= 0 && c < boardSize) board[r * boardSize + c] else null
            }
            val seqScore = countConsecutiveInSequence(antiDiagSequence)
            if (seqScore >= 20) { // Only count significant sequences
                score += seqScore / 2
            }
        }
    }
    
    return score
}

// Depth-limited minimax with alpha-beta pruning for 3x3 boards
fun minimax(
    board: Array<Player?>, 
    boardSize: Int, 
    depth: Int, 
    isMaximizing: Boolean,
    alpha: Int,
    beta: Int,
    maxDepth: Int = 9,
    winCondition: Int = boardSize
): Int {
    // Check for terminal states
    val winner = GameState(boardSize = boardSize, board = board).checkWinner(winCondition = winCondition)
    if (winner == Player.O) return 10 - depth // AI wins
    if (winner == Player.X) return depth - 10 // Player wins
    if (board.all { it != null }) return 0 // Draw
    
    // Depth limit to prevent infinite recursion
    if (depth >= maxDepth) {
        return 0 // Return neutral score at depth limit
    }

    if (isMaximizing) {
        var bestScore = Int.MIN_VALUE
        var currentAlpha = alpha
        for (i in board.indices) {
            if (board[i] == null) {
                board[i] = Player.O
                val score = minimax(board, boardSize, depth + 1, false, currentAlpha, beta, maxDepth, winCondition)
                board[i] = null
                bestScore = maxOf(bestScore, score)
                currentAlpha = maxOf(currentAlpha, bestScore)
                // Alpha-beta pruning
                if (beta <= currentAlpha) {
                    break
                }
            }
        }
        return bestScore
    } else {
        var bestScore = Int.MAX_VALUE
        var currentBeta = beta
        for (i in board.indices) {
            if (board[i] == null) {
                board[i] = Player.X
                val score = minimax(board, boardSize, depth + 1, true, alpha, currentBeta, maxDepth, winCondition)
                board[i] = null
                bestScore = minOf(bestScore, score)
                currentBeta = minOf(currentBeta, bestScore)
                // Alpha-beta pruning
                if (currentBeta <= alpha) {
                    break
                }
            }
        }
        return bestScore
    }
}

@Composable
private fun GameModeSelectionDialog(
    currentGameMode: GameMode,
    onGameModeSelected: (GameMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Game Mode") },
        text = {
            Column {
                DifficultyOption(
                    label = "vs AI",
                    description = "Play against the computer",
                    isSelected = currentGameMode == GameMode.AI,
                    onClick = { onGameModeSelected(GameMode.AI) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                DifficultyOption(
                    label = "vs Family Member",
                    description = "Play against a family member",
                    isSelected = currentGameMode == GameMode.FAMILY_USER,
                    onClick = { onGameModeSelected(GameMode.FAMILY_USER) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                DifficultyOption(
                    label = "Local 2-Player",
                    description = "Two players on one device",
                    isSelected = currentGameMode == GameMode.LOCAL_PLAYER,
                    onClick = { onGameModeSelected(GameMode.LOCAL_PLAYER) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                DifficultyOption(
                    label = "Remote Play",
                    description = "Play with a family member remotely (like playing by mail)",
                    isSelected = currentGameMode == GameMode.REMOTE_PLAY,
                    onClick = { onGameModeSelected(GameMode.REMOTE_PLAY) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun OpponentSelectionDialog(
    gameMode: GameMode,
    currentOpponentId: String?,
    currentOpponentName: String?,
    onOpponentSelected: (String?, String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: TicTacToeViewModel
) {
    var allUsers by remember { mutableStateOf<List<com.lostsierra.chorequest.domain.models.User>>(emptyList()) }
    var currentUserId by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(Unit) {
        currentUserId = viewModel.sessionManager.loadSession()?.userId
        allUsers = viewModel.userRepository.getAllUsers().first()
    }
    
    val availableUsers = remember(allUsers, currentUserId) {
        if (gameMode == GameMode.FAMILY_USER || gameMode == GameMode.REMOTE_PLAY) {
            allUsers.filter { it.id != currentUserId }
        } else {
            emptyList()
        }
    }
    
    var localPlayerName by remember { mutableStateOf(currentOpponentName ?: "") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                text = when (gameMode) {
                    GameMode.FAMILY_USER -> "Select Opponent"
                    GameMode.REMOTE_PLAY -> "Select Remote Opponent"
                    else -> "Enter Player 2 Name"
                }
            )
        },
        text = {
            if (gameMode == GameMode.FAMILY_USER || gameMode == GameMode.REMOTE_PLAY) {
                if (availableUsers.isEmpty()) {
                    Text("No other family members available")
                } else {
                    Column {
                        availableUsers.forEach { user ->
                            DifficultyOption(
                                label = user.name,
                                description = if (user.role == com.lostsierra.chorequest.domain.models.UserRole.CHILD) "Child" else "Parent",
                                isSelected = currentOpponentId == user.id,
                                onClick = { onOpponentSelected(user.id, user.name) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = localPlayerName,
                    onValueChange = { localPlayerName = it },
                    label = { Text("Player 2 Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            if (gameMode == GameMode.LOCAL_PLAYER) {
                Button(
                    onClick = { 
                        if (localPlayerName.isNotBlank()) {
                            onOpponentSelected(null, localPlayerName)
                        }
                    },
                    enabled = localPlayerName.isNotBlank()
                ) {
                    Text("Start")
                }
            }
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun RemoteGameSelectionDialog(
    availableGames: List<RemoteGameState>,
    opponentName: String,
    onGameSelected: (String) -> Unit,
    onStartNewGame: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: TicTacToeViewModel
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text("Games with $opponentName")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (availableGames.isEmpty()) {
                    Text("No games in progress")
                } else {
                    Text(
                        text = "Select a game to resume:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    availableGames.forEach { game ->
                        RemoteGameOption(
                            game = game,
                            currentUserId = viewModel.sessionManager.loadSession()?.userId ?: "",
                            onClick = { onGameSelected(game.gameId) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Button(
                    onClick = onStartNewGame,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start New Game")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AllActiveGamesDialog(
    availableGames: List<RemoteGameState>,
    onGameSelected: (String) -> Unit,
    onStartNewGame: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: TicTacToeViewModel
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text("Active Games")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (availableGames.isEmpty()) {
                    Text("No active games")
                } else {
                    Text(
                        text = "Select a game to continue:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    availableGames.forEach { game ->
                        RemoteGameOption(
                            game = game,
                            currentUserId = viewModel.sessionManager.loadSession()?.userId ?: "",
                            onClick = { onGameSelected(game.gameId) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Button(
                    onClick = onStartNewGame,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start New Game")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun RemoteGameOption(
    game: RemoteGameState,
    currentUserId: String,
    onClick: () -> Unit
) {
    val isPlayerX = game.player1Id == currentUserId
    val myPlayer = if (isPlayerX) "X" else "O"
    val opponentPlayer = if (isPlayerX) "O" else "X"
    val moveCount = game.board.count { it != null }
    val isMyTurn = (game.currentPlayer == "X" && isPlayerX) || (game.currentPlayer == "O" && !isPlayerX)
    
    // Format last updated time
    val lastUpdated = java.time.Instant.ofEpochMilli(game.lastUpdated)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDateTime()
    val timeAgo = when {
        java.time.Duration.between(lastUpdated, java.time.LocalDateTime.now()).toMinutes() < 1 -> "Just now"
        java.time.Duration.between(lastUpdated, java.time.LocalDateTime.now()).toHours() < 1 -> 
            "${java.time.Duration.between(lastUpdated, java.time.LocalDateTime.now()).toMinutes()}m ago"
        java.time.Duration.between(lastUpdated, java.time.LocalDateTime.now()).toDays() < 1 -> 
            "${java.time.Duration.between(lastUpdated, java.time.LocalDateTime.now()).toHours()}h ago"
        else -> "${java.time.Duration.between(lastUpdated, java.time.LocalDateTime.now()).toDays()}d ago"
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isMyTurn) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isPlayerX) game.player2Name else game.player1Name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isMyTurn) "Your turn ($myPlayer)" else "$opponentPlayer's turn",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isMyTurn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = timeAgo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Moves: $moveCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Text(
                    text = "Score: ${if (isPlayerX) game.playerXScore else game.playerOScore} - ${if (isPlayerX) game.playerOScore else game.playerXScore}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
