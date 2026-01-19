/**
 * ChoreQuest Google Apps Script Connector
 * Main entry point and routing
 */

// Configuration
const FOLDER_NAME = 'ChoreQuest_Data';
const REGISTRY_FILE_NAME = 'ChoreQuest_FamilyRegistry.json'; // Central registry in script owner's Drive
const FILE_NAMES = {
  FAMILY: 'family.json',
  USERS: 'users.json',
  CHORES: 'chores.json',
  RECURRING_CHORE_TEMPLATES: 'recurring_chore_templates.json',
  REWARDS: 'rewards.json',
  REWARD_REDEMPTIONS: 'reward_redemptions.json',
  TRANSACTIONS: 'transactions.json',
  ACTIVITY_LOG: 'activity_log.json'
};

/**
 * Handle GET requests
 */
function doGet(e) {
  // CRITICAL: Log immediately to verify request reaches Apps Script
  Logger.log('=== doGet CALLED ===');
  Logger.log('Timestamp: ' + new Date().toISOString());
  Logger.log('Parameters: ' + JSON.stringify(e.parameter));
  Logger.log('Query string: ' + (e.queryString || 'none'));
  
  try {
    const path = e.parameter.path || '';
    const action = e.parameter.action || '';
    
    // Diagnostic endpoint - always returns success to verify requests reach Apps Script
    if (path === 'ping' || path === 'test') {
      Logger.log('Ping/test endpoint called - request reached Apps Script!');
      return createResponse({
        success: true,
        message: 'Apps Script is receiving requests',
        timestamp: new Date().toISOString(),
        path: path,
        parameters: e.parameter
      });
    }
    
    // Special endpoint to trigger authorization
    if (path === 'authorize' || (path === '' && action === '')) {
      return handleAuthorizationRequest(e);
    }
    
    // Route to appropriate handler
    if (path === 'auth') {
      return handleAuthRequest(e);
    } else if (path === 'data') {
      return handleDataRequest(e);
    } else if (path === 'sync') {
      return handleSyncRequest(e);
    } else if (path === 'batch') {
      return handleBatchRequest(e);
    } else if (path === 'users') {
      return handleUsersRequest(e);
    } else if (path === 'chores') {
      return handleChoresRequest(e);
    } else if (path === 'rewards') {
      return handleRewardsRequest(e);
    } else if (path === 'activity') {
      return handleActivityLogsRequest(e);
    } else if (path === 'debug') {
      return handleDebugLogsRequest(e);
    } else if (path === 'photo') {
      // Photo proxy endpoint - serves image directly as binary
      return handlePhotoGet(e);
    }
    
    return createResponse({ error: 'Invalid path' }, 400);
  } catch (error) {
    Logger.log('Error in doGet: ' + error.toString());
    
    // Check if it's an authorization error
    const errorStr = error.toString().toLowerCase();
    if (errorStr.includes('unauthorized') || errorStr.includes('401') || errorStr.includes('permission')) {
      return handleAuthorizationRequest(e);
    }
    
    return createResponse({ error: error.toString() }, 500);
  }
}

/**
 * Handle authorization request - triggers OAuth flow
 * This endpoint should be visited in a browser to authorize Drive access
 */
function handleAuthorizationRequest(e) {
  try {
    // Get email from query parameter if provided (from Android app)
    const emailParam = e.parameter.email;
    Logger.log('Authorization request - email parameter: ' + (emailParam || 'not provided'));
    
    // Try to access Drive to trigger authorization
    // This will trigger OAuth flow if not already authorized
    const rootFolder = DriveApp.getRootFolder();
    const userEmail = Session.getActiveUser().getEmail();
    
    Logger.log('Drive access successful - user: ' + userEmail);
    
    // If email parameter was provided, verify it matches
    if (emailParam && emailParam !== userEmail) {
      Logger.log('WARNING: Email parameter (' + emailParam + ') does not match authenticated user (' + userEmail + ')');
      return createResponse({
        success: false,
        status: 400,
        error: 'Email mismatch',
        message: 'The email you provided (' + emailParam + ') does not match the Google account you are signed into (' + userEmail + '). Please sign out and sign in with the correct account.',
        authenticatedEmail: userEmail,
        providedEmail: emailParam
      }, 400);
    }
    
    return createResponse({
      success: true,
      message: 'Drive access authorized',
      userEmail: userEmail,
      instructions: 'You can now use the app. Drive access has been authorized for ' + userEmail + '.'
    });
  } catch (error) {
    Logger.log('Authorization error: ' + error.toString());
    // If authorization fails, return instructions
    const errorStr = error.toString().toLowerCase();
    if (errorStr.includes('unauthorized') || errorStr.includes('401') || errorStr.includes('permission')) {
      // Return HTML page with authorization instructions
      const emailParam = e.parameter.email || '';
      const emailNote = emailParam ? '<p><strong>Note:</strong> Make sure you are signed into Google with the account: <code>' + emailParam + '</code></p>' : '';
      
      const html = HtmlService.createHtmlOutput(`
        <!DOCTYPE html>
        <html>
        <head>
          <title>ChoreQuest - Authorization Required</title>
          <style>
            body { font-family: Arial, sans-serif; max-width: 600px; margin: 50px auto; padding: 20px; }
            h1 { color: #1a73e8; }
            .instructions { background: #f8f9fa; padding: 20px; border-radius: 8px; margin: 20px 0; }
            .button { background: #1a73e8; color: white; padding: 12px 24px; border: none; border-radius: 4px; cursor: pointer; font-size: 16px; }
            .button:hover { background: #1557b0; }
            code { background: #e8eaed; padding: 2px 6px; border-radius: 3px; }
          </style>
        </head>
        <body>
          <h1>ChoreQuest - Authorization Required</h1>
          <div class="instructions">
            <p><strong>You need to authorize this app to access your Google Drive.</strong></p>
            ${emailNote}
            <p>Click the button below to grant Drive access. This is a one-time setup required for each user.</p>
            <p><strong>IMPORTANT:</strong> After authorizing, you must return to the Android app and click "Retry Login". The authorization in the browser does not automatically authorize the app's API requests.</p>
            <button class="button" onclick="authorize()">Authorize Drive Access</button>
          </div>
          <script>
            function authorize() {
              // Reload the page - this will trigger the OAuth flow
              window.location.reload();
            }
          </script>
        </body>
        </html>
      `);
      return html;
    }
    
    return createResponse({ 
      error: 'Authorization failed',
      message: error.toString()
    }, 500);
  }
}

/**
 * Handle POST requests
 */
function doPost(e) {
  // CRITICAL: Log immediately to verify request reaches Apps Script
  Logger.log('=== doPost CALLED ===');
  Logger.log('Timestamp: ' + new Date().toISOString());
  Logger.log('Parameters: ' + JSON.stringify(e.parameter));
  Logger.log('Post data: ' + (e.postData ? e.postData.contents : 'none'));
  Logger.log('Post data type: ' + (e.postData ? e.postData.type : 'none'));
  Logger.log('Content length: ' + (e.postData ? e.postData.length : 0));
  
  try {
    // Parse POST body first
    let data = {};
    if (e.postData && e.postData.contents) {
      try {
        data = JSON.parse(e.postData.contents);
      } catch (parseError) {
        Logger.log('Failed to parse JSON: ' + parseError.toString());
        return createResponse({ 
          success: false,
          error: 'Invalid JSON in request body' 
        }, 400);
      }
    }
    
    // Get path and action from query params OR from request body
    const path = e.parameter.path || data.path || '';
    const action = e.parameter.action || data.action || '';
    
    Logger.log('Extracted path: ' + path);
    Logger.log('Extracted action: ' + action);
    
    // Route to appropriate handler
    if (path === 'auth') {
      return handleAuthPost(e, data);
    } else if (path === 'data') {
      return handleDataPost(e, data);
    } else if (path === 'users') {
      return handleUsersPost(e, data);
    } else if (path === 'chores') {
      return handleChoresPost(e, data);
    } else if (path === 'rewards') {
      return handleRewardsPost(e, data);
    } else if (path === 'batch') {
      return handleBatchPost(e, data);
    } else if (path === 'photos') {
      return handlePhotosPost(e, data);
    } else if (path === 'photo') {
      // GET request for photo proxy (serves image directly)
      return handlePhotoGet(e);
    }
    
    // If we get here, path was invalid
    Logger.log('No matching path found. Available paths: auth, data, users, chores, rewards, batch, photos');
    return createResponse({ 
      success: false,
      error: 'Invalid path: "' + path + '"',
      availablePaths: ['auth', 'data', 'users', 'chores', 'rewards', 'batch', 'photos']
    }, 400);
  } catch (error) {
    Logger.log('=== doPost ERROR ===');
    Logger.log('Error in doPost: ' + error.toString());
    Logger.log('Error stack: ' + (error.stack || 'no stack'));
    Logger.log('Error name: ' + error.name);
    
    // Check if it's an authorization error
    const errorStr = error.toString().toLowerCase();
    if (errorStr.includes('unauthorized') || errorStr.includes('401') || errorStr.includes('permission') || errorStr.includes('access denied') || errorStr.includes('drive access not authorized')) {
      Logger.log('Authorization error detected');
      
      // Try to get the web app URL for authorization
      let authUrl = 'Please authorize the script';
      try {
        authUrl = ScriptApp.getService().getUrl();
        Logger.log('Web app URL for authorization: ' + authUrl);
      } catch (urlError) {
        Logger.log('Could not get web app URL: ' + urlError.toString());
      }
      
      return createResponse({ 
        success: false,
        status: 401,
        error: 'Drive access not authorized',
        message: 'You need to authorize this app to access your Google Drive. Please visit the web app URL in a browser to complete authorization.',
        authorizationUrl: authUrl,
        instructions: '1. Open the authorizationUrl in a browser\n2. Sign in with your Google account\n3. Click "Allow" to grant Drive access\n4. Try your request again'
      }, 401);
    }
    
    // Check if it's a Drive access error
    if (errorStr.includes('drive') || errorStr.includes('cannot access')) {
      Logger.log('Drive access error detected - this might indicate:');
      Logger.log('1. Deployment is not set to USER_ACCESSING');
      Logger.log('2. User does not have Drive permissions');
      Logger.log('3. Drive API is not enabled');
    }
    
    return createResponse({ 
      success: false,
      status: 500,
      error: error.toString(),
      message: 'An error occurred processing the request. Please check the deployment settings.',
      stack: error.stack 
    }, 500);
  }
}

/**
 * Create JSON response
 */
function createResponse(data, statusCode = 200) {
  // Always return consistent JSON format
  const response = statusCode === 200 ? data : {
    status: statusCode,
    ...data
  };
  
  const output = ContentService.createTextOutput(JSON.stringify(response));
  output.setMimeType(ContentService.MimeType.JSON);
  
  return output;
}

/**
 * Get or create ChoreQuest folder in the authenticated user's Drive
 * With USER_ACCESSING deployment, this accesses the current user's Drive
 * Uses Drive API v3 for better compatibility with mobile app API calls
 * @param {string} ownerEmail - REQUIRED: Email of the owner (used to verify OAuth is working)
 * @returns {string} Folder ID (for V3) or Folder object (for legacy)
 */
function getChoreQuestFolder(ownerEmail) {
  // CRITICAL: ownerEmail is now required to verify OAuth is working
  if (!ownerEmail) {
    Logger.log('ERROR: ownerEmail is required for getChoreQuestFolder');
    throw new Error('ownerEmail is required to verify OAuth is working correctly');
  }
  
  // Use Drive API v3 version
  try {
    const folderId = getChoreQuestFolderV3(ownerEmail);
    // Return DriveApp folder object for backward compatibility
    return DriveApp.getFolderById(folderId);
  } catch (error) {
    Logger.log('Error in getChoreQuestFolderV3, falling back to DriveApp: ' + error.toString());
    // Fallback to old DriveApp method if V3 fails
    return getChoreQuestFolderLegacy(ownerEmail);
  }
}

/**
 * Legacy implementation using DriveApp (kept for fallback)
 */
function getChoreQuestFolderLegacy(ownerEmail) {
  // With USER_ACCESSING, DriveApp always accesses the authenticated user's Drive
  // We don't cache folder IDs because PropertiesService is shared across all users
  // and would cache the wrong user's folder ID
  
  // Log current user for debugging
  try {
    const currentUser = Session.getActiveUser();
    const userEmail = currentUser.getEmail();
    Logger.log('getChoreQuestFolder called - Current user: ' + userEmail);
    Logger.log('Owner email parameter: ' + (ownerEmail || 'not provided'));
    
    // Verify we're accessing the correct user's Drive
    if (ownerEmail && userEmail !== ownerEmail) {
      Logger.log('WARNING: Current user (' + userEmail + ') does not match ownerEmail (' + ownerEmail + ')');
      Logger.log('This is expected for QR code logins (children accessing parent Drive)');
    }
  } catch (e) {
    Logger.log('Could not get current user email: ' + e.toString());
  }
  
  // CRITICAL: Always use getRootFolder() which with USER_ACCESSING accesses the authenticated user's Drive
  // Do NOT use getFoldersByName() without rootFolder as it searches all accessible folders
  let rootFolder;
  try {
    rootFolder = DriveApp.getRootFolder();
    Logger.log('Root folder ID: ' + rootFolder.getId());
    
    // Verify root folder owner matches authenticated user
    try {
      const rootOwner = rootFolder.getOwner().getEmail();
      Logger.log('Root folder owner: ' + rootOwner);
      const currentUser = Session.getActiveUser();
      const currentEmail = currentUser.getEmail();
      if (rootOwner !== currentEmail) {
        Logger.log('ERROR: Root folder owner (' + rootOwner + ') does not match authenticated user (' + currentEmail + ')');
        Logger.log('This indicates the deployment may not be set to USER_ACCESSING correctly');
        Logger.log('Please redeploy the web app with "Execute as: User accessing the web app"');
      }
    } catch (e) {
      Logger.log('Could not verify root folder owner: ' + e.toString());
    }
  } catch (error) {
    Logger.log('ERROR: Cannot access root folder: ' + error.toString());
    Logger.log('Error details: ' + JSON.stringify(error));
    
    // Check if it's an authorization error
    const errorStr = error.toString().toLowerCase();
    if (errorStr.includes('unauthorized') || errorStr.includes('401') || errorStr.includes('permission') || errorStr.includes('access denied')) {
      Logger.log('Authorization error detected - user needs to authorize the script');
      throw new Error('Drive access not authorized. Please visit the web app URL in a browser to authorize Drive access: ' + ScriptApp.getService().getUrl());
    }
    
    Logger.log('This might indicate:');
    Logger.log('1. The deployment is not set to USER_ACCESSING');
    Logger.log('2. The user does not have Drive permissions');
    Logger.log('3. The Drive API is not enabled');
    throw new Error('Cannot access Drive: ' + error.toString());
  }
  
  // Search only in the authenticated user's root folder
  const folders = rootFolder.getFoldersByName(FOLDER_NAME);
  
  if (folders.hasNext()) {
    const folder = folders.next();
    Logger.log('Found existing ChoreQuest folder: ' + folder.getId());
    try {
      const folderOwner = folder.getOwner().getEmail();
      Logger.log('Folder owner: ' + folderOwner);
    } catch (e) {
      Logger.log('Could not get folder owner: ' + e.toString());
    }
    return folder;
  }
  
  // Folder doesn't exist, create it in the authenticated user's root
  Logger.log('Creating new ChoreQuest folder in authenticated user\'s Drive');
  const folder = rootFolder.createFolder(FOLDER_NAME);
  folder.setDescription('ChoreQuest app data storage');
  Logger.log('Created ChoreQuest folder: ' + folder.getId());
  try {
    const folderOwner = folder.getOwner().getEmail();
    Logger.log('New folder owner: ' + folderOwner);
  } catch (e) {
    Logger.log('Could not get new folder owner: ' + e.toString());
  }
  
  return folder;
}

/**
 * TEST FUNCTION: Run this to trigger Drive API authorization
 * This will prompt you to authorize the full Drive scope
 * After running this, the scopes should update automatically
 */
function requestDrivePermissions() {
  try {
    // This call requires full Drive access and will trigger authorization
    const rootFolder = DriveApp.getRootFolder();
    Logger.log('Drive access authorized! Root folder: ' + rootFolder.getName());
    return { success: true, message: 'Drive permissions authorized' };
  } catch (error) {
    Logger.log('Error requesting permissions: ' + error.toString());
    return { success: false, error: error.toString() };
  }
}

/**
 * TEST FUNCTION: Run this to trigger external request authorization
 * This will prompt you to authorize the script.external_request scope
 * After running this, the scopes should update automatically
 */
function requestExternalRequestPermissions() {
  try {
    // This call requires external request permission and will trigger authorization
    const response = UrlFetchApp.fetch('https://www.google.com');
    Logger.log('External request authorized! Response code: ' + response.getResponseCode());
    return { success: true, message: 'External request permissions authorized' };
  } catch (error) {
    Logger.log('Error requesting permissions: ' + error.toString());
    Logger.log('Error details: ' + JSON.stringify(error));
    return { success: false, error: error.toString() };
  }
}

/**
 * Initialize family data structure
 * @param {string} userEmail - User's email address
 * @param {string} userName - User's display name from Google account
 * @param {string} userPicture - User's profile picture URL from Google account (optional)
 * @param {string} accessToken - Optional: OAuth access token for Drive API calls
 */
function initializeFamilyData(userEmail, userName, userPicture, accessToken) {
  // Create a unique folder for this family in parent's Drive using Drive API v3
  const folderId = getChoreQuestFolderV3(userEmail, accessToken);
  const familyId = Utilities.getUuid();
  const userId = Utilities.getUuid();
  
  // Ensure folder is shared so children can access it via QR code
  try {
    // Use DriveApp to set sharing (easier than Drive API v3 for permissions)
    const folder = DriveApp.getFolderById(folderId);
    folder.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.EDIT);
    Logger.log('Folder shared for QR code access: ' + folderId);
  } catch (error) {
    Logger.log('Warning: Could not share folder: ' + error.toString());
    // Try using Drive API v3 to set permissions
    try {
      Drive.Permissions.create({
        role: 'writer',
        type: 'anyone'
      }, folderId);
      Logger.log('Folder shared via Drive API v3: ' + folderId);
    } catch (v3Error) {
      Logger.log('Could not share folder via API v3 either: ' + v3Error.toString());
    }
  }
  
  // Use provided userName or fall back to email prefix
  const displayName = userName || userEmail.split('@')[0];
  
  const familyData = {
    id: familyId,
    name: 'My Family',
    ownerId: userEmail,
    ownerEmail: userEmail,
    driveFileId: folderId, // Store folder ID so children can access parent's Drive
    members: [],
    inviteCodes: [],
    createdAt: new Date().toISOString(),
    settings: {
      requirePhotoProof: false,
      autoApproveChores: false,
      pointMultiplier: 1.0,
      allowQRRegeneration: true
    },
    metadata: {
      version: 1,
      lastModified: new Date().toISOString(),
      lastModifiedBy: userEmail
    }
  };
  
  // Create primary parent user
  const primaryUser = {
    id: userId,
    name: displayName,
    email: userEmail,
    role: 'parent',
    isPrimaryParent: true,
    avatarUrl: userPicture || null, // Use Google profile picture if available
    pointsBalance: 0,
    canEarnPoints: false,
    authToken: Utilities.getUuid(),
    tokenVersion: 1,
    devices: [],
    createdAt: new Date().toISOString(),
    createdBy: userId,
    settings: {
      notifications: true,
      theme: 'light',
      celebrationStyle: 'fireworks',
      soundEffects: true
    },
    stats: {
      totalChoresCompleted: 0,
      currentStreak: 0
    }
  };
  
  familyData.members.push(primaryUser);
  
  // Save to Drive (using ownerEmail to ensure family-specific folder)
  // Use V3 functions to save files (which will use Drive API v3)
  saveJsonFileV3(FILE_NAMES.FAMILY, familyData, userEmail, null, accessToken);
  saveJsonFileV3(FILE_NAMES.USERS, { users: [primaryUser] }, userEmail, null, accessToken);
  saveJsonFileV3(FILE_NAMES.CHORES, { chores: [] }, userEmail, null, accessToken);
  saveJsonFileV3(FILE_NAMES.RECURRING_CHORE_TEMPLATES, { templates: [] }, userEmail, null, accessToken);
  saveJsonFileV3(FILE_NAMES.REWARDS, { rewards: [] }, userEmail, null, accessToken);
  saveJsonFileV3(FILE_NAMES.REWARD_REDEMPTIONS, { redemptions: [] }, userEmail, null, accessToken);
  saveJsonFileV3(FILE_NAMES.TRANSACTIONS, { transactions: [] }, userEmail, null, accessToken);
  saveJsonFileV3(FILE_NAMES.ACTIVITY_LOG, { logs: [] }, userEmail, null, accessToken);
  
  // Register this family in the central registry (familyId -> ownerEmail mapping)
  registerFamily(familyId, userEmail);
  
  return { familyData, primaryUser };
}

/**
 * Register a family (no-op for now, since we'll include ownerEmail in QR code)
 * @param {string} familyId - The family ID
 * @param {string} ownerEmail - The owner's email
 */
function registerFamily(familyId, ownerEmail) {
  // No longer needed - ownerEmail will be included in QR code
  Logger.log('Family registered: ' + familyId + ' -> ' + ownerEmail + ' (ownerEmail in QR code)');
}

/**
 * Get owner email for a family ID
 * NOTE: This is no longer needed since ownerEmail will be in QR code
 * @param {string} familyId - The family ID
 * @returns {string|null} The owner email, or null if not found
 */
function getOwnerEmailForFamily(familyId) {
  // This function is deprecated - ownerEmail should come from QR code
  Logger.log('WARNING: getOwnerEmailForFamily called - ownerEmail should be in QR code');
  return null;
}

/**
 * Handle photos POST requests
 */
function handlePhotosPost(e, data) {
  try {
    const action = e.parameter.action || data.action || '';
    
    if (action === 'upload') {
      return uploadPhotoHandler(data);
    } else if (action === 'delete') {
      return deletePhotoHandler(data);
    } else if (action === 'get') {
      return getPhotoUrlHandler(data);
    }
    
    return createResponse({ error: 'Invalid action for photos' }, 400);
  } catch (error) {
    Logger.log('Error in handlePhotosPost: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}

/**
 * Handle photo upload
 */
function uploadPhotoHandler(data) {
  try {
    const { base64Data, fileName, mimeType, choreId, ownerEmail, userId } = data;
    
    if (!base64Data || !fileName || !mimeType) {
      return createResponse({ 
        success: false,
        error: 'Missing required fields: base64Data, fileName, mimeType' 
      }, 400);
    }
    
    if (!ownerEmail) {
      return createResponse({ 
        success: false,
        error: 'Missing required field: ownerEmail' 
      }, 400);
    }
    
    // Validate MIME type
    if (!mimeType.startsWith('image/')) {
      return createResponse({ 
        success: false,
        error: 'Invalid MIME type. Must be an image.' 
      }, 400);
    }
    
    // Get access token for the owner's Drive
    Logger.log('uploadPhotoHandler: Getting access token for owner: ' + ownerEmail);
    const userProps = PropertiesService.getUserProperties();
    const accessTokenKey = 'ACCESS_TOKEN_' + ownerEmail;
    const accessToken = userProps.getProperty(accessTokenKey);
    
    if (!accessToken) {
      Logger.log('ERROR: No access token found for owner: ' + ownerEmail);
      return createResponse({
        success: false,
        error: 'Drive access not authorized. Please authorize the app to access your Google Drive.'
      }, 401);
    }
    
    Logger.log('uploadPhotoHandler: Using access token for upload');
    
    // Use V3 upload function with access token
    const result = uploadImageV3(base64Data, fileName, mimeType, choreId, ownerEmail, accessToken);
    
    if (result.success) {
      // Log upload activity
      try {
        // Get family info to log activity properly
        const familyInfo = getFamilyInfo(userId);
        if (familyInfo) {
          logActivity({
            type: 'photo_uploaded',
            userId: userId || 'unknown',
            choreId: choreId || null,
            details: {
              fileName: fileName,
              fileId: result.fileId,
              size: result.size
            },
            timestamp: new Date().toISOString()
          }, familyInfo.ownerEmail, familyInfo.folderId, familyInfo.accessToken);
        }
      } catch (logError) {
        Logger.log('Failed to log activity: ' + logError.toString());
      }
      
      return createResponse(result, 200);
    } else {
      return createResponse(result, 500);
    }
  } catch (error) {
    Logger.log('Error in uploadPhotoHandler: ' + error.toString());
    Logger.log('Error stack: ' + (error.stack || 'no stack'));
    
    // Check if it's an authorization error
    const errorStr = error.toString().toLowerCase();
    if (errorStr.includes('unauthorized') || errorStr.includes('401') || errorStr.includes('permission') || errorStr.includes('access denied') || errorStr.includes('drive access not authorized')) {
      return createResponse({
        success: false,
        error: 'Drive access not authorized. Please authorize the app to access your Google Drive.'
      }, 401);
    }
    
    return createResponse({ 
      success: false,
      error: error.toString() 
    }, 500);
  }
}

/**
 * Handle photo deletion
 */
function deletePhotoHandler(data) {
  try {
    const { fileId } = data;
    
    if (!fileId) {
      return createResponse({ 
        success: false,
        error: 'Missing fileId' 
      }, 400);
    }
    
    const result = deleteImage(fileId);
    
    if (result.success) {
      return createResponse({ 
        success: true,
        message: 'Photo deleted successfully' 
      }, 200);
    } else {
      return createResponse(result, 500);
    }
  } catch (error) {
    Logger.log('Error in deletePhotoHandler: ' + error.toString());
    return createResponse({ 
      success: false,
      error: error.toString() 
    }, 500);
  }
}

/**
 * Handle get photo URL
 */
function getPhotoUrlHandler(data) {
  try {
    const { fileId } = data;
    
    if (!fileId) {
      return createResponse({ 
        success: false,
        error: 'Missing fileId' 
      }, 400);
    }
    
    const result = getImageUrl(fileId);
    return createResponse(result, result.success ? 200 : 500);
  } catch (error) {
    Logger.log('Error in getPhotoUrlHandler: ' + error.toString());
    return createResponse({ 
      success: false,
      error: error.toString() 
    }, 500);
  }
}

/**
 * Handle GET request for photo proxy (serves image directly as binary)
 * URL format: /exec?path=photo&fileId={fileId}&ownerEmail={ownerEmail}
 */
function handlePhotoGet(e) {
  try {
    const fileId = e.parameter.fileId;
    const ownerEmail = e.parameter.ownerEmail;
    
    if (!fileId) {
      return ContentService.createTextOutput(JSON.stringify({ 
        success: false,
        error: 'Missing fileId' 
      })).setMimeType(ContentService.MimeType.JSON);
    }
    
    if (!ownerEmail) {
      return ContentService.createTextOutput(JSON.stringify({ 
        success: false,
        error: 'Missing ownerEmail' 
      })).setMimeType(ContentService.MimeType.JSON);
    }
    
    Logger.log('handlePhotoGet: Fetching image fileId=' + fileId + ', ownerEmail=' + ownerEmail);
    
    // Get access token for the owner's Drive
    const userProps = PropertiesService.getUserProperties();
    const accessTokenKey = 'ACCESS_TOKEN_' + ownerEmail;
    const accessToken = userProps.getProperty(accessTokenKey);
    
    if (!accessToken) {
      Logger.log('ERROR: No access token found for owner: ' + ownerEmail);
      return ContentService.createTextOutput(JSON.stringify({
        success: false,
        error: 'Drive access not authorized'
      })).setMimeType(ContentService.MimeType.JSON);
    }
    
    // Fetch image from Drive API v3 using alt=media
    const downloadUrl = 'https://www.googleapis.com/drive/v3/files/' + fileId + '?alt=media';
    const options = {
      method: 'get',
      headers: {
        'Authorization': 'Bearer ' + accessToken
      },
      muteHttpExceptions: true
    };
    
    Logger.log('handlePhotoGet: Fetching from Drive API...');
    const httpResponse = UrlFetchApp.fetch(downloadUrl, options);
    const responseCode = httpResponse.getResponseCode();
    
    if (responseCode !== 200) {
      Logger.log('ERROR: Drive API fetch failed: ' + responseCode);
      Logger.log('Response: ' + httpResponse.getContentText());
      return ContentService.createTextOutput(JSON.stringify({
        success: false,
        error: 'Failed to fetch image: ' + responseCode
      })).setMimeType(ContentService.MimeType.JSON);
    }
    
    // Get image data as blob
    const imageBlob = httpResponse.getBlob();
    const mimeType = imageBlob.getContentType() || 'image/jpeg';
    const imageBytes = imageBlob.getBytes();
    
    Logger.log('handlePhotoGet: Image fetched successfully, size=' + imageBytes.length + ', mimeType=' + mimeType);
    
    // Convert to base64 and return as data URI
    // Apps Script web apps can't return binary blobs directly, so we use base64 encoding
    const base64Data = Utilities.base64Encode(imageBytes);
    const dataUri = 'data:' + mimeType + ';base64,' + base64Data;
    
    // Return as HTML with the image as a data URI
    // Coil can handle data URIs directly
    return ContentService.createTextOutput(dataUri)
      .setMimeType(ContentService.MimeType.TEXT);
    
  } catch (error) {
    Logger.log('Error in handlePhotoGet: ' + error.toString());
    Logger.log('Error stack: ' + (error.stack || 'no stack'));
    return ContentService.createTextOutput(JSON.stringify({ 
      success: false,
      error: error.toString() 
    })).setMimeType(ContentService.MimeType.JSON);
  }
}

/**
 * Handle activity log GET requests
 */
function handleActivityLogsRequest(e) {
  try {
    const options = {
      familyId: e.parameter.familyId || null,
      userId: e.parameter.userId || null,
      actionType: e.parameter.actionType || null,
      startDate: e.parameter.startDate || null,
      endDate: e.parameter.endDate || null,
      page: parseInt(e.parameter.page || '1'),
      pageSize: parseInt(e.parameter.pageSize || '50')
    };
    
    const result = getActivityLogs(options);
    
    if (result.success) {
      return createResponse(result, 200);
    } else {
      return createResponse({ error: result.error }, 500);
    }
  } catch (error) {
    Logger.log('Error in handleActivityLogsRequest: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}

/**
 * Test function for development
 */
function testInitialize() {
  const result = initializeFamilyData('test@example.com');
  Logger.log(JSON.stringify(result, null, 2));
}
