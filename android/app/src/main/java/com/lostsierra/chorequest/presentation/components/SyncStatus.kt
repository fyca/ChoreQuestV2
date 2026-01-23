package com.lostsierra.chorequest.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import com.lostsierra.chorequest.workers.SyncManager
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SyncStatusBar(
    syncManager: SyncManager,
    lastSyncTime: Long?,
    onManualSyncClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val workInfo by syncManager.getSyncWorkInfoLiveData().observeAsState()
    val currentState = workInfo?.firstOrNull()?.state

    val isSyncing = currentState == WorkInfo.State.RUNNING
    val hasError = currentState == WorkInfo.State.FAILED

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                hasError -> MaterialTheme.colorScheme.errorContainer
                isSyncing -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SyncIcon(isSyncing = isSyncing, hasError = hasError)
                
                Column {
                    Text(
                        text = when {
                            isSyncing -> "Syncing..."
                            hasError -> "Sync failed"
                            else -> "Synced"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            hasError -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    
                    if (!isSyncing && lastSyncTime != null) {
                        Text(
                            text = formatLastSyncTime(lastSyncTime),
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                hasError -> MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            }
                        )
                    }
                }
            }

            if (!isSyncing) {
                IconButton(onClick = onManualSyncClick) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = "Manual Sync",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncIcon(isSyncing: Boolean, hasError: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = Modifier
            .size(40.dp)
            .background(
                color = when {
                    hasError -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                    isSyncing -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    else -> MaterialTheme.colorScheme.primaryContainer
                },
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = when {
                hasError -> Icons.Default.Error
                isSyncing -> Icons.Default.CloudSync
                else -> Icons.Default.CloudDone
            },
            contentDescription = null,
            modifier = if (isSyncing) Modifier.rotate(rotation) else Modifier,
            tint = when {
                hasError -> MaterialTheme.colorScheme.error
                isSyncing -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onPrimaryContainer
            }
        )
    }
}

@Composable
fun CompactSyncIndicator(
    isSyncing: Boolean,
    modifier: Modifier = Modifier
) {
    if (isSyncing) {
        val infiniteTransition = rememberInfiniteTransition(label = "compact_sync")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "compact_rotation"
        )

        Icon(
            imageVector = Icons.Default.CloudSync,
            contentDescription = "Syncing",
            modifier = modifier.rotate(rotation),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

private fun formatLastSyncTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> {
            val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
            dateFormat.format(Date(timestamp))
        }
    }
}
