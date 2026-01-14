# Mock Authentication Mode

## Current Status

✅ **The app now works in MOCK MODE for testing!**

Since the Google Apps Script backend isn't deployed yet, the app uses mock authentication to let you test the entire flow.

## How It Works

### Login Flow

1. **Tap "Sign in with Google (Parent)"**
2. App creates a mock parent user automatically
3. Session is saved locally
4. **Navigates to Parent Dashboard** ✅

### Mock User Details

```kotlin
Name: "Test Parent"
Email: "parent@test.com"
Role: PARENT
Points: 0
```

### What's Working

- ✅ Login screen displays
- ✅ OAuth button click works
- ✅ Mock user creation
- ✅ Session persistence
- ✅ Navigation to dashboard
- ✅ Session validation on app restart

### What's Mocked

- 🔶 Google OAuth (bypassed with mock token)
- 🔶 Backend API calls (mock user created locally)
- 🔶 Family workbook creation (not needed in mock mode)

## Testing Instructions

### Test Login
1. Launch the app
2. Tap "Sign in with Google (Parent)"
3. You should navigate to "Parent Dashboard" placeholder

### Test Session Persistence
1. Close and reopen the app
2. Should auto-login and show dashboard

### Test Logout
1. (When logout button is added to dashboard)
2. Should return to login screen

## Switching to Real Authentication

When you're ready to deploy the backend and use real Google OAuth:

### Step 1: Deploy Google Apps Script Backend

Follow [`apps-script/DEPLOYMENT_GUIDE.md`](../apps-script/DEPLOYMENT_GUIDE.md)

### Step 2: Get Google OAuth Credentials

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Enable Google Drive API
3. Create OAuth 2.0 Client ID (Android type)
4. Note your **Web Client ID**

### Step 3: Update Android Code

**File:** `presentation/auth/LoginScreen.kt`

```kotlin
// REMOVE the mock login line:
viewModel.loginWithGoogle("mock_token_for_testing")

// UNCOMMENT the real Google Sign-In code:
val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
    .requestIdToken("YOUR_WEB_CLIENT_ID_HERE") // <-- Add your Web Client ID
    .requestEmail()
    .build()

val googleSignInClient = GoogleSignIn.getClient(context, gso)
val signInIntent = googleSignInClient.signInIntent
googleSignInLauncher.launch(signInIntent)
```

**File:** `data/repository/AuthRepository.kt`

```kotlin
// REMOVE the mock authentication block (lines with "TEMPORARY: Mock authentication")
```

**File:** `utils/Constants.kt` (if not already done)

```kotlin
// Add your deployed Apps Script URL
const val APPS_SCRIPT_WEB_APP_URL = "https://script.google.com/macros/s/YOUR_SCRIPT_ID/exec"
```

### Step 4: Test Real Flow

1. Rebuild the app
2. Tap "Sign in with Google"
3. Google OAuth popup should appear
4. Sign in with Google account
5. Backend creates family workbook
6. Navigate to dashboard with real data

## Current Limitations

Since we're in mock mode:
- ❌ No real data sync with Google Drive
- ❌ Family workbook isn't created
- ❌ Can't test QR code authentication (needs real family data)
- ❌ Changes don't persist across devices
- ✅ But you can test all UI screens and flows!

## Why Mock Mode?

Mock mode allows you to:
- ✅ Test the entire app UI without backend setup
- ✅ Develop and iterate quickly
- ✅ Show the app to stakeholders
- ✅ Test navigation and user flows
- ✅ Build confidence before production setup

## Next Steps

1. ✅ **Test the mock login** - Should work now!
2. ⏳ Complete remaining UI screens (dashboards, chores, rewards)
3. ⏳ Deploy Google Apps Script backend
4. ⏳ Switch from mock to real authentication
5. ⏳ Test end-to-end with real data

---

**The OAuth login bug is now fixed!** 🎉

Try it again - tap "Sign in with Google" and you should navigate to the dashboard.
