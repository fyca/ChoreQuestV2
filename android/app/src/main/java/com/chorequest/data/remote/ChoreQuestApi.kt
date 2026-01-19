package com.chorequest.data.remote

import com.chorequest.domain.models.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit API interface for ChoreQuest Apps Script backend
 */
interface ChoreQuestApi {

    // Authentication
    @POST(".")
    suspend fun authenticateWithGoogle(
        @Query("path") path: String = "auth",
        @Query("action") action: String = "google",
        @Body request: GoogleAuthRequest
    ): Response<AuthResponse>

    @POST(".")
    suspend fun authenticateWithQR(
        @Query("path") path: String = "auth",
        @Query("action") action: String = "qr",
        @Body request: QRAuthRequest
    ): Response<AuthResponse>

    @GET(".")
    suspend fun validateSession(
        @Query("path") path: String = "auth",
        @Query("action") action: String = "validate",
        @Query("userId") userId: String,
        @Query("token") token: String,
        @Query("tokenVersion") tokenVersion: Int
    ): Response<ValidationResponse>
    
    // Check authorization status (triggers OAuth flow if needed)
    @GET(".")
    suspend fun checkAuthorization(
        @Query("path") path: String = "authorize"
    ): Response<Any>

    // User Management
    @POST(".")
    suspend fun createUser(
        @Query("path") path: String = "users",
        @Query("action") action: String = "create",
        @Body request: CreateUserRequest
    ): Response<CreateUserResponse>

    @POST(".")
    suspend fun updateUser(
        @Query("path") path: String = "users",
        @Query("action") action: String = "update",
        @Body request: UpdateUserRequest
    ): Response<UpdateUserResponse>

    @GET(".")
    suspend fun listUsersLegacy(
        @Query("path") path: String = "users",
        @Query("action") action: String = "list",
        @Query("familyId") familyId: String
    ): Response<UsersData>

    // Data Operations
    @GET(".")
    suspend fun getData(
        @Query("path") path: String = "data",
        @Query("action") action: String = "get",
        @Query("type") type: String,
        @Query("familyId") familyId: String? = null
    ): Response<ApiResponse<Any>>

    @POST(".")
    suspend fun saveData(
        @Query("path") path: String = "data",
        @Query("action") action: String = "save",
        @Query("type") type: String,
        @Body data: Any
    ): Response<ApiResponse<FileMetadata>>

    // Sync & Polling
    @GET(".")
    suspend fun getSyncStatus(
        @Query("path") path: String = "sync",
        @Query("action") action: String = "status"
    ): Response<SyncStatusResponse>

    @GET(".")
    suspend fun getChangesSince(
        @Query("path") path: String = "sync",
        @Query("action") action: String = "changes",
        @Query("since") since: String,
        @Query("types") types: String? = null
    ): Response<ChangesSinceResponse>

    // Batch Operations
    @GET(".")
    suspend fun getBatchData(
        @Query("path") path: String = "batch",
        @Query("action") action: String = "read",
        @Query("types") types: String, // Comma-separated: "users,chores,rewards"
        @Query("familyId") familyId: String? = null
    ): Response<BatchDataResponse>

    // Rewards (Drive-backed via Apps Script RewardManager.gs)
    @POST(".")
    suspend fun createReward(
        @Query("path") path: String = "rewards",
        @Query("action") action: String = "create",
        @Body request: CreateRewardRequest
    ): Response<RewardResponse>

    @POST(".")
    suspend fun updateReward(
        @Query("path") path: String = "rewards",
        @Query("action") action: String = "update",
        @Body request: UpdateRewardRequest
    ): Response<RewardResponse>

    @POST(".")
    suspend fun deleteReward(
        @Query("path") path: String = "rewards",
        @Query("action") action: String = "delete",
        @Body request: DeleteRewardRequest
    ): Response<ApiResponse<Unit>>

    @POST(".")
    suspend fun redeemReward(
        @Query("path") path: String = "rewards",
        @Query("action") action: String = "redeem",
        @Body request: RedeemRewardRequest
    ): Response<RedeemRewardResponse>

    @GET(".")
    suspend fun listRewards(
        @Query("path") path: String = "rewards",
        @Query("action") action: String = "list",
        @Query("familyId") familyId: String
    ): Response<RewardsListResponse>
    
    @GET(".")
    suspend fun getRewardRedemptions(
        @Query("path") path: String = "rewards",
        @Query("action") action: String = "redemptions",
        @Query("userId") userId: String? = null,
        @Query("familyId") familyId: String? = null
    ): Response<RewardRedemptionsResponse>
    
    @POST(".")
    suspend fun approveRewardRedemption(
        @Query("path") path: String = "rewards",
        @Query("action") action: String = "approve",
        @Body request: ApproveRewardRedemptionRequest
    ): Response<RedeemRewardResponse>
    
    @POST(".")
    suspend fun denyRewardRedemption(
        @Query("path") path: String = "rewards",
        @Query("action") action: String = "deny",
        @Body request: DenyRewardRedemptionRequest
    ): Response<RedeemRewardResponse>

    // Users (Drive-backed via Apps Script UserManager.gs)
    @GET(".")
    suspend fun listUsers(
        @Query("path") path: String = "users",
        @Query("action") action: String = "list",
        @Query("familyId") familyId: String
    ): Response<UsersListResponse>

    @POST(".")
    suspend fun deleteUser(
        @Query("path") path: String = "users",
        @Query("action") action: String = "delete",
        @Body request: DeleteUserRequest
    ): Response<ApiResponse<Unit>>
    
    // Photo Upload
    @POST(".")
    suspend fun uploadPhoto(
        @Query("path") path: String = "photos",
        @Query("action") action: String = "upload",
        @Body request: PhotoUploadRequest
    ): Response<PhotoUploadResponse>
    
    @POST(".")
    suspend fun deletePhoto(
        @Query("path") path: String = "photos",
        @Query("action") action: String = "delete",
        @Body request: PhotoDeleteRequest
    ): Response<ApiResponse<Unit>>
    
    // Activity Logs
    @GET(".")
    suspend fun getActivityLogs(
        @Query("path") path: String = "activity",
        @Query("action") action: String = "list",
        @Query("familyId") familyId: String? = null,
        @Query("userId") userId: String? = null,
        @Query("actionType") actionType: String? = null,
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 50
    ): Response<ActivityLogResponse>
    
    // Debug Logs
    @GET(".")
    suspend fun getDebugLogs(
        @Query("path") path: String = "debug",
        @Query("action") action: String = "get",
        @Query("limit") limit: Int = 50
    ): Response<DebugLogsResponse>
    
    // Token Refresh
    @GET(".")
    suspend fun refreshAccessToken(
        @Query("path") path: String = "auth",
        @Query("action") action: String = "refreshToken",
        @Query("userId") userId: String,
        @Query("token") token: String
    ): Response<RefreshTokenResponse>
    
    // Data Management
    @POST(".")
    suspend fun deleteAllData(
        @Query("path") path: String = "data",
        @Query("action") action: String = "delete_all",
        @Body request: DeleteAllDataRequest
    ): Response<ApiResponse<Unit>>
    
    // Chores
    @POST(".")
    suspend fun createChore(
        @Query("path") path: String = "chores",
        @Query("action") action: String = "create",
        @Body request: CreateChoreRequest
    ): Response<ChoreResponse>
    
    @POST(".")
    suspend fun updateChore(
        @Query("path") path: String = "chores",
        @Query("action") action: String = "update",
        @Body request: UpdateChoreRequest
    ): Response<ChoreResponse>
    
    @POST(".")
    suspend fun deleteChore(
        @Query("path") path: String = "chores",
        @Query("action") action: String = "delete",
        @Body request: DeleteChoreRequest
    ): Response<ApiResponse<Unit>>
    
    @POST(".")
    suspend fun deleteRecurringChoreTemplate(
        @Query("path") path: String = "chores",
        @Query("action") action: String = "delete_template",
        @Body request: DeleteTemplateRequest
    ): Response<ApiResponse<Unit>>
    
    @POST(".")
    suspend fun completeChore(
        @Query("path") path: String = "chores",
        @Query("action") action: String = "complete",
        @Body request: CompleteChoreRequest
    ): Response<ChoreResponse>

    @POST(".")
    suspend fun verifyChore(
        @Query("path") path: String = "chores",
        @Query("action") action: String = "verify",
        @Body request: VerifyChoreRequest
    ): Response<ChoreResponse>
}

// Request/Response models
data class GoogleAuthRequest(
    val googleToken: String, // ID token for authentication
    val accessToken: String? = null, // OAuth access token for Drive API (direct from Android)
    val serverAuthCode: String? = null, // Server auth code for OAuth token exchange (fallback)
    val deviceType: String = "android",
    val path: String = "auth",
    val action: String = "google"
)

data class QRAuthRequest(
    val familyId: String,
    val userId: String,
    val token: String,
    val tokenVersion: Int,
    val deviceId: String,
    val deviceName: String,
    val deviceType: String = "android",
    val ownerEmail: String, // Email of primary parent (needed to identify which Drive to access)
    val folderId: String, // Drive folder ID where family data is stored (parent's Drive)
    val path: String = "auth",
    val action: String = "qr"
)

data class ValidationResponse(
    val valid: Boolean,
    val reason: String? = null,
    val userData: User? = null
)
