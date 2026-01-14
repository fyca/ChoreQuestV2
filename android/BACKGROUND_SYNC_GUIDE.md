# 🔄 Background Sync & Offline Handling Guide

## Overview

ChoreQuest now includes robust background synchronization and offline detection capabilities. This guide explains how these features work and how to use them.

## Architecture

### 1. Background Sync System

```
┌─────────────────┐
│ ChoreQuest App  │
└────────┬────────┘
         │
         ▼
┌─────────────────┐      Every 15 min      ┌──────────────┐
│   SyncManager   │─────────────────────────▶│  SyncWorker  │
└────────┬────────┘                         └──────┬───────┘
         │                                          │
         │                                          ▼
         │                                   ┌──────────────┐
         │                                   │ SyncRepository│
         │                                   └──────┬───────┘
         │                                          │
         ▼                                          ▼
┌─────────────────┐                         ┌──────────────┐
│ WorkManager     │                         │ ChoreQuest API│
└─────────────────┘                         └──────┬───────┘
                                                   │
                                                   ▼
                                            ┌──────────────┐
                                            │ Google Drive │
                                            └──────────────┘
```

## Components

### SyncRepository

**Location:** `data/repository/SyncRepository.kt`

**Responsibilities:**
- Fetches latest data from Google Apps Script API
- Updates local Room database
- Tracks last sync timestamp
- Handles sync errors gracefully

**Key Methods:**
```kotlin
suspend fun syncAll(): Boolean
suspend fun getLastSyncTime(): Long?
suspend fun updateLastSyncTime(timestamp: Long)
```

### SyncWorker

**Location:** `workers/SyncWorker.kt`

**Responsibilities:**
- Background work execution
- Hilt integration for dependency injection
- Success/failure reporting to WorkManager

**Features:**
- Runs in background thread
- Handles exceptions
- Returns appropriate Result (success/retry/failure)

### SyncManager

**Location:** `workers/SyncManager.kt`

**Responsibilities:**
- Schedules periodic sync
- Triggers manual sync
- Monitors sync work status
- Provides LiveData for UI observation

**Configuration:**
```kotlin
Sync Interval: 15 minutes
Flex Interval: 5 minutes (can run 10-15 min after last sync)
Network Constraint: CONNECTED (requires internet)
Backoff Policy: EXPONENTIAL (1min, 2min, 4min, etc.)
```

### SyncStatus UI

**Location:** `presentation/components/SyncStatus.kt`

**Components:**
- `SyncStatusBar` - Full status bar with timestamp and manual sync button
- `CompactSyncIndicator` - Small icon for toolbar
- `SyncIcon` - Animated rotating icon

**States:**
- **Syncing** - Rotating cloud sync icon (blue)
- **Synced** - Cloud done icon with timestamp (green)
- **Failed** - Error icon (red)

## Network Connectivity Monitoring

### NetworkConnectivityObserver

**Location:** `utils/NetworkConnectivityObserver.kt`

**Features:**
- Real-time network status monitoring
- Android ConnectivityManager callbacks
- Coroutine Flow for reactive state
- Minimal battery usage

**Network States:**
```kotlin
sealed class NetworkStatus {
    object Available    // Connected with internet
    object Unavailable  // No connection
    object Lost        // Connection dropped
}
```

### OfflineIndicator UI

**Location:** `presentation/components/OfflineIndicator.kt`

**Components:**
- `OfflineIndicator` - Full-width banner for main screens
- `CompactOfflineIndicator` - Small badge for compact areas

**Behavior:**
- Slides down when offline
- Slides up when back online
- Smooth animations
- High visibility (error colors)

## Integration with Dashboards

### Parent Dashboard

**ViewModel Changes:**
```kotlin
@Inject lateinit var syncManager: SyncManager
@Inject lateinit var syncRepository: SyncRepository
@Inject lateinit var networkObserver: NetworkConnectivityObserver

val lastSyncTime: StateFlow<Long?>
val networkStatus: StateFlow<NetworkStatus>

fun triggerSync() { /* Manual sync */ }
```

**UI Changes:**
```kotlin
// Sync status bar after welcome header
SyncStatusBar(
    syncManager = syncManager,
    lastSyncTime = lastSyncTime,
    onManualSyncClick = { viewModel.triggerSync() }
)

// Offline banner in top bar
OfflineIndicator(networkStatus = networkStatus)
```

### Child Dashboard

Same integration as Parent Dashboard.

## Usage Examples

### Manual Sync Trigger

Users can trigger immediate sync by:
1. Tapping the refresh button in the sync status bar
2. System will enqueue a one-time WorkManager job
3. Sync happens immediately (if network available)
4. UI updates with result

### Observing Sync Status

```kotlin
// In ViewModel
val workInfo = syncManager.getSyncWorkInfo().asLiveData()

// In Composable
val workInfo by syncManager.getSyncWorkInfo().observeAsState()
val isSyncing = workInfo?.firstOrNull()?.state == WorkInfo.State.RUNNING
```

### Checking Network Status

```kotlin
// In ViewModel
val networkStatus: StateFlow<NetworkStatus> = 
    networkObserver.observeConnectivity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NetworkStatus.Available)

// Quick check
if (networkObserver.isOnline()) {
    // Perform network operation
}
```

## Configuration

### Adjusting Sync Interval

Edit `SyncManager.kt`:

```kotlin
companion object {
    private const val SYNC_INTERVAL_MINUTES = 15L  // Change this
    private const val SYNC_FLEX_INTERVAL_MINUTES = 5L  // And this
}
```

**Recommendations:**
- **Frequent sync (5-10 min):** Better for active families, higher battery usage
- **Normal sync (15-20 min):** Good balance
- **Battery saver (30-60 min):** Lower battery usage, less current data

### Changing Network Constraints

Edit `SyncManager.kt`:

```kotlin
val constraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)  // Current
    // .setRequiredNetworkType(NetworkType.UNMETERED)  // WiFi only
    // .setRequiresCharging(true)  // Only when charging
    .build()
```

## Testing

### Manual Testing

1. **Background Sync:**
   - Open app
   - Wait 15+ minutes
   - Check logs: "Starting background sync..."
   - Verify data updates

2. **Manual Sync:**
   - Tap sync button in status bar
   - Observe rotating icon
   - Verify "Synced" appears after completion

3. **Offline Detection:**
   - Turn on airplane mode
   - See "No internet connection" banner
   - Turn off airplane mode
   - Banner disappears

### Debugging

**Enable WorkManager Logging:**
```kotlin
// ChoreQuestApplication.kt
val workConfig = Configuration.Builder()
    .setMinimumLoggingLevel(android.util.Log.DEBUG)  // Change to DEBUG
    .build()
```

**Check WorkManager State:**
```bash
adb shell dumpsys jobscheduler | grep chorequest
```

## Troubleshooting

### Sync Not Happening

**Check:**
1. Is device online?
2. Is app in battery saver mode?
3. Is WorkManager initialized?
4. Check logs for errors

**Solutions:**
- Trigger manual sync
- Restart app
- Check network permission
- Verify Google Apps Script URL

### Offline Indicator Not Showing

**Check:**
1. Is network permission granted?
2. Is flow being collected?
3. Check Android version (API 24+)

**Solutions:**
- Add ACCESS_NETWORK_STATE permission
- Verify ViewModel injection
- Check composable lifecycle

### Sync Failures

**Common Causes:**
1. API rate limiting
2. Invalid session token
3. Network timeout
4. Server errors

**Solutions:**
- WorkManager will retry automatically
- Check session validity
- Verify Apps Script deployment
- Increase timeout values

## Performance

### Battery Impact

**Background Sync:**
- WorkManager uses JobScheduler (efficient)
- Network constraint prevents wasted attempts
- Exponential backoff reduces retries
- **Estimated impact:** < 1% battery per day

**Network Monitoring:**
- Uses system callbacks (not polling)
- Minimal CPU usage
- **Estimated impact:** < 0.1% battery per day

### Data Usage

**Per Sync:**
- Chores: ~1-5 KB
- Rewards: ~1-3 KB  
- Users: ~1-2 KB
- **Total per sync:** ~5-10 KB

**Daily:**
- 96 syncs per day (15 min intervals)
- **Total:** ~0.5-1 MB per day

## Future Enhancements

### Potential Improvements

1. **Delta Sync** - Only sync changed items
2. **Conflict Resolution** - Handle simultaneous edits
3. **Offline Queue** - Queue actions when offline
4. **Smart Sync** - Sync more frequently when active
5. **Compression** - Reduce data transfer size
6. **Sync Priorities** - Sync critical data first

### Push Notifications

Consider adding Firebase Cloud Messaging for:
- Instant chore updates
- Due date reminders
- Point notifications
- Verification requests

## API Reference

### SyncRepository

```kotlin
class SyncRepository @Inject constructor(
    private val api: ChoreQuestApi,
    private val choreDao: ChoreDao,
    private val rewardDao: RewardDao,
    private val userDao: UserDao,
    private val authRepository: AuthRepository
)

suspend fun syncAll(): Boolean
suspend fun getLastSyncTime(): Long?
suspend fun updateLastSyncTime(timestamp: Long)
```

### SyncManager

```kotlin
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context
)

fun scheduleSyncWork()
fun cancelSyncWork()
fun triggerImmediateSync()
fun getSyncWorkInfo(): LiveData<List<WorkInfo>>
```

### NetworkConnectivityObserver

```kotlin
class NetworkConnectivityObserver @Inject constructor(
    @ApplicationContext private val context: Context
)

fun observeConnectivity(): Flow<NetworkStatus>
fun getCurrentNetworkStatus(): NetworkStatus
fun isOnline(): Boolean
```

## Conclusion

The background sync and offline detection systems provide:

✅ **Automatic data synchronization** - Runs every 15 minutes  
✅ **Manual sync option** - User-triggered when needed  
✅ **Offline awareness** - Clear visual indicators  
✅ **Battery efficient** - Smart scheduling and constraints  
✅ **Reliable** - Retry logic and error handling  
✅ **Transparent** - Users see exactly what's happening  

These features ensure ChoreQuest data stays current across all family devices while providing excellent user experience even with spotty internet connections.
