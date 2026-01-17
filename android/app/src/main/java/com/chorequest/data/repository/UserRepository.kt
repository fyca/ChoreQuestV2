package com.chorequest.data.repository

import android.content.Context
import android.util.Log
import com.chorequest.data.local.SessionManager
import com.chorequest.data.local.dao.UserDao
import com.chorequest.data.local.entities.toEntity
import com.chorequest.data.local.entities.toDomain
import com.chorequest.data.remote.ChoreQuestApi
import com.chorequest.domain.models.*
import com.chorequest.utils.Result
import com.chorequest.utils.QRCodeUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing user data
 */
@Singleton
class UserRepository @Inject constructor(
    private val api: ChoreQuestApi,
    private val userDao: UserDao,
    private val sessionManager: SessionManager,
    @ApplicationContext private val context: Context
) {

    /**
     * Get all users from local database
     */
    fun getAllUsers(): Flow<List<User>> {
        return userDao.getAllUsers().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get user by ID from local database
     */
    suspend fun getUserById(userId: String): User? {
        return userDao.getUserById(userId)?.toDomain()
    }

    /**
     * Create a new user (family member)
     */
    fun createUser(
        name: String,
        role: UserRole,
        canEarnPoints: Boolean,
        avatarUrl: String?
    ): Flow<Result<User>> = flow {
        emit(Result.Loading)
        try {
            val session = sessionManager.loadSession()
            val newUserId = UUID.randomUUID().toString()
            val currentTimestamp = Instant.now().toString()

            // Don't create locally first - server is the source of truth
            // Only create on server, then sync back to local
            if (session == null) {
                Log.e("UserRepository", "No session available, cannot create user")
                emit(Result.Error("No active session. Please log in first."))
                return@flow
            }
            
            val request = com.chorequest.data.remote.CreateUserRequest(
                parentUserId = session.userId,
                name = name,
                role = role,
                canEarnPoints = canEarnPoints,
                avatarUrl = avatarUrl
            )
            Log.d("UserRepository", "Creating user with canEarnPoints: $canEarnPoints (type: ${canEarnPoints::class.java.simpleName})")
            Log.d("UserRepository", "Request object: parentUserId=${request.parentUserId}, name=${request.name}, role=${request.role}, canEarnPoints=${request.canEarnPoints}, avatarUrl=${request.avatarUrl}")
            val response = api.createUser(request = request)
            
            if (response.isSuccessful && response.body()?.success == true) {
                val serverUser = response.body()?.user
                if (serverUser != null) {
                    // Save server-created user to local database
                    userDao.insertUser(serverUser.toEntity())
                    Log.i("UserRepository", "User created successfully on server: ${serverUser.name}")
                    emit(Result.Success(serverUser))
                } else {
                    Log.w("UserRepository", "Server response missing user data")
                    emit(Result.Error("Server response missing user data"))
                }
            } else {
                val errorBody = response.body()
                val errorMessage = errorBody?.error 
                    ?: errorBody?.message 
                    ?: response.message()
                    ?: "Failed to create user on server"
                Log.e("UserRepository", "Server sync failed for user creation: $errorMessage")
                emit(Result.Error("Failed to save user to server: $errorMessage"))
            }
        } catch (e: Exception) {
            Log.e("UserRepository", "Create user failed: ${e.message}")
            emit(Result.Error("Failed to create user: ${e.message}"))
        }
    }

    /**
     * Update user profile
     */
    fun updateUser(
        userId: String,
        name: String? = null,
        avatarUrl: String? = null,
        settings: UserSettings? = null
    ): Flow<Result<User>> = flow {
        emit(Result.Loading)
        try {
            val session = sessionManager.loadSession()
            if (session == null) {
                emit(Result.Error("No active session"))
                return@flow
            }

            val updates = com.chorequest.data.remote.UserUpdates(
                name = name,
                avatarUrl = avatarUrl,
                settings = settings
            )

            val request = com.chorequest.data.remote.UpdateUserRequest(
                parentUserId = session.userId,
                targetUserId = userId,
                updates = updates
            )

            val response = api.updateUser(request = request)

            if (response.isSuccessful && response.body()?.success == true) {
                val updatedUser = response.body()?.user
                if (updatedUser != null) {
                    // Update local database
                    userDao.updateUser(updatedUser.toEntity())
                    Log.i("UserRepository", "User updated successfully: ${updatedUser.name}")
                    emit(Result.Success(updatedUser))
                } else {
                    emit(Result.Error("Server response missing user data"))
                }
            } else {
                val errorBody = response.body()
                val errorMessage = errorBody?.error
                    ?: errorBody?.message
                    ?: response.message()
                    ?: "Failed to update user"
                Log.e("UserRepository", "Update user failed: $errorMessage")
                emit(Result.Error(errorMessage))
            }
        } catch (e: Exception) {
            Log.e("UserRepository", "Update user failed: ${e.message}")
            emit(Result.Error("Failed to update user: ${e.message}"))
        }
    }

    /**
     * Generate QR code for a user
     */
    fun generateQRCode(userId: String): Flow<Result<String>> = flow {
        emit(Result.Loading)
        try {
            val user = userDao.getUserById(userId)?.toDomain()
            if (user != null) {
                val session = sessionManager.loadSession()
                
                // Get primary parent's email (ownerEmail)
                val allUsers = userDao.getAllUsers().first()
                val primaryParent = allUsers.find { it.toDomain().isPrimaryParent }
                val ownerEmail = primaryParent?.toDomain()?.email
                    ?: throw Exception("Primary parent not found")
                
                // Get folder ID from session (driveWorkbookLink contains the folder ID)
                val folderId = session?.driveWorkbookLink ?: throw Exception("Folder ID not found in session")
                
                val qrCodeData = QRCodeUtils.generateQRCodeData(
                    userId = userId,
                    userName = user.name,
                    familyId = session?.familyId ?: "",
                    authToken = user.authToken,
                    tokenVersion = user.tokenVersion,
                    ownerEmail = ownerEmail,
                    folderId = folderId
                )
                emit(Result.Success(qrCodeData))
            } else {
                emit(Result.Error("User not found"))
            }
        } catch (e: Exception) {
            Log.e("UserRepository", "Generate QR code failed: ${e.message}")
            emit(Result.Error("Failed to generate QR code: ${e.message}"))
        }
    }

    /**
     * Regenerate QR code for a user (invalidates old codes)
     */
    fun regenerateQRCode(userId: String): Flow<Result<String>> = flow {
        emit(Result.Loading)
        try {
            val user = userDao.getUserById(userId)?.toDomain()
            if (user != null) {
                // Increment token version to invalidate old QR codes
                val updatedUser = user.copy(
                    tokenVersion = user.tokenVersion + 1,
                    authToken = UUID.randomUUID().toString()
                )
                
                // Update locally
                userDao.updateUser(updatedUser.toEntity())
                
                // Generate new QR code
                val session = sessionManager.loadSession()
                
                // Get primary parent's email (ownerEmail)
                val allUsers = userDao.getAllUsers().first()
                val primaryParent = allUsers.find { it.toDomain().isPrimaryParent }
                val ownerEmail = primaryParent?.toDomain()?.email
                    ?: throw Exception("Primary parent not found")
                
                // Get folder ID from session (driveWorkbookLink contains the folder ID)
                val folderId = session?.driveWorkbookLink ?: throw Exception("Folder ID not found in session")
                
                val qrCodeData = QRCodeUtils.generateQRCodeData(
                    userId = userId,
                    userName = updatedUser.name,
                    familyId = session?.familyId ?: "",
                    authToken = updatedUser.authToken,
                    tokenVersion = updatedUser.tokenVersion,
                    ownerEmail = ownerEmail,
                    folderId = folderId
                )
                
                emit(Result.Success(qrCodeData))
            } else {
                emit(Result.Error("User not found"))
            }
        } catch (e: Exception) {
            Log.e("UserRepository", "Regenerate QR code failed: ${e.message}")
            emit(Result.Error("Failed to regenerate QR code: ${e.message}"))
        }
    }

    /**
     * Delete a user
     */
    fun deleteUser(user: User): Flow<Result<Unit>> = flow {
        emit(Result.Loading)
        try {
            // Delete locally first
            userDao.deleteUser(user.toEntity())
            
            // Sync with server
            val session = sessionManager.loadSession()
            if (session != null) {
                val response = api.deleteUser(
                    request = com.chorequest.data.remote.DeleteUserRequest(
                        parentUserId = session.userId,
                        targetUserId = user.id
                    )
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    emit(Result.Success(Unit))
                } else {
                    Log.w("UserRepository", "Server sync failed for user deletion: ${response.message()}")
                    emit(Result.Success(Unit))
                }
            } else {
                emit(Result.Success(Unit))
            }
        } catch (e: Exception) {
            Log.e("UserRepository", "Delete user failed: ${e.message}")
            emit(Result.Success(Unit))
        }
    }

    /**
     * Fetch users from server and update local database
     */
    suspend fun syncUsers() {
        try {
            val session = sessionManager.loadSession()
            if (session != null) {
                val response = api.listUsers(familyId = session.familyId)
                if (response.isSuccessful && response.body()?.success == true) {
                    val users = response.body()?.users ?: emptyList()
                    userDao.insertUsers(users.map { it.toEntity() })
                }
            }
        } catch (e: Exception) {
            Log.e("UserRepository", "Sync users failed: ${e.message}")
        }
    }
}
