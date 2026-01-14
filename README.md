# ChoreQuest - Family Chore Management System

ChoreQuest is a comprehensive family chore management application that makes household tasks fun and rewarding for children while keeping parents organized. The app uses Google Drive as a free, secure backend through a Google Apps Script connector.

## Overview

ChoreQuest consists of three main components:

1. **Android App** (Kotlin + Jetpack Compose) - Native mobile application
2. **Web App** (React + TypeScript + Vite) - Progressive web application
3. **Google Apps Script Backend** - Serverless middleware and storage

## Key Features

### For Parents
- Create and assign chores with points and due dates
- Manage rewards marketplace
- View detailed activity logs
- Generate unique QR codes for family members
- Verify completed chores with photo proof
- Set up recurring chores
- Participate in chores without earning points

### For Children  
- View assigned chores and subtasks
- Complete chores with visual rewards (fireworks, confetti)
- Earn and track points
- Redeem rewards from marketplace
- Play games in the games area
- Colorful, attention-grabbing UI

### Technical Features
- **Persistent Login**: QR code authentication with device session management
- **Offline Support**: Local caching with automatic sync
- **Real-time Updates**: Efficient polling mechanism
- **Activity Logging**: Comprehensive tracking of all actions
- **Secure Authentication**: Google OAuth for parents, QR codes for family members
- **Free Backend**: Google Drive storage via Apps Script

## Architecture

```
┌─────────────┐          ┌──────────────────┐          ┌─────────────────┐
│   Android   │◄────────►│  Google Apps     │◄────────►│  Google Drive   │
│     App     │          │     Script       │          │   (Storage)     │
└─────────────┘          │   (Middleware)   │          └─────────────────┘
                         └──────────────────┘
┌─────────────┐                   ▲
│    Web      │                   │
│     App     │───────────────────┘
└─────────────┘
```

## Project Structure

```
ChoreQuestV2/
├── android/                    # Android application
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/chorequest/
│   │   │   │   ├── data/          # Data layer (API, Database, Repository)
│   │   │   │   ├── domain/        # Domain models
│   │   │   │   ├── presentation/  # UI layer (Composables, ViewModels)
│   │   │   │   ├── di/            # Dependency injection
│   │   │   │   └── utils/         # Utilities and constants
│   │   │   └── res/               # Resources
│   │   └── build.gradle.kts       # App-level dependencies
│   ├── BUILD_GUIDE.md             # Android build instructions
│   └── README.md
│
├── web/                        # React web application
│   ├── src/
│   │   ├── services/          # API services
│   │   ├── types/             # TypeScript types and models
│   │   └── App.tsx            # Main app component
│   ├── package.json           # Web app dependencies
│   └── README.md
│
├── apps-script/               # Google Apps Script backend
│   ├── Code.gs                # Main entry point and routing
│   ├── DriveManager.gs        # Google Drive operations
│   ├── AuthManager.gs         # Authentication logic
│   ├── UserManager.gs         # User management and QR codes
│   ├── ChoreManager.gs        # Chore CRUD operations
│   ├── RewardManager.gs       # Reward management and redemption
│   ├── SyncManager.gs         # Data synchronization
│   ├── PollingManager.gs      # Efficient polling
│   ├── ActivityLogger.gs      # Activity logging
│   ├── appsscript.json        # Project configuration
│   ├── DEPLOYMENT_GUIDE.md    # Deployment instructions
│   └── README.md
│
├── shared-models-README.md    # Shared data models documentation
└── README.md                  # This file
```

## Getting Started

### Prerequisites

- **For Android:**
  - Android Studio Hedgehog (2023.1.1) or later
  - JDK 17 or later
  - Android SDK (API 24-36)

- **For Web:**
  - Node.js 18+ and npm/yarn
  - Modern web browser

- **For Backend:**
  - Google Account with Drive access
  - Basic knowledge of Google Apps Script

### Quick Start Guide

#### 1. Deploy the Backend (First!)

The backend must be deployed first to get the API URL for the client apps.

```bash
cd apps-script
```

Follow the detailed instructions in [`apps-script/DEPLOYMENT_GUIDE.md`](apps-script/DEPLOYMENT_GUIDE.md)

**Key steps:**
1. Create a new Google Apps Script project
2. Copy all `.gs` files to the project
3. Configure OAuth consent screen
4. Deploy as web app
5. **Copy the deployed Web App URL** - you'll need this!

#### 2. Build the Android App

```bash
cd android
```

1. Update `app/src/main/java/com/chorequest/utils/Constants.kt`:
   ```kotlin
   const val APPS_SCRIPT_WEB_APP_URL = "YOUR_DEPLOYED_URL_HERE"
   ```

2. Open the `android` folder in Android Studio

3. Sync Gradle files

4. Run on device or emulator

For detailed instructions, see [`android/BUILD_GUIDE.md`](android/BUILD_GUIDE.md)

#### 3. Run the Web App

```bash
cd web
```

1. Install dependencies:
   ```bash
   npm install
   ```

2. Update `src/types/constants.ts`:
   ```typescript
   export const APPS_SCRIPT_WEB_APP_URL = 'YOUR_DEPLOYED_URL_HERE';
   ```

3. Run development server:
   ```bash
   npm run dev
   ```

4. Build for production:
   ```bash
   npm run build
   ```

## Building for Production

### Android App - Ready to Build ✅

The Android app is **ready to be built in Android Studio** right now!

**Current Build Status:**
- ✅ Project structure complete
- ✅ Gradle configuration set (API 36 target)
- ✅ Data models defined
- ✅ Room database configured
- ✅ Retrofit API interfaces created
- ✅ Repository pattern implemented
- ✅ Dependency injection with Hilt setup
- ✅ Session management implemented
- ✅ Basic UI created

**To Build:**

1. Open Android Studio
2. Open the `android` folder as a project
3. Wait for Gradle sync to complete
4. Update the Apps Script URL in `Constants.kt`
5. Click **Build > Make Project** or press `Ctrl+F9` / `Cmd+F9`
6. To run: Click the green Run button and select your device

**Build Output:**
- Debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `android/app/build/outputs/apk/release/app-release.apk`

### What's Implemented

#### Backend (Google Apps Script) ✅
- ✅ Complete authentication system (Google OAuth + QR codes)
- ✅ User management with QR generation
- ✅ Chore CRUD operations
- ✅ Reward CRUD and redemption
- ✅ Points and transaction system
- ✅ Activity logging
- ✅ Sync and polling mechanisms
- ✅ Google Drive storage
- ✅ Recurring chores support

#### Android App ✅
- ✅ Project structure and build configuration
- ✅ Data layer (API, Database, Repository)
- ✅ Domain models matching backend
- ✅ Dependency injection setup
- ✅ Session management with encryption
- ✅ QR code utilities
- ✅ Basic UI foundation
- ⏳ Authentication screens (foundation ready)
- ⏳ Dashboard UI (foundation ready)
- ⏳ Chore management screens (foundation ready)
- ⏳ Rewards marketplace UI (foundation ready)

#### Web App ✅
- ✅ Project structure with Vite + React
- ✅ TypeScript configuration
- ✅ Data models matching backend
- ✅ Authentication service
- ✅ Tailwind CSS setup
- ⏳ UI components (foundation ready)

### What's Next (Optional Enhancements)

The following features are planned but not required for the initial build:

- 🔜 Push notifications (WorkManager configured)
- 🔜 Games area for children
- 🔜 Photo verification for chores (Camera permissions granted)
- 🔜 Avatars and profile customization
- 🔜 Leaderboard and gamification
- 🔜 Dark mode theme
- 🔜 Comprehensive testing suite
- 🔜 Play Store deployment

## Technology Stack

### Android
- **Language:** Kotlin
- **UI:** Jetpack Compose with Material 3
- **Architecture:** MVVM + Clean Architecture
- **DI:** Hilt (Dagger)
- **Database:** Room
- **Networking:** Retrofit + OkHttp
- **Image Loading:** Coil
- **Async:** Kotlin Coroutines + Flow
- **QR Codes:** ML Kit + ZXing

### Web
- **Framework:** React 18
- **Language:** TypeScript
- **Build Tool:** Vite
- **Styling:** Tailwind CSS
- **Animation:** Framer Motion
- **QR Codes:** qrcode.react
- **Local Storage:** IndexedDB (planned)

### Backend
- **Runtime:** Google Apps Script (JavaScript)
- **Storage:** Google Drive
- **Authentication:** Google OAuth 2.0
- **Data Format:** JSON

## Data Models

All data models are defined and synchronized across all three platforms:

- `User` - Family member accounts
- `Family` - Family group and settings
- `Chore` - Chore definition with subtasks and recurring options
- `Reward` - Redeemable rewards
- `Transaction` - Points transaction history
- `ActivityLog` - Detailed activity tracking
- `DeviceSession` - Device authentication sessions
- `QRCodePayload` - QR code authentication data

See [`shared-models-README.md`](shared-models-README.md) for detailed model documentation.

## Security

- **Authentication:** Google OAuth 2.0 for parents
- **QR Codes:** Encrypted tokens with versioning
- **Sessions:** Encrypted local storage with device fingerprinting
- **Authorization:** Role-based access control
- **Data:** Stored securely in user's Google Drive
- **Network:** HTTPS/TLS for all communications

## Development

### Android Development

```bash
# Build debug APK
cd android
./gradlew assembleDebug

# Install on device
./gradlew installDebug

# Run tests
./gradlew test
```

### Web Development

```bash
# Install dependencies
cd web
npm install

# Development server
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview
```

### Backend Development

Use [clasp](https://github.com/google/clasp) for local development:

```bash
npm install -g @google/clasp
clasp login
clasp clone YOUR_SCRIPT_ID
clasp push
```

## Testing

### Manual Testing Checklist

- [ ] Google OAuth login (parent)
- [ ] QR code generation
- [ ] QR code authentication (child/co-parent)
- [ ] Session persistence
- [ ] Create chore with subtasks
- [ ] Complete chore
- [ ] Verify chore (parent)
- [ ] Points awarded correctly
- [ ] Create reward
- [ ] Redeem reward
- [ ] Activity log entries
- [ ] Recurring chores
- [ ] Offline mode
- [ ] Data synchronization

## Troubleshooting

### Android Build Issues

**Problem:** SDK location not found
**Solution:** Create `local.properties` in `android/` folder:
```properties
sdk.dir=C:\\Users\\YOUR_USERNAME\\AppData\\Local\\Android\\Sdk
```

**Problem:** Gradle sync fails
**Solution:** Ensure you're using JDK 17 and Gradle 8.4+

### Apps Script Issues

**Problem:** Authorization required
**Solution:** Run `testInitialize()` function manually to grant permissions

**Problem:** CORS errors
**Solution:** Ensure the web app is deployed with "Anyone" access

### General Issues

**Problem:** Data not syncing
**Solution:** Check Apps Script execution logs and verify API URL in client apps

## Contributing

This is a family chore management application. Key areas for contribution:

1. UI/UX improvements
2. Additional game implementations
3. Enhanced celebration animations
4. Additional reward types
5. Comprehensive testing suite

## License

This project is proprietary software for family use.

## Support

For build issues:
- Android: See [`android/BUILD_GUIDE.md`](android/BUILD_GUIDE.md)
- Backend: See [`apps-script/DEPLOYMENT_GUIDE.md`](apps-script/DEPLOYMENT_GUIDE.md)
- Web: Check console for errors and verify API configuration

## Acknowledgments

- Google Apps Script for free serverless backend
- Jetpack Compose for modern Android UI
- React ecosystem for web development
- Open source libraries and tools

---

**Status:** ✅ **Ready to Build in Android Studio**

The foundational architecture is complete and the Android app is ready to compile and run. The backend is fully implemented and ready to deploy. Additional UI screens and features can be added incrementally.
