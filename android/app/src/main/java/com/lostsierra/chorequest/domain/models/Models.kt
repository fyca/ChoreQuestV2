package com.lostsierra.chorequest.domain.models

/**
 * Domain models for ChoreQuest
 * These match the data structures stored in Google Drive
 */

// Enums
enum class UserRole {
    PARENT, CHILD, SYSTEM
}

enum class ThemeMode {
    LIGHT, DARK, COLORFUL
}

enum class CelebrationStyle {
    FIREWORKS, CONFETTI, STARS, SPARKLES
}

enum class ChoreStatus {
    PENDING, IN_PROGRESS, COMPLETED, VERIFIED, OVERDUE
}

enum class RecurringFrequency {
    DAILY, WEEKLY, MONTHLY
}

enum class TransactionType {
    EARN, SPEND
}

enum class RewardRedemptionStatus {
    PENDING, APPROVED, DENIED, COMPLETED
}

enum class DeviceType {
    ANDROID, WEB, UNKNOWN
}

// User Model
data class User(
    val id: String,
    val name: String,
    val email: String? = null,
    val role: UserRole,
    val isPrimaryParent: Boolean,
    val avatarUrl: String? = null,
    val pointsBalance: Int,
    val canEarnPoints: Boolean,
    val authToken: String,
    val tokenVersion: Int,
    val devices: List<Device>,
    val createdAt: String,
    val createdBy: String,
    val settings: UserSettings,
    val stats: UserStats,
    val birthdate: String? = null // ISO 8601 date string (YYYY-MM-DD), only for children
)

data class Device(
    val deviceId: String,
    val deviceName: String,
    val lastActive: String
)

data class UserSettings(
    val notifications: Boolean,
    val theme: ThemeMode,
    val celebrationStyle: CelebrationStyle,
    val soundEffects: Boolean
)

data class UserStats(
    val totalChoresCompleted: Int,
    val currentStreak: Int
)

// Chore Model
data class Chore(
    val id: String,
    val title: String,
    val description: String,
    val assignedTo: List<String>,
    val createdBy: String,
    val pointValue: Int,
    val dueDate: String? = null,
    val recurring: RecurringSchedule? = null,
    val subtasks: List<Subtask>,
    val status: ChoreStatus,
    val photoProof: String? = null,
    val requirePhotoProof: Boolean = false, // Whether photo proof is required for this chore
    val completedBy: String? = null,
    val completedAt: String? = null,
    val verifiedBy: String? = null,
    val verifiedAt: String? = null,
    val createdAt: String,
    val color: String? = null,
    val icon: String? = null,
    val templateId: String? = null, // For recurring chores - links to template
    val cycleId: String? = null // For recurring chores - identifies the cycle (e.g., "2024-01-15", "2024-W03", "2024-01")
)

data class RecurringSchedule(
    val frequency: RecurringFrequency,
    val daysOfWeek: List<Int>? = null,
    val dayOfMonth: Int? = null,
    val endDate: String? = null
)

data class Subtask(
    val id: String,
    val title: String,
    val completed: Boolean,
    val completedBy: String? = null,
    val completedAt: String? = null
)

// Recurring Chore Template Model
data class ChoreTemplate(
    val id: String,
    val title: String,
    val description: String,
    val assignedTo: List<String>,
    val createdBy: String,
    val pointValue: Int,
    val dueDate: String? = null,
    val recurring: RecurringSchedule,
    val subtasks: List<Subtask>,
    val createdAt: String,
    val color: String? = null,
    val icon: String? = null,
    val requirePhotoProof: Boolean = false,
    val lastCycleId: String? = null, // Last cycle ID for which an instance was created
    val lastDueDate: String? = null // Last due date of created instance
)

// Reward Model
data class Reward(
    val id: String,
    val title: String,
    val description: String,
    val pointCost: Int,
    val imageUrl: String? = null,
    val available: Boolean,
    val quantity: Int? = null,
    val createdBy: String,
    val redeemedCount: Int,
    val createdAt: String
)

// Reward Redemption Model
data class RewardRedemption(
    val id: String,
    val userId: String,
    val userName: String,
    val rewardId: String,
    val rewardTitle: String,
    val status: RewardRedemptionStatus,
    val requestedAt: String,
    val approvedBy: String? = null,
    val approvedAt: String? = null,
    val deniedBy: String? = null,
    val deniedAt: String? = null,
    val denialReason: String? = null,
    val completedAt: String? = null,
    val pointCost: Int
)

// Transaction Model
data class Transaction(
    val id: String,
    val userId: String,
    val type: TransactionType,
    val points: Int,
    val reason: String,
    val referenceId: String,
    val timestamp: String
)

// Activity Log Model
enum class ActivityActionType {
    CHORE_CREATED,
    CHORE_EDITED,
    CHORE_DELETED,
    CHORE_ASSIGNED,
    CHORE_UNASSIGNED,
    CHORE_STARTED,
    CHORE_COMPLETED,
    CHORE_COMPLETED_PARENT,
    CHORE_COMPLETED_CHILD,
    CHORE_VERIFIED,
    CHORE_REJECTED,
    SUBTASK_COMPLETED,
    SUBTASK_UNCOMPLETED,
    PHOTO_UPLOADED,
    REWARD_CREATED,
    REWARD_EDITED,
    REWARD_DELETED,
    REWARD_REDEEMED,
    REWARD_APPROVED,
    REWARD_DENIED,
    POINTS_EARNED,
    POINTS_SPENT,
    POINTS_ADJUSTED,
    POINTS_BONUS,
    POINTS_PENALTY,
    USER_ADDED,
    USER_REMOVED,
    USER_UPDATED,
    QR_GENERATED,
    QR_REGENERATED,
    DEVICE_LOGIN,
    DEVICE_LOGOUT,
    DEVICE_REMOVED,
    SESSION_EXPIRED,
    SETTINGS_CHANGED,
    RECURRING_CHORE_TEMPLATE_DELETED
}

data class ActivityLog(
    val id: String,
    val timestamp: String,
    val actorId: String,
    val actorName: String,
    val actorRole: UserRole,
    val actionType: ActivityActionType,
    val targetUserId: String? = null,
    val targetUserName: String? = null,
    val details: ActivityDetails,
    val referenceId: String? = null,
    val referenceType: String? = null,
    val metadata: ActivityMetadata? = null // Nullable to handle old logs that may not have metadata
)

data class ActivityDetails(
    val choreTitle: String? = null,
    val choreDueDate: String? = null,
    val subtaskTitle: String? = null,
    val pointsAmount: Int? = null,
    val pointsEarned: Int? = null,   // from backend chore_completed
    val pointsAwarded: Int? = null,  // from backend chore_verified
    val pointsPrevious: Int? = null,
    val pointsNew: Int? = null,
    val rewardTitle: String? = null,
    val rewardCost: Int? = null,
    val pointsSpent: Int? = null,   // from backend reward_redeemed
    val remainingBalance: Int? = null,
    val reason: String? = null,
    val oldValue: Any? = null,
    val newValue: Any? = null,
    val photoUrl: String? = null,
    val notes: String? = null,
    val hadPhotoProof: Boolean? = null,
    val updatedFields: List<String>? = null,
    val templateTitle: String? = null  // for recurring_chore_template_deleted
)

data class ActivityMetadata(
    val deviceType: DeviceType,
    val appVersion: String,
    val location: String? = null
)

// Family Model
data class Family(
    val id: String,
    val name: String,
    val ownerId: String,
    val ownerEmail: String,
    val driveFileId: String,
    val members: List<User>,
    val inviteCodes: List<InviteCode>,
    val createdAt: String,
    val settings: FamilySettings,
    val metadata: SyncMetadata? = null // Optional metadata for tracking changes (matches Apps Script structure)
)

data class InviteCode(
    val userId: String,
    val token: String,
    val version: Int,
    val createdAt: String,
    val lastUsed: String? = null,
    val isActive: Boolean
)

data class FamilySettings(
    val requirePhotoProof: Boolean,
    val autoApproveChores: Boolean,
    val pointMultiplier: Double,
    val allowQRRegeneration: Boolean
)

// Sync Metadata
data class SyncMetadata(
    val version: Int,
    val lastModified: String,
    val lastModifiedBy: String,
    val lastSyncedAt: String,
    val checksum: String? = null
)

// QR Code Payload
data class QRCodePayload(
    val familyId: String,
    val userId: String,
    val token: String,
    val version: Int,
    val appVersion: String,
    val timestamp: String,
    val ownerEmail: String, // Email of the primary parent (needed to identify which Drive to access)
    val folderId: String // Drive folder ID where family data is stored (parent's Drive)
)

// Device Session (stored locally)
data class DeviceSession(
    val familyId: String,
    val userId: String,
    val userName: String,
    val userRole: UserRole,
    val authToken: String,
    val tokenVersion: Int,
    val driveWorkbookLink: String,
    val ownerEmail: String, // Email of primary parent (needed for token refresh)
    val deviceId: String,
    val loginTimestamp: String,
    val lastSynced: Long? = null
)

// API Response Types
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null,
    val message: String? = null
)

data class AuthResponse(
    val success: Boolean,
    val familyData: Family,
    val userData: User,
    val sessionData: DeviceSession? = null,
    val isNewFamily: Boolean? = null
)

data class SyncStatusResponse(
    val success: Boolean,
    val timestamp: String,
    val files: Map<String, FileMetadata>
)

data class FileMetadata(
    val fileName: String,
    val lastModified: String,
    val size: Long,
    val id: String
)

data class ChangesSinceResponse(
    val success: Boolean,
    val hasChanges: Boolean,
    val timestamp: String,
    val changes: Map<String, EntityChange>
)

data class EntityChange(
    val hasChanges: Boolean,
    val data: Any? = null,
    val metadata: FileMetadata
)

// Collection Types (what's stored in Drive files)
data class UsersData(
    val users: List<User>,
    val metadata: SyncMetadata? = null
)

data class ChoresData(
    val chores: List<Chore>,
    val metadata: SyncMetadata? = null
)

data class RewardsData(
    val rewards: List<Reward>,
    val metadata: SyncMetadata? = null
)

data class RewardRedemptionsData(
    val redemptions: List<RewardRedemption>,
    val metadata: SyncMetadata? = null
)

data class TransactionsData(
    val transactions: List<Transaction>,
    val metadata: SyncMetadata? = null
)

data class ActivityLogData(
    val logs: List<ActivityLog>,
    val metadata: SyncMetadata? = null
)

data class TemplatesData(
    val templates: List<ChoreTemplate>? = null,
    val metadata: SyncMetadata? = null
)
