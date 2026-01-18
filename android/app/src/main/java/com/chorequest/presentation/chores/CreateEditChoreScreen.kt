package com.chorequest.presentation.chores

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.chorequest.domain.models.Chore
import com.chorequest.domain.models.RecurringFrequency
import com.chorequest.domain.models.RecurringSchedule
import com.chorequest.domain.models.Subtask
import com.chorequest.domain.models.User
import com.chorequest.presentation.components.ChoreQuestTopAppBar
import com.chorequest.presentation.components.ConfirmationDialog
import com.chorequest.presentation.users.UserViewModel
import com.chorequest.utils.AuthorizationHelper
import com.chorequest.utils.AgeUtils
import com.chorequest.utils.AgeGroup
import com.chorequest.utils.ChoreRecommendations
import com.chorequest.domain.models.UserRole
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Screen for creating or editing a chore
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditChoreScreen(
    choreId: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: ChoreViewModel = hiltViewModel(),
    userViewModel: UserViewModel = hiltViewModel()
) {
    val createEditState by viewModel.createEditState.collectAsState()
    val choreDetailState by viewModel.choreDetailState.collectAsState()
    val allUsers by userViewModel.allUsers.collectAsState()
    
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var pointValue by remember { mutableStateOf("10") }
    var subtasks by remember { mutableStateOf(listOf<Subtask>()) }
    var showAddSubtaskDialog by remember { mutableStateOf(false) }
    var selectedUser by remember { mutableStateOf<String?>(null) }
    var dueDate by remember { mutableStateOf<LocalDate?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showUserSelector by remember { mutableStateOf(false) }
    var currentChore by remember { mutableStateOf<Chore?>(null) }
    var isRecurring by remember { mutableStateOf(false) }
    var recurringFrequency by remember { mutableStateOf(RecurringFrequency.DAILY) }
    var selectedDaysOfWeek by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var showRecurringDialog by remember { mutableStateOf(false) }
    
    val isEditing = choreId != null

    // Load chore data if editing
    LaunchedEffect(choreId) {
        if (choreId != null) {
            viewModel.loadChoreDetail(choreId)
        }
    }

    // Populate form when chore loads
    LaunchedEffect(choreDetailState) {
        if (choreDetailState is ChoreDetailState.Success) {
            val chore = (choreDetailState as ChoreDetailState.Success).chore
            currentChore = chore
            title = chore.title
            description = chore.description
            pointValue = chore.pointValue.toString()
            subtasks = chore.subtasks
            selectedUser = chore.assignedTo.firstOrNull()
            dueDate = chore.dueDate?.let {
                try {
                    LocalDate.parse(it)
                } catch (e: Exception) {
                    null
                }
            }
            isRecurring = chore.recurring != null
            chore.recurring?.let { recurring ->
                recurringFrequency = recurring.frequency
                selectedDaysOfWeek = recurring.daysOfWeek?.toSet() ?: emptySet()
            }
        }
    }

    // Handle success/error states
    LaunchedEffect(createEditState) {
        when (createEditState) {
            is CreateEditState.Success -> {
                onNavigateBack()
                viewModel.resetCreateEditState()
            }
            else -> {}
        }
    }
    
    val context = LocalContext.current
    
    // Show authorization dialog if needed
    LaunchedEffect(createEditState) {
        android.util.Log.d("CreateEditChoreScreen", "createEditState changed: $createEditState")
        if (createEditState is CreateEditState.AuthorizationRequired) {
            android.util.Log.d("CreateEditChoreScreen", "Authorization required detected!")
        }
    }
    
    // Authorization dialog - show directly from state
    when (val state = createEditState) {
        is CreateEditState.AuthorizationRequired -> {
            AlertDialog(
                onDismissRequest = { 
                    viewModel.resetCreateEditState()
                },
                title = { Text("Authorization Required") },
                text = {
                    Column {
                        Text(state.message)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Click 'Authorize' to open your browser and grant Drive access. After authorizing, return to the app and try again.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            AuthorizationHelper.openAuthorizationUrl(context, state.url)
                            viewModel.resetCreateEditState()
                        }
                    ) {
                        Text("Authorize")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            viewModel.resetCreateEditState()
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
        else -> {}
    }

    // Show loading while fetching chore in edit mode
    if (isEditing && choreDetailState is ChoreDetailState.Loading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Scaffold(
        topBar = {
            ChoreQuestTopAppBar(
                title = if (isEditing) "Edit Chore" else "Create Chore",
                onNavigateBack = onNavigateBack,
                actions = {
                    TextButton(
                        onClick = {
                            if (title.isNotBlank() && pointValue.toIntOrNull() != null) {
                                val recurringSchedule = if (isRecurring) {
                                    RecurringSchedule(
                                        frequency = recurringFrequency,
                                        daysOfWeek = if (recurringFrequency == RecurringFrequency.WEEKLY && selectedDaysOfWeek.isNotEmpty()) {
                                            selectedDaysOfWeek.toList()
                                        } else null,
                                        endDate = null
                                    )
                                } else null

                                if (isEditing && currentChore != null) {
                                    // Update existing chore
                                    viewModel.updateChore(
                                        currentChore!!.copy(
                                            title = title,
                                            description = description,
                                            assignedTo = if (selectedUser != null) listOf(selectedUser!!) else emptyList(),
                                            pointValue = pointValue.toInt(),
                                            dueDate = dueDate?.toString(),
                                            subtasks = subtasks,
                                            recurring = recurringSchedule
                                        )
                                    )
                                } else {
                                    // Create new chore
                                    viewModel.createChore(
                                        title = title,
                                        description = description,
                                        assignedTo = if (selectedUser != null) listOf(selectedUser!!) else emptyList(),
                                        pointValue = pointValue.toInt(),
                                        dueDate = dueDate?.toString(),
                                        subtasks = subtasks,
                                        color = null,
                                        icon = null,
                                        recurring = recurringSchedule
                                    )
                                }
                            }
                        },
                        enabled = title.isNotBlank() && pointValue.toIntOrNull() != null
                    ) {
                        Text(
                            text = if (isEditing) "Save" else "Create",
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        }
    ) { padding ->
        val density = LocalDensity.current
        val itemHeightPx = remember { with(density) { 60.dp.toPx() } }
        
        // Drag and drop state for subtasks
        var draggedIndex by remember { mutableStateOf<Int?>(null) }
        var dragOffset by remember { mutableStateOf(0f) }
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Title
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Chore Title *") },
                    placeholder = { Text("e.g., Clean your room") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Title, null)
                    }
                )
            }

            // Description
            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    placeholder = { Text("Add details about this chore...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 5,
                    leadingIcon = {
                        Icon(Icons.Default.Description, null)
                    }
                )
            }

            // Points
            item {
                OutlinedTextField(
                    value = pointValue,
                    onValueChange = { if (it.all { char -> char.isDigit() }) pointValue = it },
                    label = { Text("Point Value *") },
                    placeholder = { Text("10") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    leadingIcon = {
                        Icon(Icons.Default.Stars, null)
                    }
                )
            }

            // Assigned To
            item {
                @OptIn(ExperimentalMaterial3Api::class)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    onClick = { showUserSelector = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Assigned To",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (selectedUser == null) {
                                    "Select a family member..."
                                } else {
                                    allUsers.find { it.id == selectedUser }?.name ?: "Unknown"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selectedUser == null) 
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                else 
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Age-based recommendations (only when creating and a child is selected)
            item {
                if (!isEditing && selectedUser != null) {
                    val selectedUserObj = allUsers.find { it.id == selectedUser }
                    if (selectedUserObj?.role == UserRole.CHILD) {
                        var isExpanded by remember { mutableStateOf(false) }
                        val age = AgeUtils.calculateAge(selectedUserObj.birthdate)
                        val recommendations = if (age != null) {
                            ChoreRecommendations.getRecommendedChoresForAge(age)
                        } else {
                            // If no birthdate, show general recommendations for common age groups
                            ChoreRecommendations.getRecommendedChores(AgeGroup.EARLY_ELEMENTARY) +
                            ChoreRecommendations.getRecommendedChores(AgeGroup.LATE_ELEMENTARY)
                        }.distinctBy { it.title } // Remove duplicates
                        
                        // Always show recommendations section for children
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Clickable header to expand/collapse
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isExpanded = !isExpanded },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Lightbulb,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Age-Appropriate Suggestions",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            if (age != null) {
                                                Text(
                                                    text = "Based on ${selectedUserObj.name}'s age ($age years old) • ${recommendations.size} suggestions",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                                )
                                            } else {
                                                Text(
                                                    text = "General suggestions for ${selectedUserObj.name} • ${recommendations.size} suggestions",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    }
                                    Icon(
                                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                
                                // Show recommendations when expanded
                                if (isExpanded && recommendations.isNotEmpty()) {
                                    // Scrollable list of recommendations
                                    Column(
                                        modifier = Modifier
                                            .heightIn(max = 400.dp)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        recommendations.forEach { recommendation ->
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surface
                                                ),
                                                onClick = {
                                                    title = recommendation.title
                                                    description = recommendation.description
                                                    pointValue = recommendation.suggestedPoints.toString()
                                                    // Populate subtasks if the recommendation has them
                                                    if (recommendation.subtasks.isNotEmpty()) {
                                                        subtasks = recommendation.subtasks.mapIndexed { index, subtaskTitle ->
                                                            Subtask(
                                                                id = java.util.UUID.randomUUID().toString(),
                                                                title = subtaskTitle,
                                                                completed = false
                                                            )
                                                        }
                                                    } else {
                                                        subtasks = emptyList()
                                                    }
                                                }
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                            ) {
                                                                Text(
                                                                    text = recommendation.title,
                                                                    style = MaterialTheme.typography.bodyLarge,
                                                                    fontWeight = FontWeight.Medium
                                                                )
                                                                if (recommendation.subtasks.isNotEmpty()) {
                                                                    Icon(
                                                                        Icons.Default.List,
                                                                        contentDescription = "${recommendation.subtasks.size} subtasks",
                                                                        modifier = Modifier.size(16.dp),
                                                                        tint = MaterialTheme.colorScheme.primary
                                                                    )
                                                                    Text(
                                                                        text = "${recommendation.subtasks.size}",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = MaterialTheme.colorScheme.primary
                                                                    )
                                                                }
                                                            }
                                                            Text(
                                                                text = recommendation.description,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.padding(top = 4.dp)
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = "${recommendation.suggestedPoints} pts",
                                                            style = MaterialTheme.typography.labelLarge,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    
                                    Text(
                                        text = "Tap any suggestion to use it (includes subtasks if shown)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                } else if (isExpanded && recommendations.isEmpty()) {
                                    Text(
                                        text = "No recommendations available. Add a birthdate for age-specific suggestions.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Due Date
            item {
                @OptIn(ExperimentalMaterial3Api::class)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    onClick = { showDatePicker = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Due Date",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = dueDate?.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")) ?: "No due date",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (dueDate == null) 
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                else 
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (dueDate != null) {
                            IconButton(onClick = { dueDate = null }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear date",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Recurring Chore Toggle
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Repeat,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Recurring Chore",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (isRecurring) {
                                        when (recurringFrequency) {
                                            RecurringFrequency.DAILY -> "Repeats daily"
                                            RecurringFrequency.WEEKLY -> "Repeats weekly"
                                            RecurringFrequency.MONTHLY -> "Repeats monthly"
                                        }
                                    } else "Does not repeat",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isRecurring,
                            onCheckedChange = { 
                                isRecurring = it
                                if (it) showRecurringDialog = true
                            }
                        )
                    }
                }
            }

            // Recurring settings (if enabled)
            if (isRecurring) {
                item {
                    @OptIn(ExperimentalMaterial3Api::class)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        onClick = { showRecurringDialog = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Configure Recurring Schedule",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                if (recurringFrequency == RecurringFrequency.WEEKLY && selectedDaysOfWeek.isNotEmpty()) {
                                    val dayNames = selectedDaysOfWeek.sorted().map { 
                                        when (it) {
                                            1 -> "Mon"
                                            2 -> "Tue"
                                            3 -> "Wed"
                                            4 -> "Thu"
                                            5 -> "Fri"
                                            6 -> "Sat"
                                            7 -> "Sun"
                                            else -> ""
                                        }
                                    }
                                    Text(
                                        text = "On: ${dayNames.joinToString(", ")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Subtasks section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Subtasks (${subtasks.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = { showAddSubtaskDialog = true }
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Subtask")
                    }
                }
            }

            // Subtasks list with drag and drop reordering
            itemsIndexed(subtasks) { index, subtask ->
                val isDragging = draggedIndex == index
                
                SubtaskItem(
                    subtask = subtask,
                    isDragging = isDragging,
                    dragOffset = if (isDragging) dragOffset else 0f,
                    itemHeightPx = itemHeightPx,
                    onRemove = {
                        subtasks = subtasks.toMutableList().also { it.removeAt(index) }
                    },
                    onDragStart = {
                        draggedIndex = index
                        dragOffset = 0f
                    },
                    onDrag = { dragAmount ->
                        if (draggedIndex != null) {
                            val fromIndex = draggedIndex!!
                            dragOffset += dragAmount.y
                            
                            // Calculate target index based on cumulative drag offset
                            val targetIndex = when {
                                dragOffset > itemHeightPx * 0.5f && fromIndex < subtasks.size - 1 -> fromIndex + 1
                                dragOffset < -itemHeightPx * 0.5f && fromIndex > 0 -> fromIndex - 1
                                else -> fromIndex
                            }
                            
                            // Reorder if target index changed
                            if (targetIndex != fromIndex) {
                                val newList = subtasks.toMutableList()
                                val item = newList.removeAt(fromIndex)
                                newList.add(targetIndex, item)
                                subtasks = newList
                                draggedIndex = targetIndex
                                dragOffset = 0f // Reset offset after reorder
                            }
                        }
                    },
                    onDragEnd = {
                        draggedIndex = null
                        dragOffset = 0f
                    },
                    index = index
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        // Loading overlay
        if (createEditState is CreateEditState.Loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        // Error message
        if (createEditState is CreateEditState.Error) {
            Snackbar(
                modifier = Modifier.padding(16.dp)
            ) {
                Text((createEditState as CreateEditState.Error).message)
            }
        }
    }

    // Add subtask dialog
    if (showAddSubtaskDialog) {
        AddSubtaskDialog(
            onDismiss = { showAddSubtaskDialog = false },
            onAdd = { subtaskTitle ->
                subtasks = subtasks + Subtask(
                    id = java.util.UUID.randomUUID().toString(),
                    title = subtaskTitle,
                    completed = false
                )
                showAddSubtaskDialog = false
            }
        )
    }

    // User selector dialog
    if (showUserSelector) {
        UserSelectorDialog(
            users = allUsers,
            selectedUserId = selectedUser,
            onDismiss = { showUserSelector = false },
            onConfirm = { selected ->
                selectedUser = selected
                showUserSelector = false
            }
        )
    }

    // Date picker dialog
    if (showDatePicker) {
        DatePickerDialog(
            selectedDate = dueDate,
            onDismiss = { showDatePicker = false },
            onConfirm = { date ->
                dueDate = date
                showDatePicker = false
            }
        )
    }

    // Recurring schedule dialog
    if (showRecurringDialog) {
        RecurringScheduleDialog(
            frequency = recurringFrequency,
            selectedDays = selectedDaysOfWeek,
            onDismiss = { showRecurringDialog = false },
            onConfirm = { frequency, days ->
                recurringFrequency = frequency
                selectedDaysOfWeek = days
                showRecurringDialog = false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubtaskItem(
    subtask: Subtask,
    isDragging: Boolean,
    dragOffset: Float,
    itemHeightPx: Float,
    onRemove: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (dragAmount: androidx.compose.ui.geometry.Offset) -> Unit,
    onDragEnd: () -> Unit,
    index: Int
) {
    val density = LocalDensity.current
    val offsetY = with(density) { dragOffset.toDp() }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = offsetY)
            .then(
                if (isDragging) {
                    Modifier.alpha(0.8f).zIndex(1f)
                } else {
                    Modifier
                }
            )
            .pointerInput(index) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDrag = { change, dragAmount ->
                        onDrag(dragAmount)
                    },
                    onDragEnd = { onDragEnd() }
                )
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag handle icon (using Menu icon as drag handle)
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Drag to reorder",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.CheckCircleOutline,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = subtask.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun AddSubtaskDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var subtaskTitle by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Subtask") },
        text = {
            OutlinedTextField(
                value = subtaskTitle,
                onValueChange = { subtaskTitle = it },
                label = { Text("Subtask title") },
                placeholder = { Text("e.g., Pick up toys") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (subtaskTitle.isNotBlank()) {
                        onAdd(subtaskTitle)
                    }
                },
                enabled = subtaskTitle.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun UserSelectorDialog(
    users: List<User>,
    selectedUserId: String?,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var tempSelected by remember { mutableStateOf(selectedUserId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign To", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (users.isEmpty()) {
                    Text(
                        text = "No family members yet. Add them first!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    users.forEach { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    tempSelected = if (tempSelected == user.id) null else user.id
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = user.id == tempSelected,
                                onClick = {
                                    tempSelected = if (tempSelected == user.id) null else user.id
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = user.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (user.role.name == "PARENT") "Parent" else "Child",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(tempSelected) },
                enabled = tempSelected != null
            ) {
                Text("Assign")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialog(
    selectedDate: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate?.atStartOfDay(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
            ?: System.currentTimeMillis()
    )

    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                        onConfirm(date)
                    }
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        androidx.compose.material3.DatePicker(
            state = datePickerState,
            showModeToggle = false
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringScheduleDialog(
    frequency: RecurringFrequency,
    selectedDays: Set<Int>,
    onDismiss: () -> Unit,
    onConfirm: (RecurringFrequency, Set<Int>) -> Unit
) {
    var tempFrequency by remember { mutableStateOf(frequency) }
    var tempDays by remember { mutableStateOf(selectedDays) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recurring Schedule", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Frequency",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                
                // Frequency selector
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecurringFrequency.values().forEach { freq ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { tempFrequency = freq },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = tempFrequency == freq,
                                onClick = { tempFrequency = freq }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (freq) {
                                    RecurringFrequency.DAILY -> "Daily"
                                    RecurringFrequency.WEEKLY -> "Weekly"
                                    RecurringFrequency.MONTHLY -> "Monthly"
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }

                // Days of week selector (only for weekly)
                if (tempFrequency == RecurringFrequency.WEEKLY) {
                    Text(
                        text = "Days of Week",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(
                            1 to "M",
                            2 to "T",
                            3 to "W",
                            4 to "T",
                            5 to "F",
                            6 to "S",
                            7 to "S"
                        ).forEach { (dayNum, dayLabel) ->
                            val isSelected = dayNum in tempDays
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    tempDays = if (isSelected) {
                                        tempDays - dayNum
                                    } else {
                                        tempDays + dayNum
                                    }
                                },
                                label = { Text(dayLabel) }
                            )
                        }
                    }
                    
                    if (tempDays.isEmpty()) {
                        Text(
                            text = "Select at least one day",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(tempFrequency, tempDays) },
                enabled = tempFrequency != RecurringFrequency.WEEKLY || tempDays.isNotEmpty()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
