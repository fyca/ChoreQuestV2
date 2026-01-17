package com.chorequest.presentation.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chorequest.domain.models.CelebrationStyle
import com.chorequest.domain.models.ThemeMode
import com.chorequest.domain.models.UserSettings
import com.chorequest.presentation.components.ChoreQuestTopAppBar

/**
 * Edit profile screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onNavigateBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val user by viewModel.userState.collectAsState()
    var nameText by remember { mutableStateOf("") }
    var notificationsEnabled by remember { mutableStateOf(true) }
    var soundEffectsEnabled by remember { mutableStateOf(true) }
    var selectedTheme by remember { mutableStateOf<ThemeMode?>(null) }
    var selectedCelebrationStyle by remember { mutableStateOf<CelebrationStyle?>(null) }
    val updateState by viewModel.updateProfileState.collectAsState()

    // Update fields when user loads
    LaunchedEffect(user) {
        val currentUser = user
        if (currentUser != null) {
            nameText = currentUser.name
            notificationsEnabled = currentUser.settings.notifications
            soundEffectsEnabled = currentUser.settings.soundEffects
            selectedTheme = currentUser.settings.theme
            selectedCelebrationStyle = currentUser.settings.celebrationStyle
        }
    }

    // Get context for activity recreation
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Handle update result
    LaunchedEffect(updateState) {
        when (updateState) {
            is UpdateProfileState.Success -> {
                // Check if theme changed
                val currentUser = user
                val themeChanged = currentUser != null && 
                    (selectedTheme != currentUser.settings.theme)
                
                if (themeChanged) {
                    // Show message that app needs to refresh
                    kotlinx.coroutines.delay(500)
                    // Restart activity to apply theme changes
                    if (context is android.app.Activity) {
                        // Use recreate() to restart activity and apply new theme
                        context.recreate()
                        return@LaunchedEffect
                    }
                }
                kotlinx.coroutines.delay(1000)
                onNavigateBack()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            ChoreQuestTopAppBar(
                title = "Edit Profile",
                onNavigateBack = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Basic Information Section
            Text(
                text = "Basic Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Name field
            OutlinedTextField(
                value = nameText,
                onValueChange = { nameText = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(Icons.Default.Person, null)
                },
                singleLine = true
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Preferences Section
            Text(
                text = "Preferences",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Notifications toggle
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Notifications",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Receive notifications for chores and rewards",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { notificationsEnabled = it }
                    )
                }
            }

            // Sound Effects toggle
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.VolumeUp,
                            null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Sound Effects",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Play sounds when completing chores",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = soundEffectsEnabled,
                        onCheckedChange = { soundEffectsEnabled = it }
                    )
                }
            }

            // Theme selection
            Text(
                text = "Theme",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeMode.values().forEach { theme ->
                    FilterChip(
                        selected = selectedTheme == theme,
                        onClick = { selectedTheme = theme },
                        label = { Text(theme.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Celebration Style selection
            Text(
                text = "Celebration Style",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CelebrationStyle.values().forEach { style ->
                    FilterChip(
                        selected = selectedCelebrationStyle == style,
                        onClick = { selectedCelebrationStyle = style },
                        label = { Text(style.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Error message
            if (updateState is UpdateProfileState.Error) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = (updateState as UpdateProfileState.Error).message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Success message
            if (updateState is UpdateProfileState.Success) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = "Profile updated successfully!",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Save button
            Button(
                onClick = {
                    if (nameText.isBlank()) {
                        return@Button
                    }

                    val currentUser = user
                    if (currentUser == null) {
                        return@Button
                    }

                    // Check if anything changed
                    val nameChanged = nameText != currentUser.name
                    val notificationsChanged = notificationsEnabled != currentUser.settings.notifications
                    val soundEffectsChanged = soundEffectsEnabled != currentUser.settings.soundEffects
                    val themeChanged = selectedTheme != currentUser.settings.theme
                    val celebrationChanged = selectedCelebrationStyle != currentUser.settings.celebrationStyle

                    if (!nameChanged && !notificationsChanged && !soundEffectsChanged && !themeChanged && !celebrationChanged) {
                        // No changes
                        onNavigateBack()
                        return@Button
                    }

                    // Build updated settings only if any setting changed
                    val updatedSettings = if (notificationsChanged || soundEffectsChanged || themeChanged || celebrationChanged) {
                        UserSettings(
                            notifications = notificationsEnabled,
                            theme = selectedTheme ?: currentUser.settings.theme,
                            celebrationStyle = selectedCelebrationStyle ?: currentUser.settings.celebrationStyle,
                            soundEffects = soundEffectsEnabled
                        )
                    } else {
                        null
                    }

                    viewModel.updateProfile(
                        name = if (nameChanged) nameText else null,
                        settings = updatedSettings
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = updateState !is UpdateProfileState.Loading && 
                         nameText.isNotBlank() && 
                         selectedTheme != null && 
                         selectedCelebrationStyle != null
            ) {
                if (updateState is UpdateProfileState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Changes", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
