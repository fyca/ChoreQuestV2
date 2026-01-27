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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.hilt.navigation.compose.hiltViewModel
import com.lostsierra.chorequest.presentation.components.CelebrationAnimation
import com.lostsierra.chorequest.presentation.components.CelebrationStyle
import com.lostsierra.chorequest.presentation.components.ChoreQuestTopAppBar

/**
 * Word Scramble game screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordScrambleScreen(
    onNavigateBack: () -> Unit,
    viewModel: WordScrambleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showGradeDialog by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var tilePositions by remember { mutableStateOf<Map<Int, androidx.compose.ui.geometry.Rect>>(emptyMap()) }
    var draggedOverTileId by remember { mutableStateOf<Int?>(null) }

    // Celebration animation
    if (uiState.showCelebration) {
        CelebrationAnimation(
            style = CelebrationStyle.CONFETTI,
            pointsEarned = uiState.score,
            onAnimationComplete = {
                viewModel.onCelebrationComplete()
            }
        )
    }

    Scaffold(
        topBar = {
            ChoreQuestTopAppBar(
                title = "🔤 Word Scramble",
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
            // Grade and Score Card
            GradeAndScoreCard(
                gradeLevel = uiState.gradeLevel,
                highScore = uiState.highScore,
                onGradeClick = { showGradeDialog = true }
            )

            if (!uiState.isGameComplete) {
                // Progress Card
                ProgressCard(
                    currentWord = uiState.currentWordIndex + 1,
                    totalWords = uiState.totalWords,
                    score = uiState.score,
                    correctAnswers = uiState.correctAnswers,
                    accuracy = if (uiState.currentWordIndex > 0) {
                        (uiState.correctAnswers * 100) / uiState.currentWordIndex
                    } else {
                        0
                    },
                    theme = uiState.theme
                )

                // Letter Tiles Card
                LetterTilesCard(
                    letterTiles = uiState.letterTiles,
                    isDragging = uiState.isDragging,
                    draggedTileId = uiState.draggedTileId,
                    dragOffset = dragOffset,
                    draggedOverTileId = draggedOverTileId,
                    showResult = uiState.showResult,
                    isCorrect = uiState.isCorrect,
                    onTileDragStart = { tileId ->
                        viewModel.startDrag(tileId)
                        dragOffset = Offset.Zero
                        draggedOverTileId = null
                    },
                    onTileDrag = { tileId, offset ->
                        dragOffset = offset
                        // Find which tile we're dragging over by checking if dragged center is within another tile's bounds
                        val draggedTileRect = tilePositions[tileId]
                        if (draggedTileRect != null) {
                            // Calculate current center of dragged tile
                            val currentCenterX = draggedTileRect.center.x + offset.x
                            val currentCenterY = draggedTileRect.center.y + offset.y
                            
                            // Find overlapping tile
                            var foundTarget: Int? = null
                            for ((otherTileId, otherRect) in tilePositions) {
                                if (otherTileId != tileId) {
                                    // Check if current center is within the other tile's bounds
                                    if (currentCenterX >= otherRect.left &&
                                        currentCenterX <= otherRect.right &&
                                        currentCenterY >= otherRect.top &&
                                        currentCenterY <= otherRect.bottom) {
                                        foundTarget = otherTileId
                                        break
                                    }
                                }
                            }
                            draggedOverTileId = foundTarget
                        }
                    },
                    onTileDragEnd = { tileId ->
                        val targetTileId = draggedOverTileId
                        if (targetTileId != null && targetTileId != tileId) {
                            val draggedTile = uiState.letterTiles.find { it.id == tileId }
                            val targetTile = uiState.letterTiles.find { it.id == targetTileId }
                            if (draggedTile != null && targetTile != null) {
                                viewModel.swapLetterPositions(draggedTile.position, targetTile.position)
                            }
                        }
                        viewModel.endDrag()
                        dragOffset = Offset.Zero
                        draggedOverTileId = null
                    },
                    onTilePositioned = { tileId, rect ->
                        tilePositions = tilePositions + (tileId to rect)
                    }
                )

                // Hint Button
                if (!uiState.showResult) {
                    Button(
                        onClick = { viewModel.useHint() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isGameComplete,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Hint")
                    }
                }

                // Next Word Button (only shown after result)
                if (uiState.showResult) {
                    Button(
                        onClick = {
                            viewModel.nextWord()
                            tilePositions = emptyMap()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = if (uiState.currentWordIndex < uiState.totalWords - 1) 
                                "Next Word" 
                            else 
                                "See Results"
                        )
                    }
                }
            } else {
                // Results screen
                ResultsCard(
                    score = uiState.score,
                    totalWords = uiState.totalWords,
                    correctAnswers = uiState.correctAnswers,
                    onPlayAgain = {
                        viewModel.startNewGame()
                        tilePositions = emptyMap()
                    },
                    onBackToMenu = onNavigateBack
                )
            }

            // New Game button
            if (!uiState.isGameComplete) {
                Button(
                    onClick = {
                        viewModel.startNewGame()
                        tilePositions = emptyMap()
                    },
                    modifier = Modifier.fillMaxWidth(),
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
private fun GradeAndScoreCard(
    gradeLevel: GradeLevel,
    highScore: Int,
    onGradeClick: () -> Unit
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
    }
}

@Composable
private fun ProgressCard(
    currentWord: Int,
    totalWords: Int,
    score: Int,
    correctAnswers: Int,
    accuracy: Int,
    theme: String
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
                Column {
                    Text(
                        text = "Word $currentWord of $totalWords",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (theme.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Category: $theme",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Score: $score",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Accuracy: $accuracy%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = currentWord.toFloat() / totalWords.toFloat(),
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun LetterTilesCard(
    letterTiles: List<LetterTile>,
    isDragging: Boolean,
    draggedTileId: Int?,
    dragOffset: Offset,
    draggedOverTileId: Int?,
    showResult: Boolean,
    isCorrect: Boolean,
    onTileDragStart: (Int) -> Unit,
    onTileDrag: (Int, Offset) -> Unit,
    onTileDragEnd: (Int) -> Unit,
    onTilePositioned: (Int, androidx.compose.ui.geometry.Rect) -> Unit
) {
    val backgroundColor = when {
        showResult && isCorrect -> Color(0xFF4CAF50) // Green for correct
        showResult && !isCorrect -> Color(0xFFF44336) // Red for wrong
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Calculate dynamic tile size based on available width and word length
            val availableWidth = maxWidth - 0.dp // Already accounting for padding
            val spacingBetweenTiles = 8.dp
            val totalSpacing = if (letterTiles.size > 1) {
                spacingBetweenTiles * (letterTiles.size - 1)
            } else {
                0.dp
            }
            val calculatedSize = if (letterTiles.isNotEmpty()) {
                (availableWidth - totalSpacing) / letterTiles.size
            } else {
                56.dp
            }
            // Clamp between minimum (30.dp) and maximum (56.dp) sizes
            val tileSize = calculatedSize.coerceIn(30.dp, 56.dp)
            
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    letterTiles.sortedBy { it.position }.forEachIndexed { index, tile ->
                        LetterTile(
                            tile = tile,
                            isDragging = draggedTileId == tile.id,
                            isDragTarget = draggedOverTileId == tile.id,
                            dragOffset = if (draggedTileId == tile.id) dragOffset else Offset.Zero,
                            showResult = showResult,
                            isCorrect = isCorrect,
                            isLocked = tile.isLocked,
                            tileSize = tileSize,
                            onDragStart = { onTileDragStart(tile.id) },
                            onDrag = { offset -> onTileDrag(tile.id, offset) },
                            onDragEnd = { onTileDragEnd(tile.id) },
                            onPositioned = { rect -> onTilePositioned(tile.id, rect) },
                            modifier = Modifier
                        )
                        // Add spacing between tiles, but not after the last one
                        if (index < letterTiles.size - 1) {
                            Spacer(modifier = Modifier.width(spacingBetweenTiles))
                        }
                    }
                }
                if (showResult) {
                    Icon(
                        imageVector = if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LetterTile(
    tile: LetterTile,
    isDragging: Boolean,
    isDragTarget: Boolean,
    dragOffset: Offset,
    showResult: Boolean,
    isCorrect: Boolean,
    isLocked: Boolean,
    tileSize: Dp,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onPositioned: (androidx.compose.ui.geometry.Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        showResult && isCorrect -> Color(0xFF66BB6A) // Lighter green
        showResult && !isCorrect -> Color(0xFFEF5350) // Lighter red
        isLocked -> Color(0xFF81C784) // Light green for locked letters
        isDragTarget -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    val textColor = when {
        showResult -> Color.White
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    // Calculate text style based on tile size
    val textStyle = when {
        tileSize >= 50.dp -> MaterialTheme.typography.headlineMedium
        tileSize >= 45.dp -> MaterialTheme.typography.titleLarge
        else -> MaterialTheme.typography.titleMedium
    }
    
    Card(
        modifier = modifier
            .size(tileSize)
            .zIndex(if (isDragging) 100f else 0f)
            .graphicsLayer {
                translationX = if (isDragging) dragOffset.x else 0f
                translationY = if (isDragging) dragOffset.y else 0f
            }
            .alpha(if (isDragging) 0.9f else 1f)
            .onGloballyPositioned { coordinates ->
                // Calculate base position (without graphicsLayer translation)
                val basePosition = coordinates.positionInRoot()
                val rect = if (isDragging) {
                    // During drag, subtract the dragOffset to get the base position
                    androidx.compose.ui.geometry.Rect(
                        offset = Offset(
                            basePosition.x - dragOffset.x,
                            basePosition.y - dragOffset.y
                        ),
                        size = androidx.compose.ui.geometry.Size(
                            coordinates.size.width.toFloat(),
                            coordinates.size.height.toFloat()
                        )
                    )
                } else {
                    androidx.compose.ui.geometry.Rect(
                        offset = basePosition,
                        size = androidx.compose.ui.geometry.Size(
                            coordinates.size.width.toFloat(),
                            coordinates.size.height.toFloat()
                        )
                    )
                }
                onPositioned(rect)
            }
            .pointerInput(tile.id) {
                if (!showResult && !isLocked) {
                    var cumulativeOffset = Offset.Zero
                    detectDragGestures(
                        onDragStart = { 
                            cumulativeOffset = Offset.Zero
                            onDragStart() 
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            cumulativeOffset = cumulativeOffset + dragAmount
                            onDrag(cumulativeOffset)
                        },
                        onDragEnd = { 
                            onDragEnd()
                        }
                    )
                }
            },
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 8.dp else 2.dp
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = tile.letter.toString(),
                style = textStyle,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

@Composable
private fun ResultsCard(
    score: Int,
    totalWords: Int,
    correctAnswers: Int,
    onPlayAgain: () -> Unit,
    onBackToMenu: () -> Unit
) {
    val percentage = (correctAnswers * 100) / totalWords
    val emoji = when {
        percentage >= 90 -> "🏆"
        percentage >= 70 -> "⭐"
        percentage >= 50 -> "👍"
        else -> "💪"
    }
    val message = when {
        percentage >= 90 -> "Outstanding! You're a word master!"
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
                text = "$correctAnswers / $totalWords",
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
                text = "Total Score: $score",
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
