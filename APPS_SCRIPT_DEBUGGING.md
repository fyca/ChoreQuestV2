# Apps Script Debugging Guide

## Viewing Execution Logs

Apps Script execution logs are available in the Apps Script editor:

1. **Open Apps Script Editor**: Go to [script.google.com](https://script.google.com)
2. **Select Your Project**: Open your ChoreQuest Apps Script project
3. **View Executions**: 
   - Click on "Executions" in the left sidebar (clock icon)
   - Or go to: **View** → **Executions**
4. **View Logs**:
   - Click on any execution to see the execution transcript
   - All `Logger.log()` statements will appear here
   - You can filter by function name or date

## Alternative: Use Debug Endpoint

Since you can't easily access execution logs, I've added diagnostic information directly to error responses and created a debug endpoint.

### Debug Endpoint

**URL**: `GET /exec?path=auth&action=debug&email=user@example.com`

**Example**:
```
https://script.google.com/macros/s/YOUR_SCRIPT_ID/exec?path=auth&action=debug&email=timyorba1@gmail.com
```

**Response**:
```json
{
  "success": true,
  "debug": {
    "timestamp": "2026-01-22T06:52:24.000Z",
    "requestedEmail": "timyorba1@gmail.com",
    "normalizedEmail": "timyorba1@gmail.com",
    "totalStoredProperties": 4,
    "allPropertyKeys": [
      "ACCESS_TOKEN_timyorba1@gmail.com",
      "REFRESH_TOKEN_timyorba1@gmail.com",
      ...
    ],
    "refreshTokenKeys": [
      "REFRESH_TOKEN_timyorba1@gmail.com"
    ],
    "accessTokenKeys": [
      "ACCESS_TOKEN_timyorba1@gmail.com"
    ],
    "emailDiagnostics": {
      "normalizedEmail": "timyorba1@gmail.com",
      "refreshTokenKey": "REFRESH_TOKEN_timyorba1@gmail.com",
      "accessTokenKey": "ACCESS_TOKEN_timyorba1@gmail.com",
      "refreshTokenExists": true,
      "accessTokenExists": true,
      "refreshTokenLength": 150,
      "accessTokenLength": 200,
      "canGetValidToken": true
    }
  }
}
```

### Error Response Diagnostics

All error responses now include diagnostic information:

**Example Error Response**:
```json
{
  "status": 403,
  "error": "Parent access token not available. Parent must log in again.",
  "details": "...",
  "requiresParentLogin": true,
  "diagnostics": {
    "ownerEmail": "timyorba1@gmail.com",
    "normalizedOwnerEmail": "timyorba1@gmail.com",
    "refreshTokenExists": false,
    "accessTokenExists": false,
    "refreshTokenKey": "REFRESH_TOKEN_timyorba1@gmail.com",
    "accessTokenKey": "ACCESS_TOKEN_timyorba1@gmail.com",
    "allRefreshTokenKeys": [],
    "allAccessTokenKeys": [],
    "totalStoredProperties": 0,
    "rootCause": "No refresh token stored for parent. Parent must log in via Google Sign-In."
  }
}
```

## What to Check

When debugging token refresh issues:

1. **Check if refresh token exists**: Look at `diagnostics.refreshTokenExists` or `debug.emailDiagnostics.refreshTokenExists`
2. **Check email normalization**: Compare `ownerEmail` vs `normalizedOwnerEmail` - they should match
3. **Check stored keys**: Look at `allRefreshTokenKeys` to see what tokens are stored
4. **Check root cause**: The `rootCause` field explains why the token refresh failed

## Common Issues

### Issue: Refresh Token Not Found
- **Symptom**: `refreshTokenExists: false`
- **Cause**: Parent hasn't logged in via Google Sign-In, or refresh token wasn't stored
- **Solution**: Parent must log in via Google Sign-In to store refresh token

### Issue: Email Case Mismatch
- **Symptom**: Token exists but can't be found
- **Cause**: Email stored with different case than requested
- **Solution**: Email normalization should fix this automatically

### Issue: Refresh Token Invalid
- **Symptom**: `refreshTokenExists: true` but `canGetValidToken: false`
- **Cause**: Refresh token was revoked or expired
- **Solution**: Parent must log in again to get new refresh token

## Testing the Debug Endpoint

You can test the debug endpoint using curl or your browser:

```bash
# Replace YOUR_SCRIPT_ID and email
curl "https://script.google.com/macros/s/YOUR_SCRIPT_ID/exec?path=auth&action=debug&email=timyorba1@gmail.com"
```

Or open it directly in your browser - it will show the JSON response.
