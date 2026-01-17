package com.chorequest.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chorequest.presentation.components.*
import com.chorequest.domain.models.RewardRedemption
import com.chorequest.domain.models.Reward
import com.chorequest.domain.models.RewardRedemptionStatus

/**
 * Child dashboard screen with colorful, playful design
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildDashboardScreen(
    onNavigateToMyChores: () -> Unit,
    onNavigateToRewardsMarketplace: () -> Unit,
    onNavigateToGames: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToCompleteChore: (String) -> Unit = {},
    viewModel: ChildDashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val networkStatus by viewModel.networkStatus.collectAsState()
    var currentRoute by remember { mutableStateOf("child_dashboard") }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "🎮 ChoreQuest",
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                    },
                    actions = {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, "Refresh")
                        }
                        IconButton(onClick = {
                            viewModel.logout(onNavigateToLogin)
                        }) {
                            Icon(Icons.Default.Logout, "Logout")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                OfflineIndicator(networkStatus = networkStatus)
            }
        },
        bottomBar = {
            ChoreQuestBottomNavigationBar(
                items = childNavItems,
                currentRoute = currentRoute,
                onNavigate = { route ->
                    currentRoute = route
                    when (route) {
                        "my_chores" -> onNavigateToMyChores()
                        "rewards_marketplace" -> onNavigateToRewardsMarketplace()
                        "games" -> onNavigateToGames()
                        "profile" -> onNavigateToProfile()
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is ChildDashboardState.Loading -> {
                LoadingScreen(
                    message = "Loading your quests...",
                    modifier = Modifier.padding(padding)
                )
            }
            is ChildDashboardState.Error -> {
                ErrorScreen(
                    message = state.message,
                    onRetry = { viewModel.refresh() },
                    modifier = Modifier.padding(padding)
                )
            }
            is ChildDashboardState.Success -> {
                ChildDashboardContent(
                    state = state,
                    syncManager = viewModel.syncManager,
                    lastSyncTime = lastSyncTime,
                    onManualSync = { viewModel.triggerSync() },
                    onViewMyChores = onNavigateToMyChores,
                    onViewRewards = onNavigateToRewardsMarketplace,
                    onPlayGames = onNavigateToGames,
                    onChoreClick = onNavigateToCompleteChore,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
private fun ChildDashboardContent(
    state: ChildDashboardState.Success,
    syncManager: com.chorequest.workers.SyncManager,
    lastSyncTime: Long?,
    onManualSync: () -> Unit,
    onViewMyChores: () -> Unit,
    onViewRewards: () -> Unit,
    onPlayGames: () -> Unit,
    onChoreClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome header with avatar
        item {
            ChildWelcomeHeader(
                userName = state.userName,
                totalPoints = state.totalPoints
            )
        }

        // Sync status bar
        item {
            SyncStatusBar(
                syncManager = syncManager,
                lastSyncTime = lastSyncTime,
                onManualSyncClick = onManualSync
            )
        }

        // Stats cards
        item {
            ChildStatsCards(
                pendingChores = state.pendingChoresCount,
                completedToday = state.completedTodayCount
            )
        }

        // Action buttons
        item {
            ChildActionButtons(
                onViewMyChores = onViewMyChores,
                onViewRewards = onViewRewards,
                onPlayGames = onPlayGames
            )
        }

        // My chores section
        item {
            Text(
                text = "🎯 My Quests",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // My chores list
        if (state.myChores.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.EmojiEvents,
                    title = "All Done!",
                    message = "Great job! You've completed all your chores! 🎉",
                    actionLabel = null,
                    onAction = null
                )
            }
        } else {
            items(state.myChores) { chore ->
                ChildChoreCard(
                    chore = chore,
                    onClick = { onChoreClick(chore.id) }
                )
            }
        }

        // Earn Extra Points section (unassigned chores)
        if (state.extraPointsChores.isNotEmpty()) {
            item {
                Text(
                    text = "⭐ Earn Extra Points",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Complete these chores to earn bonus points! 💰",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            items(state.extraPointsChores) { chore ->
                ChildChoreCard(
                    chore = chore,
                    onClick = { onChoreClick(chore.id) }
                )
            }
        }

        // Pending Rewards section
        if (state.pendingRewards.isNotEmpty()) {
            item {
                Text(
                    text = "🎁 Pending Rewards",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Waiting for parent approval... ⏳",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            items(state.pendingRewards) { (redemption, reward) ->
                PendingRewardCard(
                    redemption = redemption,
                    reward = reward
                )
            }
        }
    }
}

@Composable
private fun ChildWelcomeHeader(
    userName: String,
    totalPoints: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Hi, $userName! 👋",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Ready for adventure?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }

            // Points display
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = totalPoints.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 24.sp
                        )
                        Text(
                            text = "pts",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChildStatsCards(
    pendingChores: Int,
    completedToday: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ChildStatCard(
            title = "To Do",
            value = pendingChores.toString(),
            emoji = "📋",
            backgroundColor = Color(0xFFFFE5E5),
            modifier = Modifier.weight(1f)
        )
        ChildStatCard(
            title = "Done Today",
            value = completedToday.toString(),
            emoji = "✨",
            backgroundColor = Color(0xFFE5F5E5),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ChildStatCard(
    title: String,
    value: String,
    emoji: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = emoji,
                fontSize = 32.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Black.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ChildActionButtons(
    onViewMyChores: () -> Unit,
    onViewRewards: () -> Unit,
    onPlayGames: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ChildActionButton(
            label = "My Chores",
            emoji = "🎯",
            backgroundColor = Color(0xFF4ECDC4),
            onClick = onViewMyChores
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ChildActionButton(
                label = "Rewards",
                emoji = "🎁",
                backgroundColor = Color(0xFFFFA07A),
                onClick = onViewRewards,
                modifier = Modifier.weight(1f)
            )
            ChildActionButton(
                label = "Games",
                emoji = "🎮",
                backgroundColor = Color(0xFFBB8FCE),
                onClick = onPlayGames,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ChildActionButton(
    label: String,
    emoji: String,
    backgroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(70.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = emoji,
                fontSize = 28.sp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
    }
}

@Composable
private fun PendingRewardCard(
    redemption: RewardRedemption,
    reward: Reward
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reward icon
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = reward.imageUrl ?: "🎁",
                    fontSize = 32.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reward.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                
                if (reward.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = reward.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                        maxLines = 2
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Status indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Waiting for approval",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Points badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Stars,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${redemption.pointCost}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChildChoreCard(
    chore: com.chorequest.domain.models.Chore,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon placeholder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = chore.icon ?: "🎯",
                    fontSize = 24.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chore.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (chore.subtasks.isNotEmpty()) {
                    Text(
                        text = "${chore.subtasks.size} tasks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            // Points badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "+${chore.pointValue} pts",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
