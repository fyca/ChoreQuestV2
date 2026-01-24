package com.lostsierra.chorequest.data.repository

import android.util.Log
import com.lostsierra.chorequest.data.local.SessionManager
import com.lostsierra.chorequest.data.local.dao.*
import com.lostsierra.chorequest.data.local.entities.toEntity
import com.lostsierra.chorequest.data.remote.ChoreQuestApi
import com.lostsierra.chorequest.data.remote.GoogleAuthRequest
import com.lostsierra.chorequest.data.remote.QRAuthRequest
import com.lostsierra.chorequest.domain.models.DeviceSession
import com.lostsierra.chorequest.domain.models.User
import com.lostsierra.chorequest.domain.models.UsersData
import com.lostsierra.chorequest.utils.Result
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: ChoreQuestApi,
    private val sessionManager: SessionManager,
    private val userDao: UserDao,
    private val choreDao: ChoreDao,
    private val rewardDao: RewardDao,
    private val activityLogDao: ActivityLogDao,
    private val transactionDao: TransactionDao,
    private val tokenManager: com.lostsierra.chorequest.data.drive.TokenManager,
    private val gson: Gson,
    private val driveApiService: com.lostsierra.chorequest.data.drive.DriveApiService
) {
    companion object {
        private const val TAG = "AuthRepository"
    }

    /**
     * Authenticate with Google OAuth token
     * @param googleToken ID token for authentication
     * @param accessToken OAuth access token for Drive API (direct from Android, preferred)
     * @param serverAuthCode Server auth code for OAuth token exchange (fallback, optional)
     */
    fun authenticateWithGoogle(googleToken: String, accessToken: String? = null, serverAuthCode: String? = null): Flow<Result<User>> = flow {
        // Store access token if provided (before authentication)
        if (accessToken != null) {
            android.util.Log.d("AuthRepository", "Storing access token for Drive API")
            tokenManager.storeAccessToken(accessToken, expiresInSeconds = 3600)
        }
          emit(Result.Loading)
          try {
            android.util.Log.d("AuthRepository", "Attempting Google auth with URL: ${com.lostsierra.chorequest.utils.Constants.APPS_SCRIPT_WEB_APP_URL}")
            android.util.Log.d("AuthRepository", "Access token provided: ${accessToken != null}")
            android.util.Log.d("AuthRepository", "Server auth code provided: ${serverAuthCode != null}")
            
            val response = api.authenticateWithGoogle(
                request = GoogleAuthRequest(
                    googleToken = googleToken,
                    accessToken = accessToken, // Send access token directly (preferred)
                    serverAuthCode = serverAuthCode, // Fallback to server auth code if access token not available
                    deviceType = "android"
                )
            )
            android.util.Log.d("AuthRepository", "API response code: ${response.code()}, isSuccessful: ${response.isSuccessful}")
            
            // Check for authorization error (401 status code) BEFORE trying to parse body
            if (!response.isSuccessful && response.code() == 401) {
                val authUrl = com.lostsierra.chorequest.utils.AuthorizationHelper.getBaseAuthorizationUrl()
                val errorMsg = "Drive access not authorized. Please authorize the app to access your Google Drive."
                android.util.Log.e("AuthRepository", "Authorization required (401): $errorMsg")
                emit(Result.Error("AUTHORIZATION_REQUIRED:$authUrl:$errorMsg"))
                return@flow
            }
            
            val authResponse = response.body()
            android.util.Log.d("AuthRepository", "Response body success: ${authResponse?.success}, error: ${authResponse?.error}")
       
            if (response.isSuccessful && authResponse?.success == true) {
                val user = authResponse.user
                val session = authResponse.session
                
                if (user == null || session == null) {
                    val errorMsg = authResponse.error ?: "Missing user or session data"
                    android.util.Log.e("AuthRepository", "Auth response missing data: $errorMsg")
                    emit(Result.Error(errorMsg))
                    return@flow
                }
                
                android.util.Log.d("AuthRepository", "Auth successful: user=${user.name}, role=${user.role}")
       
                // Ensure session tokenVersion matches user tokenVersion (session might have different version)
                // Use user's tokenVersion as the source of truth
                val correctedSession = session.copy(
                    tokenVersion = user.tokenVersion,
                    lastSynced = null
                )
                android.util.Log.d(TAG, "Google OAuth login - saving session: userId=${session.userId}, tokenVersion=${user.tokenVersion} (corrected from ${session.tokenVersion})")
                sessionManager.saveSession(correctedSession)
       
                // Cache user locally
                userDao.insertUser(user.toEntity())
                
                // Access token was already stored at the beginning of the function if provided
                // If we only have serverAuthCode, Apps Script will handle token exchange
                // and we can retrieve it later if needed
       
                android.util.Log.d("AuthRepository", "Session saved, emitting success")
                emit(Result.Success(user))
            } else {
                // Apps Script returns 200 even for errors, check the success field
                val errorMessage = authResponse?.error 
                    ?: authResponse?.message 
                    ?: "Authentication failed"
                
                android.util.Log.e("AuthRepository", "Auth failed: $errorMessage, code=${response.code()}, success=${authResponse?.success}")
                
                if (authResponse?.stack != null) {
                    android.util.Log.e("AuthRepository", "Error stack: ${authResponse.stack}")
                }
                
                // Check if this is an OAuth setup error (shouldn't show browser authorization)
                if (authResponse?.error?.contains("OAuth credentials not configured", ignoreCase = true) == true ||
                    authResponse?.requiresOAuthSetup == true) {
                    android.util.Log.e("AuthRepository", "OAuth credentials not configured - this is a server configuration issue")
                    emit(Result.Error("OAuth credentials not configured on server. Please configure OAUTH_CLIENT_ID and OAUTH_CLIENT_SECRET in Apps Script Properties. See OAUTH_SETUP.md for instructions."))
                } else if (com.lostsierra.chorequest.utils.AuthorizationHelper.isAuthorizationError(errorMessage)) {
                    val authUrl = com.lostsierra.chorequest.utils.AuthorizationHelper.extractAuthorizationUrl(errorMessage)
                        ?: com.lostsierra.chorequest.utils.AuthorizationHelper.getBaseAuthorizationUrl()
                    val userFriendlyMsg = com.lostsierra.chorequest.utils.AuthorizationHelper.extractErrorMessage(errorMessage)
                    android.util.Log.e("AuthRepository", "Authorization required (from error message): $userFriendlyMsg")
                    emit(Result.Error("AUTHORIZATION_REQUIRED:$authUrl:$userFriendlyMsg"))
                } else {
                    emit(Result.Error(errorMessage))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Exception during auth", e)
            emit(Result.Error(e.message ?: "Network error. Backend may not be deployed yet."))
        }
    }

    /**
     * Authenticate with QR code
     */
    fun authenticateWithQR(
        familyId: String,
        userId: String,
        token: String,
        tokenVersion: Int,
        ownerEmail: String,
        folderId: String
    ): Flow<Result<User>> = flow {
        emit(Result.Loading)
        try {
            val deviceId = UUID.randomUUID().toString()
            val deviceName = android.os.Build.MODEL

            val response = api.authenticateWithQR(
                request = QRAuthRequest(
                    familyId = familyId,
                    userId = userId,
                    token = token,
                    tokenVersion = tokenVersion,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    deviceType = "android",
                    ownerEmail = ownerEmail,
                    folderId = folderId
                )
            )

            if (response.isSuccessful && response.body()?.success == true) {
                val authResponse = response.body()!!
                val user = authResponse.user ?: authResponse.userData
                val session = authResponse.session ?: authResponse.sessionData

                if (user == null || session == null) {
                    emit(Result.Error("Invalid response: missing user or session data"))
                    return@flow
                }

                // Ensure session tokenVersion matches user tokenVersion (session might have different version)
                // Use user's tokenVersion as the source of truth
                val correctedSession = session.copy(
                    tokenVersion = user.tokenVersion,
                    lastSynced = null
                )
                android.util.Log.d(TAG, "QR login successful - saving session: userId=${session.userId}, familyId=${session.familyId}, tokenVersion=${user.tokenVersion} (corrected from ${session.tokenVersion})")
                sessionManager.saveSession(correctedSession)
                
                // Verify session was saved
                val savedSession = sessionManager.loadSession()
                android.util.Log.d(TAG, "Session save verification: ${if (savedSession != null) "SUCCESS - session found" else "FAILED - session not found"}")

                // Cache user locally
                userDao.insertUser(user.toEntity())

                emit(Result.Success(user))
            } else {
                val errorBody = response.body()
                val errorMessage = errorBody?.error 
                    ?: errorBody?.message 
                    ?: response.message()
                    ?: "QR authentication failed"
                emit(Result.Error(errorMessage))
            }
        } catch (e: Exception) {
            emit(Result.Error(e.message ?: "Unknown error occurred"))
        }
    }

    /**
     * Helper: Read users from Drive using direct API
     */
    private suspend fun readUsersFromDrive(accessToken: String, folderId: String): UsersData? {
        return try {
            val fileName = "users.json"
            val fileId = driveApiService.findFileByName(accessToken, folderId, fileName)
            
            if (fileId != null) {
                val jsonContent = driveApiService.readFileContent(accessToken, fileId)
                gson.fromJson(jsonContent, UsersData::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error reading users from Drive", e)
            null
        }
    }

    /**
     * Validate current session
     * Uses direct Drive API for faster performance (no cold start)
     */
    fun validateSession(): Flow<Result<User>> = flow {
        emit(Result.Loading)
        try {
            val session = sessionManager.loadSession()
            if (session == null) {
                emit(Result.Error("No active session"))
                return@flow
            }

            // Try direct Drive API first
            val accessToken = tokenManager.getValidAccessToken()
            if (accessToken != null) {
                try {
                    android.util.Log.d(TAG, "Validating session using direct Drive API")
                    val folderId = session.driveWorkbookLink
                    val usersData = readUsersFromDrive(accessToken, folderId)
                    
                    if (usersData != null) {
                        val user = usersData.users.find { it.id == session.userId }
                        
                        if (user != null) {
                            // Validate token version first (more critical)
                            if (user.tokenVersion != session.tokenVersion) {
                                android.util.Log.w(TAG, "Token version mismatch - token was regenerated, need re-authentication")
                                sessionManager.clearSession()
                                emit(Result.Error("token_regenerated"))
                                return@flow
                            }
                            
                            // If token version matches but token itself differs, update the session with new token
                            // This can happen if token was regenerated via QR code or another device
                            if (user.authToken != session.authToken) {
                                android.util.Log.w(TAG, "Token mismatch but version matches - updating session with new token")
                                // Update session with new token from Drive
                                val updatedSession = session.copy(authToken = user.authToken)
                                sessionManager.saveSession(updatedSession)
                                android.util.Log.d(TAG, "Session token updated successfully")
                            }
                            
                            // Update cached users - save all users, not just the current one
                            val allUsers = usersData.users
                            userDao.deleteAllUsers()
                            if (allUsers.isNotEmpty()) {
                                userDao.insertUsers(allUsers.map { it.toEntity() })
                            }
                            android.util.Log.d(TAG, "Saved ${allUsers.size} users to local cache")
                            
                            android.util.Log.d(TAG, "Session validated successfully via Drive API")
                            emit(Result.Success(user))
                            return@flow
                        } else {
                            android.util.Log.w(TAG, "User not found in Drive")
                            sessionManager.clearSession()
                            emit(Result.Error("user_not_found"))
                            return@flow
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error validating session via Drive API, falling back to Apps Script", e)
                }
            } else {
                android.util.Log.d(TAG, "No access token, falling back to Apps Script")
            }

            // Fallback to Apps Script
            val response = api.validateSession(
                userId = session.userId,
                token = session.authToken,
                tokenVersion = session.tokenVersion
            )

            if (response.isSuccessful && response.body()?.valid == true) {
                val user = response.body()!!.userData!!
                
                // Update cached user
                userDao.insertUser(user.toEntity())

                emit(Result.Success(user))
            } else {
                val reason = response.body()?.reason ?: "Session expired"
                sessionManager.clearSession()
                emit(Result.Error(reason))
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error validating session", e)
            emit(Result.Error(e.message ?: "Unknown error occurred"))
        }
    }

    /**
     * Logout - clears session and all local data ONLY
     * NOTE: This does NOT delete data from Drive/backend - only clears local cache
     * Note: Google Sign-In sign out should be handled in the UI layer
     */
    suspend fun logout() {
        android.util.Log.i("AuthRepository", "Logout: Clearing local data only (NOT deleting from Drive)")
        
        // IMPORTANT: Clear session FIRST to prevent any sync operations from running
        // This ensures no background sync can interfere with logout
        sessionManager.clearSession()
        
        // Clear all local data (local cache only - does NOT affect Drive/backend)
        // Note: Session is already cleared, so any sync operations will fail safely
        choreDao.deleteAllChores()
        rewardDao.deleteAllRewards()
        activityLogDao.deleteAllLogs()
        transactionDao.deleteAllTransactions()
        userDao.deleteAllUsers()
        
        android.util.Log.i("AuthRepository", "Logout: Local data cleared. Drive data is NOT affected.")
    }

    /**
     * Check if user has valid session
     */
    fun hasValidSession(): Boolean {
        val hasSession = sessionManager.hasValidSession()
        android.util.Log.d(TAG, "hasValidSession() = $hasSession")
        return hasSession
    }

    /**
     * Get current session
     */
    fun getCurrentSession(): DeviceSession? {
        return sessionManager.loadSession()
    }
}
