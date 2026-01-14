# ChoreQuest Google Apps Script Connector

This Google Apps Script project serves as the backend connector for ChoreQuest, using Google Drive as the database.

## Overview

The Apps Script connector provides RESTful API endpoints for:
- Authentication (Google OAuth and QR code-based)
- User management
- Data storage and retrieval
- Polling and synchronization
- Activity logging

## Files

- **Code.gs** - Main entry point and routing
- **DriveManager.gs** - Drive file operations (CRUD)
- **AuthManager.gs** - Authentication and token validation
- **UserManager.gs** - User account management
- **PollingManager.gs** - Efficient polling endpoints
- **SyncManager.gs** - Conflict resolution and versioning
- **ActivityLogger.gs** - Activity logging utilities
- **appsscript.json** - Project configuration

## Setup

### 1. Create a Google Apps Script Project

1. Go to [script.google.com](https://script.google.com)
2. Create a new project named "ChoreQuest Connector"
3. Copy all `.gs` files into the project
4. Copy `appsscript.json` configuration

### 2. Enable Google Drive API

1. In the Apps Script editor, go to **Services** (+ icon)
2. Add **Google Drive API** (v3)
3. Set the identifier to `Drive`

### 3. Deploy as Web App

1. Click **Deploy** → **New deployment**
2. Select type: **Web app**
3. Description: "ChoreQuest API"
4. Execute as: **User accessing the web app**
5. Who has access: **Anyone** (for public use) or **Anyone with Google account**
6. Click **Deploy**
7. Copy the **Web app URL** - you'll need this for your client apps

### 4. Configure OAuth Consent

1. Go to Google Cloud Console
2. Navigate to **APIs & Services** → **OAuth consent screen**
3. Configure the consent screen with:
   - App name: ChoreQuest
   - User support email
   - Developer contact information
4. Add scopes:
   - `https://www.googleapis.com/auth/drive.file`
   - `https://www.googleapis.com/auth/drive.appdata`
   - `https://www.googleapis.com/auth/userinfo.email`

## API Endpoints

### Authentication

**POST** `/auth?action=google`
- Authenticate primary parent with Google OAuth
- Creates family if doesn't exist

**POST** `/auth?action=qr`
- Authenticate with QR code
- Body: `{ familyId, userId, token, tokenVersion, deviceId, deviceName }`

**GET** `/auth?action=validate`
- Validate existing session
- Params: `userId`, `token`, `tokenVersion`

**POST** `/auth?action=regenerate`
- Regenerate QR code for user
- Body: `{ parentUserId, targetUserId, reason }`

### User Management

**POST** `/users?action=create`
- Create new family member
- Body: `{ parentUserId, name, role, avatarUrl }`

**POST** `/users?action=update`
- Update family member
- Body: `{ parentUserId, targetUserId, updates }`

**POST** `/users?action=delete`
- Delete family member
- Body: `{ parentUserId, targetUserId }`

**GET** `/users?action=list&familyId={id}`
- List all family members

### Data Operations

**GET** `/data?action=get&type={entityType}`
- Get full data for entity type
- Types: `chores`, `rewards`, `users`, `transactions`, `activity_log`, `family`

**GET** `/data?action=getSince&type={entityType}&since={timestamp}`
- Get data if modified since timestamp

**POST** `/data?action=save&type={entityType}`
- Save data with conflict detection
- Body: data object with metadata

### Sync & Polling

**GET** `/sync?action=status`
- Get all file metadata (lastModified timestamps)

**GET** `/sync?action=changes&since={timestamp}&types={comma-separated}`
- Get incremental changes since timestamp

**POST** `/batch`
- Batch update multiple entities
- Body: `{ updates: [{ entityType, data }, ...] }`

## Data Storage

All data is stored as JSON files in a `ChoreQuest_Data` folder in the user's Google Drive:

- `family.json` - Family information and settings
- `users.json` - User accounts
- `chores.json` - Chores data
- `rewards.json` - Rewards and redemptions
- `transactions.json` - Point transactions
- `activity_log.json` - Activity audit log

## Security

- OAuth 2.0 authentication for primary parent
- Encrypted QR code tokens for family members
- Token versioning for invalidation
- Role-based access control (parent vs child)
- All data scoped to authenticated user's Drive
- No cross-family data access

## Quotas & Limits

**Google Apps Script Free Tier:**
- URL Fetch calls: 20,000/day
- Script runtime: 6 min per execution
- Total runtime: 90 min/day

**Optimization:**
- Polling uses ETag/If-Modified-Since headers
- Returns 304 Not Modified when no changes
- Batch operations reduce API calls
- Supports ~25-40 active families per deployment

## Testing

Use the included test functions:
```javascript
function testInitialize() {
  const result = initializeFamilyData('test@example.com');
  Logger.log(JSON.stringify(result, null, 2));
}
```

View logs: **View** → **Logs** (Cmd/Ctrl + Enter)

## Deployment URL

After deployment, your Web app URL will look like:
```
https://script.google.com/macros/s/{SCRIPT_ID}/exec
```

Add this URL to your client apps' environment variables:
- Android: `BuildConfig` or `local.properties`
- Web: `.env` as `VITE_APPS_SCRIPT_URL`

## Troubleshooting

### "Authorization required"
- Ensure OAuth scopes are configured
- Re-deploy the web app
- Clear browser cache and re-authenticate

### "Drive API not enabled"
- Enable Drive API in Apps Script Services
- Check Google Cloud Console API settings

### "Quota exceeded"
- Monitor quota usage in Apps Script dashboard
- Optimize polling intervals in client apps
- Consider each family deploying their own script

## Maintenance

### Backup Data
Use the included backup function:
```javascript
function createBackup() {
  // Creates timestamped backup folder
}
```

### Clear Old Logs
```javascript
function clearOldLogs(olderThanDays = 365) {
  // Removes logs older than specified days
}
```

### Export Activity Log
```javascript
function exportActivityLogsToCsv() {
  // Exports logs to CSV file
}
```

## Support

For issues or questions:
- Check Google Apps Script documentation
- Review error logs in the Apps Script editor
- Test endpoints using Google Apps Script's execution log
