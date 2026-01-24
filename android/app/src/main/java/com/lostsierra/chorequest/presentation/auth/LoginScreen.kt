package com.lostsierra.chorequest.presentation.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lostsierra.chorequest.utils.AuthorizationHelper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.UserRecoverableAuthException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

/**
 * Login screen with OAuth and QR code options
 */
@Composable
fun LoginScreen(
    onNavigateToParentDashboard: () -> Unit,
    onNavigateToChildDashboard: () -> Unit,
    onNavigateToQRScanner: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val loginState by viewModel.loginState.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    // Google Sign-In client - sign out on screen entry to allow account selection
    // IMPORTANT: Request Drive scopes and server auth code for OAuth access token
    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(com.lostsierra.chorequest.utils.Constants.GOOGLE_WEB_CLIENT_ID)
            .requestEmail()
            .requestProfile() // Request profile to get name and picture
            .requestScopes(
                Scope(com.google.android.gms.common.Scopes.DRIVE_FILE) // Request Drive file access
            )
            .requestServerAuthCode(com.lostsierra.chorequest.utils.Constants.GOOGLE_WEB_CLIENT_ID, true) // Request server auth code for OAuth token exchange
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    // Sign out from Google when screen is shown to allow account selection
    LaunchedEffect(Unit) {
        try {
            googleSignInClient.signOut().await()
            android.util.Log.d("LoginScreen", "Signed out from Google Sign-In")
        } catch (e: Exception) {
            android.util.Log.d("LoginScreen", "Google Sign-In sign out: ${e.message}")
            // Continue even if sign out fails (e.g., not signed in)
        }
    }

    // Store the current Google account email for authorization URL
    var currentGoogleEmail by remember { mutableStateOf<String?>(null) }
    
    // Google Sign-In launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        android.util.Log.d("LoginScreen", "Google Sign-In result code: ${result.resultCode}")
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                android.util.Log.d("LoginScreen", "Google account: ${account.email}, idToken present: ${account.idToken != null}, serverAuthCode present: ${account.serverAuthCode != null}")
                
                // Check if Drive scope was granted during sign-in
                val grantedScopes = account.grantedScopes
                val hasDriveScope = grantedScopes?.any { it.scopeUri.contains("drive") } == true
                android.util.Log.d("LoginScreen", "Granted scopes: ${grantedScopes?.joinToString { it.scopeUri } ?: "none"}, hasDriveScope: $hasDriveScope")
                
                // Store email for authorization URL
                currentGoogleEmail = account.email
                account.idToken?.let { idToken ->
                    val serverAuthCode = account.serverAuthCode
                    android.util.Log.d("LoginScreen", "Server auth code: ${if (serverAuthCode != null) "present (${serverAuthCode.length} chars)" else "null"}")
                    
                    // Get access token using GoogleAuthUtil
                    // Since we've already requested Drive scope, we can get the token directly
                    scope.launch {
                        var accessToken: String? = null
                        if (hasDriveScope && account.account != null) {
                            try {
                                // Use the full Drive file scope
                                val scopeString = "oauth2:https://www.googleapis.com/auth/drive.file"
                                android.util.Log.d("LoginScreen", "Requesting access token with scope: $scopeString")
                                accessToken = withContext(Dispatchers.IO) {
                                    try {
                                        GoogleAuthUtil.getToken(context, account.account!!, scopeString)
                                    } catch (e: UserRecoverableAuthException) {
                                        android.util.Log.w("LoginScreen", "User recoverable auth exception - may need additional authorization: ${e.message}")
                                        null
                                    } catch (e: GoogleAuthException) {
                                        android.util.Log.e("LoginScreen", "GoogleAuthException getting token", e)
                                        null
                                    } catch (e: Exception) {
                                        android.util.Log.e("LoginScreen", "Error getting access token", e)
                                        null
                                    }
                                }
                                android.util.Log.d("LoginScreen", "Access token: ${if (accessToken != null) "present (${accessToken!!.length} chars)" else "null"}")
                            } catch (e: Exception) {
                                android.util.Log.e("LoginScreen", "Error in coroutine for token", e)
                            }
                        } else {
                            android.util.Log.d("LoginScreen", "Drive scope not granted or account is null, using server auth code only")
                        }
                        
                        android.util.Log.d("LoginScreen", "Calling viewModel.loginWithGoogle")
                        viewModel.loginWithGoogle(idToken, account.email, accessToken, serverAuthCode)
                    }
                } ?: run {
                    android.util.Log.e("LoginScreen", "ID token is null")
                }
            } catch (e: ApiException) {
                android.util.Log.e("LoginScreen", "Google Sign-In failed", e)
                android.util.Log.e("LoginScreen", "Status code: ${e.statusCode}")
            }
        } else {
            android.util.Log.d("LoginScreen", "Google Sign-In cancelled or failed")
        }
    }

    // Handle navigation events
    LaunchedEffect(key1 = Unit) {
        android.util.Log.d("LoginScreen", "Setting up navigation event collector")
        viewModel.navigationEvent.collect { event ->
            android.util.Log.d("LoginScreen", "Navigation event received: ${event::class.simpleName}")
            when (event) {
                is NavigationEvent.NavigateToParentDashboard -> {
                    android.util.Log.d("LoginScreen", "Navigating to parent dashboard")
                    onNavigateToParentDashboard()
                }
                is NavigationEvent.NavigateToChildDashboard -> {
                    android.util.Log.d("LoginScreen", "Navigating to child dashboard")
                    onNavigateToChildDashboard()
                }
                is NavigationEvent.NavigateToQRScanner -> {
                    android.util.Log.d("LoginScreen", "Navigating to QR scanner")
                    onNavigateToQRScanner()
                }
            }
        }
    }

    // Navigate on success state as backup
    LaunchedEffect(key1 = loginState) {
        if (loginState is LoginState.Success) {
            val user = (loginState as LoginState.Success).user
            if (user.role == com.lostsierra.chorequest.domain.models.UserRole.PARENT) {
                onNavigateToParentDashboard()
            } else {
                onNavigateToChildDashboard()
            }
        }
    }
    
    // Show authorization dialog if needed
    when (val state = loginState) {
        is LoginState.AuthorizationRequired -> {
            AlertDialog(
                onDismissRequest = { 
                    viewModel.clearError()
                },
                title = { Text("Authorization Required") },
                text = {
                    Column {
                        Text(state.message)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Click 'Authorize' to open your browser and grant Drive access. After authorizing, return to the app and click 'Retry Login'.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                        confirmButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                // Try using UriHandler first (Compose way)
                                try {
                                    val url = state.url.takeIf { it.isNotBlank() && (it.startsWith("http://") || it.startsWith("https://")) }
                                        ?: com.lostsierra.chorequest.utils.Constants.APPS_SCRIPT_WEB_APP_URL
                                    android.util.Log.d("LoginScreen", "Opening URL with UriHandler: $url")
                                    uriHandler.openUri(url)
                                } catch (e: Exception) {
                                    android.util.Log.e("LoginScreen", "UriHandler failed, trying AuthorizationHelper: ${e.message}")
                                    // Fallback to AuthorizationHelper
                                    AuthorizationHelper.openAuthorizationUrl(context, state.url)
                                }
                                // Don't clear error - keep the dialog open so user can retry
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Authorize")
                        }
                        Button(
                            onClick = {
                                viewModel.retryLoginAfterAuthorization()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Retry Login")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            viewModel.clearError()
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
        else -> {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        when (loginState) {
            is LoginState.Loading -> {
                CircularProgressIndicator()
            }
            is LoginState.Error -> {
                ErrorContent(
                    message = (loginState as LoginState.Error).message,
                    onRetry = { viewModel.clearError() }
                )
            }
            is LoginState.AuthorizationRequired -> {
                // Show login content while authorization dialog is visible
                LoginContent(
                    onGoogleSignIn = {
                        val signInIntent = googleSignInClient.signInIntent
                        googleSignInLauncher.launch(signInIntent)
                    },
                    onQRCodeLogin = {
                        viewModel.navigateToQRScanner()
                    }
                )
            }
            else -> {
                LoginContent(
                    onGoogleSignIn = {
                        // TODO: For production, set up Google Cloud OAuth:
                        // 1. Go to Google Cloud Console
                        // 2. Enable Google Drive API
                        // 3. Create OAuth 2.0 credentials
                        // 4. Replace "YOUR_WEB_CLIENT_ID" below with your Web Client ID
                        
                        // TEMPORARY: Mock login for testing
                        // Remove this and uncomment Google Sign-In below when ready
                        //viewModel.loginWithGoogle("mock_token_for_testing")
                        
                        // Real Google Sign-In
                        val signInIntent = googleSignInClient.signInIntent
                        googleSignInLauncher.launch(signInIntent)
                        
                    },
                    onQRCodeLogin = {
                        viewModel.navigateToQRScanner()
                    }
                )
            }
        }
    }
}

@Composable
private fun LoginContent(
    onGoogleSignIn: () -> Unit,
    onQRCodeLogin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App logo/icon placeholder
        Icon(
            imageVector = Icons.Default.QrCode,
            contentDescription = "ChoreQuest Logo",
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        // App title
        Text(
            text = "ChoreQuest",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Making chores fun for the whole family!",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        // OAuth button for parent
        Button(
            onClick = onGoogleSignIn,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                painter = painterResource(id = android.R.drawable.ic_dialog_info),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Sign in with Google (Parent)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Divider with "OR"
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Divider(modifier = Modifier.weight(1f))
            Text(
                text = " OR ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Divider(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // QR code button for family members
        OutlinedButton(
            onClick = onQRCodeLogin,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Default.QrCode,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Scan QR Code (Family)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Info text
        Text(
            text = "Parents sign in to set up the family.\nFamily members use QR codes to join.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = android.R.drawable.ic_dialog_alert),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Login Failed",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Try Again")
        }
    }
}
