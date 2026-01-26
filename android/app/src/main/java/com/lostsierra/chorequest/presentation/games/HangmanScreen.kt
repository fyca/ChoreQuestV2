package com.lostsierra.chorequest.presentation.games

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lostsierra.chorequest.presentation.components.CelebrationAnimation
import com.lostsierra.chorequest.presentation.components.CelebrationStyle
import com.lostsierra.chorequest.presentation.components.ChoreQuestTopAppBar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hangman game screen - Classic spelling game
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HangmanScreen(
    onNavigateBack: () -> Unit,
    viewModel: HangmanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDifficultyDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Celebration animation
    if (uiState.showWinDialog) {
        CelebrationAnimation(
            style = CelebrationStyle.FIREWORKS,
            pointsEarned = uiState.currentScore,
            onAnimationComplete = {
                viewModel.dismissWinDialog()
            }
        )
    }

    Scaffold(
        topBar = {
            ChoreQuestTopAppBar(
                title = "🎯 Hangman",
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
                currentScore = uiState.currentScore,
                onDifficultyClick = { showDifficultyDialog = true }
            )

            // Theme Display
            if (uiState.theme.isNotEmpty()) {
                ThemeDisplay(theme = uiState.theme)
            }

            // Hangman Drawing
            HangmanDrawing(
                wrongGuesses = uiState.wrongGuesses,
                maxWrongGuesses = uiState.maxWrongGuesses
            )

            // Word Display
            WordDisplay(word = uiState.displayWord)

            // Hint Button
            if (!uiState.isGameWon && !uiState.isGameLost) {
                HintButton(
                    hintsUsed = uiState.hintsUsed,
                    onHintClick = { viewModel.useHint() }
                )
            }

            // Letter Buttons
            if (!uiState.isGameWon && !uiState.isGameLost) {
                LetterButtons(
                    guessedLetters = uiState.guessedLetters,
                    onLetterClick = { letter ->
                        viewModel.guessLetter(letter)
                    }
                )
            }

            // Game Over Buttons
            if (uiState.isGameWon || uiState.isGameLost) {
                GameOverButtons(
                    isWon = uiState.isGameWon,
                    correctWord = uiState.currentWord,
                    onPlayAgain = {
                        viewModel.startNewGame()
                    }
                )
            }

            // New Game Button
            if (!uiState.isGameWon && !uiState.isGameLost) {
                Button(
                    onClick = { viewModel.startNewGame() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Word")
                }
            }
        }
    }

    // Win Dialog
    if (uiState.showWinDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissWinDialog() },
            title = { Text("🎉 You Won! 🎉") },
            text = {
                Column {
                    Text("Congratulations! You guessed the word!")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Score: ${uiState.currentScore}")
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissWinDialog() }) {
                    Text("OK")
                }
            }
        )
    }

    // Lose Dialog
    if (uiState.showLoseDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissLoseDialog() },
            title = { Text("😔 Game Over") },
            text = {
                Column {
                    Text("The word was: ${uiState.currentWord}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Better luck next time!")
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    viewModel.dismissLoseDialog()
                    viewModel.startNewGame()
                }) {
                    Text("Play Again")
                }
            }
        )
    }

    // Extra Guesses Dialog (Easy mode only)
    if (uiState.showExtraGuessesDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissExtraGuessesDialog() },
            title = { Text("Need More Guesses?") },
            text = {
                Column {
                    Text("You've used all your guesses, but you can get 3 more!")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("This will add fingers to the hangman's hands.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Would you like to continue?")
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.grantExtraGuesses() }) {
                    Text("Yes, Give Me More!")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.declineExtraGuesses() }) {
                    Text("No, I Give Up")
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
    currentScore: Int,
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

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Score",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentScore.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun HangmanDrawing(
    wrongGuesses: Int,
    maxWrongGuesses: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val width = size.width
            val height = size.height
            val strokeWidth = 8.dp.toPx()
            val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)

            // Drawing area
            val baseY = height * 0.9f
            val gallowsHeight = height * 0.6f
            val headRadius = width * 0.08f
            val bodyLength = height * 0.2f
            val armLength = width * 0.15f
            val legLength = height * 0.2f

            // Draw gallows (always visible)
            val gallowsX = width * 0.3f
            val gallowsTopY = height * 0.1f

            // Brown color for gallows
            val brownColor = Color(0xFF8B4513) // Saddle brown
            
            // Base
            drawLine(
                color = brownColor,
                start = Offset(gallowsX - width * 0.15f, baseY),
                end = Offset(gallowsX + width * 0.15f, baseY),
                strokeWidth = strokeWidth
            )

            // Vertical post
            drawLine(
                color = brownColor,
                start = Offset(gallowsX, baseY),
                end = Offset(gallowsX, gallowsTopY + headRadius * 2),
                strokeWidth = strokeWidth
            )

            // Horizontal beam
            drawLine(
                color = brownColor,
                start = Offset(gallowsX, gallowsTopY),
                end = Offset(gallowsX + width * 0.2f, gallowsTopY),
                strokeWidth = strokeWidth
            )

            // Rope
            val ropeX = gallowsX + width * 0.2f
            drawLine(
                color = brownColor,
                start = Offset(ropeX, gallowsTopY),
                end = Offset(ropeX, gallowsTopY + headRadius * 2),
                strokeWidth = strokeWidth
            )

            // Draw hangman based on wrong guesses
            val headCenter = Offset(ropeX, gallowsTopY + headRadius * 2)
            val bodyTop = Offset(ropeX, headCenter.y + headRadius)
            val bodyBottom = Offset(ropeX, bodyTop.y + bodyLength)

            if (wrongGuesses >= 1) {
                // Head
                drawCircle(
                    color = Color.Black,
                    radius = headRadius,
                    center = headCenter,
                    style = stroke
                )
            }

            if (wrongGuesses >= 2) {
                // Body
                drawLine(
                    color = Color.Black,
                    start = bodyTop,
                    end = bodyBottom,
                    strokeWidth = strokeWidth
                )
            }

            var leftArmEnd: Offset? = null
            var rightArmEnd: Offset? = null

            if (wrongGuesses >= 3) {
                // Left arm
                leftArmEnd = Offset(
                    bodyTop.x - armLength * cos(PI / 4).toFloat(),
                    bodyTop.y + armLength * sin(PI / 4).toFloat()
                )
                drawLine(
                    color = Color.Black,
                    start = bodyTop,
                    end = leftArmEnd,
                    strokeWidth = strokeWidth
                )
            }

            if (wrongGuesses >= 4) {
                // Right arm
                rightArmEnd = Offset(
                    bodyTop.x + armLength * cos(PI / 4).toFloat(),
                    bodyTop.y + armLength * sin(PI / 4).toFloat()
                )
                drawLine(
                    color = Color.Black,
                    start = bodyTop,
                    end = rightArmEnd,
                    strokeWidth = strokeWidth
                )
            }

            if (wrongGuesses >= 5) {
                // Left leg
                val leftLegEnd = Offset(
                    bodyBottom.x - legLength * cos(PI / 4).toFloat(),
                    bodyBottom.y + legLength * sin(PI / 4).toFloat()
                )
                drawLine(
                    color = Color.Black,
                    start = bodyBottom,
                    end = leftLegEnd,
                    strokeWidth = strokeWidth
                )
            }

            if (wrongGuesses >= 6) {
                // Right leg
                val rightLegEnd = Offset(
                    bodyBottom.x + legLength * cos(PI / 4).toFloat(),
                    bodyBottom.y + legLength * sin(PI / 4).toFloat()
                )
                drawLine(
                    color = Color.Black,
                    start = bodyBottom,
                    end = rightLegEnd,
                    strokeWidth = strokeWidth
                )
            }

            // Draw fingers for extra guesses (7, 8, 9) - 3 fingers on each hand
            leftArmEnd?.let { leftHand ->
                if (wrongGuesses >= 7) {
                    // First finger on left hand
                    val finger1End = Offset(
                        leftHand.x - headRadius * 0.5f * cos(PI / 3).toFloat(),
                        leftHand.y + headRadius * 0.5f * sin(PI / 3).toFloat()
                    )
                    drawLine(
                        color = Color.Black,
                        start = leftHand,
                        end = finger1End,
                        strokeWidth = strokeWidth * 0.6f
                    )
                }
                
                if (wrongGuesses >= 8) {
                    // Second finger on left hand
                    val finger2End = Offset(
                        leftHand.x - headRadius * 0.5f * cos(PI / 4).toFloat(),
                        leftHand.y + headRadius * 0.5f * sin(PI / 4).toFloat()
                    )
                    drawLine(
                        color = Color.Black,
                        start = leftHand,
                        end = finger2End,
                        strokeWidth = strokeWidth * 0.6f
                    )
                }
                
                if (wrongGuesses >= 9) {
                    // Third finger on left hand
                    val finger3End = Offset(
                        leftHand.x - headRadius * 0.5f * cos(PI / 6).toFloat(),
                        leftHand.y + headRadius * 0.5f * sin(PI / 6).toFloat()
                    )
                    drawLine(
                        color = Color.Black,
                        start = leftHand,
                        end = finger3End,
                        strokeWidth = strokeWidth * 0.6f
                    )
                }
            }

            rightArmEnd?.let { rightHand ->
                if (wrongGuesses >= 7) {
                    // First finger on right hand
                    val finger1End = Offset(
                        rightHand.x + headRadius * 0.5f * cos(PI / 3).toFloat(),
                        rightHand.y + headRadius * 0.5f * sin(PI / 3).toFloat()
                    )
                    drawLine(
                        color = Color.Black,
                        start = rightHand,
                        end = finger1End,
                        strokeWidth = strokeWidth * 0.6f
                    )
                }
                
                if (wrongGuesses >= 8) {
                    // Second finger on right hand
                    val finger2End = Offset(
                        rightHand.x + headRadius * 0.5f * cos(PI / 4).toFloat(),
                        rightHand.y + headRadius * 0.5f * sin(PI / 4).toFloat()
                    )
                    drawLine(
                        color = Color.Black,
                        start = rightHand,
                        end = finger2End,
                        strokeWidth = strokeWidth * 0.6f
                    )
                }
                
                if (wrongGuesses >= 9) {
                    // Third finger on right hand
                    val finger3End = Offset(
                        rightHand.x + headRadius * 0.5f * cos(PI / 6).toFloat(),
                        rightHand.y + headRadius * 0.5f * sin(PI / 6).toFloat()
                    )
                    drawLine(
                        color = Color.Black,
                        start = rightHand,
                        end = finger3End,
                        strokeWidth = strokeWidth * 0.6f
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeDisplay(theme: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
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
                text = "Theme:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = theme,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun WordDisplay(word: String) {
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = word,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 8.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun HintButton(
    hintsUsed: Int,
    onHintClick: () -> Unit
) {
    Button(
        onClick = onHintClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiary
        )
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (hintsUsed > 0) 
                "Get Another Hint (Used: $hintsUsed)" 
            else 
                "Get Hint",
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LetterButtons(
    guessedLetters: Set<Char>,
    onLetterClick: (Char) -> Unit
) {
    // QWERTY keyboard layout
    val keyboardRows = listOf(
        listOf('Q', 'W', 'E', 'R', 'T', 'Y', 'U', 'I', 'O', 'P'),
        listOf('A', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L'),
        listOf('Z', 'X', 'C', 'V', 'B', 'N', 'M')
    )
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Guess a Letter",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            // Create rows in QWERTY layout
            keyboardRows.forEach { rowLetters ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    rowLetters.forEach { letter ->
                        val isGuessed = letter in guessedLetters
                        LetterButton(
                            letter = letter,
                            isGuessed = isGuessed,
                            onClick = { onLetterClick(letter) }
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun LetterButton(
    letter: Char,
    isGuessed: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isGuessed) 
        MaterialTheme.colorScheme.surfaceVariant 
    else 
        MaterialTheme.colorScheme.primary
    
    val textColor = if (isGuessed) 
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    else 
        Color(0xFFFFFFFF) // Explicit white color
    
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(enabled = !isGuessed, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter.toString(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
private fun GameOverButtons(
    isWon: Boolean,
    correctWord: String,
    onPlayAgain: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isWon) 
                Color(0xFF4CAF50).copy(alpha = 0.1f)
            else 
                Color(0xFFF44336).copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isWon) "🎉 You Won! 🎉" else "😔 Game Over",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (isWon) Color(0xFF4CAF50) else Color(0xFFF44336)
            )
            if (!isWon) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "The word was: $correctWord",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onPlayAgain,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isWon) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
            ) {
                Text("Play Again")
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
                    description = "Short words (3-4 letters) - Perfect for beginners!",
                    isSelected = currentDifficulty == "easy",
                    onClick = { onDifficultySelected("easy") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                DifficultyOption(
                    label = "Medium",
                    description = "Medium words (6-8 letters) - Good challenge",
                    isSelected = currentDifficulty == "medium",
                    onClick = { onDifficultySelected("medium") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                DifficultyOption(
                    label = "Hard",
                    description = "Long words (9+ letters) - Expert level!",
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
