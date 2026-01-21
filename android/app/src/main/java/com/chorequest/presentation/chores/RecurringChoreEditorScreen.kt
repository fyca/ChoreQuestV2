package com.chorequest.presentation.chores

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
import com.chorequest.domain.models.ChoreTemplate
import com.chorequest.domain.models.RecurringFrequency
import com.chorequest.presentation.components.*

/**
 * Screen for managing recurring chore templates - edit or delete
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringChoreEditorScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEditChore: (String) -> Unit,
    viewModel: ChoreViewModel = hiltViewModel()
) {
    val templates by viewModel.recurringChoreTemplates.collectAsState()
    val isLoadingTemplates by viewModel.isLoadingTemplates.collectAsState()
    val isDeletingTemplate by viewModel.isDeletingTemplate.collectAsState()
    
    var showDeleteDialog by remember { mutableStateOf<ChoreTemplate?>(null) }

    // Refresh templates when screen opens
    LaunchedEffect(Unit) {
        viewModel.loadRecurringChoreTemplates(showLoading = true)
    }

    Scaffold(
        topBar = {
            ChoreQuestTopAppBar(
                title = "Recurring Chores",
                onNavigateBack = onNavigateBack
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                if (templates.isEmpty() && !isLoadingTemplates) {
                    EmptyState(
                        icon = Icons.Default.Repeat,
                        title = "No Recurring Chore Templates",
                        message = "Create a chore with a recurring schedule to see it here.",
                        actionLabel = null,
                        onAction = null
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(templates) { template ->
                            RecurringChoreTemplateCard(
                                template = template,
                                onEdit = { onNavigateToEditChore(template.id) },
                                onDelete = { showDeleteDialog = template },
                                isDeleting = isDeletingTemplate
                            )
                        }
                    }
                }
            }

            // Loading overlay for initial load
            if (isLoadingTemplates) {
                LoadingOverlay(message = "Loading templates...")
            }

            // Loading overlay for deletion
            if (isDeletingTemplate) {
                LoadingOverlay(message = "Deleting template...")
            }
        }
    }

    // Delete confirmation dialog
    showDeleteDialog?.let { template ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Recurring Chore Template?") },
            text = { 
                Text("Are you sure you want to delete \"${template.title}\"? This will remove the recurring schedule template and stop creating future instances.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteRecurringChoreTemplate(template.id)
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun RecurringChoreTemplateCard(
    template: ChoreTemplate,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    isDeleting: Boolean = false
) {
    val recurring = template.recurring
    
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
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = template.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = template.description.take(80) + if (template.description.length > 80) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Recurring schedule info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = formatRecurringSchedule(recurring),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Points and assigned info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PointsBadge(points = template.pointValue)
                        if (template.assignedTo.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${template.assignedTo.size} assigned",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            
            Divider(modifier = Modifier.padding(vertical = 12.dp))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    enabled = !isDeleting
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit")
                }
                
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    enabled = !isDeleting,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun formatRecurringSchedule(recurring: com.chorequest.domain.models.RecurringSchedule): String {
    return when (recurring.frequency) {
        RecurringFrequency.DAILY -> "Daily"
        RecurringFrequency.WEEKLY -> {
            val days = recurring.daysOfWeek
            if (days != null && days.isNotEmpty()) {
                val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                val selectedDays = days.sorted().map { dayNames[it] }
                "Weekly: ${selectedDays.joinToString(", ")}"
            } else {
                "Weekly"
            }
        }
        RecurringFrequency.MONTHLY -> {
            val dayOfMonth = recurring.dayOfMonth
            if (dayOfMonth != null) {
                "Monthly: Day $dayOfMonth"
            } else {
                "Monthly"
            }
        }
    }
}
