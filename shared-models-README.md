# ChoreQuest - Shared Data Models

This document describes the shared data models used across all ChoreQuest platforms (Android, Web, and Google Apps Script).

## Overview

All platforms use the same data structures to ensure consistency when syncing data through Google Drive. The models are defined in:

- **TypeScript**: `web/src/types/models.ts` (Web app)
- **Kotlin**: `android/app/src/main/java/com/chorequest/domain/models/Models.kt` (Android app)
- **Google Apps Script**: Defined implicitly in the `.gs` files

## Core Models

### User

Represents a family member (parent or child).

**Key Fields:**
- `id`: Unique identifier
- `name`: Display name
- `role`: `parent` or `child`
- `isPrimaryParent`: True for the Google-authenticated parent
- `pointsBalance`: Points earned (for children)
- `canEarnPoints`: False for parents
- `authToken`: For QR code authentication
- `tokenVersion`: Incremented when QR code is regenerated
- `devices`: List of logged-in devices

### Chore

Represents a task to be completed.

**Key Fields:**
- `id`: Unique identifier
- `title`: Chore name
- `assignedTo`: Array of user IDs (can include parents and children)
- `createdBy`: Parent user ID
- `pointValue`: Points awarded (only to children)
- `subtasks`: List of subtasks
- `status`: `pending`, `in_progress`, `completed`, `verified`, or `overdue`
- `recurring`: Optional recurring schedule
- `completedBy`: User ID who completed it (parent or child)

### Reward

Represents a reward that can be redeemed with points.

**Key Fields:**
- `id`: Unique identifier
- `title`: Reward name
- `pointCost`: Points required to redeem
- `available`: Whether it's currently available
- `quantity`: Optional inventory limit

### Transaction

Records point earnings and spending.

**Key Fields:**
- `id`: Unique identifier
- `userId`: Child user ID
- `type`: `earn` or `spend`
- `points`: Amount
- `reason`: Chore name or reward name
- `referenceId`: ID of related chore or reward

### ActivityLog

Comprehensive audit trail of all actions.

**Key Fields:**
- `id`: Unique identifier
- `timestamp`: When the action occurred
- `actorId`: Who performed the action
- `actionType`: Type of action (30+ types defined)
- `targetUserId`: Who was affected (if applicable)
- `details`: Action-specific details

**Action Types Include:**
- Chore actions: created, edited, deleted, assigned, completed, verified
- Point actions: earned, spent, adjusted, bonus, penalty
- Reward actions: created, redeemed, approved, denied
- User actions: added, removed, QR generated/regenerated
- Device actions: login, logout, removed

### Family

Represents the family unit.

**Key Fields:**
- `id`: Unique identifier
- `ownerId`: Primary parent's Google ID
- `members`: Array of all family members
- `inviteCodes`: Active QR codes with version tracking
- `settings`: Family-wide settings

## Session & Authentication

### DeviceSession

Stored locally on each device (encrypted).

**Fields:**
- `familyId`: Links to family data
- `userId`: Current user
- `authToken`: Authentication token
- `tokenVersion`: For invalidation detection
- `driveWorkbookLink`: Path to family's Drive folder
- `deviceId`: Unique device identifier
- `loginTimestamp`: When user first logged in
- `lastSynced`: Last successful sync

### QRCodePayload

Data encoded in QR codes.

**Fields:**
- `familyId`: Links to family data
- `userId`: User this QR code is for
- `token`: Encrypted authentication token
- `version`: Token version
- `timestamp`: When QR was generated

## Data Storage Format

All data is stored in Google Drive as JSON files:

```
ChoreQuest_Data/
├── family.json          # Family info and settings
├── users.json           # { users: User[] }
├── chores.json          # { chores: Chore[] }
├── rewards.json         # { rewards: Reward[] }
├── transactions.json    # { transactions: Transaction[] }
└── activity_log.json    # { logs: ActivityLog[] }
```

Each file includes metadata:
```json
{
  "metadata": {
    "version": 1,
    "lastModified": "2026-01-12T10:30:00Z",
    "lastModifiedBy": "user-id",
    "lastSyncedAt": "2026-01-12T10:30:00Z",
    "checksum": "optional-md5-hash"
  }
}
```

## Enumerations

### UserRole
- `parent`: Has admin privileges
- `child`: Can earn points

### ChoreStatus
- `pending`: Not started
- `in_progress`: Started but not completed
- `completed`: Done, awaiting verification
- `verified`: Approved by parent, points awarded
- `overdue`: Past due date

### ThemeMode
- `light`: Professional, subtle
- `dark`: Nighttime use
- `colorful`: Kid-friendly (default for children)

### CelebrationStyle
- `fireworks`: Particle explosions
- `confetti`: Falling pieces
- `stars`: Swirling stars
- `sparkles`: Magical trail

### RecurringFrequency
- `daily`: Every day
- `weekly`: Specific days of week
- `monthly`: Once per month

## Type Safety

All platforms enforce type safety:

- **TypeScript**: Compile-time type checking
- **Kotlin**: Strong static typing with data classes
- **Apps Script**: Runtime validation in CRUD operations

## Sync Metadata

Every data file includes sync metadata for conflict resolution:

- **version**: Schema version number
- **lastModified**: ISO 8601 timestamp
- **lastModifiedBy**: User ID who made the change
- **checksum**: Optional integrity check (MD5)

## Constants

Shared constants are defined in:
- **Web**: `web/src/types/constants.ts`
- **Android**: `android/app/src/main/java/com/chorequest/utils/Constants.kt`

Key constants include:
- Polling intervals (30s foreground, 15min background)
- API endpoints
- Storage keys
- Validation rules (min/max values)
- Default points values
- Color palettes
- Error/success messages

## Validation Rules

- **Names**: Max 50 characters
- **Descriptions**: Max 500 characters
- **Points**: 1-1000 range
- **Subtasks**: Max 20 per chore
- **Activity Log**: Max 1000 entries (auto-pruned)

## Best Practices

1. **Always include metadata** when saving data
2. **Use UUIDs** for all IDs (generated server-side)
3. **ISO 8601 timestamps** for all dates
4. **Nullable fields** use `?` (TypeScript) or `?` (Kotlin)
5. **Enums over strings** for type safety
6. **Validate on both client and server**
7. **Encrypt sensitive data** (auth tokens, session files)

## Migration Strategy

When updating models:

1. Increment `version` in metadata
2. Add migration logic in sync managers
3. Support backward compatibility for 1 version
4. Create version snapshots before major changes
5. Test with existing family data

## Related Documentation

- Android data models: `android/app/src/main/java/com/chorequest/domain/models/`
- Web type definitions: `web/src/types/`
- Apps Script storage: `apps-script/DriveManager.gs`
- Sync logic: `apps-script/SyncManager.gs`
