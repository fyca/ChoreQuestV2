package com.chorequest.presentation.chores

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.chorequest.domain.models.Subtask
import com.chorequest.domain.models.ChoreStatus
import com.chorequest.presentation.components.ChoreQuestTopAppBar
import com.chorequest.presentation.components.LoadingScreen
import com.chorequest.presentation.components.CelebrationAnimation
import com.chorequest.presentation.components.CelebrationStyle
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Screen for completing a chore
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompleteChoreScreen(
    choreId: String,
    onNavigateBack: () -> Unit,
    viewModel: ChoreViewModel = hiltViewModel()
) {
    val choreDetailState by viewModel.choreDetailState.collectAsState()
    val uploadProgress by viewModel.uploadProgress.collectAsState()
    var subtasks by remember { mutableStateOf(listOf<Subtask>()) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showCelebration by remember { mutableStateOf(false) }
    var pointsEarned by remember { mutableStateOf(0) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var showPhotoOptions by remember { mutableStateOf(false) }
    var initialChoreStatus by remember { mutableStateOf<ChoreStatus?>(null) }
    
    val context = LocalContext.current
    
    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (!success) {
            photoUri = null
        }
    }

    LaunchedEffect(choreId) {
        viewModel.loadChoreDetail(choreId)
    }

    // Update subtasks when chore loads and track initial status
    LaunchedEffect(choreDetailState) {
        if (choreDetailState is ChoreDetailState.Success) {
            val chore = (choreDetailState as ChoreDetailState.Success).chore
            if (subtasks.isEmpty()) {
                subtasks = chore.subtasks
            }
            // Store initial status if not set
            if (initialChoreStatus == null) {
                initialChoreStatus = chore.status
            }
        }
    }
    
    // Show celebration when chore is successfully completed
    LaunchedEffect(choreDetailState) {
        if (choreDetailState is ChoreDetailState.Success) {
            val chore = (choreDetailState as ChoreDetailState.Success).chore
            // Only show celebration if the chore status changed from pending to completed/verified
            val wasPending = initialChoreStatus == ChoreStatus.PENDING
            val isNowCompleted = chore.status == ChoreStatus.COMPLETED || 
                                 chore.status == ChoreStatus.VERIFIED
            if (wasPending && isNowCompleted && !showCelebration) {
                pointsEarned = chore.pointValue
                showCelebration = true
            }
        }
    }

    when (val state = choreDetailState) {
        is ChoreDetailState.Loading -> {
            LoadingScreen(message = "Loading quest...")
        }
        is ChoreDetailState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(state.message)
            }
        }
        is ChoreDetailState.Success -> {
            val chore = state.chore
            val allSubtasksCompleted = subtasks.isEmpty() || subtasks.all { it.completed }

            Scaffold(
                topBar = {
                    ChoreQuestTopAppBar(
                        title = "Complete Quest",
                        onNavigateBack = onNavigateBack
                    )
                },
                bottomBar = {
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
                            OutlinedButton(
                                onClick = onNavigateBack,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = { showConfirmDialog = true },
                                modifier = Modifier.weight(1f),
                                enabled = allSubtasksCompleted
                            ) {
                                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Complete!")
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
                        ChoreHeader(
                            title = chore.title,
                            description = chore.description,
                            pointValue = chore.pointValue,
                            icon = chore.icon
                        )
                    }

                    // Subtasks section
                    if (subtasks.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Tasks to Complete",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${subtasks.count { it.completed }}/${subtasks.size}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        itemsIndexed(subtasks) { index, subtask ->
                            SubtaskCheckItem(
                                subtask = subtask,
                                onToggle = {
                                    subtasks = subtasks.toMutableList().also {
                                        it[index] = subtask.copy(completed = !subtask.completed)
                                    }
                                }
                            )
                        }
                    } else {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "No subtasks! Just tap Complete when you're done.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }

                    // Photo proof section
                    item {
                        Text(
                            text = "📸 Photo Proof (Optional)",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    item {
                        if (photoUri != null) {
                            // Show captured photo
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column {
                                    Image(
                                        painter = rememberAsyncImagePainter(photoUri),
                                        contentDescription = "Chore proof photo",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Photo attached ✓",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        TextButton(onClick = { photoUri = null }) {
                                            Text("Remove")
                                        }
                                    }
                                }
                            }
                        } else {
                            // Show photo capture button
                            OutlinedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp),
                                onClick = {
                                    // Create file for photo
                                    val photoFile = File(
                                        context.cacheDir,
                                        "photos"
                                    ).apply { mkdirs() }.let {
                                        File(
                                            it,
                                            "chore_${choreId}_${System.currentTimeMillis()}.jpg"
                                        )
                                    }
                                    
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        photoFile
                                    )
                                    photoUri = uri
                                    cameraLauncher.launch(uri)
                                }
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoCamera,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Take a Photo",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Show what you've done!",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
            
            // Upload progress overlay
            when (uploadProgress) {
                is UploadProgress.Compressing -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Compressing image...", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                is UploadProgress.Uploading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Uploading to Drive...", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                is UploadProgress.Error -> {
                    LaunchedEffect(Unit) {
                        // Show error and reset
                        kotlinx.coroutines.delay(3000)
                        viewModel.resetUploadProgress()
                    }
                }
                else -> { /* No overlay */ }
            }

            // Confirmation dialog
            if (showConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showConfirmDialog = false },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Celebration,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                    },
                    title = {
                        Text(
                            text = "Complete This Quest?",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column {
                            Text("You'll earn ${chore.pointValue} points!")
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Great job! 🎉",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.completeChore(choreId, photoUri)
                                showConfirmDialog = false
                                // Don't show celebration yet - wait for success response
                            }
                        ) {
                            Text("Yes, Complete!")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfirmDialog = false }) {
                            Text("Not Yet")
                        }
                    }
                )
            }
        }
        is ChoreDetailState.Deleted -> {
            // Should not happen in complete flow
            onNavigateBack()
        }
    }

    // Celebration animation overlay
    if (showCelebration) {
        CelebrationAnimation(
            style = CelebrationStyle.FIREWORKS,
            pointsEarned = pointsEarned,
            onAnimationComplete = {
                showCelebration = false
                onNavigateBack()
            }
        )
    }
}

@Composable
private fun ChoreHeader(
    title: String,
    description: String,
    pointValue: Int,
    icon: String?
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = icon ?: "🎯",
                    fontSize = 36.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Points banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Stars,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Earn $pointValue Points!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 20.sp
                )
            }
        }
    }
}

@Composable
private fun SubtaskCheckItem(
    subtask: Subtask,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (subtask.completed) 
                MaterialTheme.colorScheme.secondaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = subtask.completed,
                onCheckedChange = { onToggle() },
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = subtask.title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            if (subtask.completed) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Completed",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
