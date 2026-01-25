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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.hilt.navigation.compose.hiltViewModel
import com.lostsierra.chorequest.presentation.components.CelebrationAnimation
import com.lostsierra.chorequest.presentation.components.CelebrationStyle
import com.lostsierra.chorequest.presentation.components.ChoreQuestTopAppBar

/**
 * Math Game screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MathGameScreen(
    onNavigateBack: () -> Unit,
    viewModel: MathGameViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showDifficultyDialog by remember { mutableStateOf(false) }
    var showProblemTypesDialog by remember { mutableStateOf(false) }
    var showGradeDialog by remember { mutableStateOf(false) }

    // Celebration animation
    if (uiState.showCelebration) {
        CelebrationAnimation(
            style = CelebrationStyle.CONFETTI,
            pointsEarned = uiState.totalPoints,
            onAnimationComplete = {
                viewModel.onCelebrationComplete()
            }
        )
    }

    Scaffold(
        topBar = {
            ChoreQuestTopAppBar(
                title = "🔢 Math Game",
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Grade, Difficulty, High Score, and Problem Types Card
            GradeDifficultyAndScoreCard(
                gradeLevel = uiState.gradeLevel,
                difficulty = uiState.difficulty,
                highScore = uiState.highScore,
                problemTypes = uiState.problemTypes,
                onGradeClick = { showGradeDialog = true },
                onDifficultyClick = { showDifficultyDialog = true },
                onProblemTypesClick = { showProblemTypesDialog = true }
            )

            // Progress indicator
            if (!uiState.isGameComplete && uiState.problems.isNotEmpty() && uiState.currentProblemIndex < uiState.problems.size) {
                ProgressCard(
                    currentProblem = uiState.currentProblemIndex + 1,
                    totalProblems = uiState.problems.size,
                    score = uiState.score,
                    totalPoints = uiState.totalPoints
                )

                // Problem card
                if (uiState.currentProblemIndex < uiState.problems.size) {
                    ProblemCard(
                        problem = uiState.problems[uiState.currentProblemIndex],
                        selectedAnswer = uiState.selectedAnswer,
                        showResult = uiState.showResult,
                        onAnswerSelected = { answerIndex ->
                            viewModel.selectAnswer(answerIndex)
                        }
                    )

                    // Next button
                    if (uiState.showResult) {
                        Button(
                            onClick = { viewModel.nextProblem() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = if (uiState.currentProblemIndex < uiState.problems.size - 1) 
                                    "Next Problem" 
                                else 
                                    "See Results"
                            )
                        }
                    }
                }
            } else if (uiState.isGameComplete) {
                // Results screen
                ResultsCard(
                    score = uiState.score,
                    totalProblems = uiState.problems.size,
                    totalPoints = uiState.totalPoints,
                    onPlayAgain = { viewModel.startNewGame() },
                    onBackToMenu = onNavigateBack
                )
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.startNewGame() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Game")
                }
            }
        }
    }

    // Grade selection dialog
    if (showGradeDialog) {
        GradeSelectionDialog(
            currentGrade = uiState.gradeLevel,
            onGradeSelected = { grade ->
                viewModel.setGradeLevel(grade)
                showGradeDialog = false
            },
            onDismiss = { showGradeDialog = false }
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

    // Problem types selection dialog
    if (showProblemTypesDialog) {
        ProblemTypesSelectionDialog(
            currentTypes = uiState.problemTypes,
            onTypesSelected = { types ->
                viewModel.setProblemTypes(types)
                showProblemTypesDialog = false
            },
            onDismiss = { showProblemTypesDialog = false }
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
private fun GradeDifficultyAndScoreCard(
    gradeLevel: GradeLevel,
    difficulty: String,
    highScore: Int,
    problemTypes: Set<MathOperation>,
    onGradeClick: () -> Unit,
    onDifficultyClick: () -> Unit,
    onProblemTypesClick: () -> Unit
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
                        text = "Grade Level",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(onClick = onGradeClick)
                    ) {
                        Text(
                            text = gradeLevel.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "Change grade",
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
                        text = "$highScore pts",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Divider()
            
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "Change difficulty",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Problem Types",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(onClick = onProblemTypesClick)
                    ) {
                        val typesText = when {
                            problemTypes.size == 4 -> "All Types"
                            problemTypes.contains(MathOperation.ADDITION) && problemTypes.size == 1 -> "Addition Only"
                            problemTypes.contains(MathOperation.SUBTRACTION) && problemTypes.size == 1 -> "Subtraction Only"
                            problemTypes.contains(MathOperation.MULTIPLICATION) && problemTypes.size == 1 -> "Multiplication Only"
                            problemTypes.contains(MathOperation.DIVISION) && problemTypes.size == 1 -> "Division Only"
                            else -> "${problemTypes.size} Types"
                        }
                        Text(
                            text = typesText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "Change problem types",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(
    currentProblem: Int,
    totalProblems: Int,
    score: Int,
    totalPoints: Int
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
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Problem $currentProblem of $totalProblems",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Score: $score",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Points: $totalPoints",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = currentProblem.toFloat() / totalProblems.toFloat(),
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun ProblemCard(
    problem: MathProblem,
    selectedAnswer: Int?,
    showResult: Boolean,
    onAnswerSelected: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = problem.questionText,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            problem.answerChoices.forEachIndexed { index, answer ->
                AnswerButton(
                    text = answer.toString(),
                    isSelected = selectedAnswer == index,
                    isCorrect = problem.correctAnswerIndex == index,
                    showResult = showResult,
                    onClick = {
                        if (!showResult) {
                            onAnswerSelected(index)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AnswerButton(
    text: String,
    isSelected: Boolean,
    isCorrect: Boolean,
    showResult: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        showResult && isCorrect -> Color(0xFF4CAF50) // Green for correct
        showResult && isSelected && !isCorrect -> Color(0xFFF44336) // Red for wrong
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = when {
        showResult && (isCorrect || (isSelected && !isCorrect)) -> Color.White
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ), label = "buttonScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(enabled = !showResult, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            if (showResult) {
                val icon = if (isCorrect) {
                    Icons.Default.CheckCircle
                } else if (isSelected) {
                    Icons.Default.Cancel
                } else {
                    null
                }
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultsCard(
    score: Int,
    totalProblems: Int,
    totalPoints: Int,
    onPlayAgain: () -> Unit,
    onBackToMenu: () -> Unit
) {
    val percentage = (score * 100 / totalProblems)
    val emoji = when {
        percentage >= 90 -> "🏆"
        percentage >= 70 -> "⭐"
        percentage >= 50 -> "👍"
        else -> "💪"
    }
    val message = when {
        percentage >= 90 -> "Outstanding! You're a math master!"
        percentage >= 70 -> "Great job! You're doing amazing!"
        percentage >= 50 -> "Good effort! Keep practicing!"
        else -> "Keep practicing! You'll get better!"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = emoji,
                fontSize = 64.sp
            )
            Text(
                text = "Game Complete!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$score / $totalProblems",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Total Points: $totalPoints",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(16.dp))
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
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Play Again")
                }
                OutlinedButton(
                    onClick = onBackToMenu,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Back to Games")
                }
            }
        }
    }
}

@Composable
private fun GradeSelectionDialog(
    currentGrade: GradeLevel,
    onGradeSelected: (GradeLevel) -> Unit,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Grade Level") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(scrollState)
            ) {
                GradeLevel.values().forEach { grade ->
                    GradeOption(
                        label = grade.displayName,
                        isSelected = currentGrade == grade,
                        onClick = { onGradeSelected(grade) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
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
private fun GradeOption(
    label: String,
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
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
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
                    description = "Simple problems - Perfect for beginners!",
                    isSelected = currentDifficulty == "easy",
                    onClick = { onDifficultySelected("easy") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                DifficultyOption(
                    label = "Medium",
                    description = "Moderate problems - Good challenge",
                    isSelected = currentDifficulty == "medium",
                    onClick = { onDifficultySelected("medium") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                DifficultyOption(
                    label = "Hard",
                    description = "Challenging problems - For experts!",
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
private fun ProblemTypesSelectionDialog(
    currentTypes: Set<MathOperation>,
    onTypesSelected: (Set<MathOperation>) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Problem Types") },
        text = {
            Column {
                ProblemTypeOption(
                    label = "Addition (+)",
                    isSelected = currentTypes.contains(MathOperation.ADDITION),
                    onClick = {
                        val newTypes = if (currentTypes.contains(MathOperation.ADDITION)) {
                            currentTypes - MathOperation.ADDITION
                        } else {
                            currentTypes + MathOperation.ADDITION
                        }
                        if (newTypes.isNotEmpty()) {
                            onTypesSelected(newTypes)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ProblemTypeOption(
                    label = "Subtraction (-)",
                    isSelected = currentTypes.contains(MathOperation.SUBTRACTION),
                    onClick = {
                        val newTypes = if (currentTypes.contains(MathOperation.SUBTRACTION)) {
                            currentTypes - MathOperation.SUBTRACTION
                        } else {
                            currentTypes + MathOperation.SUBTRACTION
                        }
                        if (newTypes.isNotEmpty()) {
                            onTypesSelected(newTypes)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ProblemTypeOption(
                    label = "Multiplication (×)",
                    isSelected = currentTypes.contains(MathOperation.MULTIPLICATION),
                    onClick = {
                        val newTypes = if (currentTypes.contains(MathOperation.MULTIPLICATION)) {
                            currentTypes - MathOperation.MULTIPLICATION
                        } else {
                            currentTypes + MathOperation.MULTIPLICATION
                        }
                        if (newTypes.isNotEmpty()) {
                            onTypesSelected(newTypes)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ProblemTypeOption(
                    label = "Division (÷)",
                    isSelected = currentTypes.contains(MathOperation.DIVISION),
                    onClick = {
                        val newTypes = if (currentTypes.contains(MathOperation.DIVISION)) {
                            currentTypes - MathOperation.DIVISION
                        } else {
                            currentTypes + MathOperation.DIVISION
                        }
                        if (newTypes.isNotEmpty()) {
                            onTypesSelected(newTypes)
                        }
                    }
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
private fun ProblemTypeOption(
    label: String,
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
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
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
