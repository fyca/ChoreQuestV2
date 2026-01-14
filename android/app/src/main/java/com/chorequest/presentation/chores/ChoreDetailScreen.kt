package com.chorequest.presentation.chores

import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.chorequest.domain.models.ChoreStatus
import com.chorequest.presentation.components.ChoreQuestTopAppBar
import com.chorequest.presentation.components.LoadingScreen
import com.chorequest.presentation.components.ErrorScreen

/**
 * Chore detail screen for parents to view and verify chores
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoreDetailScreen(
    choreId: String,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    viewModel: ChoreViewModel = hiltViewModel()
) {
    val choreDetailState by viewModel.choreDetailState.collectAsState()
    val createEditState by viewModel.createEditState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showVerifyDialog by remember { mutableStateOf(false) }
    var verifyError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(choreId) {
        viewModel.loadChoreDetail(choreId)
    }

    when (val state = choreDetailState) {
        is ChoreDetailState.Loading -> {
            LoadingScreen(message = "Loading chore...")
        }
        is ChoreDetailState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(state.message)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onNavigateBack) {
                        Text("Go Back")
                    }
                }
            }
        }
        is ChoreDetailState.Success -> {
            val chore = state.chore

            Scaffold(
                topBar = {
                    ChoreQuestTopAppBar(
                        title = "Chore Details",
                        onNavigateBack = onNavigateBack,
                        actions = {
                            IconButton(onClick = { onNavigateToEdit(choreId) }) {
                                Icon(Icons.Default.Edit, "Edit")
                            }
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                },
                bottomBar = {
                    if (chore.status == ChoreStatus.COMPLETED) {
                        Surface(
                            tonalElevation = 3.dp,
                            shadowElevation = 8.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { showVerifyDialog = true },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary
                                    )
                                ) {
                                    Icon(Icons.Default.Verified, null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Verify & Award Points")
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    // Chore header
                    item {
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
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(70.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = chore.icon ?: "🎯",
                                        fontSize = 36.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = chore.title,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    StatusChip(status = chore.status)
                                }
                            }
                        }
                    }

                    // Description
                    if (chore.description.isNotBlank()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Description",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = chore.description,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }

                    // Details
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "Details",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                DetailRow(icon = Icons.Default.Stars, label = "Point Value", value = "${chore.pointValue} pts")
                                DetailRow(icon = Icons.Default.Person, label = "Assigned To", value = "${chore.assignedTo.size} people")
                                if (chore.dueDate != null) {
                                    DetailRow(icon = Icons.Default.CalendarToday, label = "Due Date", value = chore.dueDate)
                                }
                            }
                        }
                    }

                    // Photo proof
                    if (chore.photoProof != null) {
                        item {
                            Text(
                                text = "📸 Photo Proof",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Image(
                                    painter = rememberAsyncImagePainter(chore.photoProof),
                                    contentDescription = "Chore completion proof",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(250.dp),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }

                    // Subtasks
                    if (chore.subtasks.isNotEmpty()) {
                        item {
                            Text(
                                text = "Subtasks (${chore.subtasks.count { it.completed }}/${chore.subtasks.size} completed)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        items(chore.subtasks) { subtask ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (subtask.completed)
                                        MaterialTheme.colorScheme.secondaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (subtask.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (subtask.completed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = subtask.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }

            // Delete confirmation dialog
            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                    },
                    title = { Text("Delete Chore?", fontWeight = FontWeight.Bold) },
                    text = { Text("This action cannot be undone. The chore will be permanently deleted.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.deleteChore(chore)
                                showDeleteDialog = false
                                onNavigateBack()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Verify confirmation dialog
            if (showVerifyDialog) {
                AlertDialog(
                    onDismissRequest = { showVerifyDialog = false },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(48.dp)
                        )
                    },
                    title = { Text("Verify Completion?", fontWeight = FontWeight.Bold) },
                    text = { Text("Award ${chore.pointValue} points to the user who completed this chore?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                verifyError = null
                                viewModel.verifyChore(choreId = chore.id, approved = true)
                                showVerifyDialog = false
                            }
                        ) {
                            Text("Verify & Award")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showVerifyDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Show verify error if needed
            if (verifyError != null) {
                AlertDialog(
                    onDismissRequest = { verifyError = null },
                    title = { Text("Verify Failed") },
                    text = { Text(verifyError ?: "Unknown error") },
                    confirmButton = {
                        TextButton(onClick = { verifyError = null }) { Text("OK") }
                    }
                )
            }

            // React to verify result via createEditState
            LaunchedEffect(createEditState) {
                when (val s = createEditState) {
                    is CreateEditState.Success -> {
                        // If we just verified, return to dashboard
                        onNavigateBack()
                        viewModel.resetCreateEditState()
                    }
                    is CreateEditState.Error -> {
                        verifyError = s.message
                        viewModel.resetCreateEditState()
                    }
                    else -> {}
                }
            }
        }
        is ChoreDetailState.Deleted -> {
            LaunchedEffect(Unit) {
                onNavigateBack()
            }
        }
    }
}

@Composable
private fun StatusChip(status: ChoreStatus) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                when (status) {
                    ChoreStatus.PENDING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    ChoreStatus.IN_PROGRESS -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                    ChoreStatus.COMPLETED -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                    ChoreStatus.VERIFIED -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                    ChoreStatus.OVERDUE -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                }
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = when (status) {
                ChoreStatus.PENDING -> "Pending"
                ChoreStatus.IN_PROGRESS -> "In Progress"
                ChoreStatus.COMPLETED -> "Awaiting Verification"
                ChoreStatus.VERIFIED -> "Verified ✓"
                ChoreStatus.OVERDUE -> "Overdue"
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = when (status) {
                ChoreStatus.PENDING -> MaterialTheme.colorScheme.primary
                ChoreStatus.IN_PROGRESS -> MaterialTheme.colorScheme.tertiary
                ChoreStatus.COMPLETED -> MaterialTheme.colorScheme.secondary
                ChoreStatus.VERIFIED -> MaterialTheme.colorScheme.secondary
                ChoreStatus.OVERDUE -> MaterialTheme.colorScheme.error
            }
        )
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
