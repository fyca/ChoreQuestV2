/**
 * Shared constants for ChoreQuest
 */

export const APPS_SCRIPT_WEB_APP_URL = 'https://script.google.com/macros/s/AKfycbyL0_R1jwrZ9pjAlMjW8zYP7bgyLAADsA_xIRY_HA7GgdF20cPJtlKg14JHaULN3Lxk/exec'

// API Endpoints
export const API_PATHS = {
  AUTH: '/auth',
  USERS: '/users',
  DATA: '/data',
  SYNC: '/sync',
  BATCH: '/batch',
} as const;

// Entity Types
export const ENTITY_TYPES = {
  FAMILY: 'family',
  USERS: 'users',
  CHORES: 'chores',
  REWARDS: 'rewards',
  TRANSACTIONS: 'transactions',
  ACTIVITY_LOG: 'activity_log',
} as const;

// Polling Intervals (milliseconds)
export const POLLING_INTERVALS = {
  FOREGROUND_ACTIVE: 30000,      // 30 seconds when tab is active
  FOREGROUND_INACTIVE: 300000,   // 5 minutes when tab is hidden
  SMART_POLLING: 10000,          // 10 seconds for 2 minutes after user action
  SMART_POLLING_DURATION: 120000, // 2 minutes
} as const;

// Storage Keys
export const STORAGE_KEYS = {
  SESSION: 'chorequest_session',
  PREFERENCES: 'chorequest_preferences',
  CACHE_PREFIX: 'chorequest_cache_',
} as const;

// QR Code
export const QR_CODE = {
  URI_SCHEME: 'chorequest://auth',
  VERSION: '1.0.0',
} as const;

// Validation
export const VALIDATION = {
  MIN_PASSWORD_LENGTH: 6,
  MAX_NAME_LENGTH: 50,
  MAX_DESCRIPTION_LENGTH: 500,
  MAX_CHORE_TITLE_LENGTH: 100,
  MIN_POINTS: 1,
  MAX_POINTS: 1000,
  MAX_SUBTASKS: 20,
} as const;

// Points
export const POINTS = {
  DEFAULT_CHORE_VALUE: 10,
  MIN_VALUE: 1,
  MAX_VALUE: 1000,
  DEFAULT_MULTIPLIER: 1.0,
} as const;

// Activity Log
export const ACTIVITY_LOG = {
  PAGE_SIZE: 50,
  DEFAULT_RETENTION_DAYS: 365,
  MAX_LOGS: 1000,
} as const;

// Colors
export const CHORE_COLORS = [
  { name: 'Red', value: '#E74C3C' },
  { name: 'Orange', value: '#E67E22' },
  { name: 'Yellow', value: '#F39C12' },
  { name: 'Green', value: '#27AE60' },
  { name: 'Blue', value: '#3498DB' },
  { name: 'Purple', value: '#8E44AD' },
  { name: 'Pink', value: '#E91E63' },
] as const;

// Status Colors
export const STATUS_COLORS = {
  PENDING: '#95A5A6',
  IN_PROGRESS: '#3498DB',
  COMPLETED: '#27AE60',
  VERIFIED: '#16A085',
  OVERDUE: '#E74C3C',
} as const;

// Notification Types
export const NOTIFICATION_TYPES = {
  CHORE_DUE_SOON: 'chore_due_soon',
  CHORE_OVERDUE: 'chore_overdue',
  CHORE_ASSIGNED: 'chore_assigned',
  CHORE_COMPLETED: 'chore_completed',
  POINTS_EARNED: 'points_earned',
  REWARD_REDEEMED: 'reward_redeemed',
} as const;

// App Version
export const APP_VERSION = '1.0.0';

// Error Messages
export const ERROR_MESSAGES = {
  NETWORK_ERROR: 'Network error. Please check your connection.',
  AUTH_FAILED: 'Authentication failed. Please try again.',
  SESSION_EXPIRED: 'Your session has expired. Please scan your QR code again.',
  QR_INVALID: 'Invalid QR code. Please ask your parent for a new one.',
  PERMISSION_DENIED: 'You do not have permission to perform this action.',
  UNKNOWN_ERROR: 'An unexpected error occurred. Please try again.',
} as const;

// Success Messages
export const SUCCESS_MESSAGES = {
  CHORE_COMPLETED: 'Great job! Chore completed! 🎉',
  REWARD_REDEEMED: 'Reward redeemed successfully!',
  SAVED: 'Changes saved successfully.',
} as const;
