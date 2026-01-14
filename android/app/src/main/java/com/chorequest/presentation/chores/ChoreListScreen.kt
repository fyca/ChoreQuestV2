package com.chorequest.presentation.chores

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.chorequest.domain.models.Chore
import com.chorequest.domain.models.ChoreStatus
import com.chorequest.presentation.components.*

/**
 * Chore list screen for parents
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoreListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCreateChore: () -> Unit,
    onNavigateToChoreDetail: (String) -> Unit,
    viewModel: ChoreViewModel = hiltViewModel()
) {
    val allChores by viewModel.allChores.collectAsState()
    var selectedFilter by remember { mutableStateOf(ChoreFilter.ALL) }

    val filteredChores = when (selectedFilter) {
        ChoreFilter.ALL -> allChores
        ChoreFilter.PENDING -> allChores.filter { it.status == ChoreStatus.PENDING }
        ChoreFilter.COMPLETED -> allChores.filter { 
            it.status == ChoreStatus.COMPLETED || it.status == ChoreStatus.VERIFIED 
        }
        ChoreFilter.AWAITING -> allChores.filter { it.status == ChoreStatus.COMPLETED }
    }

    Scaffold(
        topBar = {
            ChoreQuestTopAppBar(
                title = "All Chores",
                onNavigateBack = onNavigateBack
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToCreateChore,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, "Create Chore")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter chips
            ChoreFilterRow(
                selectedFilter = selectedFilter,
                onFilterSelected = { selectedFilter = it },
                counts = mapOf(
                    ChoreFilter.ALL to allChores.size,
                    ChoreFilter.PENDING to allChores.count { it.status == ChoreStatus.PENDING },
                    ChoreFilter.COMPLETED to allChores.count { 
                        it.status == ChoreStatus.COMPLETED || it.status == ChoreStatus.VERIFIED 
                    },
                    ChoreFilter.AWAITING to allChores.count { it.status == ChoreStatus.COMPLETED }
                )
            )

            // Chore list
            if (filteredChores.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Assignment,
                    title = when (selectedFilter) {
                        ChoreFilter.ALL -> "No Chores Yet"
                        ChoreFilter.PENDING -> "No Pending Chores"
                        ChoreFilter.COMPLETED -> "No Completed Chores"
                        ChoreFilter.AWAITING -> "No Chores Awaiting Verification"
                    },
                    message = when (selectedFilter) {
                        ChoreFilter.ALL -> "Create your first chore to get started!"
                        else -> "Try a different filter"
                    },
                    actionLabel = if (selectedFilter == ChoreFilter.ALL) "Create Chore" else null,
                    onAction = if (selectedFilter == ChoreFilter.ALL) onNavigateToCreateChore else null
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredChores) { chore ->
                        ChoreCard(
                            chore = chore,
                            onClick = { onNavigateToChoreDetail(chore.id) }
                        )
                    }
                }
            }
        }
    }
}

enum class ChoreFilter(val label: String) {
    ALL("All"),
    PENDING("Pending"),
    COMPLETED("Completed"),
    AWAITING("Awaiting")
}

@Composable
private fun ChoreFilterRow(
    selectedFilter: ChoreFilter,
    onFilterSelected: (ChoreFilter) -> Unit,
    counts: Map<ChoreFilter, Int>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
            ChoreFilter.values().forEach { filter ->
                @OptIn(ExperimentalMaterial3Api::class)
                FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = {
                    Text("${filter.label} (${counts[filter] ?: 0})")
                },
                leadingIcon = if (selectedFilter == filter) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else null
            )
        }
    }
}

@Composable
private fun ChoreCard(
    chore: Chore,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = when (chore.status) {
                            ChoreStatus.PENDING -> MaterialTheme.colorScheme.primary
                            ChoreStatus.IN_PROGRESS -> MaterialTheme.colorScheme.tertiary
                            ChoreStatus.COMPLETED -> MaterialTheme.colorScheme.secondary
                            ChoreStatus.VERIFIED -> MaterialTheme.colorScheme.secondary
                            ChoreStatus.OVERDUE -> MaterialTheme.colorScheme.error
                        }.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (chore.status) {
                        ChoreStatus.PENDING -> Icons.Default.Schedule
                        ChoreStatus.IN_PROGRESS -> Icons.Default.HourglassTop
                        ChoreStatus.COMPLETED -> Icons.Default.CheckCircle
                        ChoreStatus.VERIFIED -> Icons.Default.Verified
                        ChoreStatus.OVERDUE -> Icons.Default.Warning
                    },
                    contentDescription = null,
                    tint = when (chore.status) {
                        ChoreStatus.PENDING -> MaterialTheme.colorScheme.primary
                        ChoreStatus.IN_PROGRESS -> MaterialTheme.colorScheme.tertiary
                        ChoreStatus.COMPLETED -> MaterialTheme.colorScheme.secondary
                        ChoreStatus.VERIFIED -> MaterialTheme.colorScheme.secondary
                        ChoreStatus.OVERDUE -> MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chore.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = chore.description.take(60) + if (chore.description.length > 60) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Assigned to count
                    if (chore.assignedTo.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${chore.assignedTo.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Recurring indicator
                    if (chore.recurring != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Repeat,
                                contentDescription = "Recurring",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
                    // Subtasks count
                    if (chore.subtasks.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Checklist,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${chore.subtasks.count { it.completed }}/${chore.subtasks.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Points badge
            PointsBadge(points = chore.pointValue)
        }
    }
}
