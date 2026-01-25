package com.lostsierra.chorequest.presentation.dashboard

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lostsierra.chorequest.presentation.components.*
import com.lostsierra.chorequest.domain.models.RewardRedemption
import com.lostsierra.chorequest.domain.models.Reward
import com.lostsierra.chorequest.domain.models.User
import com.lostsierra.chorequest.utils.ChoreDateUtils
import kotlinx.coroutines.delay

/**
 * Parent dashboard screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentDashboardScreen(
    onNavigateToChoreList: () -> Unit,
    onNavigateToRewardList: () -> Unit,
    onNavigateToUserList: () -> Unit,
    onNavigateToActivityLog: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToGames: () -> Unit = {},
    onNavigateToChoreDetail: (String) -> Unit = {},
    onNavigateToCompleteChore: (String) -> Unit = {},
    onNavigateToCreateChore: () -> Unit = {},
    viewModel: ParentDashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val networkStatus by viewModel.networkStatus.collectAsState()
    var currentRoute by remember { mutableStateOf("parent_dashboard") }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "ChoreQuest",
                            fontWeight = FontWeight.Bold
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
                items = parentNavItems,
                currentRoute = currentRoute,
                onNavigate = { route ->
                    currentRoute = route
                    when (route) {
                        "chore_list" -> onNavigateToChoreList()
                        "reward_list" -> onNavigateToRewardList()
                        "user_list" -> onNavigateToUserList()
                        "games" -> onNavigateToGames()
                        "settings" -> onNavigateToSettings()
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is ParentDashboardState.Loading -> {
                LoadingScreen(modifier = Modifier.padding(padding))
            }
            is ParentDashboardState.Error -> {
                ErrorScreen(
                    message = state.message,
                    onRetry = { viewModel.refresh() },
                    modifier = Modifier.padding(padding)
                )
            }
            is ParentDashboardState.Success -> {
                ParentDashboardContent(
                    state = state,
                    syncManager = viewModel.syncManager,
                    lastSyncTime = lastSyncTime,
                    onManualSync = { viewModel.triggerSync() },
                    onCreateChore = onNavigateToCreateChore,
                    onCreateReward = onNavigateToRewardList,
                    onViewActivity = onNavigateToActivityLog,
                    onChoreClick = onNavigateToChoreDetail,
                    onCompleteChoreClick = onNavigateToCompleteChore,
                    currentUserId = viewModel.currentUserId,
                    viewModel = viewModel,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
private fun ParentDashboardContent(
    state: ParentDashboardState.Success,
    syncManager: com.lostsierra.chorequest.workers.SyncManager,
    lastSyncTime: Long?,
    onManualSync: () -> Unit,
    onCreateChore: () -> Unit,
    onCreateReward: () -> Unit,
    onViewActivity: () -> Unit,
    onChoreClick: (String) -> Unit,
    onCompleteChoreClick: (String) -> Unit,
    currentUserId: String?,
    viewModel: ParentDashboardViewModel,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome header
        item {
            WelcomeHeader(userName = state.userName)
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
            StatsCardsRow(
                pendingChores = state.pendingChoresCount,
                completedChores = state.completedChoresCount,
                awaitingVerification = state.awaitingVerificationCount
            )
        }

        // Quick actions
        item {
            QuickActionsSection(
                onCreateChore = onCreateChore,
                onCreateReward = onCreateReward,
                onViewActivity = onViewActivity
            )
        }

        // Pending Rewards section (for approval)
        if (state.pendingRewards.isNotEmpty()) {
            item {
                Text(
                    text = "🎁 Pending Reward Approvals",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            items(state.pendingRewards) { (redemption, reward, user) ->
                PendingRewardApprovalCard(
                    redemption = redemption,
                    reward = reward,
                    user = user,
                    onApprove = { viewModel.approveReward(redemption.id) },
                    onDeny = { viewModel.denyReward(redemption.id) }
                )
            }
        }

        // Chores Waiting for Verification section
        if (state.awaitingApprovalChores.isNotEmpty()) {
            item {
                Text(
                    text = "Waiting for Verification",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            
            items(state.awaitingApprovalChores) { chore ->
                ChorePreviewCard(
                    chore = chore,
                    isAssignedToMe = currentUserId?.let { chore.assignedTo.contains(it) } ?: false,
                    onClick = { onChoreClick(chore.id) },
                    onCompleteClick = null
                )
            }
        }

        // Active Chores section
        item {
            Text(
                text = "Active Chores",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // Active chores list (with assigned to me highlighted)
        if (state.activeChores.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Assignment,
                    title = "No Active Chores",
                    message = "Create your first chore to get started!",
                    actionLabel = "Create Chore",
                    onAction = onCreateChore
                )
            }
        } else {
            items(state.activeChores) { chore ->
                ChorePreviewCard(
                    chore = chore,
                    isAssignedToMe = currentUserId?.let { chore.assignedTo.contains(it) } ?: false,
                    onClick = { onChoreClick(chore.id) },
                    onCompleteClick = if (currentUserId != null && chore.assignedTo.contains(currentUserId) && chore.status == com.lostsierra.chorequest.domain.models.ChoreStatus.PENDING) {
                        { onCompleteChoreClick(chore.id) }
                    } else null
                )
            }
        }

    }
}

@Composable
private fun WelcomeHeader(userName: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "Welcome back,",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = userName,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun StatsCardsRow(
    pendingChores: Int,
    completedChores: Int,
    awaitingVerification: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            title = "Pending",
            value = pendingChores.toString(),
            icon = Icons.Default.Schedule,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Completed",
            value = completedChores.toString(),
            icon = Icons.Default.CheckCircle,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Verify",
            value = awaitingVerification.toString(),
            icon = Icons.Default.Verified,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuickActionsSection(
    onCreateChore: () -> Unit,
    onCreateReward: () -> Unit,
    onViewActivity: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Quick Actions",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionButton(
                label = "Create Chore",
                icon = Icons.Default.Add,
                onClick = onCreateChore,
                modifier = Modifier.weight(1f)
            )
            QuickActionButton(
                label = "Add Reward",
                icon = Icons.Default.CardGiftcard,
                onClick = onCreateReward,
                modifier = Modifier.weight(1f)
            )
            QuickActionButton(
                label = "Activity",
                icon = Icons.Default.History,
                onClick = onViewActivity,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun PendingRewardApprovalCard(
    redemption: RewardRedemption,
    reward: Reward,
    user: User,
    onApprove: () -> Unit,
    onDeny: () -> Unit
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
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Requested by: ${user.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                    
                    if (reward.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = reward.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                            maxLines = 2
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
                        OutlinedText(
                            text = "${redemption.pointCost}",
                            fontSize = MaterialTheme.typography.titleSmall.fontSize,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
            
            Divider(modifier = Modifier.padding(vertical = 12.dp))
            
            // Approve/Deny buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Approve")
                }
                
                OutlinedButton(
                    onClick = onDeny,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Deny")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChorePreviewCard(
    chore: com.lostsierra.chorequest.domain.models.Chore,
    isAssignedToMe: Boolean = false,
    onClick: () -> Unit,
    onCompleteClick: (() -> Unit)? = null
) {
    // Update countdown every minute
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    
    LaunchedEffect(chore.id, chore.dueDate, chore.status) {
        if (chore.dueDate != null && chore.status == com.lostsierra.chorequest.domain.models.ChoreStatus.PENDING) {
            while (true) {
                delay(60000) // Update every minute
                currentTime = System.currentTimeMillis()
                // Check if we should stop updating
                val remaining = ChoreDateUtils.calculateTimeRemaining(chore.dueDate)
                if (remaining == null) break // Expired or invalid
            }
        }
    }
    
    val timeRemaining = remember(chore.dueDate, currentTime, chore.status) {
        if (chore.status == com.lostsierra.chorequest.domain.models.ChoreStatus.PENDING && chore.dueDate != null) {
            ChoreDateUtils.calculateTimeRemaining(chore.dueDate)
        } else {
            null
        }
    }
    
    val expirationProgress = remember(chore.dueDate, chore.recurring, chore.cycleId, currentTime, chore.status) {
        if (chore.status == com.lostsierra.chorequest.domain.models.ChoreStatus.PENDING && chore.dueDate != null) {
            ChoreDateUtils.calculateExpirationProgress(
                chore.dueDate,
                chore.recurring?.frequency,
                chore.cycleId
            )
        } else {
            null
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isAssignedToMe) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status indicator
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            color = when (chore.status) {
                                com.lostsierra.chorequest.domain.models.ChoreStatus.PENDING -> MaterialTheme.colorScheme.primary
                                com.lostsierra.chorequest.domain.models.ChoreStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
                                com.lostsierra.chorequest.domain.models.ChoreStatus.VERIFIED -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            shape = RoundedCornerShape(50)
                        )
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = chore.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = chore.description.take(50) + if (chore.description.length > 50) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Time remaining countdown
                    if (timeRemaining != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (timeRemaining.isVeryUrgent) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else if (timeRemaining.isUrgent) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Text(
                                text = "Expires in ${timeRemaining.formatted}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (timeRemaining.isVeryUrgent) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else if (timeRemaining.isUrgent) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = if (timeRemaining.isUrgent) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    } else if (chore.dueDate != null && chore.status == com.lostsierra.chorequest.domain.models.ChoreStatus.PENDING) {
                        // Show expired message if due date exists but is expired
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Expired",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    
                    // Progress bar showing expiration progress
                    expirationProgress?.let { progress ->
                        if (chore.status == com.lostsierra.chorequest.domain.models.ChoreStatus.PENDING) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = progress,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = when {
                                    progress >= 0.9f -> MaterialTheme.colorScheme.error
                                    progress >= 0.7f -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }

                PointsBadge(points = chore.pointValue)
            }
            
            // Complete button if assigned to me and pending
            if (onCompleteClick != null) {
                Divider()
                TextButton(
                    onClick = { onCompleteClick() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Complete This Chore")
                }
            }
        }
    }
}
