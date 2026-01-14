package com.chorequest.data.repository

import com.chorequest.data.local.SessionManager
import com.chorequest.data.local.dao.UserDao
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
    private val userDao: UserDao
) {

    /**
     * Authenticate with Google OAuth token
     */
    fun authenticateWithGoogle(googleToken: String): Flow<Result<User>> = flow {
          emit(Result.Loading)
          try {
            android.util.Log.d("AuthRepository", "Attempting Google auth with URL: ${com.chorequest.utils.Constants.APPS_SCRIPT_WEB_APP_URL}")
            val response = api.authenticateWithGoogle(
                request = GoogleAuthRequest(
                    googleToken = googleToken,
                    deviceType = "android"
                )
            )
                android.util.Log.d("AuthRepository", "API response code: ${response.code()}, isSuccessful: ${response.isSuccessful}")
                
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
       
                    // Save session
                    sessionManager.saveSession(session)
       
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
                    
                    emit(Result.Error(errorMessage))
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
        tokenVersion: Int
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
                    deviceType = "android"
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

                // Save session
                sessionManager.saveSession(session)

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
     * Logout - clears session and local data
     * Note: Google Sign-In sign out should be handled in the UI layer
     */
    suspend fun logout() {
        sessionManager.clearSession()
        userDao.deleteAllUsers()
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
