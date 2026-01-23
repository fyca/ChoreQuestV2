package com.lostsierra.chorequest.presentation.chores

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lostsierra.chorequest.data.local.SessionManager
import com.lostsierra.chorequest.domain.models.Chore
import com.lostsierra.chorequest.domain.models.ChoreStatus
import com.lostsierra.chorequest.presentation.components.*
import com.lostsierra.chorequest.utils.ChoreDateUtils
import kotlinx.coroutines.delay

/**
 * My chores screen for children
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyChoresScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCompleteChore: (String) -> Unit,
    viewModel: ChoreViewModel = hiltViewModel()
) {
    // Refresh chores when screen is opened (triggers expired removal and current creation)
    LaunchedEffect(Unit) {
        viewModel.loadAllChores()
    }
    
    val allChores by viewModel.allChores.collectAsState()
    val currentUserId = viewModel.currentUserId
    
    val myChores = allChores.filter { chore ->
        chore.assignedTo.contains(currentUserId)
    }

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("To Do", "Completed")

    val filteredChores = when (selectedTab) {
        0 -> myChores.filter { it.status == ChoreStatus.PENDING }
        1 -> myChores.filter { 
            it.status == ChoreStatus.COMPLETED || it.status == ChoreStatus.VERIFIED 
        }
        else -> myChores
    }

    Scaffold(
        topBar = {
            ChoreQuestTopAppBar(
                title = "🎯 My Quests",
                onNavigateBack = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = "$title (${if (index == 0) 
                                    myChores.count { it.status == ChoreStatus.PENDING } 
                                else 
                                    myChores.count { it.status == ChoreStatus.COMPLETED || it.status == ChoreStatus.VERIFIED }
                                })",
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // Chores list
            if (filteredChores.isEmpty()) {
                EmptyState(
                    icon = if (selectedTab == 0) Icons.Default.Celebration else Icons.Default.EmojiEvents,
                    title = if (selectedTab == 0) "All Done!" else "No Completed Quests",
                    message = if (selectedTab == 0) 
                        "You've completed all your quests! Great job! 🎉" 
                    else 
                        "Complete some quests to see them here!",
                    actionLabel = null,
                    onAction = null
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredChores) { chore ->
                        ChildChoreCard(
                            chore = chore,
                            onClick = { 
                                if (chore.status == ChoreStatus.PENDING) {
                                    onNavigateToCompleteChore(chore.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChildChoreCard(
    chore: Chore,
    onClick: () -> Unit
) {
    val canComplete = chore.status == ChoreStatus.PENDING
    
    // Update countdown every minute
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    
    LaunchedEffect(chore.id, chore.dueDate, chore.status) {
        if (chore.dueDate != null && chore.status == ChoreStatus.PENDING) {
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
        if (chore.status == ChoreStatus.PENDING && chore.dueDate != null) {
            ChoreDateUtils.calculateTimeRemaining(chore.dueDate)
        } else {
            null
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canComplete, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = when (chore.status) {
                ChoreStatus.PENDING -> Color(0xFFFFE5E5)
                ChoreStatus.COMPLETED -> Color(0xFFE5F5E5)
                ChoreStatus.VERIFIED -> Color(0xFFE5F5E5)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(
                        when (chore.status) {
                            ChoreStatus.PENDING -> MaterialTheme.colorScheme.primary
                            ChoreStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
                            ChoreStatus.VERIFIED -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = chore.icon ?: "🎯",
                    fontSize = 32.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chore.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))

                if (chore.subtasks.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Checklist,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.Black.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${chore.subtasks.count { it.completed }}/${chore.subtasks.size} tasks done",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Black.copy(alpha = 0.6f)
                        )
                    }
                }
                
                // Photo proof requirement indicator
                if (chore.requirePhotoProof && chore.status == ChoreStatus.PENDING) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Photo required",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                
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
                            modifier = Modifier.size(16.dp),
                            tint = if (timeRemaining.isVeryUrgent) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else if (timeRemaining.isUrgent) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            }
                        )
                        Text(
                            text = "Expires in ${timeRemaining.formatted}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (timeRemaining.isVeryUrgent) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else if (timeRemaining.isUrgent) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            },
                            fontWeight = if (timeRemaining.isUrgent) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Points badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Stars,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "+${chore.pointValue} pts",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            // Status indicator
            if (chore.status != ChoreStatus.PENDING) {
                Icon(
                    imageVector = when (chore.status) {
                        ChoreStatus.COMPLETED -> Icons.Default.CheckCircle
                        ChoreStatus.VERIFIED -> Icons.Default.Verified
                        else -> Icons.Default.CheckCircle
                    },
                    contentDescription = null,
                    tint = when (chore.status) {
                        ChoreStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
                        ChoreStatus.VERIFIED -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(36.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Start",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
