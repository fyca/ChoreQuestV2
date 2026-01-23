package com.lostsierra.chorequest.data.remote

import com.lostsierra.chorequest.domain.models.*
import com.google.gson.annotations.SerializedName

/**
 * Common API response wrapper
 */
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null,
    val message: String? = null
)

/**
 * Authentication response
 */
data class AuthResponse(
    val success: Boolean,
    val status: Int? = null,
    val user: User? = null,
    // Some backend routes return userData/sessionData/familyData (legacy)
    @SerializedName("userData") val userData: User? = null,
    val family: Family? = null,
    @SerializedName("familyData") val familyData: Family? = null,
    val session: DeviceSession? = null,
    @SerializedName("sessionData") val sessionData: DeviceSession? = null,
    val message: String? = null,
    val error: String? = null,
    val stack: String? = null,
    val authorizationUrl: String? = null,
    val instructions: String? = null,
    val requiresOAuthSetup: Boolean? = null // Indicates OAuth credentials need to be configured
)

/**
 * Users data response
 */
data class UsersData(
    val users: List<User>
)

/**
 * File metadata returned from Apps Script
 */
data class FileMetadata(
    val id: String,
    val name: String,
    val mimeType: String,
    val modifiedTime: String,
    val etag: String?,
    val version: Long?
)

/**
 * Sync status response for polling
 */
data class SyncStatusResponse(
    val success: Boolean,
    val lastModified: String,
    val fileMetadata: Map<String, FileMetadata>,
    val hasChanges: Boolean
)

/**
 * Changes since timestamp response
 */
data class ChangesSinceResponse(
    val success: Boolean,
    val changes: Changes,
    val timestamp: String
)

/**
 * Changes data structure
 */
data class Changes(
    val chores: List<Chore>? = null,
    val rewards: List<Reward>? = null,
    val users: List<User>? = null,
    val transactions: List<Transaction>? = null,
    val activityLogs: List<ActivityLog>? = null,
    val family: Family? = null
)

/**
 * Batch data response - contains multiple entity types in a single response
 */
data class BatchDataResponse(
    val success: Boolean,
    val data: Map<String, Any>? = null, // Keyed by entity type: "users", "chores", etc.
    val errors: Map<String, String>? = null, // Any errors per entity type
    val error: String? = null
)

/**
 * Batch operation request
 */
data class BatchRequest(
    val operations: List<Operation>
)

/**
 * Single operation in a batch
 */
data class Operation(
    val type: String, // "create", "update", "delete"
    val entity: String, // "chore", "reward", "user", etc.
    val data: Any
)

/**
 * Batch operation response
 */
data class BatchResponse(
    val success: Boolean,
    val results: List<OperationResult>
)

/**
 * Result of a single operation in a batch
 */
data class OperationResult(
    val success: Boolean,
    val operationIndex: Int,
    val data: Any? = null,
    val error: String? = null
)

/**
 * Photo upload request
 */
data class PhotoUploadRequest(
    val base64Data: String,
    val fileName: String,
    val mimeType: String,
    val choreId: String? = null,
    val userId: String? = null,
    val ownerEmail: String? = null
)

/**
 * Photo upload response
 */
data class PhotoUploadResponse(
    val success: Boolean,
    val fileId: String? = null,
    val fileName: String? = null,
    val url: String? = null,
    val downloadUrl: String? = null,
    val thumbnailUrl: String? = null,
    val webViewLink: String? = null,
    val size: Long? = null,
    val mimeType: String? = null,
    val createdDate: String? = null,
    val error: String? = null
)

/**
 * Photo delete request
 */
data class PhotoDeleteRequest(
    val fileId: String
)

/**
 * Activity log response
 */
data class ActivityLogResponse(
    val success: Boolean,
    val logs: List<ActivityLog>,
    val totalCount: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 50,
    val hasMore: Boolean = false,
    val error: String? = null
)

/**
 * Debug log entry from Apps Script
 */
data class DebugLogEntry(
    val timestamp: String,
    val level: String,
    val message: String,
    val data: Map<String, Any>? = null
)

/**
 * Debug logs response
 */
data class DebugLogsResponse(
    val success: Boolean,
    val logs: List<DebugLogEntry>? = null,
    val count: Int = 0,
    val error: String? = null,
    val message: String? = null
)

/**
 * Rewards (Apps Script RewardManager.gs uses { success, reward } and { success, rewards })
 */
data class CreateRewardRequest(
    val creatorId: String,
    val title: String,
    val description: String? = null,
    val pointCost: Int,
    val imageUrl: String? = null,
    val available: Boolean? = null,
    val quantity: Int? = null
)

data class RewardUpdates(
    val title: String? = null,
    val description: String? = null,
    val pointCost: Int? = null,
    val imageUrl: String? = null,
    val available: Boolean? = null,
    val quantity: Int? = null
)

data class UpdateRewardRequest(
    val userId: String,
    val rewardId: String,
    val updates: RewardUpdates
)

data class DeleteRewardRequest(
    val userId: String,
    val rewardId: String
)

data class RedeemRewardRequest(
    val userId: String,
    val rewardId: String
)

data class RewardResponse(
    val success: Boolean,
    val reward: Reward? = null,
    val error: String? = null,
    val message: String? = null
)

data class RewardsListResponse(
    val success: Boolean,
    val rewards: List<Reward> = emptyList(),
    val error: String? = null
)

data class RedeemRewardResponse(
    val success: Boolean,
    val reward: Reward? = null,
    val redemption: RewardRedemption? = null,
    val transaction: Transaction? = null,
    val newBalance: Int? = null,
    val message: String? = null,
    val error: String? = null
)

data class RewardRedemptionsResponse(
    val success: Boolean,
    val redemptions: List<RewardRedemption> = emptyList(),
    val error: String? = null
)

data class ApproveRewardRedemptionRequest(
    val parentId: String,
    val redemptionId: String
)

data class DenyRewardRedemptionRequest(
    val parentId: String,
    val redemptionId: String
)

/**
 * Users (Apps Script UserManager.gs uses { success, users } and delete uses { success, message })
 */
data class UsersListResponse(
    val success: Boolean,
    val users: List<User> = emptyList(),
    val error: String? = null
)

/**
 * Create user request
 */
data class CreateUserRequest(
    val parentUserId: String,
    val name: String,
    val role: UserRole,
    val canEarnPoints: Boolean = true,
    val avatarUrl: String? = null,
    val birthdate: String? = null, // ISO 8601 date string (YYYY-MM-DD), only for children
    val ownerEmail: String? = null // Email of primary parent (needed to identify which Drive to access)
)

/**
 * Create user response (Apps Script UserManager.gs returns { success, user, qrData })
 */
data class CreateUserResponse(
    val success: Boolean,
    val user: User? = null,
    val qrData: QRCodePayload? = null,
    val error: String? = null,
    val message: String? = null
)

/**
 * Token refresh response
 */
data class RefreshTokenResponse(
    val success: Boolean,
    val accessToken: String? = null,
    val expiresIn: Int? = null,
    val error: String? = null,
    val message: String? = null
)

/**
 * Update user request
 */
data class UpdateUserRequest(
    val parentUserId: String,
    val targetUserId: String,
    val updates: UserUpdates
)

/**
 * User updates object
 */
data class UserUpdates(
    val name: String? = null,
    val avatarUrl: String? = null,
    val settings: UserSettings? = null
)

/**
 * Update user response (Apps Script UserManager.gs returns { success, user })
 */
data class UpdateUserResponse(
    val success: Boolean,
    val user: User? = null,
    val error: String? = null,
    val message: String? = null
)

data class DeleteUserRequest(
    val parentUserId: String,
    val targetUserId: String
)

/**
 * Delete all data request (only for primary parent)
 */
data class DeleteAllDataRequest(
    val userId: String,
    val familyId: String
)

/**
 * Create chore request
 */
data class CreateChoreRequest(
    val creatorId: String,
    val title: String,
    val description: String? = null,
    val assignedTo: List<String>,
    val pointValue: Int,
    val dueDate: String? = null,
    val recurring: RecurringSchedule? = null,
    val subtasks: List<Subtask>? = null,
    val color: String? = null,
    val icon: String? = null,
    val requirePhotoProof: Boolean = false
)

/**
 * Update chore request
 */
data class UpdateChoreRequest(
    val userId: String,
    val choreId: String,
    val updates: ChoreUpdates
)

/**
 * Chore updates object
 */
data class ChoreUpdates(
    val title: String? = null,
    val description: String? = null,
    val assignedTo: List<String>? = null,
    val pointValue: Int? = null,
    val dueDate: String? = null,
    val recurring: RecurringSchedule? = null,
    val subtasks: List<Subtask>? = null,
    val color: String? = null,
    val icon: String? = null,
    val requirePhotoProof: Boolean? = null
)

/**
 * Delete chore request
 */
data class DeleteChoreRequest(
    val userId: String,
    val choreId: String
)

/**
 * Delete recurring chore template request
 */
data class DeleteTemplateRequest(
    val userId: String,
    val templateId: String
)

/**
 * Complete chore request
 */
data class CompleteChoreRequest(
    val userId: String,
    val choreId: String,
    val photoProof: String? = null
)

/**
 * Chore operation response (Apps Script returns { success: true, chore: {...} })
 */
data class ChoreResponse(
    val success: Boolean,
    val chore: Chore? = null,
    val error: String? = null,
    val message: String? = null
)

/**
 * Verify chore request (Apps Script expects { parentId, choreId, approved })
 */
data class VerifyChoreRequest(
    val parentId: String,
    val choreId: String,
    val approved: Boolean
)
