package com.lostsierra.chorequest.data.repository

import android.content.Context
import android.util.Log
import com.lostsierra.chorequest.data.local.SessionManager
import com.lostsierra.chorequest.data.local.dao.UserDao
import com.lostsierra.chorequest.data.local.entities.toEntity
import com.lostsierra.chorequest.data.local.entities.toDomain
import com.lostsierra.chorequest.data.remote.ChoreQuestApi
import com.lostsierra.chorequest.domain.models.*
import com.lostsierra.chorequest.utils.Result
import com.lostsierra.chorequest.utils.QRCodeUtils
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
    private val gson: Gson,
    private val driveApiService: com.lostsierra.chorequest.data.drive.DriveApiService,
    private val tokenManager: com.lostsierra.chorequest.data.drive.TokenManager,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "UserRepository"
        private const val LOAD_DEBOUNCE_MS = 2000L // Don't load more than once every 2 seconds
    }
    
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val loadMutex = Mutex()
    private var isLoading = false
    private var lastLoadTime = 0L

    /**
     * Get all users - loads from Drive on-demand, then returns from local cache
     */
    fun getAllUsers(): Flow<List<User>> {
        // Trigger background load from Drive (non-blocking) - only if not already loading
        repositoryScope.launch {
            loadUsersFromDrive()
        }
        
        // Return from local cache immediately, then update when Drive data loads
        return userDao.getAllUsers().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Load users from Drive and update local cache
     * Uses mutex to prevent concurrent loads and debouncing to prevent rapid successive loads
     */
    private suspend fun loadUsersFromDrive() {
        val now = System.currentTimeMillis()
        
        // Prevent concurrent loads - quick check before acquiring lock
        if (isLoading) {
            Log.d(TAG, "Load already in progress, skipping")
            return
        }
        
        // Debounce: don't load if we loaded recently
        if (now - lastLoadTime < LOAD_DEBOUNCE_MS) {
            Log.d(TAG, "Load debounced (last load was ${now - lastLoadTime}ms ago), skipping")
            return
        }
        
        loadMutex.withLock {
            // Double-check after acquiring lock
            if (isLoading) {
                Log.d(TAG, "Load already in progress (double-check), skipping")
                return@withLock
            }
            
            // Double-check debounce after acquiring lock
            val nowAfterLock = System.currentTimeMillis()
            if (nowAfterLock - lastLoadTime < LOAD_DEBOUNCE_MS) {
                Log.d(TAG, "Load debounced after lock (last load was ${nowAfterLock - lastLoadTime}ms ago), skipping")
                return@withLock
            }
            
            isLoading = true
            lastLoadTime = nowAfterLock
            try {
                val session = sessionManager.loadSession()
                if (session != null) {
                    val accessToken = tokenManager.getValidAccessToken()
                    
                    if (accessToken != null) {
                        try {
                            Log.d(TAG, "Loading users from Drive on-demand")
                            val folderId = session.driveWorkbookLink
                            val usersData = readUsersFromDrive(accessToken, folderId)
                            
                            if (usersData != null) {
                                val users = usersData.users
                                // Update local cache
                                userDao.deleteAllUsers()
                                if (users.isNotEmpty()) {
                                    userDao.insertUsers(users.map { it.toEntity() })
                                }
                                Log.d(TAG, "Loaded ${users.size} users from Drive")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error loading users from Drive, using local cache", e)
                        }
                    } else {
                        Log.d(TAG, "No access token, using local cache only")
                    }
                } else {
                    Log.d(TAG, "No session, skipping load")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in loadUsersFromDrive", e)
            } finally {
                isLoading = false
            }
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
                // File doesn't exist, return null
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading users from Drive", e)
            null
        }
    }

    /**
     * Helper: Write users to Drive using direct API
     */
    private suspend fun writeUsersToDrive(accessToken: String, folderId: String, usersData: UsersData): Boolean {
        return try {
            val fileName = "users.json"
            val jsonContent = gson.toJson(usersData)
            driveApiService.writeFileContent(accessToken, folderId, fileName, jsonContent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing users to Drive", e)
            false
        }
    }

    /**
     * Helper: Read family from Drive using direct API
     */
    private suspend fun readFamilyFromDrive(accessToken: String, folderId: String): Family? {
        return try {
            val fileName = "family.json"
            val fileId = driveApiService.findFileByName(accessToken, folderId, fileName)
            
            if (fileId != null) {
                val jsonContent = driveApiService.readFileContent(accessToken, fileId)
                gson.fromJson(jsonContent, Family::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading family from Drive", e)
            null
        }
    }

    /**
     * Helper: Write family to Drive using direct API
     */
    private suspend fun writeFamilyToDrive(accessToken: String, folderId: String, familyData: Family): Boolean {
        return try {
            val fileName = "family.json"
            val jsonContent = gson.toJson(familyData)
            driveApiService.writeFileContent(accessToken, folderId, fileName, jsonContent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing family to Drive", e)
            false
        }
    }


    /**
     * Update login streak when app opens in foreground
     * Compares today's date with lastLoginDate to determine if streak should increment or reset
     * Uses direct Drive API first, falls back to local cache update if Drive API fails
     */
    suspend fun updateLoginStreak(userId: String) {
        try {
            val session = sessionManager.loadSession()
            if (session == null) {
                Log.d(TAG, "No session found, skipping streak update")
                return
            }
            
            // Try direct Drive API first
            val accessToken = tokenManager.getValidAccessToken()
            if (accessToken != null) {
                try {
                    Log.d(TAG, "Using direct Drive API to update login streak")
                    val folderId = session.driveWorkbookLink
                    
                    // Read current users from Drive
                    val usersData = readUsersFromDrive(accessToken, folderId)
                    
                    if (usersData != null) {
                        val user = usersData.users.find { it.id == userId }
                        if (user != null) {
                            val today = LocalDate.now()
                            val lastLoginDate = user.stats.lastLoginDate?.let {
                                try {
                                    LocalDate.parse(it)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Error parsing lastLoginDate: $it", e)
                                    null
                                }
                            }
                            
                            val newStreak: Int
                            val newLastLoginDate: String = today.toString()
                            
                            if (lastLoginDate == null) {
                                // First time logging in, start streak at 1
                                newStreak = 1
                                Log.d(TAG, "First login for user ${user.name}, starting streak at 1")
                            } else {
                                val daysDiff = java.time.temporal.ChronoUnit.DAYS.between(lastLoginDate, today)
                                
                                when {
                                    daysDiff == 0L -> {
                                        // Already logged in today, no change
                                        Log.d(TAG, "User ${user.name} already logged in today, no streak change")
                                        return // Don't update if already logged in today
                                    }
                                    daysDiff == 1L -> {
                                        // Consecutive day - increment streak
                                        newStreak = user.stats.currentStreak + 1
                                        Log.d(TAG, "Consecutive login for user ${user.name}, incrementing streak: ${user.stats.currentStreak} -> $newStreak")
                                    }
                                    else -> {
                                        // Gap in login - reset streak to 1 (today's login)
                                        newStreak = 1
                                        Log.d(TAG, "Login gap detected for user ${user.name} (${daysDiff} days), resetting streak to 1")
                                    }
                                }
                            }
                            
                            // Update user with new streak and last login date
                            val updatedUser = user.copy(
                                stats = user.stats.copy(
                                    currentStreak = newStreak,
                                    lastLoginDate = newLastLoginDate
                                )
                            )
                            
                            // Update users array
                            val updatedUsers = usersData.users.map {
                                if (it.id == userId) updatedUser else it
                            }
                            val updatedUsersData = usersData.copy(users = updatedUsers)
                            
                            // Save to Drive
                            val usersWritten = writeUsersToDrive(accessToken, folderId, updatedUsersData)
                            if (usersWritten) {
                                // Also update family.json
                                val familyData = readFamilyFromDrive(accessToken, folderId)
                                if (familyData != null) {
                                    val updatedMembers = familyData.members.map { member ->
                                        if (member.id == userId) updatedUser else member
                                    }
                                    val updatedFamilyData = familyData.copy(members = updatedMembers)
                                    writeFamilyToDrive(accessToken, folderId, updatedFamilyData)
                                }
                                
                                // Update local cache
                                userDao.updateUser(updatedUser.toEntity())
                                Log.d(TAG, "Successfully updated login streak via Drive API for user ${user.name}")
                                return // Success, exit early
                            } else {
                                Log.w(TAG, "Failed to write updated streak to Drive, falling back to local cache update")
                            }
                        } else {
                            Log.w(TAG, "User not found in Drive data, falling back to local cache update")
                        }
                    } else {
                        Log.w(TAG, "Could not read users data from Drive, falling back to local cache update")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error using direct Drive API for streak update, falling back to local cache", e)
                }
            } else {
                Log.d(TAG, "No access token available, falling back to local cache update")
            }
            
            // Fallback: Update local cache only (Drive sync will eventually sync the correct data)
            // This ensures the UI shows updated streak even if Drive API fails
            try {
                val localUser = userDao.getUserById(userId)?.toDomain()
                if (localUser != null) {
                    val today = LocalDate.now()
                    val lastLoginDate = localUser.stats.lastLoginDate?.let {
                        try {
                            LocalDate.parse(it)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    
                    // Only update if we haven't already logged in today
                    if (lastLoginDate == null || java.time.temporal.ChronoUnit.DAYS.between(lastLoginDate, today) != 0L) {
                        val newStreak = when {
                            lastLoginDate == null -> 1
                            java.time.temporal.ChronoUnit.DAYS.between(lastLoginDate, today) == 1L -> localUser.stats.currentStreak + 1
                            else -> 1
                        }
                        
                        val updatedUser = localUser.copy(
                            stats = localUser.stats.copy(
                                currentStreak = newStreak,
                                lastLoginDate = today.toString()
                            )
                        )
                        
                        userDao.updateUser(updatedUser.toEntity())
                        Log.d(TAG, "Updated login streak in local cache only (Drive API unavailable)")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating streak in local cache", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating login streak", e)
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
     * Uses Google Drive Direct API first, falls back to Apps Script if needed
     */
    fun createUser(
        name: String,
        role: UserRole,
        canEarnPoints: Boolean,
        avatarUrl: String?,
        birthdate: String? = null
    ): Flow<Result<User>> = flow {
        emit(Result.Loading)
        try {
            val session = sessionManager.loadSession()
            
            if (session == null) {
                Log.e("UserRepository", "No session available, cannot create user")
                emit(Result.Error("No active session. Please log in first."))
                return@flow
            }
            
            // Get primary parent's email (ownerEmail) - needed to identify which Drive to access
            val allUsers = userDao.getAllUsers().first()
            val primaryParent = allUsers.find { it.toDomain().isPrimaryParent }
            val ownerEmail = primaryParent?.toDomain()?.email
            
            if (ownerEmail == null) {
                Log.e("UserRepository", "Primary parent not found or missing email")
                emit(Result.Error("Primary parent not found. Please log in again."))
                return@flow
            }
            
            // Try Drive Direct API first
            val accessToken = tokenManager.getValidAccessToken()
            val folderId = session.driveWorkbookLink
            
            if (accessToken != null && folderId != null) {
                try {
                    Log.d(TAG, "Attempting user creation via Drive Direct API")
                    
                    // Read users and family data
                    val usersData = readUsersFromDrive(accessToken, folderId)
                    val familyData = readFamilyFromDrive(accessToken, folderId)
                    
                    if (usersData != null && familyData != null) {
                        // Verify parent is authorized
                        val parentUser = usersData.users.find { it.id == session.userId }
                        if (parentUser == null || parentUser.role != UserRole.PARENT) {
                            Log.w(TAG, "Unauthorized: parent not found or not a parent, falling back to Apps Script")
                        } else {
                            // Create new user
                            val userId = UUID.randomUUID().toString()
                            val authToken = UUID.randomUUID().toString()
                            val normalizedRole = role.name.lowercase() // Convert enum to lowercase string for JSON
                            
                            val newUser = User(
                                id = userId,
                                name = name,
                                email = null,
                                role = role,
                                isPrimaryParent = false,
                                avatarUrl = avatarUrl,
                                pointsBalance = 0,
                                canEarnPoints = canEarnPoints,
                                authToken = authToken,
                                tokenVersion = 1,
                                devices = emptyList(),
                                createdAt = Instant.now().toString(),
                                createdBy = session.userId,
                                settings = UserSettings(
                                    notifications = true,
                                    theme = if (role == UserRole.CHILD) ThemeMode.COLORFUL else ThemeMode.LIGHT,
                                    celebrationStyle = CelebrationStyle.FIREWORKS,
                                    soundEffects = true
                                ),
                                stats = UserStats(
                                    totalChoresCompleted = 0,
                                    currentStreak = 0,
                                    lastLoginDate = null
                                ),
                                birthdate = birthdate
                            )
                            
                            // Add to users array
                            val updatedUsers = usersData.users + newUser
                            val updatedUsersData = com.lostsierra.chorequest.domain.models.UsersData(users = updatedUsers)
                            
                            // Add to family members (create a copy)
                            val newUserCopy = newUser.copy() // Deep copy
                            val updatedMembers = familyData.members + newUserCopy
                            
                            // Update family metadata (Apps Script format: version, lastModified, lastModifiedBy)
                            val now = Instant.now().toString()
                            val updatedMetadata = if (familyData.metadata != null) {
                                familyData.metadata.copy(
                                    lastModified = now,
                                    lastModifiedBy = session.userId,
                                    version = (familyData.metadata.version ?: 0) + 1,
                                    lastSyncedAt = familyData.metadata.lastSyncedAt // Preserve existing lastSyncedAt if present
                                )
                            } else {
                                com.lostsierra.chorequest.domain.models.SyncMetadata(
                                    version = 1,
                                    lastModified = now,
                                    lastModifiedBy = session.userId,
                                    lastSyncedAt = now // Use current time as default
                                )
                            }
                            
                            val updatedFamilyData = familyData.copy(
                                members = updatedMembers,
                                metadata = updatedMetadata
                            )
                            
                            // Write both files to Drive
                            val usersWritten = writeUsersToDrive(accessToken, folderId, updatedUsersData)
                            val familyWritten = writeFamilyToDrive(accessToken, folderId, updatedFamilyData)
                            
                            if (usersWritten && familyWritten) {
                                Log.i(TAG, "User created successfully via Drive Direct API: ${newUser.name}")
                                
                                // Save to local database
                                userDao.insertUser(newUser.toEntity())
                                
                                emit(Result.Success(newUser))
                                return@flow
                            } else {
                                Log.w(TAG, "Failed to write user to Drive, falling back to Apps Script")
                            }
                        }
                    } else {
                        Log.w(TAG, "Could not read users.json or family.json, falling back to Apps Script")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error using Drive Direct API, falling back to Apps Script", e)
                }
            } else {
                Log.d(TAG, "No access token or folder ID, using Apps Script")
            }
            
            // Fallback to Apps Script
            Log.d(TAG, "Falling back to Apps Script for user creation")
            val request = com.lostsierra.chorequest.data.remote.CreateUserRequest(
                parentUserId = session.userId,
                name = name,
                role = role,
                canEarnPoints = canEarnPoints,
                avatarUrl = avatarUrl,
                birthdate = birthdate,
                ownerEmail = ownerEmail
            )
            
            val response = api.createUser(request = request)
            
            if (response.isSuccessful && response.body()?.success == true) {
                val serverUser = response.body()?.user
                if (serverUser != null) {
                    Log.d(TAG, "Server user received - name: ${serverUser.name}, birthdate: ${serverUser.birthdate}")
                    // Save server-created user to local database
                    val entity = serverUser.toEntity()
                    userDao.insertUser(entity)
                    Log.i(TAG, "User created successfully via Apps Script: ${serverUser.name}")
                    emit(Result.Success(serverUser))
                } else {
                    Log.w(TAG, "Server response missing user data")
                    emit(Result.Error("Server response missing user data"))
                }
            } else {
                val errorBody = response.body()
                val errorMessage = errorBody?.error 
                    ?: errorBody?.message 
                    ?: response.message()
                    ?: "Failed to create user on server"
                Log.e(TAG, "Server sync failed for user creation: $errorMessage")
                emit(Result.Error("Failed to save user to server: $errorMessage"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Create user failed: ${e.message}", e)
            emit(Result.Error("Failed to create user: ${e.message}"))
        }
    }

    /**
     * Update user profile
     * Tries direct Drive API first for better performance, falls back to Apps Script if needed
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

            // Try direct Drive API first
            val accessToken = tokenManager.getValidAccessToken()
            if (accessToken != null) {
                try {
                    Log.d(TAG, "Using direct Drive API to update user")
                    val folderId = session.driveWorkbookLink
                    
                    // Read current users
                    val usersData = readUsersFromDrive(accessToken, folderId)
                    if (usersData != null) {
                        // Verify parent is authorized (or user updating themselves)
                        val parentUser = usersData.users.find { it.id == session.userId }
                        if (parentUser == null || (parentUser.role != UserRole.PARENT && session.userId != userId)) {
                            Log.w(TAG, "Unauthorized: parent not found or not authorized")
                            // Fall through to Apps Script for proper error handling
                        } else {
                            // Find target user
                            val targetUser = usersData.users.find { it.id == userId }
                            if (targetUser != null) {
                                // Merge settings if provided (like Apps Script does)
                                val mergedSettings = if (settings != null) {
                                    targetUser.settings.copy(
                                        notifications = settings.notifications,
                                        theme = settings.theme,
                                        celebrationStyle = settings.celebrationStyle,
                                        soundEffects = settings.soundEffects
                                    )
                                } else {
                                    targetUser.settings
                                }
                                
                                // Apply updates
                                val updatedUser = targetUser.copy(
                                    name = name ?: targetUser.name,
                                    avatarUrl = avatarUrl ?: targetUser.avatarUrl,
                                    settings = mergedSettings
                                )
                                
                                // Update users array
                                val updatedUsers = usersData.users.map { 
                                    if (it.id == userId) updatedUser else it
                                }
                                val updatedUsersData = usersData.copy(users = updatedUsers)
                                
                                // Write users.json back to Drive
                                if (writeUsersToDrive(accessToken, folderId, updatedUsersData)) {
                                    // Also update family.json to keep in sync
                                    val familyData = readFamilyFromDrive(accessToken, folderId)
                                    if (familyData != null) {
                                        val familyMemberIndex = familyData.members.indexOfFirst { it.id == userId }
                                        if (familyMemberIndex != -1) {
                                            // Create a deep copy of the updated user
                                            val updatedFamilyMembers = familyData.members.mapIndexed { index, member ->
                                                if (index == familyMemberIndex) updatedUser else member
                                            }
                                            val updatedFamilyData = familyData.copy(members = updatedFamilyMembers)
                                            
                                            if (writeFamilyToDrive(accessToken, folderId, updatedFamilyData)) {
                                                // Update local database
                                                userDao.updateUser(updatedUser.toEntity())
                                                Log.d(TAG, "User updated successfully via Drive API: ${updatedUser.name}")
                                                emit(Result.Success(updatedUser))
                                                return@flow
                                            } else {
                                                Log.w(TAG, "Failed to update family.json, falling back to Apps Script")
                                            }
                                        } else {
                                            Log.w(TAG, "User not found in family.json, falling back to Apps Script")
                                        }
                                    } else {
                                        Log.w(TAG, "Could not read family.json, falling back to Apps Script")
                                    }
                                } else {
                                    Log.w(TAG, "Failed to update users.json, falling back to Apps Script")
                                }
                            } else {
                                Log.w(TAG, "User not found in users.json, falling back to Apps Script")
                            }
                        }
                    } else {
                        Log.w(TAG, "Could not read users.json, falling back to Apps Script")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error using direct Drive API, falling back to Apps Script", e)
                }
            }

            // Fallback to Apps Script
            val updates = com.lostsierra.chorequest.data.remote.UserUpdates(
                name = name,
                avatarUrl = avatarUrl,
                settings = settings
            )

            val request = com.lostsierra.chorequest.data.remote.UpdateUserRequest(
                parentUserId = session.userId,
                targetUserId = userId,
                updates = updates
            )

            val response = api.updateUser(request = request)
            
            // Check for authorization error (401 status code)
            if (!response.isSuccessful && response.code() == 401) {
                val authUrl = com.lostsierra.chorequest.utils.AuthorizationHelper.getBaseAuthorizationUrl()
                val errorMsg = "Drive access not authorized. Please authorize the app to access your Google Drive."
                Log.e(TAG, "Authorization required: $errorMsg")
                emit(Result.Error("AUTHORIZATION_REQUIRED:$authUrl:$errorMsg"))
                return@flow
            }

            if (response.isSuccessful && response.body()?.success == true) {
                val updatedUser = response.body()?.user
                if (updatedUser != null) {
                    // Update local database
                    userDao.updateUser(updatedUser.toEntity())
                    Log.i("UserRepository", "User updated successfully via Apps Script: ${updatedUser.name}")
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
                    request = com.lostsierra.chorequest.data.remote.DeleteUserRequest(
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
