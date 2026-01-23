package com.lostsierra.chorequest.presentation.users

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lostsierra.chorequest.presentation.components.ChoreQuestTopAppBar
import com.lostsierra.chorequest.presentation.components.LoadingScreen
import com.lostsierra.chorequest.utils.QRCodeUtils
import kotlinx.coroutines.launch

/**
 * Screen for displaying a user's QR code for login
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRCodeDisplayScreen(
    userId: String,
    onNavigateBack: () -> Unit,
    viewModel: UserViewModel = hiltViewModel()
) {
    val userDetailState by viewModel.userDetailState.collectAsState()
    val qrCodeState by viewModel.qrCodeState.collectAsState()
    var showRegenerateDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(userId) {
        viewModel.loadUserDetail(userId)
        viewModel.generateQRCode(userId)
    }

    when (val state = userDetailState) {
        is UserDetailState.Loading -> {
            LoadingScreen(message = "Loading user...")
        }
        is UserDetailState.Error -> {
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
        is UserDetailState.Success -> {
            val user = state.user
            val snackbarHostState = remember { SnackbarHostState() }

            Scaffold(
                topBar = {
                    ChoreQuestTopAppBar(
                        title = "${user.name}'s QR Code",
                        onNavigateBack = onNavigateBack
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Instructions card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Scan this code to log in as ${user.name}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Use the QR code scanner on the login screen",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // QR code display
                    when (val qrState = qrCodeState) {
                        is QRCodeState.Loading -> {
                            CircularProgressIndicator()
                        }
                        is QRCodeState.Success -> {
                            val qrCodeBitmap = remember(qrState.qrCodeData) {
                                QRCodeUtils.generateQRCodeBitmap(qrState.qrCodeData, 512)
                            }
                            
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Card(
                                    modifier = Modifier.size(300.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        qrCodeBitmap?.let {
                                            Image(
                                                bitmap = it.asImageBitmap(),
                                                contentDescription = "QR Code",
                                                modifier = Modifier.size(280.dp)
                                            )
                                        }
                                    }
                                }
                                
                                // Download button
                                val coroutineScope = rememberCoroutineScope()
                                Button(
                                    onClick = {
                                        qrCodeBitmap?.let { bitmap ->
                                            val fileName = "${user.name.replace(" ", "_")}_QRCode"
                                            val uri = QRCodeUtils.saveQRCodeToDownloads(context, bitmap, fileName)
                                            coroutineScope.launch {
                                                if (uri != null) {
                                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                                        snackbarHostState.showSnackbar("QR code saved to Pictures/ChoreQuest")
                                                    } else {
                                                        snackbarHostState.showSnackbar("QR code saved to Downloads")
                                                    }
                                                } else {
                                                    snackbarHostState.showSnackbar("Failed to save QR code")
                                                }
                                            }
                                        } ?: run {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("QR code not available")
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.Download,
                                        null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Download QR Code")
                                }
                            }
                        }
                        is QRCodeState.Error -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Text(
                                    text = qrState.message,
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                        is QRCodeState.Idle -> {
                            // Do nothing
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Regenerate button
                    OutlinedButton(
                        onClick = { showRegenerateDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Regenerate QR Code")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Info text
                    Text(
                        text = "Regenerate the QR code if you think the old one has been compromised",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Regenerate confirmation dialog
            if (showRegenerateDialog) {
                AlertDialog(
                    onDismissRequest = { showRegenerateDialog = false },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                    },
                    title = { Text("Regenerate QR Code?", fontWeight = FontWeight.Bold) },
                    text = {
                        Text("This will invalidate the old QR code. Anyone using the old code will need to scan the new one.")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.regenerateQRCode(userId)
                                showRegenerateDialog = false
                            }
                        ) {
                            Text("Regenerate")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRegenerateDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
        is UserDetailState.Deleted -> {
            LaunchedEffect(Unit) {
                onNavigateBack()
            }
        }
    }
}
