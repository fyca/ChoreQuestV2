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
    
    // Check if family already exists
    Logger.log('Loading family data...');
    const familyData = loadJsonFile(FILE_NAMES.FAMILY);
    
    if (familyData) {
      Logger.log('Family data found, checking for existing user...');
      // Family exists, return existing data
      const usersData = loadJsonFile(FILE_NAMES.USERS);
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
          driveWorkbookLink: familyData.driveWorkbookLink || '',
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
    const result = initializeFamilyData(userEmail, userName, userPicture);
    Logger.log('Family created successfully');
    
    // Create session data
    const session = {
      familyId: result.familyData.id,
      userId: result.primaryUser.id,
      userName: result.primaryUser.name,
      userRole: result.primaryUser.role,
      authToken: result.primaryUser.authToken,
      tokenVersion: result.primaryUser.tokenVersion,
      driveWorkbookLink: result.familyData.driveWorkbookLink || '',
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
    
    const { familyId, userId, token, tokenVersion } = data;
    
    if (!familyId || !userId || !token) {
      Logger.log('ERROR: Missing required fields');
      return createResponse({ error: 'Missing required fields' }, 400);
    }
    
    Logger.log('Looking for userId: ' + userId);
    Logger.log('Looking for familyId: ' + familyId);
    
    // Load family and user data
    const familyData = loadJsonFile(FILE_NAMES.FAMILY);
    const usersData = loadJsonFile(FILE_NAMES.USERS);
    
    if (!familyData) {
      Logger.log('ERROR: Family data not found');
      return createResponse({ error: 'Family not found' }, 404);
    }
    
    if (familyData.id !== familyId) {
      Logger.log('ERROR: Family ID mismatch. Expected: ' + familyData.id + ', Got: ' + familyId);
      return createResponse({ error: 'Invalid family ID' }, 404);
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
    
    // Log device login
    logActivity({
      actorId: userId,
      actorName: user.name,
      actorRole: user.role,
      actionType: 'device_login',
      details: {
        deviceName: deviceName
      }
    });
    
    // Create session data matching Google auth format
    const session = {
      familyId: familyId,
      userId: userId,
      userName: user.name,
      userRole: user.role,
      authToken: token,
      tokenVersion: tokenVersion,
      driveWorkbookLink: familyData.driveWorkbookLink || familyData.driveFileId || '',
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
    
    // Log QR regeneration
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
    });
    
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
