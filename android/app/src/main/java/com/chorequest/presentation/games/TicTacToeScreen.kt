package com.chorequest.presentation.games

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import com.chorequest.data.local.GamePreferencesManager
import com.chorequest.presentation.components.CelebrationAnimation
import com.chorequest.presentation.components.CelebrationStyle
import com.chorequest.presentation.components.ChoreQuestTopAppBar
import com.chorequest.utils.SoundManager
import kotlinx.coroutines.delay

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
            // Difficulty and High Score Card
            DifficultyAndScoreCard(
                difficulty = uiState.difficulty,
                highScore = uiState.highScore,
                onDifficultyClick = { showDifficultyDialog = true }
            )

            // Game status
            GameStatusCard(
                currentPlayer = uiState.gameState.currentPlayer,
                playerXScore = uiState.gameState.playerXScore,
                playerOScore = uiState.gameState.playerOScore,
                isAITurn = uiState.isAITurn
            )

            // Game board
            GameBoard(
                board = uiState.gameState.board,
                onCellClick = { index ->
                    viewModel.onCellClick(index)
                }
            )

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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

    // Win dialog
    if (uiState.showWinDialog) {
        val winner = uiState.gameState.checkWinner()
        AlertDialog(
            onDismissRequest = { viewModel.dismissWinDialog() },
            title = {
                Text(
                    text = if (winner == Player.X) "🎉 You Win! 🎉" else "🤖 AI Wins!",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = if (winner == Player.X) 
                        "Great job! You beat the AI!" 
                    else 
                        "Better luck next time!",
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.newGame()
                }) {
                    Text("Play Again")
                }
            }
        )
    }

    // Draw dialog
    if (uiState.showDrawDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDrawDialog() },
            title = {
                Text(
                    text = "🤝 It's a Draw!",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = "Good game! Try again!",
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.newGame()
                }) {
                    Text("Play Again")
                }
            }
        )
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
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@Composable
private fun DifficultyAndScoreCard(
    difficulty: String,
    highScore: Int,
    onDifficultyClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Difficulty",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onDifficultyClick)
                ) {
                    Text(
                        text = difficulty.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "Change difficulty",
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
                    description = "AI makes random moves - Easy to win!",
                    isSelected = currentDifficulty == "easy",
                    onClick = { onDifficultySelected("easy") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                DifficultyOption(
                    label = "Medium",
                    description = "AI makes some mistakes - Fair challenge",
                    isSelected = currentDifficulty == "medium",
                    onClick = { onDifficultySelected("medium") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                DifficultyOption(
                    label = "Hard",
                    description = "AI is unbeatable - Best you can do is draw!",
                    isSelected = currentDifficulty == "hard",
                    onClick = { onDifficultySelected("hard") }
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
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Game Settings") },
        text = {
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun GameStatusCard(
    currentPlayer: Player,
    playerXScore: Int,
    playerOScore: Int,
    isAITurn: Boolean
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
                text = if (isAITurn) "🤖 AI is thinking..." else "Your turn!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ScoreDisplay(label = "You (X)", score = playerXScore, isActive = currentPlayer == Player.X && !isAITurn)
                Text("VS", style = MaterialTheme.typography.bodyLarge)
                ScoreDisplay(label = "AI (O)", score = playerOScore, isActive = currentPlayer == Player.O && isAITurn)
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
    onCellClick: (Int) -> Unit
) {
    Card(
        modifier = Modifier.size(300.dp),
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
            repeat(3) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(3) { col ->
                        val index = row * 3 + col
                        GameCell(
                            player = board[index],
                            onClick = { onCellClick(index) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
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
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (player != null) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ), label = "cellScale"
    )

    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .scale(scale),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (player != null) 4.dp else 2.dp
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
                        fontSize = 48.sp,
                        color = Color(0xFF2196F3),
                        fontWeight = FontWeight.Bold
                    )
                }
                Player.O -> {
                    Text(
                        text = "○",
                        fontSize = 48.sp,
                        color = Color(0xFFF44336),
                        fontWeight = FontWeight.Bold
                    )
                }
                null -> {
                    // Empty cell
                    Text(
                        text = "",
                        fontSize = 48.sp
                    )
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
    EASY, MEDIUM, HARD
}

data class GameState(
    val board: Array<Player?> = arrayOfNulls(9),
    val currentPlayer: Player = Player.X,
    val playerXScore: Int = 0,
    val playerOScore: Int = 0
) {
    fun makeMove(index: Int, player: Player): GameState {
        if (board[index] != null || index < 0 || index >= 9) {
            return this
        }

        val newBoard = board.copyOf()
        newBoard[index] = player

        val winner = checkWinner(newBoard)
        val newPlayerXScore = if (winner == Player.X) playerXScore + 1 else playerXScore
        val newPlayerOScore = if (winner == Player.O) playerOScore + 1 else playerOScore

        return copy(
            board = newBoard,
            currentPlayer = if (player == Player.X) Player.O else Player.X,
            playerXScore = newPlayerXScore,
            playerOScore = newPlayerOScore
        )
    }

    fun checkWinner(board: Array<Player?> = this.board): Player? {
        // Check rows
        for (i in 0 until 3) {
            val start = i * 3
            if (board[start] != null &&
                board[start] == board[start + 1] &&
                board[start] == board[start + 2]) {
                return board[start]
            }
        }

        // Check columns
        for (i in 0 until 3) {
            if (board[i] != null &&
                board[i] == board[i + 3] &&
                board[i] == board[i + 6]) {
                return board[i]
            }
        }

        // Check diagonals
        if (board[0] != null && board[0] == board[4] && board[0] == board[8]) {
            return board[0]
        }
        if (board[2] != null && board[2] == board[4] && board[2] == board[6]) {
            return board[2]
        }

        return null
    }

    fun isBoardFull(): Boolean {
        return board.all { it != null }
    }

    fun isGameOver(): Boolean {
        return checkWinner() != null || isBoardFull()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GameState

        if (!board.contentEquals(other.board)) return false
        if (currentPlayer != other.currentPlayer) return false
        if (playerXScore != other.playerXScore) return false
        if (playerOScore != other.playerOScore) return false

        return true
    }

    override fun hashCode(): Int {
        var result = board.contentHashCode()
        result = 31 * result + currentPlayer.hashCode()
        result = 31 * result + playerXScore
        result = 31 * result + playerOScore
        return result
    }
}

// AI logic with difficulty levels
fun findBestMove(board: Array<Player?>, difficulty: Difficulty): Int {
    return when (difficulty) {
        Difficulty.EASY -> findRandomMove(board)
        Difficulty.MEDIUM -> findMediumMove(board)
        Difficulty.HARD -> findBestMoveHard(board)
    }
}

fun findRandomMove(board: Array<Player?>): Int {
    val availableMoves = board.indices.filter { board[it] == null }
    return if (availableMoves.isNotEmpty()) {
        availableMoves.random()
    } else {
        -1
    }
}

fun findMediumMove(board: Array<Player?>): Int {
    // 70% chance to make best move, 30% chance to make random move
    return if (kotlin.random.Random.nextFloat() < 0.7f) {
        findBestMoveHard(board)
    } else {
        findRandomMove(board)
    }
}

fun findBestMoveHard(board: Array<Player?>): Int {
    var bestScore = Int.MIN_VALUE
    var bestMove = -1

    for (i in board.indices) {
        if (board[i] == null) {
            board[i] = Player.O
            val score = minimax(board, 0, false)
            board[i] = null

            if (score > bestScore) {
                bestScore = score
                bestMove = i
            }
        }
    }

    return bestMove
}

fun minimax(board: Array<Player?>, depth: Int, isMaximizing: Boolean): Int {
    // Check for terminal states
    val winner = GameState(board = board).checkWinner()
    if (winner == Player.O) return 10 - depth // AI wins
    if (winner == Player.X) return depth - 10 // Player wins
    if (board.all { it != null }) return 0 // Draw

    if (isMaximizing) {
        var bestScore = Int.MIN_VALUE
        for (i in board.indices) {
            if (board[i] == null) {
                board[i] = Player.O
                val score = minimax(board, depth + 1, false)
                board[i] = null
                bestScore = maxOf(bestScore, score)
            }
        }
        return bestScore
    } else {
        var bestScore = Int.MAX_VALUE
        for (i in board.indices) {
            if (board[i] == null) {
                board[i] = Player.X
                val score = minimax(board, depth + 1, true)
                board[i] = null
                bestScore = minOf(bestScore, score)
            }
        }
        return bestScore
    }
}
