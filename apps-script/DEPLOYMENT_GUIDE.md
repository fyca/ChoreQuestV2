# ChoreQuest Apps Script Deployment Guide

## Overview

This guide will help you deploy the ChoreQuest Google Apps Script backend that acts as a middleware between the mobile/web apps and Google Drive storage.

## Prerequisites

- Google Account
- Basic understanding of Google Apps Script
- Access to Google Drive

## Files in This Directory

- `Code.gs` - Main entry point and routing
- `DriveManager.gs` - Google Drive file operations
- `AuthManager.gs` - Authentication and session management
- `UserManager.gs` - User CRUD operations and QR code management
- `ChoreManager.gs` - Chore CRUD and completion logic
- `RewardManager.gs` - Reward CRUD and redemption logic
- `SyncManager.gs` - Data synchronization and conflict resolution
- `PollingManager.gs` - Efficient polling for client updates
- `ActivityLogger.gs` - Activity logging system
- `appsscript.json` - Project manifest and configuration

## Deployment Steps

### 1. Create a New Apps Script Project

1. Go to [Google Apps Script](https://script.google.com/)
2. Click **New Project**
3. Name your project "ChoreQuest Backend" or similar

### 2. Add the Script Files

For each `.gs` file in this directory:

1. Click the **+** button next to "Files" in the Apps Script editor
2. Select **Script** (.gs file)
3. Name it exactly as shown (without the .gs extension):
   - Code
   - DriveManager
   - AuthManager
   - UserManager
   - ChoreManager
   - RewardManager
   - SyncManager
   - PollingManager
   - ActivityLogger

4. Copy and paste the content from each file into its corresponding script file

### 3. Configure the Project Manifest

1. Click the gear icon (Project Settings) in the left sidebar
2. Check "Show `appsscript.json` manifest file in editor"
3. Go back to the Editor
4. Click on `appsscript.json` in the file list
5. Replace its contents with the content from `appsscript.json` in this directory

### 4. Set Up OAuth Consent Screen

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Select your project (or create a new one)
3. Navigate to **APIs & Services > OAuth consent screen**
4. Select **External** user type (or Internal if using Google Workspace)
5. Fill in the required information:
   - App name: "ChoreQuest"
   - User support email: Your email
   - Developer contact information: Your email
6. Add scopes:
   - `https://www.googleapis.com/auth/userinfo.email`
   - `https://www.googleapis.com/auth/drive.file`
   - `https://www.googleapis.com/auth/drive.appdata`
7. Save and continue

### 5. Enable Required APIs

In Google Cloud Console, enable the following APIs:
1. Google Drive API
2. Google People API (for user info)

### 6. Deploy as Web App

1. In the Apps Script editor, click **Deploy > New deployment**
2. Click the gear icon next to "Select type"
3. Select **Web app**
4. Configure deployment:
   - **Description**: "ChoreQuest Backend v1.0"
   - **Execute as**: Me (your email)
   - **Who has access**: Anyone (for public app) or Anyone with Google account
5. Click **Deploy**
6. **IMPORTANT**: Copy the Web App URL - you'll need this for the Android and web apps

The URL will look like:
```
https://script.google.com/macros/s/SCRIPT_ID/exec
```

### 7. Test the Deployment

#### Test Initialization

1. In the Apps Script editor, select the `testInitialize` function from the dropdown
2. Click **Run**
3. Grant the necessary permissions when prompted
4. Check your Google Drive - a folder named "ChoreQuest_Data" should be created with JSON files

#### Test API Endpoints

Use a tool like Postman or curl to test:

**Test GET (Health Check)**
```bash
curl "https://script.google.com/macros/s/YOUR_SCRIPT_ID/exec?path=sync&action=status"
```

Expected response:
```json
{
  "success": true,
  "lastModified": "...",
  "fileMetadata": {...}
}
```

### 8. Update Client Apps

Update the following constants in your client apps with the deployed Web App URL:

#### Android App
File: `android/app/src/main/java/com/chorequest/utils/Constants.kt`
```kotlin
const val APPS_SCRIPT_WEB_APP_URL = "https://script.google.com/macros/s/YOUR_SCRIPT_ID/exec"
```

#### Web App
File: `web/src/types/constants.ts`
```typescript
export const APPS_SCRIPT_WEB_APP_URL = 'https://script.google.com/macros/s/YOUR_SCRIPT_ID/exec';
```

### 9. Set Up Triggers (Optional but Recommended)

For automated cleanup and maintenance:

1. In Apps Script editor, click the clock icon (Triggers)
2. Click **Add Trigger**
3. Create the following triggers:

**Daily Cleanup (if needed)**
- Function: `cleanupOldLogs` (you'll need to create this)
- Event source: Time-driven
- Type: Day timer
- Time: 1am to 2am

## API Endpoints

### Authentication
- `GET ?path=auth&action=validate&userId=...&token=...&tokenVersion=...`
- `POST ?path=auth&action=google` - Google OAuth login
- `POST ?path=auth&action=qr` - QR code login

### Users
- `GET ?path=users&action=list&familyId=...`
- `POST ?path=users&action=create` - Create family member
- `POST ?path=users&action=update` - Update user
- `POST ?path=users&action=delete` - Remove user
- `POST ?path=users&action=regenerateQR` - Regenerate QR code

### Chores
- `GET ?path=chores&action=list&familyId=...&userId=...`
- `GET ?path=chores&action=get&choreId=...`
- `POST ?path=chores&action=create` - Create chore
- `POST ?path=chores&action=update` - Update chore
- `POST ?path=chores&action=complete` - Mark chore complete
- `POST ?path=chores&action=verify` - Verify completed chore
- `POST ?path=chores&action=delete` - Delete chore

### Rewards
- `GET ?path=rewards&action=list&familyId=...`
- `GET ?path=rewards&action=get&rewardId=...`
- `POST ?path=rewards&action=create` - Create reward
- `POST ?path=rewards&action=update` - Update reward
- `POST ?path=rewards&action=redeem` - Redeem reward
- `POST ?path=rewards&action=delete` - Delete reward

### Sync & Polling
- `GET ?path=sync&action=status` - Get sync status
- `GET ?path=sync&action=changes&since=...&types=...` - Get changes since timestamp

### Data (Generic)
- `GET ?path=data&action=get&type=...`
- `POST ?path=data&action=save&type=...`

### Batch Operations
- `POST ?path=batch` - Execute multiple operations in one request

## Security Considerations

1. **Authentication**: Every request should include a valid session token
2. **Authorization**: Check user roles before allowing operations
3. **Data Validation**: Validate all input data
4. **Rate Limiting**: Consider implementing rate limiting for production
5. **Encryption**: QR code tokens are UUIDs - consider encrypting sensitive data
6. **CORS**: The deployed web app allows CORS by default

## Data Structure

Data is stored in Google Drive in the "ChoreQuest_Data" folder as JSON files:

- `family.json` - Family information and settings
- `users.json` - All family member accounts
- `chores.json` - All chores (active and completed)
- `rewards.json` - All rewards
- `transactions.json` - Points transaction history
- `activity_log.json` - Activity log entries

## Monitoring and Debugging

### View Logs
1. In Apps Script editor, click **Executions** (clock with arrow icon)
2. View execution logs and errors
3. Use `Logger.log()` statements in the code for debugging

### Common Issues

**Issue: "Authorization required"**
- Solution: Re-authorize the script by running any function manually

**Issue: "Script is taking too long"**
- Solution: Apps Script has a 6-minute execution timeout. Optimize long-running operations

**Issue: "Service invoked too many times"**
- Solution: Apps Script has quota limits. Implement caching and reduce API calls

**Issue: "Drive API quota exceeded"**
- Solution: Implement exponential backoff and caching strategies

## Updating the Deployment

When you make changes to the script:

1. Edit the files in the Apps Script editor
2. Click **Deploy > Manage deployments**
3. Click the pencil icon next to your active deployment
4. Change the **Version** to "New version"
5. Add a description of changes
6. Click **Deploy**

**Note**: The Web App URL remains the same, so you don't need to update your client apps.

## Backup and Version Control

### Manual Backup
1. Download all script files locally
2. Keep them in version control (Git)
3. Store the script ID and deployment URL securely

### Automated Backup (Optional)
Use [clasp](https://github.com/google/clasp) for command-line access:

```bash
npm install -g @google/clasp
clasp login
clasp clone YOUR_SCRIPT_ID
```

## Production Checklist

Before going to production:

- [ ] Enable OAuth consent screen
- [ ] Enable required APIs
- [ ] Test all API endpoints
- [ ] Update client app constants with deployed URL
- [ ] Set up error logging and monitoring
- [ ] Implement rate limiting if needed
- [ ] Document any custom settings or configurations
- [ ] Create backup of all script files
- [ ] Test authentication flow end-to-end
- [ ] Test data synchronization
- [ ] Verify activity logging works
- [ ] Test QR code generation and authentication

## Support

For issues or questions:
- Check the Apps Script [documentation](https://developers.google.com/apps-script)
- Review the [execution logs](#view-logs)
- Check [quota limits](https://developers.google.com/apps-script/guides/services/quotas)

## License

This Apps Script backend is part of the ChoreQuest application.
