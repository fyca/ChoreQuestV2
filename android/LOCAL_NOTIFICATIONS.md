# Local Notifications Implementation

This document describes how local notifications work in ChoreQuest.

## Overview

ChoreQuest uses Android's local notification system to notify users when chore status changes are detected during background sync. No external services (like Firebase) are required - notifications are triggered locally when the app detects changes in chore statuses.

## How It Works

### Background Sync

The app performs background sync every 3 minutes using WorkManager:
- Syncs chores, rewards, users, and activity logs
- Compares previous chore statuses with current statuses
- Detects changes and triggers appropriate notifications

### Notification Triggers

Notifications are displayed when:

1. **For Parents**: 
   - A child completes a chore (status changes to "completed")
   - Notification: "[Child Name] completed: [Chore Title]"

2. **For Children**:
   - Their completed chore is verified by a parent (status changes from "completed" to "verified")
   - Notification: "Chore Verified! 🎉 You earned [points] points for: [Chore Title]"

### Notification Channels

The app creates three notification channels:
- **Default**: General app notifications
- **Chore Updates**: Notifications about chore completions and verifications
- **Points Updates**: Notifications about points earned

### User Settings

Users can enable/disable notifications in their profile settings. The app respects the `notifications` setting in `UserSettings`.

## Implementation Details

### Files Involved

1. **NotificationManager.kt**: Manages notification creation and display
   - Creates notification channels
   - Shows notifications when changes are detected
   - Respects user notification preferences

2. **SyncRepository.kt**: Detects changes during sync
   - Compares previous and current chore statuses
   - Calls NotificationManager when changes are detected

3. **AndroidManifest.xml**: 
   - Includes `POST_NOTIFICATIONS` permission
   - Notification channels are created automatically

### Notification Flow

```
Background Sync (every 15 min)
    ↓
SyncRepository.syncChores()
    ↓
Compare previousChores vs currentChores
    ↓
NotificationManager.checkAndDisplayNotifications()
    ↓
Check user notification settings
    ↓
Show notification if enabled
```

## Testing

1. **Enable notifications** in user settings
2. **Complete a chore** as a child user
3. **Sync parent app** (or wait for background sync - up to 3 minutes)
4. **Verify notification** appears for parents
5. **Verify chore** as a parent
6. **Sync child app** (or wait for background sync)
7. **Verify notification** appears for children

## Troubleshooting

### Notifications not appearing

1. Check that notifications are enabled in user settings
2. Check notification permissions in Android settings (Android 13+)
3. Ensure app is syncing (check sync status in dashboard)
4. Verify chore status actually changed (check activity log)

### Notifications appearing too late

- Background sync runs every 3 minutes by default
- Users can manually sync by tapping the sync button
- Notifications appear immediately after manual sync

## Notes

- Notifications work when app is in background or foreground
- Notifications are triggered locally - no server-side push required
- The app checks for notifications every sync (3 minutes by default)
- Users can disable notifications in their profile settings
- Notifications respect Android's Do Not Disturb settings
