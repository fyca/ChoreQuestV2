# Google OAuth Setup - Fix Sign-In Issue

## ❌ Current Problem

Your logcat shows:
```
LoginScreen: Google Sign-In result code: 0
LoginScreen: Google Sign-In cancelled or failed
```

**Result code `0` means the sign-in failed.** This is almost always due to incorrect OAuth configuration.

## ✅ Step-by-Step Fix

### Step 1: Get Your SHA-1 Fingerprint

You need to add your app's SHA-1 fingerprint to Google Cloud Console.

**For Debug Build (Development):**

Open terminal in your project root and run:

```bash
cd android
# Windows:
keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore -alias androiddebugkey -storepass android -keypass android

# Mac/Linux:
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

Look for the **SHA1** line in the output:
```
Certificate fingerprints:
         SHA1: AB:CD:EF:12:34:56:78:90:AB:CD:EF:12:34:56:78:90:AB:CD:EF:12
         SHA256: ...
```

**Copy that SHA1 value!**

### Step 2: Configure Google Cloud Console

1. **Go to Google Cloud Console:**
   - Visit: https://console.cloud.google.com/
   - Select your project (or create one if you haven't)

2. **Enable Google Drive API:**
   - Go to **APIs & Services** > **Library**
   - Search for "Google Drive API"
   - Click **Enable** (if not already enabled)

3. **Configure OAuth Consent Screen:**
   - Go to **APIs & Services** > **OAuth consent screen**
   - Choose **External** (or Internal if G Workspace)
   - Fill in:
     - App name: `ChoreQuest`
     - User support email: Your email
     - Developer contact: Your email
   - Click **Save and Continue**
   - **Add Scopes:** Click "Add or Remove Scopes"
     - Add: `https://www.googleapis.com/auth/drive.file`
     - Add: `https://www.googleapis.com/auth/drive.appdata`
   - Click **Save and Continue**
   - **Test users:** Add your Google account email
   - Click **Save and Continue**

4. **Create OAuth 2.0 Credentials:**
   - Go to **APIs & Services** > **Credentials**
   - Click **+ CREATE CREDENTIALS** > **OAuth client ID**
   
   **IMPORTANT: Create a Web application client, NOT Android!**
   
   - Application type: **Web application**
   - Name: `ChoreQuest Web Client`
   - Authorized redirect URIs: (leave empty for now)
   - Click **Create**
   
   **Copy the Client ID!** It looks like:
   ```
   123456789-abcdefghijklmnop.apps.googleusercontent.com
   ```

5. **Add SHA-1 to OAuth Consent:**
   - Still in **Credentials**, look for **OAuth 2.0 Client IDs** section
   - Find your Web application client
   - Click the name to expand details
   - Scroll down to **SHA-1 certificate fingerprints**
   - Click **+ ADD FINGERPRINT**
   - Paste your SHA-1 from Step 1
   - Click **Save**

### Step 3: Update Your Android App

Update `android/app/src/main/java/com/chorequest/utils/Constants.kt`:

```kotlin
// Google OAuth Web Client ID (from Google Cloud Console)
const val GOOGLE_WEB_CLIENT_ID = "YOUR_WEB_CLIENT_ID_HERE.apps.googleusercontent.com"
```

Replace `YOUR_WEB_CLIENT_ID_HERE` with the Client ID from Step 2.

### Step 4: Rebuild and Test

```bash
cd android
./gradlew clean assembleDebug
```

Install and run the app, then try signing in again.

## 🔍 Verify Your Setup

### Checklist:

- [ ] **SHA-1 fingerprint** added to Google Cloud Console
- [ ] **OAuth consent screen** configured with test users
- [ ] **Google Drive API** enabled
- [ ] **Web application** OAuth client created (NOT Android client)
- [ ] **Client ID** copied to `Constants.kt`
- [ ] **Scopes** added: `drive.file` and `drive.appdata`
- [ ] **Test user** (your Google account) added to OAuth consent screen
- [ ] App **rebuilt** after updating client ID

## 🎯 Expected Behavior

After fixing, your logcat should show:

```
LoginScreen: Google Sign-In result code: -1
LoginScreen: Google account: your@gmail.com, idToken present: true
LoginScreen: Calling viewModel.loginWithGoogle
LoginViewModel: loginWithGoogle called with token length: 1234
```

Result code `-1` (RESULT_OK) means success!

## ❓ Common Issues

### Issue 1: "Sign in failed" with error code 10
**Cause:** SHA-1 fingerprint not added or incorrect  
**Fix:** Double-check SHA-1 in Google Cloud Console matches your debug keystore

### Issue 2: "Developer Error"
**Cause:** OAuth client ID is incorrect or app package name doesn't match  
**Fix:** 
- Verify client ID in `Constants.kt`
- Check package name is `com.lostsierra.chorequest` in `build.gradle.kts`

### Issue 3: "App not verified" screen
**Cause:** OAuth consent screen in testing mode  
**Fix:** Click "Advanced" > "Go to ChoreQuest (unsafe)" - this is normal for testing

### Issue 4: Still returns to login screen (result code 0)
**Cause:** Using Android OAuth client instead of Web client  
**Fix:** 
- Delete the Android client from Google Cloud Console
- Create a new **Web application** client
- Use that client ID

### Issue 5: "Access blocked: This app's request is invalid"
**Cause:** OAuth consent screen not configured or scopes not added  
**Fix:** Complete Step 2, section 3 above

## 📝 Why Web Client ID?

You might wonder why we use a **Web application** client ID for an Android app.

**Answer:** When using Google Sign-In with your own backend (Apps Script), you need to:
1. Get an ID token from Google on the device (Android client)
2. Send that ID token to your backend (Apps Script)
3. Your backend validates it using the Web client ID

The Web client ID allows your Apps Script to verify the token came from your app.

## 🔄 Alternative: Use Mock Mode for Testing

If you can't configure OAuth right now, enable mock mode:

In `AuthRepository.kt`, uncomment the mock authentication section:
```kotlin
// TEMPORARY: Mock authentication for testing
if (com.lostsierra.chorequest.utils.Constants.USE_MOCK_AUTH) {
    // ... mock code ...
}
```

And in `Constants.kt`:
```kotlin
const val USE_MOCK_AUTH = true
```

This lets you test the app without Google Sign-In.

## 📞 Still Stuck?

If Google Sign-In still fails after following all steps:

1. **Check logcat** for error codes
2. **Verify all checkboxes** above are checked
3. **Wait 5 minutes** after making changes in Google Cloud Console (changes take time to propagate)
4. **Try a different Google account** to rule out account-specific issues
5. **Uninstall and reinstall** the app to clear cached credentials

### Share these details if asking for help:
- Full logcat output with filters: `LoginScreen|LoginViewModel|AuthRepository`
- Screenshot of your Google Cloud Console credentials page
- Your `Constants.kt` file (with client ID visible)
- Result of `keytool` command showing SHA-1

---

**TIP:** The most common mistake is using an Android OAuth client instead of a Web client. Make sure you create a **Web application** client in Google Cloud Console!
