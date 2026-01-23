package com.lostsierra.chorequest.presentation.chores

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import com.lostsierra.chorequest.domain.models.Subtask
import com.lostsierra.chorequest.domain.models.ChoreStatus
import com.lostsierra.chorequest.presentation.components.ChoreQuestTopAppBar
import com.lostsierra.chorequest.presentation.components.LoadingScreen
import com.lostsierra.chorequest.presentation.components.CelebrationAnimation
import com.lostsierra.chorequest.presentation.components.CelebrationStyle
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
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
    var isCompleting by remember { mutableStateOf(false) }
    
    // Track when completion starts - check both Loading state and when completeChore is called
    LaunchedEffect(choreDetailState) {
        when (choreDetailState) {
            is ChoreDetailState.Loading -> isCompleting = true
            is ChoreDetailState.Success -> {
                // Reset loading state when completion succeeds
                if (isCompleting) {
                    isCompleting = false
                }
            }
            is ChoreDetailState.Error -> {
                // Reset loading state when completion fails
                if (isCompleting) {
                    isCompleting = false
                }
            }
            else -> {}
        }
    }
    
    // Also track upload progress to show loading during photo operations
    LaunchedEffect(uploadProgress) {
        isCompleting = uploadProgress != com.lostsierra.chorequest.presentation.chores.UploadProgress.Idle
    }
    
    val context = LocalContext.current
    
    // Camera launcher
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && pendingPhotoUri != null) {
            photoUri = pendingPhotoUri
        } else {
            photoUri = null
            pendingPhotoUri = null
        }
    }
    
    // Permission launcher for camera
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && pendingPhotoUri != null) {
            // Permission granted, launch camera with the prepared URI
            cameraLauncher.launch(pendingPhotoUri!!)
        } else {
            pendingPhotoUri = null
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
            val isLoading = isCompleting || uploadProgress != com.lostsierra.chorequest.presentation.chores.UploadProgress.Idle
            val canComplete = allSubtasksCompleted && (!chore.requirePhotoProof || photoUri != null)

            Box(modifier = Modifier.fillMaxSize()) {
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
                                modifier = Modifier.weight(1f),
                                enabled = !isLoading
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = { 
                                    if (canComplete) {
                                        showConfirmDialog = true
                                    } else if (chore.requirePhotoProof && photoUri == null) {
                                        // Show a message or do nothing - button is disabled
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = canComplete && !isLoading
                            ) {
                                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (chore.requirePhotoProof && photoUri == null) {
                                        "Photo Required"
                                    } else {
                                        "Complete!"
                                    }
                                )
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
                                enabled = !isLoading,
                                onToggle = {
                                    if (!isLoading) {
                                        subtasks = subtasks.toMutableList().also {
                                            it[index] = subtask.copy(completed = !subtask.completed)
                                        }
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
                            text = if (chore.requirePhotoProof) "📸 Photo Proof (Required)" else "📸 Photo Proof (Optional)",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // Show message if photo is required
                    if (chore.requirePhotoProof && photoUri == null) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "A photo is required to complete this chore",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
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
                                        TextButton(
                                            onClick = { if (!isLoading) photoUri = null },
                                            enabled = !isLoading
                                        ) {
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
                                enabled = !isLoading,
                                onClick = {
                                    if (isLoading) return@OutlinedCard
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
                                    
                                    // Check camera permission
                                    when {
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.CAMERA
                                        ) == PackageManager.PERMISSION_GRANTED -> {
                                            // Permission already granted, launch camera
                                            pendingPhotoUri = uri
                                            cameraLauncher.launch(uri)
                                        }
                                        else -> {
                                            // Request camera permission first
                                            pendingPhotoUri = uri
                                            permissionLauncher.launch(Manifest.permission.CAMERA)
                                        }
                                    }
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
            
            // Loading overlay - blocks all touch when completing chore
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                        .pointerInput(Unit) {
                            // Block all touch events
                            detectTapGestures { }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                            strokeWidth = 4.dp
                        )
                        Text(
                            text = when (uploadProgress) {
                                is com.lostsierra.chorequest.presentation.chores.UploadProgress.Compressing -> "Compressing image..."
                                is com.lostsierra.chorequest.presentation.chores.UploadProgress.Uploading -> "Uploading photo..."
                                else -> "Completing quest..."
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Please wait...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
                                isCompleting = true
                                viewModel.completeChore(choreId, photoUri, subtasks)
                                showConfirmDialog = false
                                // Don't show celebration yet - wait for success response
                            },
                            enabled = !isLoading
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
    enabled: Boolean = true,
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
                onCheckedChange = { if (enabled) onToggle() },
                enabled = enabled,
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
