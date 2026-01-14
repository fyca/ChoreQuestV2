# ChoreQuest Android App

ChoreQuest is a family chore management app built with Kotlin and Jetpack Compose.

## Tech Stack

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose
- **Architecture:** MVVM with Clean Architecture
- **Dependency Injection:** Hilt
- **Local Database:** Room
- **Networking:** Retrofit + OkHttp
- **Image Loading:** Coil
- **QR Code:** CameraX + ML Kit Barcode Scanning + ZXing
- **Background Work:** WorkManager
- **Secure Storage:** EncryptedSharedPreferences

## Building the Project

1. Open the project in Android Studio
2. Sync Gradle files
3. Run the app on an emulator or physical device

## Minimum Requirements

- Android API 24 (Android 7.0)
- Target API 34 (Android 14)

## Project Structure

```
app/src/main/java/com/chorequest/
├── data/           # Data layer (repositories, data sources)
├── domain/         # Business logic (use cases, domain models)
├── presentation/   # UI layer (Compose screens, ViewModels)
└── utils/          # Utility classes
```

## Key Features

- QR code authentication for family members
- Colorful, kid-friendly UI
- Celebration animations on chore completion
- Offline mode with sync
- Google Drive integration
- Parent and child dashboards
