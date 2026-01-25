package com.lostsierra.chorequest.presentation.games

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lostsierra.chorequest.presentation.components.ChoreQuestTopAppBar
import com.lostsierra.chorequest.presentation.components.OutlinedText
import com.lostsierra.chorequest.presentation.theme.*

/**
 * Games area screen for children
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTicTacToe: () -> Unit = {},
    onNavigateToChoreQuiz: () -> Unit = {},
    onNavigateToMemoryMatch: () -> Unit = {},
    onNavigateToRockPaperScissors: () -> Unit = {},
    onNavigateToJigsawPuzzle: () -> Unit = {},
    onNavigateToSnakeGame: () -> Unit = {},
    onNavigateToBreakoutGame: () -> Unit = {},
    onNavigateToMathGame: () -> Unit = {}
) {
    val games = remember {
        listOf(
            Game(
                id = "memory",
                title = "Memory Match",
                emoji = "🧠",
                description = "Match the cards!",
                color = Color(0xFF4CAF50),
                isLocked = false
            ),
            Game(
                id = "quiz",
                title = "Chore Quiz",
                emoji = "❓",
                description = "Test your knowledge",
                color = Color(0xFF2196F3),
                isLocked = false
            ),
            Game(
                id = "rock-paper-scissors",
                title = "Rock Paper Scissors",
                emoji = "✂️",
                description = "Play against AI",
                color = Color(0xFFFF9800),
                isLocked = false
            ),
            Game(
                id = "puzzle",
                title = "Jigsaw Puzzle",
                emoji = "🧩",
                description = "Complete the puzzle",
                color = Color(0xFF9C27B0),
                isLocked = false
            ),
            Game(
                id = "snake",
                title = "Snake Game",
                emoji = "🐍",
                description = "Classic snake!",
                color = Color(0xFFE91E63),
                isLocked = false
            ),
            Game(
                id = "tic-tac-toe",
                title = "Tic-Tac-Toe",
                emoji = "⭕",
                description = "Play against AI",
                color = Color(0xFF00BCD4),
                isLocked = false
            ),
            Game(
                id = "breakout",
                title = "Breakout",
                emoji = "🎮",
                description = "Break the bricks!",
                color = Color(0xFFFF5722),
                isLocked = false
            ),
            Game(
                id = "math",
                title = "Math Game",
                emoji = "🔢",
                description = "Solve math problems!",
                color = Color(0xFF673AB7),
                isLocked = false
            )
        )
    }

    Scaffold(
        topBar = {
            ChoreQuestTopAppBar(
                title = "🎮 Games Area",
                onNavigateBack = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            GrassGreen.copy(alpha = 0.3f),
                            SkyBlue.copy(alpha = 0.3f)
                        )
                    )
                )
        ) {
            // Header
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🎉 Fun Time! 🎉",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Earn points to unlock more games!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Games grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(games) { game ->
                    GameCard(
                        game = game,
                        onClick = {
                            when (game.id) {
                                "tic-tac-toe" -> onNavigateToTicTacToe()
                                "quiz" -> onNavigateToChoreQuiz()
                                "memory" -> onNavigateToMemoryMatch()
                                "rock-paper-scissors" -> onNavigateToRockPaperScissors()
                                "puzzle" -> onNavigateToJigsawPuzzle()
                                "snake" -> onNavigateToSnakeGame()
                                "breakout" -> onNavigateToBreakoutGame()
                                "math" -> onNavigateToMathGame()
                                // TODO: Add navigation for other games
                                else -> {
                                    // Placeholder for other games
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GameCard(
    game: Game,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clickable(enabled = !game.isLocked, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (game.isLocked) 
                Color.Gray.copy(alpha = 0.3f) 
            else 
                game.color.copy(alpha = 0.9f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = game.emoji,
                    fontSize = 56.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedText(
                    text = game.title,
                    fontSize = MaterialTheme.typography.titleMedium.fontSize,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedText(
                    text = game.description,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )
            }

            // Lock indicator
            if (game.isLocked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedText(
                            text = "${game.requiredPoints} pts",
                            fontSize = MaterialTheme.typography.labelLarge.fontSize,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        OutlinedText(
                            text = "to unlock",
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

private data class Game(
    val id: String,
    val title: String,
    val emoji: String,
    val description: String,
    val color: Color,
    val isLocked: Boolean = false,
    val requiredPoints: Int = 0
)
