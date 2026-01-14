/**
 * AuthManager.gs - UPDATED VERSION
 * Copy this entire file content and replace your AuthManager.gs
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
  
  Logger.log('handleAuthPost called with action: ' + action);
  
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
 * Handle Google OAuth authentication (primary parent)
 */
function handleGoogleAuth(data) {
  try {
    Logger.log('=== handleGoogleAuth START ===');
    Logger.log('Received data keys: ' + Object.keys(data).join(', '));
    
    // Verify Google ID token
    const googleToken = data.googleToken;
    if (!googleToken) {
      Logger.log('ERROR: Missing Google token');
      return createResponse({ success: false, error: 'Missing Google token' }, 400);
    }
    
    Logger.log('Token received, length: ' + googleToken.length);
    
    // Decode the JWT token (basic decode without signature verification)
    const tokenParts = googleToken.split('.');
    if (tokenParts.length !== 3) {
      Logger.log('ERROR: Invalid token format, parts: ' + tokenParts.length);
      return createResponse({ success: false, error: 'Invalid token format' }, 400);
    }
    
    Logger.log('Token has 3 parts, decoding payload...');
    
    // Decode the payload (middle part) - base64url decode
    let payload;
    try {
      const base64Url = tokenParts[1];
      // Convert base64url to base64
      const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
      const decoded = Utilities.newBlob(Utilities.base64Decode(base64)).getDataAsString();
      payload = JSON.parse(decoded);
      Logger.log('Payload decoded successfully');
    } catch (decodeError) {
      Logger.log('ERROR decoding token: ' + decodeError.toString());
      return createResponse({ 
        success: false,
        error: 'Failed to decode token: ' + decodeError.toString() 
      }, 400);
    }
    
    const userEmail = payload.email;
    const userName = payload.name || userEmail.split('@')[0];
    
    Logger.log('Email: ' + userEmail);
    Logger.log('Name: ' + userName);
    
    if (!userEmail) {
      Logger.log('ERROR: Email not found in token');
      return createResponse({ success: false, error: 'Email not found in token' }, 401);
    }
    
    // Check if family already exists
    Logger.log('Checking for existing family...');
    const familyData = loadJsonFile(FILE_NAMES.FAMILY);
    
    if (familyData) {
      Logger.log('Family data found, checking for user...');
      const usersData = loadJsonFile(FILE_NAMES.USERS);
      const user = usersData.users.find(u => u.email === userEmail && u.isPrimaryParent);
      
      if (user) {
        Logger.log('Existing user found: ' + user.name);
        
        // Create session data
        const session = {
          familyId: familyData.id,
          userId: user.id,
          userName: user.name,
          userRole: user.role,
          authToken: user.authToken,
          tokenVersion: user.tokenVersion,
          driveWorkbookLink: familyData.driveWorkbookLink || '',
          deviceId: data.deviceId || 'android',
          loginTimestamp: new Date().toISOString(),
          lastSynced: new Date().getTime()
        };
        
        Logger.log('=== handleGoogleAuth SUCCESS (existing user) ===');
        return createResponse({
          success: true,
          message: 'Login successful',
          user: user,
          session: session
        });
      }
    }
    
    // Create new family
    Logger.log('No existing family/user, creating new...');
    const result = initializeFamilyData(userEmail, userName);
    
    Logger.log('Family created, building session...');
    
    // Create session data
    const session = {
      familyId: result.familyData.id,
      userId: result.primaryUser.id,
      userName: result.primaryUser.name,
      userRole: result.primaryUser.role,
      authToken: result.primaryUser.authToken,
      tokenVersion: result.primaryUser.tokenVersion,
      driveWorkbookLink: result.familyData.driveWorkbookLink || '',
      deviceId: data.deviceId || 'android',
      loginTimestamp: new Date().toISOString(),
      lastSynced: new Date().getTime()
    };
    
    Logger.log('=== handleGoogleAuth SUCCESS (new family) ===');
    return createResponse({
      success: true,
      message: 'Family created successfully',
      user: result.primaryUser,
      session: session
    });
    
  } catch (error) {
    Logger.log('=== handleGoogleAuth FATAL ERROR ===');
    Logger.log('Error: ' + error.toString());
    Logger.log('Stack: ' + error.stack);
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
    const { familyId, userId, token, tokenVersion } = data;
    
    if (!familyId || !userId || !token) {
      return createResponse({ error: 'Missing required fields' }, 400);
    }
    
    // Load family and user data
    const familyData = loadJsonFile(FILE_NAMES.FAMILY);
    const usersData = loadJsonFile(FILE_NAMES.USERS);
    
    if (!familyData || familyData.id !== familyId) {
      return createResponse({ error: 'Invalid family ID' }, 404);
    }
    
    // Find user
    const user = usersData.users.find(u => u.id === userId);
    
    if (!user) {
      return createResponse({ error: 'User not found' }, 404);
    }
    
    // Validate token
    if (user.authToken !== token) {
      return createResponse({ error: 'Invalid token' }, 401);
    }
    
    // Validate token version
    if (user.tokenVersion !== tokenVersion) {
      return createResponse({ 
        error: 'Token expired. Please scan a new QR code.',
        reason: 'token_regenerated'
      }, 401);
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
    
    // Save updated user data
    saveJsonFile(FILE_NAMES.USERS, usersData);
    
    return createResponse({
      success: true,
      familyData: familyData,
      userData: user,
      sessionData: {
        familyId: familyId,
        userId: userId,
        userName: user.name,
        userRole: user.role,
        authToken: token,
        tokenVersion: tokenVersion,
        driveWorkbookLink: familyData.driveFileId,
        deviceId: deviceId
      }
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
    
    const usersData = loadJsonFile(FILE_NAMES.USERS);
    const user = usersData.users.find(u => u.id === userId);
    
    if (!user) {
      return createResponse({ valid: false, reason: 'user_not_found' });
    }
    
    if (user.authToken !== token) {
      return createResponse({ valid: false, reason: 'invalid_token' });
    }
    
    if (user.tokenVersion !== tokenVersion) {
      return createResponse({ valid: false, reason: 'token_regenerated' });
    }
    
    return createResponse({ valid: true, userData: user });
    
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
    
    const usersData = loadJsonFile(FILE_NAMES.USERS);
    
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
    const newVersion = targetUser.tokenVersion + 1;
    
    targetUser.authToken = newToken;
    targetUser.tokenVersion = newVersion;
    
    // Save updated data
    saveJsonFile(FILE_NAMES.USERS, usersData);
    
    const familyData = loadJsonFile(FILE_NAMES.FAMILY);
    
    return createResponse({
      success: true,
      qrData: {
        familyId: familyData.id,
        userId: targetUserId,
        token: newToken,
        version: newVersion,
        appVersion: '1.0.0',
        timestamp: new Date().toISOString()
      }
    });
    
  } catch (error) {
    Logger.log('Error in regenerateQRCode: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}
