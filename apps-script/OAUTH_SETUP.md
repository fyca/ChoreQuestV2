# OAuth Setup Guide for ChoreQuest

## Problem
The app needs OAuth credentials to exchange server auth codes for access tokens, which allows the app to access each user's Google Drive.

## Solution
Configure OAuth 2.0 credentials in Google Cloud Console and add them to Apps Script Properties.

## Step-by-Step Setup

### 1. Create OAuth 2.0 Credentials in Google Cloud Console

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Select your project (or create one)
3. Navigate to **APIs & Services** > **Credentials**
4. Click **+ CREATE CREDENTIALS** > **OAuth client ID**
5. If prompted, configure the OAuth consent screen:
   - Choose **External** (or Internal if using Google Workspace)
   - Fill in required fields:
     - App name: `ChoreQuest`
     - User support email: Your email
     - Developer contact: Your email
   - Click **Save and Continue**
   - Add scopes:
     - `https://www.googleapis.com/auth/drive.file`
     - `https://www.googleapis.com/auth/drive.appdata`
   - Click **Save and Continue**
   - Add test users (your Google account email)
   - Click **Save and Continue**
6. Create OAuth client ID:
   - Application type: **Web application**
   - Name: `ChoreQuest Apps Script`
   - **Authorized redirect URIs**: Leave this EMPTY or add:
     - `http://localhost` (if Google requires at least one redirect URI)
     - Note: For Android server auth codes, redirect URIs are not typically required
   - Click **Create**
7. **IMPORTANT**: Copy the **Client ID** and **Client Secret** - you'll need these next!

### 2. Add Credentials to Apps Script Properties

1. Open your Apps Script project
2. Go to **Project Settings** (gear icon on the left)
3. Scroll down to **Script Properties**
4. Click **Add script property** and add:
   - Property: `OAUTH_CLIENT_ID`
   - Value: Your OAuth Client ID (from step 1)
5. Click **Add script property** again and add:
   - Property: `OAUTH_CLIENT_SECRET`
   - Value: Your OAuth Client Secret (from step 1)
6. Click **Save project**

### 3. Verify Setup

1. In Apps Script, go to **Executions** (clock icon)
2. Run a test function or make a request from the app
3. Check the logs to verify:
   - "OAuth client ID found: yes"
   - "OAuth client secret found: yes"
   - "Token exchange response code: 200"

## Troubleshooting

### Error: "OAuth credentials not configured"
- Make sure you added both `OAUTH_CLIENT_ID` and `OAUTH_CLIENT_SECRET` to Script Properties
- Property names must be exactly: `OAUTH_CLIENT_ID` and `OAUTH_CLIENT_SECRET` (case-sensitive)

### Error: "Failed to exchange server auth code"
- Check that the OAuth Client ID matches the one used in the Android app (`GOOGLE_WEB_CLIENT_ID` in Constants.kt)
- **Redirect URI**: For Android server auth codes, you typically don't need to set a redirect URI in Google Cloud Console. If Google requires one, use `http://localhost` (it won't be used for server auth codes)
- Check Apps Script execution logs for detailed error messages
- Common error: `invalid_grant` - Server auth code expired (they expire quickly, try signing in again)
- Common error: `invalid_client` - Client ID or Secret mismatch

### Error: "401 Unauthorized" (HTML response)
- This usually means OAuth credentials are missing or incorrect
- Check Script Properties are set correctly
- Verify the OAuth consent screen is configured
- Make sure the OAuth client ID in Google Cloud Console matches the one in the Android app

## Notes

- The OAuth Client ID used in Apps Script must be the same as the one in the Android app (`GOOGLE_WEB_CLIENT_ID`)
- The server auth code from Android is exchanged for an access token, which is then used for all Drive API calls
- Access tokens expire after 1 hour, but refresh tokens can be used to get new access tokens
- Each user's access token is stored in User Properties (separate per user)
