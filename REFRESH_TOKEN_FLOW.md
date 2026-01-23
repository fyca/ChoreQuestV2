# Refresh Token Flow Verification

## Route Configuration

### Apps Script Route Handler
- **File**: `apps-script/AuthManager.gs`
- **Function**: `handleAuthRequest(e)`
- **Route**: `GET /exec?path=auth&action=refreshToken`
- **Handler**: Calls `refreshTokenForUser(e)`

### Android API Endpoint
- **File**: `android/app/src/main/java/com/lostsierra/chorequest/data/remote/ChoreQuestApi.kt`
- **Method**: `refreshAccessToken()`
- **HTTP Method**: `GET`
- **Query Parameters**:
  - `path=auth` (default)
  - `action=refreshToken` (default)
  - `userId` (required)
  - `token` (required)
  - `ownerEmail` (required) ✅ Added for reliable token refresh

## Function Flow

### 1. `refreshTokenForUser(e)` - Entry Point
**Location**: `apps-script/AuthManager.gs`

**Parameters Expected**:
- `e.parameter.userId` - User ID from session
- `e.parameter.token` - Auth token from session
- `e.parameter.ownerEmail` - Primary parent's email (from QR code, stored in session)

**Flow**:
1. Validates `userId`, `token`, and `ownerEmail` are present
2. Calls `getValidAccessToken(ownerEmail)` to get/refresh parent's access token
3. Returns new access token or error

**Status**: ✅ Correct - Uses `ownerEmail` directly from request

### 2. `getValidAccessToken(userEmail)` - Token Manager
**Location**: `apps-script/AuthManager.gs`

**Purpose**: Gets a valid access token, refreshing if needed

**Flow**:
1. Checks for stored access token in `PropertiesService.getUserProperties()`
2. If found, returns stored token (assumes valid)
3. If not found, calls `refreshAccessToken(userEmail)` to refresh
4. Returns refreshed token or null

**Status**: ✅ Correct - Handles token storage and refresh

### 3. `refreshAccessToken(userEmail)` - Token Refresh
**Location**: `apps-script/AuthManager.gs`

**Purpose**: Refreshes an access token using a refresh token

**Flow**:
1. Gets refresh token from `PropertiesService.getUserProperties()` using key `REFRESH_TOKEN_{userEmail}`
2. If no refresh token found, returns null
3. Calls Google OAuth token endpoint with refresh token
4. Stores new access token (and refresh token if provided)
5. Returns new access token or null

**Status**: ✅ Correct - Standard OAuth refresh flow

## Android Side

### TokenManager.kt
**File**: `android/app/src/main/java/com/lostsierra/chorequest/data/drive/TokenManager.kt`

**Function**: `getValidAccessToken()`

**Flow**:
1. Loads session from `SessionManager`
2. Checks for stored access token with expiry
3. If expired or missing, calls `api.refreshAccessToken()` with:
   - `userId` from session
   - `token` (authToken) from session
   - `ownerEmail` from session ✅ (originally from QR code)
4. Stores new access token if refresh succeeds
5. Returns access token or null

**Status**: ✅ Correct - Passes `ownerEmail` from session

## Potential Issues to Check

1. **Refresh Token Storage**: Verify refresh tokens are being stored during Google auth
   - Check: `exchangeServerAuthCodeForAccessToken()` stores refresh token
   - Key format: `REFRESH_TOKEN_{userEmail}`

2. **Refresh Token Retrieval**: Verify refresh tokens are being retrieved correctly
   - Check: `refreshAccessToken()` looks up correct key
   - Key format: `REFRESH_TOKEN_{userEmail}`

3. **OAuth Configuration**: Verify OAuth request includes `access_type=offline`
   - Check: Token exchange payload includes `access_type: 'offline'`
   - This is required for Google to return refresh tokens

4. **Token Expiry**: Current implementation assumes stored tokens are valid
   - Consider: Adding expiry checking to avoid using expired tokens
   - Access tokens typically expire in 1 hour

## Verification Checklist

- [x] Route handler correctly routes `refreshToken` action
- [x] `refreshTokenForUser` receives `ownerEmail` parameter
- [x] `getValidAccessToken` uses `ownerEmail` to identify parent
- [x] `refreshAccessToken` retrieves refresh token using `ownerEmail`
- [x] Android passes `ownerEmail` from session to API
- [x] Session stores `ownerEmail` from QR code
- [ ] Verify refresh tokens are being stored during Google auth
- [ ] Verify refresh tokens are being retrieved correctly
- [ ] Verify OAuth request includes `access_type=offline`
