package com.lostsierra.chorequest.presentation.games

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lostsierra.chorequest.presentation.components.ChoreQuestTopAppBar

/**
 * Breakout Game screen - Classic breakout game with paddle and ball
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreakoutGameScreen(
    onNavigateBack: () -> Unit,
    viewModel: BreakoutGameViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showDifficultyDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Save game state when app goes to background
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            android.util.Log.d("BreakoutGame", "Lifecycle event: $event")
            if (event == Lifecycle.Event.ON_PAUSE) {
                android.util.Log.d("BreakoutGame", "ON_PAUSE detected - saving game state")
                viewModel.saveGameStateOnPause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            android.util.Log.d("BreakoutGame", "DisposableEffect onDispose - saving game state")
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Also save when composable is disposed (e.g., navigation away)
            viewModel.saveGameStateOnPause()
        }
    }

    // Initialize game on first load
    LaunchedEffect(Unit) {
        // Check for saved game first
        val hasSavedGame = context.getSharedPreferences("chorequest_games", android.content.Context.MODE_PRIVATE)
            .getBoolean("breakout_game_has_saved_game", false)
        
        if (hasSavedGame && uiState.bricks.isEmpty()) {
            // Saved game will be loaded in ViewModel init
            android.util.Log.d("BreakoutGame", "Saved game detected, will be loaded by ViewModel")
        } else if (!hasSavedGame && uiState.bricks.isEmpty()) {
            // Initialize new game if no saved game
            viewModel.startNewGame()
        }
    }

    Scaffold(
        topBar = {
            ChoreQuestTopAppBar(
                title = "🎮 Breakout",
                onNavigateBack = {
                    // Save game state before navigating back
                    android.util.Log.d("BreakoutGame", "Back button pressed - saving game state")
                    viewModel.saveGameStateOnPause()
                    onNavigateBack()
                },
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Score, Lives, Level, and High Score Card
            ScoreCard(
                score = uiState.score,
                lives = uiState.lives,
                level = uiState.level,
                highScore = uiState.highScore,
                difficulty = uiState.difficulty,
                onDifficultyClick = { showDifficultyDialog = true }
            )

            // Game Board
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .aspectRatio(BreakoutGameViewModel.BOARD_WIDTH.toFloat() / BreakoutGameViewModel.BOARD_HEIGHT.toFloat()),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1A1A1A))
                        .clickable(enabled = !uiState.isGameStarted || uiState.isGameOver) {
                            // Start game on tap if not started, or restart if game over
                            if (uiState.isGameOver) {
                                // Reset and start new game
                                viewModel.startNewGame()
                                viewModel.startGame()
                            } else if (!uiState.isGameStarted) {
                                // Start the game
                                viewModel.startGame()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (!uiState.isGameStarted || uiState.bricks.isEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "🎮",
                                fontSize = 48.sp
                            )
                            Text(
                                text = "Tap to Start",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (uiState.isGameOver) {
                                Text(
                                    text = "Game Over!",
                                    color = Color(0xFFFF4444),
                                    fontSize = 16.sp
                                )
                            }
                        }
                    } else {
                        GameBoard(
                            bricks = uiState.bricks,
                            paddleX = uiState.paddleX,
                            ballX = uiState.ballX,
                            ballY = uiState.ballY,
                            boardWidth = BreakoutGameViewModel.BOARD_WIDTH,
                            boardHeight = BreakoutGameViewModel.BOARD_HEIGHT
                        )
                    }
                }
            }

            // Spacer between game board and touch control area
            Spacer(modifier = Modifier.height(16.dp))

            // Touch Control Area for Paddle (below game board)
            if (uiState.isGameStarted && !uiState.isPaused && !uiState.isGameOver) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        // Handle initial touch - paddle moves to where finger is placed
                                        // Map finger position so paddle reaches board edges BEFORE finger reaches touch area edges
                                        val areaWidth = size.width
                                        val maxPaddleX = BreakoutGameViewModel.BOARD_WIDTH - BreakoutGameViewModel.PADDLE_WIDTH
                                        // Add padding so paddle reaches edge when finger is still 10% away from edge
                                        val padding = areaWidth * 0.1f
                                        val effectiveWidth = areaWidth - (padding * 2f)
                                        val adjustedX = (offset.x - padding).coerceIn(0f, effectiveWidth)
                                        val normalizedX = (adjustedX / effectiveWidth) * maxPaddleX
                                        viewModel.setPaddlePosition(normalizedX)
                                    },
                                    onDragEnd = {},
                                    onDrag = { change, _ ->
                                        // Handle finger movement - paddle follows finger
                                        // Map finger position so paddle reaches board edges BEFORE finger reaches touch area edges
                                        val currentX = change.position.x
                                        val areaWidth = size.width
                                        val maxPaddleX = BreakoutGameViewModel.BOARD_WIDTH - BreakoutGameViewModel.PADDLE_WIDTH
                                        // Add padding so paddle reaches edge when finger is still 10% away from edge
                                        val padding = areaWidth * 0.1f
                                        val effectiveWidth = areaWidth - (padding * 2f)
                                        val adjustedX = (currentX - padding).coerceIn(0f, effectiveWidth)
                                        val normalizedX = (adjustedX / effectiveWidth) * maxPaddleX
                                        viewModel.setPaddlePosition(normalizedX)
                                    }
                                )
                            }
                ) {
                    val areaWidthDp = maxWidth
                    val paddleWidthDp = areaWidthDp / BreakoutGameViewModel.BOARD_WIDTH * BreakoutGameViewModel.PADDLE_WIDTH
                    val paddleXRatio = uiState.paddleX / BreakoutGameViewModel.BOARD_WIDTH.toFloat()
                    val paddleXCenterDp = (paddleXRatio * areaWidthDp.value).dp
                    val paddleXOffsetDp = paddleXCenterDp - paddleWidthDp / 2
                    
                    // Visual indicator showing paddle position
                    Box(
                        modifier = Modifier
                            .width(paddleWidthDp)
                            .height(4.dp)
                            .offset(x = paddleXOffsetDp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                    
                    // Hint text
                    Text(
                        text = "← Slide to move paddle →",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(top = 24.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                    }
                }
            }

            // Control Buttons (below touch area)
            ControlButtons(
                isGameStarted = uiState.isGameStarted,
                isPaused = uiState.isPaused,
                isGameOver = uiState.isGameOver,
                onPause = { viewModel.pauseGame() },
                onResume = { viewModel.resumeGame() },
                onNewGame = { viewModel.startNewGame() }
            )
        }
    }

    // Game Over Dialog
    if (uiState.showGameOverDialog) {
        GameOverDialog(
            score = uiState.score,
            highScore = uiState.highScore,
            onDismiss = { viewModel.dismissGameOverDialog() },
            onPlayAgain = {
                viewModel.dismissGameOverDialog()
                viewModel.startNewGame()
            }
        )
    }

    // Level Complete Dialog
    if (uiState.showLevelCompleteDialog) {
        LevelCompleteDialog(
            level = uiState.level,
            score = uiState.score,
            onDismiss = { viewModel.dismissLevelCompleteDialog() }
        )
    }

    // Win Dialog
    if (uiState.showWinDialog) {
        WinDialog(
            score = uiState.score,
            highScore = uiState.highScore,
            onDismiss = { viewModel.dismissWinDialog() },
            onPlayAgain = {
                viewModel.dismissWinDialog()
                viewModel.startNewGame()
            }
        )
    }

    // Settings Dialog
    if (showSettingsDialog) {
        GameSettingsDialog(
            soundEnabled = uiState.soundEnabled,
            onSoundToggled = { viewModel.setSoundEnabled(it) },
            onDismiss = { showSettingsDialog = false }
        )
    }

    // Difficulty Selection Dialog
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
}

@Composable
private fun ScoreCard(
    score: Int,
    lives: Int,
    level: Int,
    highScore: Int,
    difficulty: String,
    onDifficultyClick: () -> Unit
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Score",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = score.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Level",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = level.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Lives",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "❤️".repeat(lives),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "High Score",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = highScore.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Difficulty: ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = difficulty.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.clickable(onClick = onDifficultyClick)
                )
            }
        }
    }
}

@Composable
private fun GameBoard(
    bricks: List<Brick>,
    paddleX: Float,
    ballX: Float,
    ballY: Float,
    boardWidth: Int,
    boardHeight: Int
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cellWidth = maxWidth / boardWidth
        val cellHeight = maxHeight / boardHeight
        
        // Draw bricks
        bricks.forEach { brick ->
            Box(
                modifier = Modifier
                    .size(cellWidth, cellHeight)
                    .offset(
                        x = cellWidth * brick.x,
                        y = cellHeight * brick.y
                    )
                    .background(
                        color = Color(brick.color),
                        shape = RoundedCornerShape(4.dp)
                    )
            )
        }
        
        // Draw paddle
        Box(
            modifier = Modifier
                .size(cellWidth * BreakoutGameViewModel.PADDLE_WIDTH, cellHeight * 0.8f)
                .offset(
                    x = cellWidth * paddleX,
                    y = cellHeight * BreakoutGameViewModel.PADDLE_Y
                )
                .background(
                    color = Color(0xFF4CAF50),
                    shape = RoundedCornerShape(8.dp)
                )
        )
        
        // Draw ball
        Box(
            modifier = Modifier
                .size(cellWidth * 0.8f, cellHeight * 0.8f)
                .offset(
                    x = cellWidth * ballX - cellWidth * 0.4f,
                    y = cellHeight * ballY - cellHeight * 0.4f
                )
                .background(
                    color = Color.White,
                    shape = RoundedCornerShape(50)
                )
        )
    }
}

@Composable
private fun ControlButtons(
    isGameStarted: Boolean,
    isPaused: Boolean,
    isGameOver: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onNewGame: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Action buttons (Pause/Resume/New Game) - Always show when game is started
        if (isGameStarted) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isPaused) {
                    Button(
                        onClick = onResume,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Resume")
                    }
                } else {
                    Button(
                        onClick = onPause,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pause")
                    }
                }
                
                Button(
                    onClick = onNewGame,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Game")
                }
            }
        }
    }
}

@Composable
private fun GameOverDialog(
    score: Int,
    highScore: Int,
    onDismiss: () -> Unit,
    onPlayAgain: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Game Over!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Your Score: $score",
                    style = MaterialTheme.typography.bodyLarge
                )
                if (score == highScore && score > 0) {
                    Text(
                        text = "🎉 New High Score! 🎉",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "High Score: $highScore",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onPlayAgain) {
                Text("Play Again")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun LevelCompleteDialog(
    level: Int,
    score: Int,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "🎉 Level $level Complete! 🎉",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Great job! Speed will increase for the next level.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Score: $score",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Continue")
            }
        }
    )
}

@Composable
private fun WinDialog(
    score: Int,
    highScore: Int,
    onDismiss: () -> Unit,
    onPlayAgain: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "🎉 You Win! 🎉",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Congratulations! You cleared all the bricks!",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Your Score: $score",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                if (score == highScore && score > 0) {
                    Text(
                        text = "🎉 New High Score! 🎉",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "High Score: $highScore",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onPlayAgain) {
                Text("Play Again")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
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
                    description = "Slower ball - Great for beginners",
                    isSelected = currentDifficulty == "easy",
                    onClick = { onDifficultySelected("easy") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                DifficultyOption(
                    label = "Medium",
                    description = "Moderate speed - Balanced challenge",
                    isSelected = currentDifficulty == "medium",
                    onClick = { onDifficultySelected("medium") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                DifficultyOption(
                    label = "Hard",
                    description = "Fast ball - For experts!",
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
