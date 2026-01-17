package com.chorequest.presentation.activity

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chorequest.domain.models.ActivityActionType
import com.chorequest.domain.models.ActivityLog
import com.chorequest.presentation.components.ChoreQuestTopAppBar
import com.chorequest.presentation.components.EmptyState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Activity log screen showing all actions in the family
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogScreen(
    onNavigateBack: () -> Unit,
    viewModel: ActivityLogViewModel = hiltViewModel()
) {
    val activityLogsState by viewModel.activityLogsState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    Scaffold(
        topBar = {
            ChoreQuestTopAppBar(
                title = "📋 Activity Log",
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = activityLogsState) {
                is ActivityLogsState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is ActivityLogsState.Error -> {
                    EmptyState(
                        icon = Icons.Default.ErrorOutline,
                        title = "Error Loading Logs",
                        message = state.message,
                        actionLabel = "Retry",
                        onAction = { viewModel.loadActivityLogs() }
                    )
                }
                is ActivityLogsState.Success -> {
                    if (state.logs.isEmpty()) {
                        EmptyState(
                            icon = Icons.Default.History,
                            title = "No Activity Yet",
                            message = "Activity will appear here as family members complete chores and redeem rewards",
                            actionLabel = null,
                            onAction = null
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Show refresh indicator at top if refreshing
                            if (isRefreshing) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                            }
                            
                            items(state.logs) { log ->
                                ActivityLogCard(log)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityLogCard(log: ActivityLog) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getIconForActionType(log.actionType),
                contentDescription = null,
                tint = getColorForActionType(log.actionType),
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatActivityDescription(log),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatTimestamp(log.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Show additional details if available
                val additionalDetails = buildString {
                    // Show points details
                    when {
                        log.details.pointsPrevious != null && log.details.pointsNew != null -> {
                            append("Points: ${log.details.pointsPrevious} → ${log.details.pointsNew}")
                        }
                        log.details.pointsAmount != null && log.actionType == ActivityActionType.POINTS_EARNED -> {
                            append("Earned ${log.details.pointsAmount} points")
                        }
                        log.details.pointsAmount != null && log.actionType == ActivityActionType.POINTS_SPENT -> {
                            append("Spent ${log.details.pointsAmount} points")
                        }
                        log.details.rewardCost != null -> {
                            append("Cost: ${log.details.rewardCost} points")
                        }
                    }
                    
                    // Show reason if available
                    log.details.reason?.let { reason ->
                        if (isNotEmpty()) append(" • ")
                        append(reason)
                    }
                    
                    // Show notes if available
                    log.details.notes?.let { notes ->
                        if (isNotEmpty()) append(" • ")
                        append(notes)
                    }
                }
                
                if (additionalDetails.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = additionalDetails,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun getIconForActionType(actionType: ActivityActionType) = when (actionType) {
    ActivityActionType.CHORE_COMPLETED,
    ActivityActionType.CHORE_COMPLETED_PARENT,
    ActivityActionType.CHORE_COMPLETED_CHILD -> Icons.Default.CheckCircle
    ActivityActionType.CHORE_VERIFIED -> Icons.Default.Verified
    ActivityActionType.CHORE_CREATED -> Icons.Default.Add
    ActivityActionType.REWARD_REDEEMED -> Icons.Default.CardGiftcard
    ActivityActionType.POINTS_EARNED -> Icons.Default.TrendingUp
    ActivityActionType.POINTS_SPENT -> Icons.Default.ShoppingCart
    ActivityActionType.POINTS_ADJUSTED -> Icons.Default.Edit
    ActivityActionType.USER_ADDED -> Icons.Default.PersonAdd
    ActivityActionType.USER_REMOVED -> Icons.Default.PersonRemove
    ActivityActionType.PHOTO_UPLOADED -> Icons.Default.PhotoCamera
    ActivityActionType.QR_GENERATED -> Icons.Default.QrCode2
    else -> Icons.Default.Info
}

@Composable
private fun getColorForActionType(actionType: ActivityActionType) = when (actionType) {
    ActivityActionType.CHORE_COMPLETED,
    ActivityActionType.CHORE_COMPLETED_PARENT,
    ActivityActionType.CHORE_COMPLETED_CHILD,
    ActivityActionType.CHORE_VERIFIED,
    ActivityActionType.POINTS_EARNED -> MaterialTheme.colorScheme.primary
    ActivityActionType.REWARD_REDEEMED,
    ActivityActionType.POINTS_SPENT -> MaterialTheme.colorScheme.secondary
    ActivityActionType.POINTS_ADJUSTED -> MaterialTheme.colorScheme.tertiary
    ActivityActionType.USER_REMOVED,
    ActivityActionType.CHORE_REJECTED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun formatActivityDescription(log: ActivityLog): String {
    val actorName = log.actorName
    val targetName = log.targetUserName
    val choreTitle = log.details.choreTitle
    val rewardTitle = log.details.rewardTitle
    val points = log.details.pointsAmount
    val pointsEarned = log.details.pointsEarned ?: log.details.pointsAmount // chore_completed
    val pointsAwarded = log.details.pointsAwarded // chore_verified
    val pointsPrevious = log.details.pointsPrevious
    val pointsNew = log.details.pointsNew
    val rewardCost = log.details.rewardCost
    val reason = log.details.reason
    val notes = log.details.notes
    
    return when (log.actionType) {
        // Chore actions
        ActivityActionType.CHORE_CREATED -> {
            val pointValue = points?.let { " (${it} pts)" } ?: ""
            val assignedTo = if (targetName != null) " for $targetName" else ""
            "${actorName} created chore \"${choreTitle ?: "Untitled"}\"$pointValue$assignedTo"
        }
        ActivityActionType.CHORE_EDITED -> {
            val changes = reason ?: "updated"
            "${actorName} edited chore \"${choreTitle ?: "Untitled"}\" ($changes)"
        }
        ActivityActionType.CHORE_DELETED -> {
            "${actorName} deleted chore \"${choreTitle ?: "Untitled"}\""
        }
        ActivityActionType.CHORE_ASSIGNED -> {
            "${actorName} assigned chore \"${choreTitle ?: "Untitled"}\" to ${targetName ?: "someone"}"
        }
        ActivityActionType.CHORE_UNASSIGNED -> {
            "${actorName} unassigned chore \"${choreTitle ?: "Untitled"}\" from ${targetName ?: "someone"}"
        }
        ActivityActionType.CHORE_STARTED -> {
            "${actorName} started chore \"${choreTitle ?: "Untitled"}\""
        }
        ActivityActionType.CHORE_COMPLETED, 
        ActivityActionType.CHORE_COMPLETED_PARENT, 
        ActivityActionType.CHORE_COMPLETED_CHILD -> {
            val pointsText = if (pointsEarned != null && pointsEarned > 0) " and earned $pointsEarned points" else ""
            "${actorName} completed chore \"${choreTitle ?: "Untitled"}\"$pointsText"
        }
        ActivityActionType.CHORE_VERIFIED -> {
            val pts = pointsAwarded ?: points
            val pointsText = if (pts != null && pts > 0) " (awarded $pts points)" else ""
            "${actorName} verified ${targetName ?: "someone"}'s chore \"${choreTitle ?: "Untitled"}\"$pointsText"
        }
        ActivityActionType.CHORE_REJECTED -> {
            val reasonText = reason?.let { " - $it" } ?: ""
            "${actorName} rejected ${targetName ?: "someone"}'s chore \"${choreTitle ?: "Untitled"}\"$reasonText"
        }
        ActivityActionType.SUBTASK_COMPLETED -> {
            val subtask = log.details.subtaskTitle ?: "a subtask"
            "${actorName} completed subtask \"$subtask\" in \"${choreTitle ?: "a chore"}\""
        }
        ActivityActionType.SUBTASK_UNCOMPLETED -> {
            val subtask = log.details.subtaskTitle ?: "a subtask"
            "${actorName} uncompleted subtask \"$subtask\" in \"${choreTitle ?: "a chore"}\""
        }
        ActivityActionType.PHOTO_UPLOADED -> {
            "${actorName} uploaded a photo for chore \"${choreTitle ?: "Untitled"}\""
        }
        
        // Reward actions
        ActivityActionType.REWARD_CREATED -> {
            val costText = rewardCost?.let { " (${it} pts)" } ?: ""
            "${actorName} created reward \"${rewardTitle ?: "Untitled"}\"$costText"
        }
        ActivityActionType.REWARD_EDITED -> {
            val changes = reason ?: "updated"
            "${actorName} edited reward \"${rewardTitle ?: "Untitled"}\" ($changes)"
        }
        ActivityActionType.REWARD_DELETED -> {
            "${actorName} deleted reward \"${rewardTitle ?: "Untitled"}\""
        }
        ActivityActionType.REWARD_REDEEMED -> {
            val costText = (log.details.pointsSpent ?: rewardCost)?.let { " for $it points" } ?: ""
            "${actorName} redeemed reward \"${rewardTitle ?: "Untitled"}\"$costText"
        }
        ActivityActionType.REWARD_APPROVED -> {
            "${actorName} approved ${targetName ?: "someone"}'s reward request \"${rewardTitle ?: "Untitled"}\""
        }
        ActivityActionType.REWARD_DENIED -> {
            val reasonText = reason?.let { " - $it" } ?: ""
            "${actorName} denied ${targetName ?: "someone"}'s reward request \"${rewardTitle ?: "Untitled"}\"$reasonText"
        }
        
        // Points actions
        ActivityActionType.POINTS_EARNED -> {
            val fromText = choreTitle?.let { " from \"$it\"" } ?: ""
            "${actorName} earned ${points ?: 0} points$fromText"
        }
        ActivityActionType.POINTS_SPENT -> {
            val onText = rewardTitle?.let { " on \"$it\"" } ?: ""
            "${actorName} spent ${points ?: 0} points$onText"
        }
        ActivityActionType.POINTS_ADJUSTED -> {
            val changeText = when {
                pointsPrevious != null && pointsNew != null -> {
                    val diff = pointsNew - pointsPrevious
                    val sign = if (diff >= 0) "+" else ""
                    " ($pointsPrevious → $pointsNew, $sign$diff)"
                }
                points != null -> " (${if (points >= 0) "+" else ""}$points)"
                else -> ""
            }
            "${actorName} adjusted ${targetName ?: "someone"}'s points$changeText"
        }
        ActivityActionType.POINTS_BONUS -> {
            val reasonText = reason?.let { " - $it" } ?: ""
            "${actorName} gave ${targetName ?: "someone"} a bonus of ${points ?: 0} points$reasonText"
        }
        ActivityActionType.POINTS_PENALTY -> {
            val reasonText = reason?.let { " - $it" } ?: ""
            "${actorName} deducted ${points ?: 0} points from ${targetName ?: "someone"}$reasonText"
        }
        
        // User actions
        ActivityActionType.USER_ADDED -> {
            val roleText = log.targetUserId?.let { "" } ?: "" // Could add role if available
            "${actorName} added ${targetName ?: "a new user"}$roleText"
        }
        ActivityActionType.USER_REMOVED -> {
            "${actorName} removed ${targetName ?: "a user"}"
        }
        ActivityActionType.USER_UPDATED -> {
            val changes = reason ?: "updated"
            "${actorName} updated ${targetName ?: "a user"}'s profile ($changes)"
        }
        ActivityActionType.QR_GENERATED -> {
            "${actorName} generated a QR code for ${targetName ?: "a user"}"
        }
        ActivityActionType.QR_REGENERATED -> {
            "${actorName} regenerated QR code for ${targetName ?: "a user"}"
        }
        
        // Device/Session actions
        ActivityActionType.DEVICE_LOGIN -> {
            "${actorName} logged in"
        }
        ActivityActionType.DEVICE_LOGOUT -> {
            "${actorName} logged out"
        }
        ActivityActionType.DEVICE_REMOVED -> {
            "${actorName} removed a device"
        }
        ActivityActionType.SESSION_EXPIRED -> {
            "${actorName}'s session expired"
        }
        
        // Settings
        ActivityActionType.SETTINGS_CHANGED -> {
            val changeText = reason ?: "settings"
            "${actorName} changed $changeText"
        }
        
        // Fallback for any unhandled types
        else -> {
            val actionName = log.actionType.name.lowercase().replace("_", " ")
            "${actorName} $actionName${choreTitle?.let { " - \"$it\"" } ?: ""}${rewardTitle?.let { " - \"$it\"" } ?: ""}"
        }
    }
}

private fun formatTimestamp(timestamp: String): String {
    return try {
        val instant = Instant.parse(timestamp)
        val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' hh:mm a")
            .withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (e: Exception) {
        timestamp
    }
}
