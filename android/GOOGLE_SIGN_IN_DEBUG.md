# Google Sign-In Debug Guide

## Issues Fixed

### 1. **Wrong Script ID in AuthRepository** ✅ FIXED
**Problem:** The `AuthRepository` was using the Google OAuth client ID instead of the Apps Script deployment ID when making API calls.

**Before:**
```kotlin
val scriptId = "156195149694-6ed18krslhe5eosrph00o1ire06ek3di.apps.googleusercontent.com"
```

**After:**
```kotlin
val scriptId = com.lostsierra.chorequest.utils.Constants.APPS_SCRIPT_WEB_APP_ID
```

**Why this matters:** The Google OAuth client ID is used to request an ID token from Google, but the Apps Script deployment ID is needed to call your backend API.

### 2. **Added Comprehensive Logging** ✅ ADDED

Added detailed logging throughout the authentication flow:

#### LoginScreen.kt
- Google Sign-In result code
- Account email and ID token presence
- Error details with status codes

#### AuthRepository.kt
- Script ID being used
- API response codes
- Auth response details
- Session save confirmation
- Error body contents

#### LoginViewModel.kt
- Token length
- State transitions
- Navigation events
- Success/error details

## How to Debug

### 1. **Build and Install the App**
```bash
cd android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 2. **Run Logcat with Filters**
```bash
adb logcat | grep -E "LoginScreen|LoginViewModel|AuthRepository"
```

Or in Android Studio:
- Open **Logcat** tab
- Set filter to: `LoginScreen|LoginViewModel|AuthRepository`

### 3. **Test Google Sign-In**

1. **Tap "Sign in with Google"**
2. **Select your Google account**
3. **Watch the logs**

### Expected Log Flow (Success)

```
LoginScreen: Google Sign-In result code: -1
LoginScreen: Google account: user@gmail.com, idToken present: true
LoginScreen: Calling viewModel.loginWithGoogle
LoginViewModel: loginWithGoogle called with token length: 1234
LoginViewModel: State set to Loading
AuthRepository: Attempting Google auth with scriptId: AKfycbyL0_R1jwrZ9pjAlMjW8zYP7bgyLAADsA_xIRY_HA7GgdF20cPJtlKg14JHaULN3Lxk
AuthRepository: API response code: 200, isSuccessful: true
AuthRepository: Auth successful: user=John Doe, role=PARENT
AuthRepository: Session saved, emitting success
LoginViewModel: Auth result received: Success
LoginViewModel: Login successful: John Doe, role=PARENT
LoginViewModel: Emitting NavigateToParentDashboard event
```

### Common Issues and What to Look For

#### Issue 1: ID Token is Null
```
LoginScreen: ID token is null
```
**Cause:** OAuth client ID not configured correctly in Google Cloud Console  
**Solution:** 
1. Go to Google Cloud Console
2. Ensure you're using the **Web application** client ID (not Android)
3. Add SHA-1 fingerprint to OAuth consent screen

#### Issue 2: API Error 404
```
AuthRepository: API response code: 404, isSuccessful: false
```
**Cause:** Apps Script not deployed or wrong deployment ID  
**Solution:**
1. Deploy your Apps Script as a Web App
2. Copy the deployment ID (after `/macros/s/` in the URL)
3. Update `Constants.APPS_SCRIPT_WEB_APP_ID`

#### Issue 3: API Error 401/403
```
AuthRepository: API response code: 401, isSuccessful: false
```
**Cause:** Apps Script permissions or authentication failure  
**Solution:**
1. Ensure Apps Script is deployed with "Anyone" access
2. Check that the ID token is valid
3. Verify Apps Script `doPost` function handles authentication

#### Issue 4: Network Error / Exception
```
AuthRepository: Exception during auth: <error details>
```
**Cause:** Network connectivity or backend unavailable  
**Solution:**
1. Check internet connection
2. Verify Apps Script URL is accessible
3. Check if Apps Script execution quota exceeded

#### Issue 5: Session Not Saving
```
AuthRepository: Auth successful: user=John Doe
(missing "Session saved" log)
```
**Cause:** Room database issue or session manager error  
**Solution:** Check logcat for Room/database errors

## Verification Checklist

Before testing, verify:

### ✅ Google Cloud Console
- [ ] OAuth 2.0 client created (Web application type)
- [ ] Client ID added to `LoginScreen.kt` line 117
- [ ] Authorized redirect URIs configured
- [ ] OAuth consent screen configured

### ✅ Apps Script
- [ ] Deployed as Web App
- [ ] Access: "Anyone" or "Anyone with the link"
- [ ] Execute as: "Me" (your account)
- [ ] `doPost` function handles `/auth` path
- [ ] Returns proper `AuthResponse` format

### ✅ Android App
- [ ] `Constants.APPS_SCRIPT_WEB_APP_ID` is set correctly
- [ ] Google Services JSON file in `app/` directory
- [ ] SHA-1 fingerprint added to Firebase/Google Cloud

## Testing with Mock Mode

If the backend isn't ready, you can use mock mode:

In `Constants.kt`:
```kotlin
const val USE_MOCK_AUTH = true  // Set to true for testing
```

This bypasses the real API and logs you in with mock data.

## Current Configuration

**Google OAuth Client ID (in LoginScreen.kt):**
```
156195149694-6ed18krslhe5eosrph00o1ire06ek3di.apps.googleusercontent.com
```

**Apps Script Deployment ID (in Constants.kt):**
```
AKfycbyL0_R1jwrZ9pjAlMjW8zYP7bgyLAADsA_xIRY_HA7GgdF20cPJtlKg14JHaULN3Lxk
```

**Note:** Make sure these match your actual Google Cloud and Apps Script configurations!

## What to Share When Asking for Help

If Google Sign-In still doesn't work after checking the logs:

1. **Full logcat output** with the filters mentioned above
2. **Apps Script deployment URL** (the full web app URL)
3. **Google OAuth client ID** being used
4. **Error message** shown to user (if any)
5. **Device/emulator** being tested on

## Next Steps After Sign-In Works

Once authentication is working:
1. Verify family data is created in Google Drive
2. Check that session persists across app restarts
3. Test QR code generation for other family members
4. Verify data sync between devices

---

**Remember:** The most common issue is mixing up the Google OAuth client ID with the Apps Script deployment ID. They are different and used for different purposes!
