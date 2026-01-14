# ChoreQuest Android Build Guide

## Prerequisites

Before building the ChoreQuest Android app, ensure you have the following installed:

### Required Software
- **Android Studio** Hedgehog (2023.1.1) or later
- **JDK** 17 or later
- **Android SDK**:
  - Minimum SDK: API 24 (Android 7.0)
  - Target SDK: API 36 (Android 14+)
  - Compile SDK: API 36

### Gradle
- Gradle 8.2 or later (automatically installed by Gradle Wrapper)

## Setup Instructions

### 1. Clone and Open Project

```bash
cd ChoreQuestV2/android
```

Open the `android` folder in Android Studio as an Android project.

### 2. Sync Gradle

Android Studio should automatically prompt you to sync Gradle. If not, click:
**File > Sync Project with Gradle Files**

### 3. Configure Google Services

#### A. Get Google OAuth 2.0 Credentials

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select existing one
3. Enable **Google Drive API**
4. Go to **Credentials** > **Create Credentials** > **OAuth 2.0 Client ID**
5. Select **Android** as application type
6. Get your SHA-1 fingerprint:
   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```
7. Add the SHA-1 fingerprint and package name (`com.chorequest`) to your OAuth client

#### B. Update Constants

Edit `app/src/main/java/com/chorequest/utils/Constants.kt` and replace:
```kotlin
const val APPS_SCRIPT_WEB_APP_URL = "YOUR_DEPLOYED_APPS_SCRIPT_WEB_APP_URL_HERE"
```

With your deployed Google Apps Script web app URL from the `apps-script` folder.

### 4. Build the App

#### Using Android Studio

1. Select **Build > Make Project** (or press `Ctrl+F9` / `Cmd+F9`)
2. Wait for the build to complete
3. Check the **Build** tab for any errors

#### Using Command Line

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug
```

### 5. Run the App

1. Connect an Android device via USB or start an emulator
2. Click the **Run** button (green triangle) in Android Studio
3. Select your target device
4. The app will be installed and launched automatically

## Project Structure

```
android/
├── app/
│   ├── build.gradle.kts              # App-level Gradle config
│   ├── proguard-rules.pro            # ProGuard rules for release
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml   # App manifest
│           ├── java/com/chorequest/
│           │   ├── ChoreQuestApplication.kt    # Application class
│           │   ├── MainActivity.kt              # Main entry point
│           │   ├── data/                        # Data layer
│           │   │   ├── local/                   # Room database, DAOs
│           │   │   │   ├── ChoreQuestDatabase.kt
│           │   │   │   ├── Converters.kt
│           │   │   │   ├── SessionManager.kt
│           │   │   │   ├── dao/                 # Data Access Objects
│           │   │   │   └── entities/            # Room entities
│           │   │   ├── remote/                  # API interfaces
│           │   │   │   ├── ChoreQuestApi.kt
│           │   │   │   └── ApiModels.kt
│           │   │   └── repository/              # Repository pattern
│           │   ├── di/                          # Dependency injection
│           │   │   ├── AppModule.kt
│           │   │   └── RepositoryModule.kt
│           │   ├── domain/                      # Domain layer
│           │   │   └── models/
│           │   │       └── Models.kt            # Data models
│           │   ├── presentation/                # UI layer (to be expanded)
│           │   │   └── theme/                   # Jetpack Compose theme
│           │   │       ├── Color.kt
│           │   │       ├── Theme.kt
│           │   │       └── Type.kt
│           │   └── utils/                       # Utilities
│           │       ├── Constants.kt
│           │       └── Result.kt
│           └── res/                             # Resources (layouts, strings, etc.)
├── build.gradle.kts                    # Project-level Gradle config
├── gradle.properties                   # Gradle properties
├── settings.gradle.kts                 # Gradle settings
└── BUILD_GUIDE.md                      # This file
```

## Key Technologies

### Architecture
- **MVVM** (Model-View-ViewModel) pattern
- **Clean Architecture** with data, domain, and presentation layers
- **Repository Pattern** for data access abstraction

### Libraries Used

#### Core Android
- Kotlin 1.9.20
- AndroidX Core KTX
- Lifecycle & ViewModel

#### UI
- Jetpack Compose (Material 3)
- Compose Navigation
- Compose Animations

#### Dependency Injection
- Hilt (Dagger)

#### Networking
- Retrofit 2
- OkHttp 3
- Gson

#### Local Storage
- Room Database
- Encrypted SharedPreferences
- DataStore

#### Authentication
- Google Play Services Auth
- ML Kit Barcode Scanning
- ZXing (QR code generation)

#### Image Loading
- Coil

#### Background Tasks
- WorkManager

## Build Variants

### Debug
- Debugging enabled
- Logging enabled
- No code obfuscation
- Cleartext traffic allowed (for development)

### Release
- ProGuard enabled (code obfuscation)
- Logging disabled
- Optimized for performance
- Requires signing configuration

## Signing Configuration (for Release)

### Create a Keystore

```bash
keytool -genkey -v -keystore chorequest-release.keystore -alias chorequest -keyalg RSA -keysize 2048 -validity 10000
```

### Configure Signing in `app/build.gradle.kts`

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("../chorequest-release.keystore")
            storePassword = "YOUR_KEYSTORE_PASSWORD"
            keyAlias = "chorequest"
            keyPassword = "YOUR_KEY_PASSWORD"
        }
    }
    
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // ... other release config
        }
    }
}
```

> **Security Note:** Never commit keystore files or passwords to version control!

## Common Build Issues

### Issue: "SDK location not found"
**Solution:** Create `local.properties` file in the `android/` directory:
```properties
sdk.dir=C\:\\Users\\YOUR_USERNAME\\AppData\\Local\\Android\\Sdk
```
(Replace with your actual SDK path)

### Issue: "resource mipmap/ic_launcher not found"
**Solution:** Generate launcher icons using Android Studio's Image Asset Studio (takes 2 minutes):
1. Right-click `app/src/main/res` > **New > Image Asset**
2. Select **Launcher Icons (Adaptive and Legacy)**
3. Choose any clip art icon (e.g., checkmark, star)
4. Use color **#4ECDC4** for background
5. Click **Finish**

**See [LAUNCHER_ICON_FIX.md](LAUNCHER_ICON_FIX.md) for detailed instructions.**

### Issue: "Unsupported class file major version"
**Solution:** Ensure you're using JDK 17:
- Android Studio > File > Project Structure > SDK Location > Gradle Settings
- Set Gradle JDK to "jbr-17" (Jetbrains Runtime 17)

### Issue: "Room schema export error"
**Solution:** Add schema export directory in `app/build.gradle.kts`:
```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

### Issue: "Hilt component not generated"
**Solution:** Rebuild project: Build > Rebuild Project

## Testing

### Run Unit Tests
```bash
./gradlew test
```

### Run Instrumented Tests
```bash
./gradlew connectedAndroidTest
```

## Performance Optimization

- **Code Shrinking:** Enabled via ProGuard in release builds
- **Resource Shrinking:** Automatically removes unused resources
- **APK Splitting:** Configure in `build.gradle.kts` for smaller APK sizes

## Next Steps

After successful build:

1. **Deploy Apps Script:** Follow instructions in `apps-script/README.md`
2. **Update API URLs:** Replace placeholder URLs in `Constants.kt`
3. **Implement UI Components:** Build authentication screens, dashboard, etc.
4. **Test Authentication:** Test Google OAuth and QR code login flows
5. **Implement Sync:** Complete polling and offline sync features

## Support

For build issues:
- Check Android Studio Build log
- Run `./gradlew build --stacktrace` for detailed error information
- Ensure all dependencies are downloaded: `./gradlew build --refresh-dependencies`

## Current Build Status

✅ **Ready to Build in Android Studio**

The foundational structure is complete:
- ✅ Data models defined
- ✅ Room database configured
- ✅ API interfaces created
- ✅ Dependency injection setup with Hilt
- ✅ Session management implemented
- ✅ Repository pattern established
- ✅ Basic MainActivity created

**What's Next:**
- Authentication UI screens
- QR code scanner implementation
- Main dashboard UI
- Chore management screens
- Rewards marketplace UI
- Celebration animations
- Background sync worker
