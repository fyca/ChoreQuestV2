package com.chorequest.data.repository

import com.chorequest.data.local.SessionManager
import com.chorequest.data.local.dao.*
import com.chorequest.data.local.entities.toEntity
import com.chorequest.data.remote.ChoreQuestApi
import com.chorequest.data.remote.GoogleAuthRequest
import com.chorequest.data.remote.QRAuthRequest
import com.chorequest.domain.models.DeviceSession
import com.chorequest.domain.models.User
import com.chorequest.utils.Result
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
    private val transactionDao: TransactionDao
) {

    /**
     * Authenticate with Google OAuth token
     * @param googleToken ID token for authentication
     * @param accessToken OAuth access token for Drive API (direct from Android, preferred)
     * @param serverAuthCode Server auth code for OAuth token exchange (fallback, optional)
     */
    fun authenticateWithGoogle(googleToken: String, accessToken: String? = null, serverAuthCode: String? = null): Flow<Result<User>> = flow {
          emit(Result.Loading)
          try {
            android.util.Log.d("AuthRepository", "Attempting Google auth with URL: ${com.chorequest.utils.Constants.APPS_SCRIPT_WEB_APP_URL}")
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
                val authUrl = com.chorequest.utils.AuthorizationHelper.getBaseAuthorizationUrl()
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
       
                // Save session with lastSynced cleared to ensure sync runs after login
                val sessionWithClearedSync = session.copy(lastSynced = null)
                sessionManager.saveSession(sessionWithClearedSync)
       
                // Cache user locally
                userDao.insertUser(user.toEntity())
       
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
                } else if (com.chorequest.utils.AuthorizationHelper.isAuthorizationError(errorMessage)) {
                    val authUrl = com.chorequest.utils.AuthorizationHelper.extractAuthorizationUrl(errorMessage)
                        ?: com.chorequest.utils.AuthorizationHelper.getBaseAuthorizationUrl()
                    val userFriendlyMsg = com.chorequest.utils.AuthorizationHelper.extractErrorMessage(errorMessage)
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

                // Save session with lastSynced cleared to ensure sync runs after login
                val sessionWithClearedSync = session.copy(lastSynced = null)
                sessionManager.saveSession(sessionWithClearedSync)

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
     * Validate current session
     */
    fun validateSession(): Flow<Result<User>> = flow {
        emit(Result.Loading)
        try {
            val session = sessionManager.loadSession()
            if (session == null) {
                emit(Result.Error("No active session"))
                return@flow
            }

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
        return sessionManager.hasValidSession()
    }

    /**
     * Get current session
     */
    fun getCurrentSession(): DeviceSession? {
        return sessionManager.loadSession()
    }
}
