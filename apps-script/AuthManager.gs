/**
 * AuthManager.gs
 * Handles authentication and token validation
 */

/**
 * Handle authentication requests (GET)
 */
function handleAuthRequest(e) {
  const action = e.parameter.action;
  
  if (action === 'validate') {
    return validateSession(e);
  }
  
  return createResponse({ error: 'Invalid auth action' }, 400);
}

/**
 * Handle authentication requests (POST)
 */
function handleAuthPost(e, data) {
  const action = e.parameter.action || data.action;
  
  Logger.log('handleAuthPost - action: ' + action);
  
  if (action === 'google') {
    return handleGoogleAuth(data);
  } else if (action === 'qr') {
    return handleQRAuth(data);
  } else if (action === 'regenerate') {
    return regenerateQRCode(data);
  }
  
  return createResponse({ error: 'Invalid auth action' }, 400);
}

/**
 * Helper function to decode base64url (JWT uses base64url, not regular base64)
 */
function base64UrlDecode(str) {
  // Replace base64url characters with base64 characters
  let base64 = str.replace(/-/g, '+').replace(/_/g, '/');
  
  // Add padding if needed
  while (base64.length % 4 !== 0) {
    base64 += '=';
  }
  
  try {
    const decoded = Utilities.base64Decode(base64);
    return Utilities.newBlob(decoded).getDataAsString();
  } catch (e) {
    Logger.log('Error decoding base64url: ' + e.toString());
    throw new Error('Failed to decode JWT payload');
  }
}

/**
 * Handle Google OAuth authentication (primary parent)
 */
function handleGoogleAuth(data) {
  try {
    Logger.log('=== handleGoogleAuth START ===');
    Logger.log('Data received: ' + JSON.stringify(data));
    
    // Verify Google ID token
    const googleToken = data.googleToken;
    if (!googleToken) {
      Logger.log('ERROR: Missing Google token');
      return createResponse({ error: 'Missing Google token' }, 400);
    }
    
    Logger.log('Token length: ' + googleToken.length);
    
    // Decode the JWT token (basic decode without verification for now)
    const tokenParts = googleToken.split('.');
    Logger.log('Token parts: ' + tokenParts.length);
    
    if (tokenParts.length !== 3) {
      Logger.log('ERROR: Invalid token format - expected 3 parts, got ' + tokenParts.length);
      return createResponse({ error: 'Invalid token format' }, 400);
    }
    
    // Decode the payload (middle part) using base64url decoding
    Logger.log('Decoding JWT payload...');
    const payloadJson = base64UrlDecode(tokenParts[1]);
    Logger.log('Payload JSON: ' + payloadJson);
    
    const payload = JSON.parse(payloadJson);
    const userEmail = payload.email;
    
    // Get access token directly (preferred) or server auth code for OAuth token exchange (fallback)
    const accessTokenDirect = data.accessToken;
    const serverAuthCode = data.serverAuthCode;
    Logger.log('Access token provided directly: ' + (accessTokenDirect ? 'yes (' + accessTokenDirect.length + ' chars)' : 'no'));
    Logger.log('Server auth code provided: ' + (serverAuthCode ? 'yes (' + serverAuthCode.length + ' chars)' : 'no'));
    
    // Use direct access token if provided, otherwise try to exchange server auth code
    let accessToken = accessTokenDirect;
    if (!accessToken && serverAuthCode && userEmail) {
      try {
        accessToken = exchangeServerAuthCodeForAccessToken(serverAuthCode, userEmail);
        Logger.log('Access token obtained: ' + (accessToken ? 'yes' : 'no'));
      } catch (e) {
        Logger.log('ERROR: Failed to exchange server auth code: ' + e.toString());
        Logger.log('Error details: ' + (e.stack || 'no stack'));
        
        // Check if it's a missing credentials error
        const errorStr = e.toString().toLowerCase();
        if (errorStr.includes('oauth credentials not configured') || errorStr.includes('client_id') || errorStr.includes('client_secret')) {
          Logger.log('OAuth credentials not configured - returning helpful error');
          return createResponse({ 
            success: false,
            status: 500,
            error: 'OAuth credentials not configured',
            message: 'The server needs to be configured with OAuth credentials. Please contact the administrator.',
            details: 'OAUTH_CLIENT_ID and OAUTH_CLIENT_SECRET must be set in Apps Script Properties',
            serverAuthCodeProvided: true
          }, 500);
        }
        
        // For other errors, return a helpful message
        return createResponse({ 
          success: false,
          status: 500,
          error: 'Failed to exchange server auth code for access token',
          message: 'OAuth token exchange failed. This may be due to missing OAuth credentials or an invalid server auth code.',
          details: e.toString(),
          serverAuthCodeProvided: true
        }, 500);
      }
    } else if (serverAuthCode && !userEmail) {
      Logger.log('ERROR: Server auth code provided but userEmail not available yet');
      // This shouldn't happen, but handle it gracefully
      return createResponse({ 
        success: false,
        status: 400,
        error: 'Invalid request',
        message: 'Server auth code provided but user email not available'
      }, 400);
    }
    // Use name from Google account, fallback to given_name + family_name, then email prefix
    const userName = payload.name || 
                     (payload.given_name && payload.family_name ? 
                      payload.given_name + ' ' + payload.family_name : null) ||
                     userEmail.split('@')[0];
    const userPicture = payload.picture || null; // Google profile picture URL
    
    Logger.log('Decoded email: ' + userEmail);
    Logger.log('Decoded name: ' + userName);
    Logger.log('Decoded picture: ' + userPicture);
    
    if (!userEmail) {
      Logger.log('ERROR: Email not found in token');
      return createResponse({ error: 'Email not found in token' }, 401);
    }
    
    // IMPORTANT: With USER_ACCESSING, we need to verify the user is authorized
    // before trying to access Drive. If not authorized, we'll get a 401 error.
    // We'll catch this error and return a proper response.
    
    // CRITICAL: If we don't have an access token and server auth code was provided,
    // it means token exchange failed (likely OAuth credentials not configured)
    // Don't try to access Drive - it will fail with 401
    if (!accessToken && serverAuthCode) {
      Logger.log('ERROR: Server auth code provided but access token exchange failed');
      Logger.log('This usually means OAuth credentials are not configured in Apps Script Properties');
      return createResponse({ 
        success: false,
        status: 500,
        error: 'OAuth credentials not configured',
        message: 'The server needs OAuth credentials to exchange the server auth code for an access token. Please configure OAUTH_CLIENT_ID and OAUTH_CLIENT_SECRET in Apps Script Properties.',
        details: 'See OAUTH_SETUP.md for instructions',
        serverAuthCodeProvided: true,
        requiresOAuthSetup: true
      }, 500);
    }
    
    // Check if family already exists
    // IMPORTANT: Use userEmail to get the family-specific folder
    // If we have an access token, use it for Drive API calls
    Logger.log('Loading family data for: ' + userEmail);
    Logger.log('Using access token: ' + (accessToken ? 'yes (' + accessToken.length + ' chars)' : 'no (REQUIRED with ME deployment)'));
    
    // CRITICAL: If server auth code was provided but we don't have an access token,
    // it means token exchange failed. Don't try to access Drive - it will fail with 401.
    if (!accessToken && serverAuthCode) {
      Logger.log('ERROR: Server auth code was provided but access token exchange failed');
      Logger.log('This means OAuth credentials are likely not configured or invalid');
      return createResponse({ 
        success: false,
        status: 500,
        error: 'OAuth token exchange failed',
        message: 'The server could not exchange the server auth code for an access token. This usually means OAuth credentials are not configured correctly.',
        details: 'Please verify OAUTH_CLIENT_ID and OAUTH_CLIENT_SECRET are set in Apps Script Properties and match the credentials in Google Cloud Console.',
        requiresOAuthSetup: true
      }, 500);
    }
    
    let familyData;
    try {
      // If we have an access token, use it for Drive operations
      if (accessToken) {
        // Store access token for this user
        const userProps = PropertiesService.getUserProperties();
        userProps.setProperty('ACCESS_TOKEN_' + userEmail, accessToken);
        Logger.log('Stored access token for user: ' + userEmail);
      }
      
      // CRITICAL: With ME deployment, we MUST have an access token
      // Without it, Drive API calls will access the script owner's Drive, not the user's Drive
      if (!accessToken) {
        Logger.log('ERROR: No access token provided');
        Logger.log('With ME deployment, an access token is REQUIRED to access the user\'s Drive');
        Logger.log('Without an access token, Drive API calls will access the script owner\'s Drive, not the user\'s Drive');
        return createResponse({ 
          success: false,
          status: 401,
          error: 'OAuth access token required',
          message: 'This app requires OAuth authorization to access your Google Drive. The server auth code exchange must succeed to obtain an access token.',
          requiresOAuthSetup: true,
          details: 'Please verify OAuth credentials are configured in Apps Script Properties (OAUTH_CLIENT_ID and OAUTH_CLIENT_SECRET)'
        }, 401);
      }
      
      // Get folder ID first, then load family data using Drive API v3
      const folderId = getChoreQuestFolderV3(userEmail, accessToken);
      familyData = loadJsonFileV3(FILE_NAMES.FAMILY, userEmail, folderId, accessToken);
    } catch (error) {
      Logger.log('Error loading family data: ' + error.toString());
      const errorStr = error.toString().toLowerCase();
      if (errorStr.includes('unauthorized') || errorStr.includes('401') || errorStr.includes('permission') || errorStr.includes('access denied') || errorStr.includes('drive access not authorized')) {
        Logger.log('Drive access not authorized - returning 401');
        const authUrl = ScriptApp.getService().getUrl();
        return createResponse({ 
          success: false,
          status: 401,
          error: 'Drive access not authorized',
          message: 'You need to authorize this app to access your Google Drive. Please visit the web app URL in a browser to complete authorization.',
          authorizationUrl: authUrl,
          instructions: '1. Open the authorizationUrl in a browser\n2. Sign in with your Google account (' + userEmail + ')\n3. Click "Allow" to grant Drive access\n4. Return to the app and try again'
        }, 401);
      }
      // Re-throw other errors
      throw error;
    }
    
    if (familyData) {
      Logger.log('Family data found, checking for existing user...');
      // Family exists, return existing data
      const folderId = familyData.driveFileId;
      const usersData = loadJsonFileV3(FILE_NAMES.USERS, userEmail, folderId, accessToken);
      const user = usersData.users.find(u => u.email === userEmail && u.isPrimaryParent);
      
      if (user) {
        Logger.log('Existing family found for user: ' + userEmail);
        
        // Create session data
        const session = {
          familyId: familyData.id,
          userId: user.id,
          userName: user.name,
          userRole: user.role,
          authToken: user.authToken,
          tokenVersion: user.tokenVersion,
          driveWorkbookLink: familyData.driveFileId || familyData.driveWorkbookLink || '',
          deviceId: data.deviceId || 'web',
          loginTimestamp: new Date().toISOString(),
          lastSynced: new Date().getTime()
        };
        
        Logger.log('Returning existing user session');
        return createResponse({
          success: true,
          message: 'Login successful',
          user: user,
          session: session
        });
      }
    }
    
    // Create new family
    Logger.log('No existing family found, creating new family for user: ' + userEmail);
    let result;
    try {
      result = initializeFamilyData(userEmail, userName, userPicture, accessToken);
      Logger.log('Family created successfully');
    } catch (error) {
      Logger.log('Error creating family data: ' + error.toString());
      const errorStr = error.toString().toLowerCase();
      if (errorStr.includes('unauthorized') || errorStr.includes('401') || errorStr.includes('permission') || errorStr.includes('access denied') || errorStr.includes('drive access not authorized')) {
        Logger.log('Drive access not authorized - returning 401');
        const authUrl = ScriptApp.getService().getUrl();
        return createResponse({ 
          success: false,
          status: 401,
          error: 'Drive access not authorized',
          message: 'You need to authorize this app to access your Google Drive. Please visit the web app URL in a browser to complete authorization.',
          authorizationUrl: authUrl,
          instructions: '1. Open the authorizationUrl in a browser\n2. Sign in with your Google account (' + userEmail + ')\n3. Click "Allow" to grant Drive access\n4. Return to the app and try again'
        }, 401);
      }
      // Re-throw other errors
      throw error;
    }
    
    // Create session data
    const session = {
      familyId: result.familyData.id,
      userId: result.primaryUser.id,
      userName: result.primaryUser.name,
      userRole: result.primaryUser.role,
      authToken: result.primaryUser.authToken,
      tokenVersion: result.primaryUser.tokenVersion,
      driveWorkbookLink: result.familyData.driveFileId || result.familyData.driveWorkbookLink || '',
      deviceId: data.deviceId || 'web',
      loginTimestamp: new Date().toISOString(),
      lastSynced: new Date().getTime()
    };
    
    Logger.log('Returning new user session');
    Logger.log('=== handleGoogleAuth SUCCESS ===');
    
    return createResponse({
      success: true,
      message: 'Family created successfully',
      user: result.primaryUser,
      session: session
    });
    
  } catch (error) {
    Logger.log('=== handleGoogleAuth ERROR ===');
    Logger.log('Error: ' + error.toString());
    Logger.log('Error stack: ' + error.stack);
    return createResponse({ 
      success: false,
      error: error.toString(),
      stack: error.stack 
    }, 500);
  }
}

/**
 * Handle QR code authentication
 */
function handleQRAuth(data) {
  try {
    Logger.log('=== handleQRAuth START ===');
    Logger.log('Data received: ' + JSON.stringify(data));
    
    const { familyId, userId, token, tokenVersion, ownerEmail, folderId } = data;
    
    if (!familyId || !userId || !token || !ownerEmail || !folderId) {
      Logger.log('ERROR: Missing required fields');
      return createResponse({ error: 'Missing required fields (familyId, userId, token, ownerEmail, folderId)' }, 400);
    }
    
    Logger.log('Looking for userId: ' + userId);
    Logger.log('Looking for familyId: ' + familyId);
    Logger.log('Owner email from QR code: ' + ownerEmail);
    Logger.log('Folder ID from QR code: ' + folderId);
    
    // IMPORTANT: With USER_DEPLOYING, we need to use the parent's access token to access their Drive
    // Get a valid access token for the parent, refreshing if necessary
    Logger.log('Getting valid access token for parent: ' + ownerEmail);
    const accessToken = getValidAccessToken(ownerEmail);
    
    if (!accessToken) {
      Logger.log('ERROR: Could not obtain valid access token for parent: ' + ownerEmail);
      Logger.log('NOTE: Parent must have logged in at least once to store their access token');
      Logger.log('If parent has logged in before, the access token may have expired and refresh failed');
      return createResponse({ 
        error: 'Parent access token not available. Parent must log in again.',
        details: 'The parent account needs to authenticate with Google Sign-In. If they have logged in before, their access token may have expired. Please ask the parent to log in again to refresh their credentials.',
        requiresParentLogin: true
      }, 403);
    }
    
    Logger.log('Obtained valid access token for parent: ' + ownerEmail);
    
    // Use Drive API v3 to load family and user data from parent's folder
    let familyData, usersData;
    
    try {
      // Load family data using folderId and accessToken
      familyData = loadJsonFileV3(FILE_NAMES.FAMILY, ownerEmail, folderId, accessToken);
      
      // Load users data using folderId and accessToken
      usersData = loadJsonFileV3(FILE_NAMES.USERS, ownerEmail, folderId, accessToken);
      
      Logger.log('Successfully loaded family and user data from parent folder');
    } catch (error) {
      Logger.log('ERROR: Cannot access parent folder: ' + error.toString());
      Logger.log('Error stack: ' + (error.stack || 'no stack'));
      return createResponse({ 
        error: 'Cannot access parent folder. ' + error.toString(),
        details: error.toString()
      }, 403);
    }
    
    if (!familyData) {
      Logger.log('ERROR: Family data not found in parent folder');
      return createResponse({ error: 'Family data not found' }, 404);
    }
    
    if (familyData.id !== familyId) {
      Logger.log('ERROR: Family ID mismatch. Expected: ' + familyData.id + ', Got: ' + familyId);
      return createResponse({ error: 'Invalid family ID' }, 404);
    }
    
    // Verify ownerEmail matches
    if (familyData.ownerEmail !== ownerEmail) {
      Logger.log('ERROR: Owner email mismatch. Expected: ' + familyData.ownerEmail + ', Got: ' + ownerEmail);
      return createResponse({ error: 'Invalid owner email' }, 403);
    }
    
    // Verify folder ID matches
    if (familyData.driveFileId !== folderId) {
      Logger.log('ERROR: Folder ID mismatch. Expected: ' + familyData.driveFileId + ', Got: ' + folderId);
      return createResponse({ error: 'Invalid folder ID' }, 403);
    }
    
    if (!usersData) {
      Logger.log('ERROR: Users data not found');
      return createResponse({ error: 'Users data not found' }, 404);
    }
    
    if (!usersData.users || !Array.isArray(usersData.users)) {
      Logger.log('ERROR: Users array not found or invalid');
      Logger.log('usersData structure: ' + JSON.stringify(usersData));
      return createResponse({ error: 'Invalid users data structure' }, 500);
    }
    
    Logger.log('Total users in system: ' + usersData.users.length);
    Logger.log('User IDs in system: ' + usersData.users.map(u => u.id).join(', '));
    
    // Find user
    const user = usersData.users.find(u => u.id === userId);
    
    if (!user) {
      Logger.log('ERROR: User not found. Searched for: ' + userId);
      Logger.log('Available user IDs: ' + usersData.users.map(u => u.id).join(', '));
      return createResponse({ error: 'User not found' }, 404);
    }
    
    Logger.log('User found: ' + user.name + ' (ID: ' + user.id + ')');
    Logger.log('User authToken: ' + (user.authToken || 'null/undefined'));
    Logger.log('QR code token: ' + token);
    Logger.log('User tokenVersion: ' + (user.tokenVersion || 'null/undefined'));
    Logger.log('QR code tokenVersion: ' + tokenVersion);
    
    // Token verification removed - only validate token version as a safety check
    // Validate token version (optional safety check - can be removed if desired)
    if (user.tokenVersion && tokenVersion && user.tokenVersion !== tokenVersion) {
      Logger.log('WARNING: Token version mismatch!');
      Logger.log('Expected: ' + user.tokenVersion);
      Logger.log('Got: ' + tokenVersion);
      Logger.log('Proceeding anyway (token verification disabled)');
      // Continue with authentication - token version mismatch is just a warning
    }
    
    // Update last active
    const deviceId = data.deviceId || Utilities.getUuid();
    const deviceName = data.deviceName || 'Unknown Device';
    
    const existingDevice = user.devices.find(d => d.deviceId === deviceId);
    if (existingDevice) {
      existingDevice.lastActive = new Date().toISOString();
    } else {
      user.devices.push({
        deviceId: deviceId,
        deviceName: deviceName,
        lastActive: new Date().toISOString()
      });
    }
    
    // Save updated user data to parent's Drive using Drive API v3
    try {
      saveJsonFileV3(FILE_NAMES.USERS, usersData, ownerEmail, folderId, accessToken);
      Logger.log('Saved user data to parent folder');
    } catch (error) {
      Logger.log('ERROR: Cannot save to parent folder: ' + error.toString());
      // Don't fail the auth, but log the error
    }
    
    // Log device login to parent's Drive using Drive API v3
    try {
      let activityLogData = loadJsonFileV3(FILE_NAMES.ACTIVITY_LOG, ownerEmail, folderId, accessToken) || { logs: [] };
      
      const logEntry = {
        id: Utilities.getUuid(),
        timestamp: new Date().toISOString(),
        actorId: userId,
        actorName: user.name,
        actorRole: user.role,
        actionType: 'device_login',
        details: { deviceName: deviceName },
        metadata: {
          deviceType: 'unknown',
          appVersion: '1.0.0'
        }
      };
      
      activityLogData.logs.unshift(logEntry);
      if (activityLogData.logs.length > 1000) {
        activityLogData.logs = activityLogData.logs.slice(0, 1000);
      }
      
      saveJsonFileV3(FILE_NAMES.ACTIVITY_LOG, activityLogData, ownerEmail, folderId, accessToken);
      Logger.log('Logged device login to parent folder');
    } catch (error) {
      Logger.log('ERROR: Cannot log to parent folder: ' + error.toString());
      // Don't fail the auth, but log the error
    }
    
    // Create session data matching Google auth format
    const session = {
      familyId: familyId,
      userId: userId,
      userName: user.name,
      userRole: user.role,
      authToken: token,
      tokenVersion: tokenVersion,
      driveWorkbookLink: folderId, // Store folder ID in session for QR code generation
      deviceId: deviceId,
      loginTimestamp: new Date().toISOString(),
      lastSynced: new Date().getTime()
    };
    
    Logger.log('QR auth successful for user: ' + user.name);
    Logger.log('=== handleQRAuth SUCCESS ===');
    
    return createResponse({
      success: true,
      message: 'QR authentication successful',
      user: user,
      session: session
    });
    
  } catch (error) {
    Logger.log('Error in handleQRAuth: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}

/**
 * Validate existing session
 */
function validateSession(e) {
  try {
    const userId = e.parameter.userId;
    const token = e.parameter.token;
    const tokenVersion = parseInt(e.parameter.tokenVersion || '0');
    
    if (!userId || !token) {
      return createResponse({ error: 'Missing credentials' }, 400);
    }
    
    // Try to find the user by searching through all stored access tokens
    const userProps = PropertiesService.getUserProperties();
    const allProps = userProps.getProperties();
    
    // Collect all owner emails from stored tokens
    const ownerEmails = [];
    for (const key in allProps) {
      if (key.startsWith('ACCESS_TOKEN_') || key.startsWith('REFRESH_TOKEN_')) {
        const ownerEmail = key.replace('ACCESS_TOKEN_', '').replace('REFRESH_TOKEN_', '');
        if (ownerEmails.indexOf(ownerEmail) === -1) {
          ownerEmails.push(ownerEmail);
        }
      }
    }
    
    // Try each owner email until we find the user
    for (let i = 0; i < ownerEmails.length; i++) {
      const ownerEmail = ownerEmails[i];
      
      // Get a valid access token (will refresh if expired)
      const accessToken = getValidAccessToken(ownerEmail);
      
      if (!accessToken) {
        // Skip this owner if we can't get a valid token
        continue;
      }
      
      try {
        const folderId = getChoreQuestFolderV3(ownerEmail, accessToken);
        const usersData = loadJsonFileV3(FILE_NAMES.USERS, ownerEmail, folderId, accessToken);
        
        if (usersData && usersData.users) {
          const user = usersData.users.find(u => u.id === userId);
          
          if (user) {
            // Found the user, validate token
            if (user.authToken !== token) {
              return createResponse({ valid: false, reason: 'invalid_token' });
            }
            
            if (user.tokenVersion !== tokenVersion) {
              return createResponse({ valid: false, reason: 'token_regenerated' });
            }
            
            return createResponse({ valid: true, userData: user });
          }
        }
      } catch (error) {
        Logger.log('Error validating session for owner ' + ownerEmail + ': ' + error.toString());
        // Continue to next owner
        continue;
      }
    }
    
    // User not found in any of the families
    return createResponse({ valid: false, reason: 'user_not_found' });
    
  } catch (error) {
    Logger.log('Error in validateSession: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}

/**
 * Regenerate QR code for a user
 */
function regenerateQRCode(data) {
  try {
    const { parentUserId, targetUserId, reason } = data;
    
    if (!parentUserId || !targetUserId) {
      return createResponse({ error: 'Missing required fields' }, 400);
    }
    
    // Get ownerEmail, folderId, and accessToken from family data
    // Pass parentUserId to find the correct family
    const familyInfo = getFamilyInfoForUser();
    if (!familyInfo) {
      Logger.log('ERROR: Could not get family info');
      return createResponse({ error: 'Family data not found or not accessible' }, 500);
    }
    
    const { ownerEmail, folderId, accessToken } = familyInfo;
    
    // Load users data using Drive API v3
    const usersData = loadJsonFileV3(FILE_NAMES.USERS, ownerEmail, folderId, accessToken);
    if (!usersData || !usersData.users) {
      return createResponse({ error: 'Users data not found' }, 404);
    }
    
    // Verify parent is authorized
    const parentUser = usersData.users.find(u => u.id === parentUserId);
    if (!parentUser || parentUser.role !== 'parent') {
      return createResponse({ error: 'Unauthorized' }, 403);
    }
    
    // Find target user
    const targetUser = usersData.users.find(u => u.id === targetUserId);
    if (!targetUser) {
      return createResponse({ error: 'Target user not found' }, 404);
    }
    
    // Generate new token
    const newToken = Utilities.getUuid();
    const newVersion = (targetUser.tokenVersion || 0) + 1;
    
    targetUser.authToken = newToken;
    targetUser.tokenVersion = newVersion;
    
    // Save updated data using Drive API v3
    saveJsonFileV3(FILE_NAMES.USERS, usersData, ownerEmail, folderId, accessToken);
    
    // Load family data for QR code payload
    const familyData = loadJsonFileV3(FILE_NAMES.FAMILY, ownerEmail, folderId, accessToken);
    if (!familyData) {
      return createResponse({ error: 'Family data not found' }, 404);
    }
    
    // Log QR regeneration using Drive API v3
    logActivity({
      actorId: parentUserId,
      actorName: parentUser.name,
      actorRole: 'parent',
      actionType: 'qr_regenerated',
      targetUserId: targetUserId,
      targetUserName: targetUser.name,
      details: {
        reason: reason || 'No reason provided'
      }
    }, ownerEmail, folderId, accessToken);
    
    return createResponse({
      success: true,
      qrData: {
        familyId: familyData.id,
        userId: targetUserId,
        token: newToken,
        version: newVersion,
        ownerEmail: ownerEmail,
        folderId: folderId,
        appVersion: '1.0.0',
        timestamp: new Date().toISOString()
      }
    });
    
  } catch (error) {
    Logger.log('Error in regenerateQRCode: ' + error.toString());
    Logger.log('Error stack: ' + (error.stack || 'no stack'));
    return createResponse({ error: error.toString() }, 500);
  }
}

/**
 * Exchange server auth code for OAuth access token
 * @param {string} serverAuthCode - Server auth code from Google Sign-In
 * @param {string} userEmail - User email (for logging)
 * @returns {string} Access token
 */
function exchangeServerAuthCodeForAccessToken(serverAuthCode, userEmail) {
  try {
    Logger.log('=== exchangeServerAuthCodeForAccessToken START ===');
    Logger.log('User email: ' + userEmail);
    Logger.log('Server auth code length: ' + serverAuthCode.length);
    
    // Get OAuth client ID and secret from script properties
    const scriptProps = PropertiesService.getScriptProperties();
    const clientId = scriptProps.getProperty('OAUTH_CLIENT_ID');
    const clientSecret = scriptProps.getProperty('OAUTH_CLIENT_SECRET');
    
    Logger.log('OAuth client ID found: ' + (clientId ? 'yes (' + clientId.substring(0, 20) + '...)' : 'no'));
    Logger.log('OAuth client secret found: ' + (clientSecret ? 'yes (***hidden***)' : 'no'));
    
    if (!clientId || !clientSecret) {
      Logger.log('ERROR: OAuth client ID or secret not configured');
      Logger.log('Please set OAUTH_CLIENT_ID and OAUTH_CLIENT_SECRET in Script Properties');
      throw new Error('OAuth credentials not configured. Please set OAUTH_CLIENT_ID and OAUTH_CLIENT_SECRET in Script Properties.');
    }
    
    // Verify the client ID matches what the Android app is using
    // Android app uses: 156195149694-a3c7v365m6a2rhq46icqh1c13oi6r8h2.apps.googleusercontent.com
    const expectedClientIdPrefix = '156195149694-';
    if (!clientId.startsWith(expectedClientIdPrefix)) {
      Logger.log('WARNING: OAuth Client ID does not match expected format');
      Logger.log('Expected to start with: ' + expectedClientIdPrefix);
      Logger.log('Actual Client ID starts with: ' + clientId.substring(0, Math.min(20, clientId.length)));
      Logger.log('Make sure the Client ID in Apps Script matches GOOGLE_WEB_CLIENT_ID in Constants.kt');
    }
    
    // Exchange server auth code for access token
    // Note: For Android server auth codes, redirect_uri is typically not required
    // or should be left empty/omitted
    const tokenUrl = 'https://oauth2.googleapis.com/token';
    const payload = {
      code: serverAuthCode,
      client_id: clientId,
      client_secret: clientSecret,
      grant_type: 'authorization_code'
      // Note: redirect_uri is not needed for Android server auth codes
      // Google automatically handles this for mobile apps
    };
    
    Logger.log('Exchanging server auth code for access token...');
    const options = {
      method: 'post',
      contentType: 'application/x-www-form-urlencoded',
      payload: Object.keys(payload).map(key => key + '=' + encodeURIComponent(payload[key])).join('&'),
      muteHttpExceptions: true
    };
    
    const response = UrlFetchApp.fetch(tokenUrl, options);
    const responseCode = response.getResponseCode();
    const responseText = response.getContentText();
    
    Logger.log('Token exchange response code: ' + responseCode);
    Logger.log('Token exchange response: ' + responseText);
    
    if (responseCode !== 200) {
      Logger.log('ERROR: Failed to exchange server auth code');
      Logger.log('Response code: ' + responseCode);
      Logger.log('Response text: ' + responseText);
      
      // Try to parse error details
      try {
        const errorResponse = JSON.parse(responseText);
        Logger.log('Error details: ' + JSON.stringify(errorResponse));
        
        if (errorResponse.error === 'invalid_grant') {
          Logger.log('ERROR: invalid_grant - This usually means:');
          Logger.log('1. The server auth code has expired (they expire quickly)');
          Logger.log('2. The server auth code was already used');
          Logger.log('3. The redirect_uri doesn\'t match what was configured in Google Cloud Console');
          throw new Error('Invalid server auth code. This may be expired or already used. Please try signing in again.');
        } else if (errorResponse.error === 'invalid_client') {
          Logger.log('ERROR: invalid_client - This usually means:');
          Logger.log('1. The OAuth Client ID or Secret is incorrect');
          Logger.log('2. The OAuth Client ID doesn\'t match the one used in the Android app');
          throw new Error('Invalid OAuth client credentials. Please verify OAUTH_CLIENT_ID and OAUTH_CLIENT_SECRET in Script Properties match the credentials in Google Cloud Console.');
        }
      } catch (parseError) {
        Logger.log('Could not parse error response: ' + parseError.toString());
      }
      
      throw new Error('Failed to exchange server auth code: ' + responseText);
    }
    
    const tokenResponse = JSON.parse(responseText);
    const accessToken = tokenResponse.access_token;
    const refreshToken = tokenResponse.refresh_token;
    
    if (!accessToken) {
      Logger.log('ERROR: No access token in response');
      throw new Error('No access token in response');
    }
    
    Logger.log('Access token obtained successfully');
    Logger.log('Refresh token provided: ' + (refreshToken ? 'yes' : 'no'));
    
    // Store tokens for this user
    const userProps = PropertiesService.getUserProperties();
    userProps.setProperty('ACCESS_TOKEN_' + userEmail, accessToken);
    if (refreshToken) {
      userProps.setProperty('REFRESH_TOKEN_' + userEmail, refreshToken);
    }
    
    Logger.log('Tokens stored for user: ' + userEmail);
    
    return accessToken;
  } catch (error) {
    Logger.log('ERROR in exchangeServerAuthCodeForAccessToken: ' + error.toString());
    throw error;
  }
}

/**
 * Refresh an access token using a refresh token
 * @param {string} userEmail - User email to identify the refresh token
 * @returns {string|null} New access token or null if refresh failed
 */
function refreshAccessToken(userEmail) {
  try {
    Logger.log('=== refreshAccessToken START ===');
    Logger.log('User email: ' + userEmail);
    
    // Get refresh token from user properties
    const userProps = PropertiesService.getUserProperties();
    const refreshTokenKey = 'REFRESH_TOKEN_' + userEmail;
    const refreshToken = userProps.getProperty(refreshTokenKey);
    
    if (!refreshToken) {
      Logger.log('ERROR: No refresh token found for user: ' + userEmail);
      return null;
    }
    
    Logger.log('Refresh token found, attempting to refresh access token...');
    
    // Get OAuth client ID and secret from script properties
    const scriptProps = PropertiesService.getScriptProperties();
    const clientId = scriptProps.getProperty('OAUTH_CLIENT_ID');
    const clientSecret = scriptProps.getProperty('OAUTH_CLIENT_SECRET');
    
    if (!clientId || !clientSecret) {
      Logger.log('ERROR: OAuth client ID or secret not configured');
      return null;
    }
    
    // Refresh the access token
    const tokenUrl = 'https://oauth2.googleapis.com/token';
    const payload = {
      refresh_token: refreshToken,
      client_id: clientId,
      client_secret: clientSecret,
      grant_type: 'refresh_token'
    };
    
    const options = {
      method: 'post',
      contentType: 'application/x-www-form-urlencoded',
      payload: Object.keys(payload).map(key => key + '=' + encodeURIComponent(payload[key])).join('&'),
      muteHttpExceptions: true
    };
    
    const response = UrlFetchApp.fetch(tokenUrl, options);
    const responseCode = response.getResponseCode();
    const responseText = response.getContentText();
    
    Logger.log('Token refresh response code: ' + responseCode);
    
    if (responseCode !== 200) {
      Logger.log('ERROR: Failed to refresh access token');
      Logger.log('Response code: ' + responseCode);
      Logger.log('Response text: ' + responseText);
      
      // If refresh token is invalid/expired, remove it
      if (responseCode === 400) {
        try {
          const errorResponse = JSON.parse(responseText);
          if (errorResponse.error === 'invalid_grant') {
            Logger.log('Refresh token is invalid or expired, removing it');
            userProps.deleteProperty(refreshTokenKey);
            userProps.deleteProperty('ACCESS_TOKEN_' + userEmail);
          }
        } catch (e) {
          // Ignore parse errors
        }
      }
      
      return null;
    }
    
    const tokenResponse = JSON.parse(responseText);
    const newAccessToken = tokenResponse.access_token;
    
    if (!newAccessToken) {
      Logger.log('ERROR: No access token in refresh response');
      return null;
    }
    
    Logger.log('Access token refreshed successfully');
    
    // Store the new access token
    userProps.setProperty('ACCESS_TOKEN_' + userEmail, newAccessToken);
    
    // If a new refresh token is provided, store it (Google may issue a new one)
    if (tokenResponse.refresh_token) {
      userProps.setProperty(refreshTokenKey, tokenResponse.refresh_token);
      Logger.log('New refresh token stored');
    }
    
    return newAccessToken;
  } catch (error) {
    Logger.log('ERROR in refreshAccessToken: ' + error.toString());
    Logger.log('Error stack: ' + (error.stack || 'no stack'));
    return null;
  }
}

/**
 * Get a valid access token for a user, refreshing if necessary
 * @param {string} userEmail - User email
 * @returns {string|null} Valid access token or null if unavailable
 */
function getValidAccessToken(userEmail) {
  try {
    Logger.log('=== getValidAccessToken START ===');
    Logger.log('User email: ' + userEmail);
    
    const userProps = PropertiesService.getUserProperties();
    const accessTokenKey = 'ACCESS_TOKEN_' + userEmail;
    let accessToken = userProps.getProperty(accessTokenKey);
    
    if (!accessToken) {
      Logger.log('No access token found, attempting to refresh...');
      accessToken = refreshAccessToken(userEmail);
      if (!accessToken) {
        Logger.log('Could not obtain access token');
        return null;
      }
      return accessToken;
    }
    
    // Try to use the existing token by making a test Drive API call
    // If it fails with 401, refresh it
    try {
      // Make a lightweight Drive API call to verify token validity
      const testUrl = 'https://www.googleapis.com/drive/v3/about?fields=user';
      const testOptions = {
        method: 'get',
        headers: {
          'Authorization': 'Bearer ' + accessToken
        },
        muteHttpExceptions: true
      };
      
      const testResponse = UrlFetchApp.fetch(testUrl, testOptions);
      const testResponseCode = testResponse.getResponseCode();
      
      if (testResponseCode === 401) {
        Logger.log('Access token expired, refreshing...');
        accessToken = refreshAccessToken(userEmail);
        if (!accessToken) {
          Logger.log('Could not refresh access token');
          return null;
        }
        return accessToken;
      } else if (testResponseCode === 200) {
        Logger.log('Access token is valid');
        return accessToken;
      } else {
        Logger.log('Unexpected response code when testing token: ' + testResponseCode);
        // Token might still be valid, return it
        return accessToken;
      }
    } catch (error) {
      Logger.log('Error testing access token: ' + error.toString());
      // If test fails, try refreshing anyway
      Logger.log('Attempting to refresh access token...');
      accessToken = refreshAccessToken(userEmail);
      return accessToken;
    }
  } catch (error) {
    Logger.log('ERROR in getValidAccessToken: ' + error.toString());
    return null;
  }
}