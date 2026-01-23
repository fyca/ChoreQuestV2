/**
 * Shared data models for ChoreQuest
 * These types match the data structures stored in Google Drive
 */

export type UserRole = 'parent' | 'child';
export type ThemeMode = 'light' | 'dark' | 'colorful';
export type CelebrationStyle = 'fireworks' | 'confetti' | 'stars' | 'sparkles';
export type ChoreStatus = 'pending' | 'in_progress' | 'completed' | 'verified' | 'overdue';
export type RecurringFrequency = 'daily' | 'weekly' | 'monthly';
export type TransactionType = 'earn' | 'spend';

// User Model
export interface User {
  id: string;
  name: string;
  email?: string;
  role: UserRole;
  isPrimaryParent: boolean;
  avatarUrl?: string;
  pointsBalance: number;
  canEarnPoints: boolean;
  authToken: string;
  tokenVersion: number;
  devices: Device[];
  createdAt: string;
  createdBy: string;
  settings: UserSettings;
  stats: UserStats;
}

export interface Device {
  deviceId: string;
  deviceName: string;
  lastActive: string;
}

export interface UserSettings {
  notifications: boolean;
  theme: ThemeMode;
  celebrationStyle: CelebrationStyle;
  soundEffects: boolean;
}

export interface UserStats {
  totalChoresCompleted: number;
  currentStreak: number;
}

// Chore Model
export interface Chore {
  id: string;
  title: string;
  description: string;
  assignedTo: string[];
  createdBy: string;
  pointValue: number;
  dueDate?: string;
  recurring?: RecurringSchedule;
  subtasks: Subtask[];
  status: ChoreStatus;
  photoProof?: string;
  requirePhotoProof?: boolean;
  completedBy?: string;
  completedAt?: string;
  verifiedBy?: string;
  verifiedAt?: string;
  createdAt: string;
  color?: string;
  icon?: string;
}

export interface RecurringSchedule {
  frequency: RecurringFrequency;
  daysOfWeek?: number[];
  dayOfMonth?: number;
  endDate?: string;
}

export interface Subtask {
  id: string;
  title: string;
  completed: boolean;
  completedBy?: string;
  completedAt?: string;
}

// Reward Model
export interface Reward {
  id: string;
  title: string;
  description: string;
  pointCost: number;
  imageUrl?: string;
  available: boolean;
  quantity?: number;
  createdBy: string;
  redeemedCount: number;
  createdAt: string;
}

// Transaction Model
export interface Transaction {
  id: string;
  userId: string;
  type: TransactionType;
  points: number;
  reason: string;
  referenceId: string;
  timestamp: string;
}

// Activity Log Model
export type ActivityActionType =
  | 'chore_created'
  | 'chore_edited'
  | 'chore_deleted'
  | 'chore_assigned'
  | 'chore_unassigned'
  | 'chore_started'
  | 'chore_completed'
  | 'chore_completed_parent'
  | 'chore_completed_child'
  | 'chore_verified'
  | 'chore_rejected'
  | 'subtask_completed'
  | 'subtask_uncompleted'
  | 'photo_uploaded'
  | 'reward_created'
  | 'reward_edited'
  | 'reward_deleted'
  | 'reward_redeemed'
  | 'reward_approved'
  | 'reward_denied'
  | 'points_earned'
  | 'points_spent'
  | 'points_adjusted'
  | 'points_bonus'
  | 'points_penalty'
  | 'user_added'
  | 'user_removed'
  | 'user_updated'
  | 'qr_generated'
  | 'qr_regenerated'
  | 'device_login'
  | 'device_logout'
  | 'device_removed'
  | 'session_expired'
  | 'settings_changed';

export interface ActivityLog {
  id: string;
  timestamp: string;
  actorId: string;
  actorName: string;
  actorRole: UserRole;
  actionType: ActivityActionType;
  targetUserId?: string;
  targetUserName?: string;
  details: ActivityDetails;
  referenceId?: string;
  referenceType?: 'chore' | 'reward' | 'user' | 'family';
  metadata: ActivityMetadata;
}

export interface ActivityDetails {
  choreTitle?: string;
  choreDueDate?: string;
  subtaskTitle?: string;
  pointsAmount?: number;
  pointsPrevious?: number;
  pointsNew?: number;
  rewardTitle?: string;
  rewardCost?: number;
  reason?: string;
  oldValue?: any;
  newValue?: any;
  photoUrl?: string;
  notes?: string;
}

export interface ActivityMetadata {
  deviceType: 'android' | 'web';
  appVersion: string;
  location?: string;
}

// Family Model
export interface Family {
  id: string;
  name: string;
  ownerId: string;
  ownerEmail: string;
  driveFileId: string;
  members: User[];
  inviteCodes: InviteCode[];
  createdAt: string;
  settings: FamilySettings;
}

export interface InviteCode {
  userId: string;
  token: string;
  version: number;
  createdAt: string;
  lastUsed?: string;
  isActive: boolean;
}

export interface FamilySettings {
  requirePhotoProof: boolean;
  autoApproveChores: boolean;
  pointMultiplier: number;
  allowQRRegeneration: boolean;
}

// Sync Metadata
export interface SyncMetadata {
  version: number;
  lastModified: string;
  lastModifiedBy: string;
  lastSyncedAt: string;
  checksum?: string;
}

// QR Code Payload
export interface QRCodePayload {
  familyId: string;
  userId: string;
  token: string;
  version: number;
  appVersion: string;
  timestamp: string;
}

// Device Session (stored locally)
export interface DeviceSession {
  familyId: string;
  userId: string;
  userName: string;
  userRole: UserRole;
  authToken: string;
  tokenVersion: number;
  driveWorkbookLink: string;
  deviceId: string;
  loginTimestamp: string;
  lastSynced: string;
}

// API Response Types
export interface ApiResponse<T = any> {
  success: boolean;
  data?: T;
  error?: string;
  message?: string;
}

export interface AuthResponse {
  success: boolean;
  familyData: Family;
  userData: User;
  sessionData?: DeviceSession;
  isNewFamily?: boolean;
}

export interface SyncStatusResponse {
  success: boolean;
  timestamp: string;
  files: {
    [fileName: string]: FileMetadata;
  };
}

export interface FileMetadata {
  fileName: string;
  lastModified: string;
  size: number;
  id: string;
}

export interface ChangesSinceResponse {
  success: boolean;
  hasChanges: boolean;
  timestamp: string;
  changes: {
    [entityType: string]: {
      hasChanges: boolean;
      data?: any;
      metadata: FileMetadata;
    };
  };
}

// Collection Types (what's stored in Drive files)
export interface UsersData {
  users: User[];
  metadata?: SyncMetadata;
}

export interface ChoresData {
  chores: Chore[];
  metadata?: SyncMetadata;
}

export interface RewardsData {
  rewards: Reward[];
  metadata?: SyncMetadata;
}

export interface TransactionsData {
  transactions: Transaction[];
  metadata?: SyncMetadata;
}

export interface ActivityLogData {
  logs: ActivityLog[];
  metadata?: SyncMetadata;
}
