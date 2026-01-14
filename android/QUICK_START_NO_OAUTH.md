# Quick Start - Test Without OAuth Setup

## ✅ **Mock Authentication Enabled**

I've enabled mock authentication so you can test the app immediately without configuring Google OAuth!

### What Changed:

1. **`Constants.kt`**: Added `USE_MOCK_AUTH = true`
2. **`AuthRepository.kt`**: Enabled the mock authentication code

### How to Test Now:

```bash
cd C:\Programming\ChoreQuestV2
.\gradlew.bat assembleDebug
```

Install and run the app. Now when you click "Sign in with Google":
- ✅ It will bypass the actual Google OAuth
- ✅ You'll be logged in as a mock parent user: "Test Parent"
- ✅ You can test all features without OAuth configuration

---

## 🔄 **Later: Enable Real Google OAuth**

When you're ready to set up real Google OAuth:

### Option 1: Get SHA-1 from Android Studio (Easiest!)

1. Open **Android Studio**
2. Click the **Gradle** tab on the right side (looks like an elephant icon)
3. Expand: **ChoreQuest > android > app > Tasks > android**
4. Double-click **signingReport**
5. Look at the **Run** tab at the bottom
6. Find this section:
   ```
   Variant: debug
   Config: debug
   Store: C:\Users\timyo\.android\debug.keystore
   Alias: AndroidDebugKey
   MD5: ...
   SHA1: AB:CD:EF:12:34:56:78:90:AB:CD:EF:12:34:56:78:90:AB:CD:EF:12
   SHA-256: ...
   ```
7. **Copy the SHA1 value**

### Option 2: Use Java's keytool (if you have Java installed)

```bash
# Find Java installation (usually in Program Files)
"C:\Program Files\Java\jdk-XX\bin\keytool.exe" -list -v -keystore %USERPROFILE%\.android\debug.keystore -alias androiddebugkey -storepass android -keypass android
```

Replace `jdk-XX` with your actual JDK version.

### Option 3: Generate Debug Keystore Info

If you can't find the SHA-1, you can create a new debug keystore:

```bash
keytool -genkey -v -keystore debug.keystore -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000
```

---

## 📋 **Once You Have SHA-1:**

1. **Go to Google Cloud Console**
   - https://console.cloud.google.com/

2. **Navigate to**: APIs & Services > Credentials

3. **Find your Web application OAuth client**
   - Client ID should end with `.apps.googleusercontent.com`

4. **Click to edit it**

5. **Scroll down to**: SHA-1 certificate fingerprints

6. **Click**: + ADD FINGERPRINT

7. **Paste your SHA-1** (format: `AB:CD:EF:12:...`)

8. **Click Save**

9. **Wait 5 minutes** for changes to propagate

10. **Disable mock mode**:
    - In `Constants.kt`, change: `USE_MOCK_AUTH = false`
    - Rebuild the app

---

## 🎮 **Test the App NOW**

Mock mode is enabled, so you can:

✅ Test the login flow  
✅ Test the parent dashboard  
✅ Create chores  
✅ Assign chores to family members  
✅ Complete chores  
✅ Redeem rewards  
✅ View activity logs  

Everything works except:
- ❌ Real Google account data
- ❌ Data persistence across devices (it's all local to the emulator)

---

## ⚡ **Quick Commands**

### Build the app:
```bash
cd C:\Programming\ChoreQuestV2
.\gradlew.bat assembleDebug
```

### Install on emulator/device:
```bash
.\gradlew.bat installDebug
```

### View logs while testing:
```bash
adb logcat | findstr "LoginScreen LoginViewModel AuthRepository"
```

---

## 🔧 **Troubleshooting**

### "App crashes on login"
- Check that `USE_MOCK_AUTH = true` in `Constants.kt`
- Rebuild the app

### "Still shows Google Sign-In screen"
- Mock mode bypasses the actual OAuth, but you still see the button
- Just click the button and it will auto-login as "Test Parent"

### "Want to disable mock mode"
- Set `USE_MOCK_AUTH = false` in `Constants.kt`
- Make sure you've added SHA-1 to Google Cloud Console
- Rebuild the app

---

## 📱 **Mock User Details**

When using mock authentication, you're logged in as:

- **Name**: Test Parent
- **Email**: testparent@chorequest.com
- **Role**: Parent
- **User ID**: mock-parent-id

You can modify these values in `AuthRepository.kt` if needed.

---

**Now go build and test the app!** 🚀

```bash
cd C:\Programming\ChoreQuestV2
.\gradlew.bat assembleDebug
```
