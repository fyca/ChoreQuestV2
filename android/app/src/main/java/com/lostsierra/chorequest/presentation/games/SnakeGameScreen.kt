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
import kotlin.math.abs

/**
 * Snake Game screen - Classic snake game with swipe and button controls
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnakeGameScreen(
    onNavigateBack: () -> Unit,
    viewModel: SnakeGameViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showDifficultyDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Save game state when app goes to background
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            android.util.Log.d("SnakeGame", "Lifecycle event: $event")
            if (event == Lifecycle.Event.ON_PAUSE) {
                android.util.Log.d("SnakeGame", "ON_PAUSE detected - saving game state")
                viewModel.saveGameStateOnPause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            android.util.Log.d("SnakeGame", "DisposableEffect onDispose - saving game state")
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Also save when composable is disposed (e.g., navigation away)
            viewModel.saveGameStateOnPause()
        }
    }

    // Initialize game on first load
    LaunchedEffect(Unit) {
        // Check for saved game first
        val hasSavedGame = context.getSharedPreferences("chorequest_games", android.content.Context.MODE_PRIVATE)
            .getBoolean("snake_game_has_saved_game", false)
        
        if (hasSavedGame && uiState.snake.isEmpty()) {
            // Saved game will be loaded in ViewModel init
            android.util.Log.d("SnakeGame", "Saved game detected, will be loaded by ViewModel")
        } else if (!hasSavedGame && uiState.snake.isEmpty()) {
            // Initialize new game if no saved game
            viewModel.startNewGame()
        }
    }

    Scaffold(
        topBar = {
            ChoreQuestTopAppBar(
                title = "🐍 Snake Game",
                onNavigateBack = {
                    // Save game state before navigating back
                    android.util.Log.d("SnakeGame", "Back button pressed - saving game state")
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
            // Score and High Score Card
            ScoreCard(
                score = uiState.score,
                highScore = uiState.highScore,
                difficulty = uiState.difficulty,
                onDifficultyClick = { showDifficultyDialog = true }
            )

            // Game Board
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .aspectRatio(1f),
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
                        }
                        .pointerInput(uiState.isGameStarted && !uiState.isPaused && !uiState.isGameOver) {
                            if (uiState.isGameStarted && !uiState.isPaused && !uiState.isGameOver) {
                                var lastDirectionChange = 0L
                                detectDragGestures(
                                    onDragEnd = {},
                                    onDrag = { change, dragAmount ->
                                        val now = System.currentTimeMillis()
                                        // Throttle direction changes to prevent rapid-fire updates
                                        if (now - lastDirectionChange < 50) return@detectDragGestures
                                        lastDirectionChange = now
                                        
                                        if (abs(dragAmount.x) > abs(dragAmount.y)) {
                                            // Horizontal swipe
                                            if (dragAmount.x > 0) {
                                                viewModel.changeDirection(Direction.RIGHT)
                                            } else {
                                                viewModel.changeDirection(Direction.LEFT)
                                            }
                                        } else {
                                            // Vertical swipe
                                            if (dragAmount.y > 0) {
                                                viewModel.changeDirection(Direction.DOWN)
                                            } else {
                                                viewModel.changeDirection(Direction.UP)
                                            }
                                        }
                                    }
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (!uiState.isGameStarted || uiState.snake.isEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "🐍",
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
                            snake = uiState.snake,
                            food = uiState.food,
                            gridSize = SnakeGameViewModel.GRID_SIZE
                        )
                    }
                }
            }

            // Control Buttons
            ControlButtons(
                isGameStarted = uiState.isGameStarted,
                isPaused = uiState.isPaused,
                isGameOver = uiState.isGameOver,
                onPause = { viewModel.pauseGame() },
                onResume = { viewModel.resumeGame() },
                onNewGame = { viewModel.startNewGame() },
                onDirectionChange = { viewModel.changeDirection(it) }
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
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

@Composable
private fun GameBoard(
    snake: List<Pair<Int, Int>>,
    food: Pair<Int, Int>,
    gridSize: Int
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cellSize = maxWidth / gridSize
        
        // Draw food
        Box(
            modifier = Modifier
                .size(cellSize)
                .offset(
                    x = cellSize * food.first,
                    y = cellSize * food.second
                )
                .background(
                    color = Color(0xFFFF4444),
                    shape = RoundedCornerShape(50)
                )
        )
        
        // Draw snake
        snake.forEachIndexed { index, segment ->
            val isHead = index == 0
            Box(
                modifier = Modifier
                    .size(cellSize * 0.9f)
                    .offset(
                        x = cellSize * segment.first + cellSize * 0.05f,
                        y = cellSize * segment.second + cellSize * 0.05f
                    )
                    .background(
                        color = if (isHead) Color(0xFF4CAF50) else Color(0xFF66BB6A),
                        shape = RoundedCornerShape(4.dp)
                    )
            )
        }
    }
}

@Composable
private fun ControlButtons(
    isGameStarted: Boolean,
    isPaused: Boolean,
    isGameOver: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onNewGame: () -> Unit,
    onDirectionChange: (Direction) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Action buttons (Pause/Resume/New Game)
        // Only show buttons when game is started
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
        
        // Direction buttons (only show when game is started and not paused)
        if (isGameStarted && !isPaused && !isGameOver) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Up button
                DirectionButton(
                    icon = Icons.Default.KeyboardArrowUp,
                    onClick = { onDirectionChange(Direction.UP) },
                    modifier = Modifier.size(60.dp)
                )
                
                // Middle row (Left, Down, Right)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DirectionButton(
                        icon = Icons.Default.KeyboardArrowLeft,
                        onClick = { onDirectionChange(Direction.LEFT) },
                        modifier = Modifier.size(60.dp)
                    )
                    DirectionButton(
                        icon = Icons.Default.KeyboardArrowDown,
                        onClick = { onDirectionChange(Direction.DOWN) },
                        modifier = Modifier.size(60.dp)
                    )
                    DirectionButton(
                        icon = Icons.Default.KeyboardArrowRight,
                        onClick = { onDirectionChange(Direction.RIGHT) },
                        modifier = Modifier.size(60.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DirectionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(32.dp)
        )
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
                    description = "Slower speed - Great for beginners",
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
                    description = "Fast speed - For experts!",
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
