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
import androidx.compose.ui.draw.rotate
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
                boardSize = uiState.gameState.boardSize,
                isFlippingColumns = uiState.isFlippingColumns,
                columnsToFlip = uiState.columnsToFlip,
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
                    label = "Hard-Flip",
                    description = "5x5 board - Columns flip randomly after each turn!",
                    isSelected = currentDifficulty == "hard-flip",
                    onClick = { onDifficultySelected("hard-flip") }
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
    boardSize: Int,
    isFlippingColumns: Boolean,
    columnsToFlip: Set<Int>,
    onCellClick: (Int) -> Unit
) {
    // Adjust board size based on boardSize (3x3 = 300dp, 4x4 = 360dp, 5x5 = 420dp)
    val boardDimension = when (boardSize) {
        3 -> 300.dp
        4 -> 360.dp
        5 -> 420.dp
        else -> 300.dp
    }
    
    // Animation for column flipping - single animation state shared across all columns
    // Use a key to reset animation when flipping starts
    val flipKey = remember(isFlippingColumns) { 
        if (isFlippingColumns) kotlin.random.Random.nextInt() else 0 
    }
    
    val flipRotation by animateFloatAsState(
        targetValue = if (isFlippingColumns) 360f else 0f,
        animationSpec = tween(
            durationMillis = 800,
            easing = FastOutSlowInEasing
        ),
        label = "flipRotation_$flipKey"
    )
    
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
                        val isColumnFlipping = isFlippingColumns && columnsToFlip.contains(col)
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .then(
                                    if (isColumnFlipping) {
                                        Modifier.rotate(flipRotation)
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            GameCell(
                                player = board.getOrNull(index),
                                onClick = { onCellClick(index) },
                                modifier = Modifier
                                    .fillMaxSize(),
                                boardSize = boardSize
                            )
                        }
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
    boardSize: Int = 3
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
                    // Empty cell
                    Text(
                        text = "",
                        fontSize = fontSize
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
    EASY, MEDIUM, HARD, HARD_FLIP
}

// Helper function to get board size from difficulty
fun getBoardSize(difficulty: Difficulty): Int {
    return when (difficulty) {
        Difficulty.EASY -> 3
        Difficulty.MEDIUM -> 4
        Difficulty.HARD -> 5
        Difficulty.HARD_FLIP -> 5
    }
}

fun getBoardSize(difficulty: String): Int {
    return when (difficulty.lowercase()) {
        "easy" -> 3
        "medium" -> 4
        "hard" -> 5
        "hard-flip" -> 5
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
    fun makeMove(index: Int, player: Player): GameState {
        val boardSizeSquared = boardSize * boardSize
        if (board[index] != null || index < 0 || index >= boardSizeSquared) {
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
        for (row in 0 until boardSize) {
            val start = row * boardSize
            val first = board[start]
            if (first != null) {
                var allMatch = true
                for (col in 1 until boardSize) {
                    if (board[start + col] != first) {
                        allMatch = false
                        break
                    }
                }
                if (allMatch) return first
            }
        }

        // Check columns
        for (col in 0 until boardSize) {
            val first = board[col]
            if (first != null) {
                var allMatch = true
                for (row in 1 until boardSize) {
                    if (board[row * boardSize + col] != first) {
                        allMatch = false
                        break
                    }
                }
                if (allMatch) return first
            }
        }

        // Check main diagonal (top-left to bottom-right)
        val mainDiagFirst = board[0]
        if (mainDiagFirst != null) {
            var allMatch = true
            for (i in 1 until boardSize) {
                if (board[i * boardSize + i] != mainDiagFirst) {
                    allMatch = false
                    break
                }
            }
            if (allMatch) return mainDiagFirst
        }

        // Check anti-diagonal (top-right to bottom-left)
        val antiDiagFirst = board[boardSize - 1]
        if (antiDiagFirst != null) {
            var allMatch = true
            for (i in 1 until boardSize) {
                if (board[i * boardSize + (boardSize - 1 - i)] != antiDiagFirst) {
                    allMatch = false
                    break
                }
            }
            if (allMatch) return antiDiagFirst
        }

        return null
    }

    fun isBoardFull(): Boolean {
        return board.all { it != null }
    }

    fun isGameOver(): Boolean {
        return checkWinner() != null || isBoardFull()
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
fun findBestMove(board: Array<Player?>, boardSize: Int, difficulty: Difficulty): Int {
    return when (difficulty) {
        Difficulty.EASY -> findRandomMove(board)
        Difficulty.MEDIUM -> findMediumMove(board, boardSize)
        Difficulty.HARD -> findBestMoveHard(board, boardSize)
        Difficulty.HARD_FLIP -> findBestMoveHard(board, boardSize) // Same as hard, columns will flip after
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

fun findMediumMove(board: Array<Player?>, boardSize: Int): Int {
    // For larger boards, use heuristic-based approach
    if (boardSize > 3) {
        // 70% chance to make smart move, 30% chance to make random move
        return if (kotlin.random.Random.nextFloat() < 0.7f) {
            findHeuristicMove(board, boardSize)
        } else {
            findRandomMove(board)
        }
    }
    // For 3x3, use minimax with depth limit
    return if (kotlin.random.Random.nextFloat() < 0.7f) {
        findBestMoveHard(board, boardSize)
    } else {
        findRandomMove(board)
    }
}

fun findBestMoveHard(board: Array<Player?>, boardSize: Int): Int {
    // For 3x3 boards, use full minimax
    if (boardSize == 3) {
        var bestScore = Int.MIN_VALUE
        var bestMove = -1

        for (i in board.indices) {
            if (board[i] == null) {
                board[i] = Player.O
                val score = minimax(board, boardSize, 0, false, Int.MIN_VALUE, Int.MAX_VALUE, maxDepth = 9)
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
        return findHeuristicMove(board, boardSize)
    }
}

// Heuristic-based move finder for larger boards (4x4, 5x5)
fun findHeuristicMove(board: Array<Player?>, boardSize: Int): Int {
    // 1. Check for winning move
    for (i in board.indices) {
        if (board[i] == null) {
            board[i] = Player.O
            val winner = GameState(boardSize = boardSize, board = board).checkWinner()
            board[i] = null
            if (winner == Player.O) {
                return i
            }
        }
    }

    // 2. Check for blocking move (prevent player from winning)
    for (i in board.indices) {
        if (board[i] == null) {
            board[i] = Player.X
            val winner = GameState(boardSize = boardSize, board = board).checkWinner()
            board[i] = null
            if (winner == Player.X) {
                return i
            }
        }
    }

    // 3. Try to take center or strategic positions
    val center = boardSize / 2
    val centerIndex = center * boardSize + center
    if (board.getOrNull(centerIndex) == null) {
        return centerIndex
    }

    // 4. Try corners
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

    // 5. Find move that creates longest line for AI
    var bestMove = -1
    var bestScore = -1
    
    for (i in board.indices) {
        if (board[i] == null) {
            val score = evaluateMove(board, boardSize, i, Player.O)
            if (score > bestScore) {
                bestScore = score
                bestMove = i
            }
        }
    }
    
    return if (bestMove != -1) bestMove else findRandomMove(board)
}

// Evaluate how good a move is by counting potential lines
fun evaluateMove(board: Array<Player?>, boardSize: Int, index: Int, player: Player): Int {
    val row = index / boardSize
    val col = index % boardSize
    var score = 0
    
    // Count potential in row
    var count = 1 // the move itself
    for (c in 0 until boardSize) {
        val idx = row * boardSize + c
        if (idx != index && board.getOrNull(idx) == player) {
            count++
        }
    }
    score += count
    
    // Count potential in column
    count = 1
    for (r in 0 until boardSize) {
        val idx = r * boardSize + col
        if (idx != index && board.getOrNull(idx) == player) {
            count++
        }
    }
    score += count
    
    // Count potential in main diagonal
    if (row == col) {
        count = 1
        for (i in 0 until boardSize) {
            val idx = i * boardSize + i
            if (idx != index && board.getOrNull(idx) == player) {
                count++
            }
        }
        score += count
    }
    
    // Count potential in anti-diagonal
    if (row + col == boardSize - 1) {
        count = 1
        for (i in 0 until boardSize) {
            val idx = i * boardSize + (boardSize - 1 - i)
            if (idx != index && board.getOrNull(idx) == player) {
                count++
            }
        }
        score += count
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
    maxDepth: Int = 9
): Int {
    // Check for terminal states
    val winner = GameState(boardSize = boardSize, board = board).checkWinner()
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
                val score = minimax(board, boardSize, depth + 1, false, currentAlpha, beta, maxDepth)
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
                val score = minimax(board, boardSize, depth + 1, true, alpha, currentBeta, maxDepth)
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
