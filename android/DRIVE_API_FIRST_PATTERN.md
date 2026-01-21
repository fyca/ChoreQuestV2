# Drive API First Pattern

## Overview

All repository methods should follow this pattern:
1. **Try Direct Drive API first** (faster, no cold start)
2. **Fall back to Apps Script** if Drive API fails or token unavailable

## Pattern Structure

```kotlin
fun someOperation(): Flow<Result<T>> = flow {
    emit(Result.Loading)
    val session = sessionManager.loadSession()
    if (session == null) {
        emit(Result.Error("No active session"))
        return@flow
    }

    // Try direct Drive API first
    val accessToken = tokenManager.getValidAccessToken()
    if (accessToken != null) {
        try {
            Log.d(TAG, "Using direct Drive API for operation")
            val folderId = session.driveWorkbookLink
            
            // Perform operation using Drive API
            // ... Drive API operations ...
            
            // Success - update local cache and return
            emit(Result.Success(result))
            return@flow
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Re-throw cancellation exceptions
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error using direct Drive API, falling back to Apps Script", e)
            // Continue to fallback below
        }
    } else {
        Log.d(TAG, "No access token available, using Apps Script")
    }

    // Fallback to Apps Script
    val response = api.someOperation(...)
    
    // Handle response...
}
```

## Benefits

1. **Performance**: Direct Drive API is faster (no Apps Script cold start)
2. **Reliability**: Apps Script fallback ensures operation succeeds even if Drive API fails
3. **User Experience**: Faster operations = better UX

## Current Status

### ✅ Already Following Pattern
- `ChoreRepository.createChore()` - Tries Drive API first
- `ChoreRepository.updateChore()` - Tries Drive API first
- `ChoreRepository.deleteChore()` - Tries Drive API first
- `ChoreRepository.completeChore()` - Tries Drive API first
- `ChoreRepository.verifyChore()` - Tries Drive API first
- `ChoreRepository.getRecurringChoreTemplates()` - Tries Drive API first
- `ChoreRepository.deleteRecurringChoreTemplate()` - Tries Drive API first
- `UserRepository.createUser()` - Tries Drive API first
- `UserRepository.updateUser()` - Tries Drive API first
- `SyncRepository.syncActivityLogs()` - Tries Drive API first

### ⚠️ Needs Update
- `RewardRepository.createReward()` - Currently calls Apps Script directly
- `RewardRepository.updateReward()` - Currently calls Apps Script directly
- `RewardRepository.deleteReward()` - Currently calls Apps Script directly
- `RewardRepository.redeemReward()` - Currently calls Apps Script directly
- `RewardRepository.approveRewardRedemption()` - Currently calls Apps Script directly
- `RewardRepository.denyRewardRedemption()` - Currently calls Apps Script directly

## Error Handling

### 401 Unauthorized Errors
When Drive API returns 401:
1. Try refreshing the token
2. Retry the operation
3. If refresh fails, fall back to Apps Script

### Other Errors
- Log the error
- Fall back to Apps Script
- Apps Script will handle authorization if needed

## Token Management

- Use `tokenManager.getValidAccessToken()` to get current token
- Use `tokenManager.forceRefreshToken()` to refresh expired tokens
- If token unavailable, fall back to Apps Script (which handles auth internally)
