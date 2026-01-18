/**
 * UserManager.gs
 * Handles user account creation and management
 */

/**
 * Helper function to get ownerEmail, folderId, and accessToken from family data and user properties
 * This function tries to find any stored access token and use it to load family data
 */
function getFamilyInfoForUser() {
  try {
    Logger.log('getFamilyInfoForUser: Attempting to get family info');
    
    // Get all stored access tokens from user properties
    const userProps = PropertiesService.getUserProperties();
    const allProps = userProps.getProperties();
    
    // Try each stored access token until we find one that works
    for (const key in allProps) {
      if (key.startsWith('ACCESS_TOKEN_')) {
        const ownerEmail = key.replace('ACCESS_TOKEN_', '');
        const accessToken = allProps[key];
        
        Logger.log('getFamilyInfoForUser: Trying access token for: ' + ownerEmail);
        
        try {
          // Try to load family data using this access token
          // We'll use ownerEmail to get the folder, then load family data
          const folderId = getChoreQuestFolderV3(ownerEmail, accessToken);
          Logger.log('getFamilyInfoForUser: Got folder ID: ' + folderId);
          
          // Now try to load family data using folderId and accessToken
          const familyData = loadJsonFileV3(FILE_NAMES.FAMILY, ownerEmail, folderId, accessToken);
          
          if (familyData && familyData.ownerEmail === ownerEmail) {
            Logger.log('getFamilyInfoForUser: Successfully loaded family data');
            Logger.log('getFamilyInfoForUser: ownerEmail=' + familyData.ownerEmail + ', folderId=' + familyData.driveFileId);
            
            return {
              ownerEmail: familyData.ownerEmail,
              folderId: familyData.driveFileId,
              familyId: familyData.id,
              accessToken: accessToken
            };
          }
        } catch (error) {
          Logger.log('getFamilyInfoForUser: Failed with token for ' + ownerEmail + ': ' + error.toString());
          // Continue to next token
          continue;
        }
      }
    }
    
    Logger.log('getFamilyInfoForUser: No working access token found');
    return null;
  } catch (error) {
    Logger.log('getFamilyInfoForUser: Error: ' + error.toString());
    Logger.log('getFamilyInfoForUser: Error stack: ' + (error.stack || 'no stack'));
    return null;
  }
}

/**
 * Handle user requests (GET)
 */
function handleUsersRequest(e) {
  const action = e.parameter.action;
  const familyId = e.parameter.familyId;
  
  if (action === 'list' && familyId) {
    return listFamilyMembers(familyId);
  }
  
  return createResponse({ error: 'Invalid users action' }, 400);
}

/**
 * Handle user requests (POST)
 */
function handleUsersPost(e, data) {
  const action = e.parameter.action || data.action;
  
  Logger.log('handleUsersPost - action: ' + action);
  Logger.log('handleUsersPost - data: ' + JSON.stringify(data));
  
  if (action === 'create') {
    return createFamilyMember(data);
  } else if (action === 'update') {
    return updateFamilyMember(data);
  } else if (action === 'delete') {
    return deleteFamilyMember(data);
  } else if (action === 'regenerateQR') {
    return regenerateQRCode(data);
  }
  
  Logger.log('ERROR: Invalid users action: ' + action);
  return createResponse({ error: 'Invalid users action: ' + action }, 400);
}

/**
 * Create a new family member
 */
function createFamilyMember(data) {
  try {
    Logger.log('=== createFamilyMember START ===');
    Logger.log('Data received: ' + JSON.stringify(data));
    Logger.log('Data type: ' + typeof data);
    Logger.log('canEarnPoints in data: ' + ('canEarnPoints' in data));
    Logger.log('canEarnPoints value (from data object): ' + data.canEarnPoints);
    Logger.log('canEarnPoints type: ' + typeof data.canEarnPoints);
    
    const { parentUserId, name, role, avatarUrl, birthdate } = data;
    // Get canEarnPoints directly from data object to avoid destructuring issues
    const canEarnPoints = data.canEarnPoints;
    
    if (!parentUserId || !name || !role) {
      Logger.log('ERROR: Missing required fields');
      Logger.log('parentUserId: ' + parentUserId);
      Logger.log('name: ' + name);
      Logger.log('role: ' + role);
      return createResponse({ error: 'Missing required fields' }, 400);
    }
    
    // Normalize role to lowercase for comparison (Android sends "CHILD" or "PARENT" as enum)
    const normalizedRole = role ? role.toLowerCase() : role;
    Logger.log('createFamilyMember: role from request: ' + role);
    Logger.log('createFamilyMember: normalized role: ' + normalizedRole);
    
    // Handle canEarnPoints - it might come as boolean, string, or undefined
    // Convert string "true"/"false" to boolean, handle explicit boolean values
    let shouldEarnPoints;
    if (canEarnPoints !== undefined && canEarnPoints !== null) {
      // Handle string "true"/"false" or boolean true/false
      if (typeof canEarnPoints === 'string') {
        shouldEarnPoints = canEarnPoints.toLowerCase() === 'true';
        Logger.log('createFamilyMember: canEarnPoints was string, converted to: ' + shouldEarnPoints);
      } else {
        // For boolean, use it directly (don't use Boolean() as it might coerce incorrectly)
        shouldEarnPoints = canEarnPoints === true;
        Logger.log('createFamilyMember: canEarnPoints was boolean: ' + canEarnPoints + ', using: ' + shouldEarnPoints);
      }
      Logger.log('createFamilyMember: canEarnPoints from request (type: ' + typeof canEarnPoints + ', value: ' + canEarnPoints + '): ' + shouldEarnPoints);
    } else {
      // Default based on role if not provided - default to true for children
      shouldEarnPoints = normalizedRole === 'child';
      Logger.log('createFamilyMember: canEarnPoints not provided (undefined or null), defaulting based on role (' + normalizedRole + '): ' + shouldEarnPoints);
    }
    Logger.log('createFamilyMember: Final canEarnPoints value that will be saved: ' + shouldEarnPoints);
    
    Logger.log('Getting family info (ownerEmail, folderId, accessToken)...');
    const familyInfo = getFamilyInfoForUser();
    
    if (!familyInfo) {
      Logger.log('ERROR: Could not get family info');
      return createResponse({ error: 'Family data not found or not accessible' }, 500);
    }
    
    const { ownerEmail, folderId, accessToken } = familyInfo;
    Logger.log('Family info retrieved - ownerEmail: ' + ownerEmail + ', folderId: ' + folderId + ', hasAccessToken: ' + (accessToken ? 'yes' : 'no'));
    
    Logger.log('Loading users and family data...');
    const usersData = loadJsonFile(FILE_NAMES.USERS, ownerEmail, folderId, accessToken);
    const familyData = loadJsonFile(FILE_NAMES.FAMILY, ownerEmail, folderId, accessToken);
    
    if (!usersData) {
      Logger.log('ERROR: Users data file not found');
      return createResponse({ error: 'Users data not found' }, 500);
    }
    
    if (!familyData) {
      Logger.log('ERROR: Family data file not found');
      return createResponse({ error: 'Family data not found' }, 500);
    }
    
    Logger.log('Users data loaded: ' + usersData.users.length + ' users');
    Logger.log('Family data loaded: ' + familyData.members.length + ' members');
    
    // Verify parent is authorized
    const parentUser = usersData.users.find(u => u.id === parentUserId);
    if (!parentUser || parentUser.role !== 'parent') {
      return createResponse({ error: 'Unauthorized' }, 403);
    }
    
    // Create new user
    const userId = Utilities.getUuid();
    const authToken = Utilities.getUuid();
    
    const newUser = {
      id: userId,
      name: name,
      email: null,
      role: normalizedRole, // Store normalized role (lowercase)
      isPrimaryParent: false,
      avatarUrl: avatarUrl || null,
      pointsBalance: 0,
      canEarnPoints: shouldEarnPoints,
      authToken: authToken,
      tokenVersion: 1,
      devices: [],
      createdAt: new Date().toISOString(),
      createdBy: parentUserId,
      settings: {
        notifications: true,
        theme: normalizedRole === 'child' ? 'colorful' : 'light',
        celebrationStyle: 'fireworks',
        soundEffects: true
      },
      stats: {
        totalChoresCompleted: 0,
        currentStreak: 0
      },
      birthdate: birthdate || null // ISO 8601 date string (YYYY-MM-DD), only for children
    };
    
    Logger.log('createFamilyMember: newUser.canEarnPoints = ' + newUser.canEarnPoints);
    Logger.log('createFamilyMember: newUser object: ' + JSON.stringify(newUser));
    
    // Add to users data (source of truth)
    Logger.log('Adding user to users array...');
    usersData.users.push(newUser);
    const usersSaveResult = saveJsonFile(FILE_NAMES.USERS, usersData, ownerEmail, folderId, accessToken);
    Logger.log('Users file saved: ' + (usersSaveResult ? 'SUCCESS' : 'FAILED'));
    
    // Add to family members - create a copy to avoid reference issues
    Logger.log('Adding user to family members...');
    // Create a deep copy to ensure both files have the same data
    const newUserCopy = JSON.parse(JSON.stringify(newUser));
    Logger.log('createFamilyMember: newUserCopy.canEarnPoints = ' + newUserCopy.canEarnPoints);
    familyData.members.push(newUserCopy);
    // Update family metadata
    if (!familyData.metadata) {
      familyData.metadata = {};
    }
    familyData.metadata.lastModified = new Date().toISOString();
    familyData.metadata.lastModifiedBy = parentUserId;
    familyData.metadata.version = (familyData.metadata.version || 0) + 1;
    const familySaveResult = saveJsonFile(FILE_NAMES.FAMILY, familyData, ownerEmail, folderId, accessToken);
    Logger.log('Family file saved: ' + (familySaveResult ? 'SUCCESS' : 'FAILED'));
    
    // Log user creation
    logActivity({
      actorId: parentUserId,
      actorName: parentUser.name,
      actorRole: 'parent',
      actionType: 'user_added',
      targetUserId: userId,
      targetUserName: name,
      details: {
        role: role
      }
    }, ownerEmail, folderId, accessToken);
    
    // Generate QR code data
    const qrData = {
      familyId: familyData.id,
      userId: userId,
      token: authToken,
      version: 1,
      appVersion: '1.0.0',
      timestamp: new Date().toISOString()
    };
    
    Logger.log('=== createFamilyMember SUCCESS ===');
    Logger.log('Created user: ' + newUser.name + ' (ID: ' + newUser.id + ')');
    
    return createResponse({
      success: true,
      user: newUser,
      qrData: qrData
    });
    
  } catch (error) {
    Logger.log('=== createFamilyMember ERROR ===');
    Logger.log('Error: ' + error.toString());
    Logger.log('Stack: ' + error.stack);
    return createResponse({ error: error.toString() }, 500);
  }
}

/**
 * Update family member
 */
function updateFamilyMember(data) {
  try {
    const { parentUserId, targetUserId, updates } = data;
    
    if (!parentUserId || !targetUserId || !updates) {
      return createResponse({ error: 'Missing required fields' }, 400);
    }
    
    Logger.log('Getting family info (ownerEmail, folderId, accessToken)...');
    const familyInfo = getFamilyInfoForUser();
    
    if (!familyInfo) {
      Logger.log('ERROR: Could not get family info');
      return createResponse({ error: 'Family data not found or not accessible' }, 500);
    }
    
    const { ownerEmail, folderId, accessToken } = familyInfo;
    
    const usersData = loadJsonFile(FILE_NAMES.USERS, ownerEmail, folderId, accessToken);
    
    // Verify parent is authorized (or user updating themselves)
    const parentUser = usersData.users.find(u => u.id === parentUserId);
    if (!parentUser || (parentUser.role !== 'parent' && parentUserId !== targetUserId)) {
      return createResponse({ error: 'Unauthorized' }, 403);
    }
    
    // Find target user
    const targetUser = usersData.users.find(u => u.id === targetUserId);
    if (!targetUser) {
      return createResponse({ error: 'User not found' }, 404);
    }
    
    // Update allowed fields
    const allowedFields = ['name', 'avatarUrl', 'settings'];
    allowedFields.forEach(field => {
      if (updates[field] !== undefined) {
        if (field === 'settings') {
          targetUser.settings = { ...targetUser.settings, ...updates.settings };
        } else {
          targetUser[field] = updates[field];
        }
      }
    });
    
    // Save updates to users file (source of truth)
    saveJsonFile(FILE_NAMES.USERS, usersData, ownerEmail, folderId, accessToken);
    
    // Update family data as well - use a copy of the updated user from users array
    const familyData = loadJsonFile(FILE_NAMES.FAMILY, ownerEmail, folderId, accessToken);
    const familyMemberIndex = familyData.members.findIndex(m => m.id === targetUserId);
    if (familyMemberIndex !== -1) {
      // Get the updated user from users array (source of truth) and create a copy
      const updatedUserFromUsers = usersData.users.find(u => u.id === targetUserId);
      if (updatedUserFromUsers) {
        // Create a deep copy to ensure both files stay in sync
        familyData.members[familyMemberIndex] = JSON.parse(JSON.stringify(updatedUserFromUsers));
        saveJsonFile(FILE_NAMES.FAMILY, familyData, ownerEmail, folderId, accessToken);
      }
    }
    
    // Log update
    logActivity({
      actorId: parentUserId,
      actorName: parentUser.name,
      actorRole: parentUser.role,
      actionType: 'user_updated',
      targetUserId: targetUserId,
      targetUserName: targetUser.name,
      details: {
        updatedFields: Object.keys(updates)
      }
    }, ownerEmail, folderId, accessToken);
    
    return createResponse({
      success: true,
      user: targetUser
    });
    
  } catch (error) {
    Logger.log('Error in updateFamilyMember: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}

/**
 * Delete family member
 */
function deleteFamilyMember(data) {
  try {
    const { parentUserId, targetUserId } = data;
    
    if (!parentUserId || !targetUserId) {
      return createResponse({ error: 'Missing required fields' }, 400);
    }
    
    Logger.log('Getting family info (ownerEmail, folderId, accessToken)...');
    const familyInfo = getFamilyInfoForUser();
    
    if (!familyInfo) {
      Logger.log('ERROR: Could not get family info');
      return createResponse({ error: 'Family data not found or not accessible' }, 500);
    }
    
    const { ownerEmail, folderId, accessToken } = familyInfo;
    
    const usersData = loadJsonFile(FILE_NAMES.USERS, ownerEmail, folderId, accessToken);
    
    // Verify parent is authorized
    const parentUser = usersData.users.find(u => u.id === parentUserId);
    if (!parentUser || parentUser.role !== 'parent') {
      return createResponse({ error: 'Unauthorized' }, 403);
    }
    
    // Find target user
    const targetUserIndex = usersData.users.findIndex(u => u.id === targetUserId);
    if (targetUserIndex === -1) {
      return createResponse({ error: 'User not found' }, 404);
    }
    
    const targetUser = usersData.users[targetUserIndex];
    
    // Cannot delete primary parent
    if (targetUser.isPrimaryParent) {
      return createResponse({ error: 'Cannot delete primary parent' }, 403);
    }
    
    // Remove from users
    usersData.users.splice(targetUserIndex, 1);
    saveJsonFile(FILE_NAMES.USERS, usersData, ownerEmail, folderId, accessToken);
    
    // Remove from family
    const familyData = loadJsonFile(FILE_NAMES.FAMILY, ownerEmail, folderId, accessToken);
    familyData.members = familyData.members.filter(m => m.id !== targetUserId);
    saveJsonFile(FILE_NAMES.FAMILY, familyData, ownerEmail, folderId, accessToken);
    
    // Log removal
    logActivity({
      actorId: parentUserId,
      actorName: parentUser.name,
      actorRole: 'parent',
      actionType: 'user_removed',
      targetUserId: targetUserId,
      targetUserName: targetUser.name
    }, ownerEmail, folderId, accessToken);
    
    return createResponse({
      success: true,
      message: 'User removed successfully'
    });
    
  } catch (error) {
    Logger.log('Error in deleteFamilyMember: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}

/**
 * List all family members
 * This function can be called without authentication context, so we need to find the family data
 * by trying all stored access tokens until we find one that matches the familyId
 */
function listFamilyMembers(familyId) {
  try {
    Logger.log('listFamilyMembers: Looking for family with ID: ' + familyId);
    
    // Get all stored access tokens from user properties
    const userProps = PropertiesService.getUserProperties();
    const allProps = userProps.getProperties();
    
    // Try each stored access token until we find the family with matching familyId
    for (const key in allProps) {
      if (key.startsWith('ACCESS_TOKEN_')) {
        const ownerEmail = key.replace('ACCESS_TOKEN_', '');
        const accessToken = allProps[key];
        
        Logger.log('listFamilyMembers: Trying access token for: ' + ownerEmail);
        
        try {
          // Try to get the folder ID and load family data
          const folderId = getChoreQuestFolderV3(ownerEmail, accessToken);
          Logger.log('listFamilyMembers: Got folder ID: ' + folderId);
          
          // Load family data to check if it matches the requested familyId
          const familyData = loadJsonFileV3(FILE_NAMES.FAMILY, ownerEmail, folderId, accessToken);
          
          if (familyData && familyData.id === familyId) {
            Logger.log('listFamilyMembers: Found matching family! ownerEmail=' + familyData.ownerEmail + ', folderId=' + familyData.driveFileId);
            
            // Load users data
            const usersData = loadJsonFileV3(FILE_NAMES.USERS, ownerEmail, folderId, accessToken);
            
            if (!usersData || !usersData.users) {
              Logger.log('ERROR: Users data not found or invalid structure');
              return createResponse({ error: 'Users data not found' }, 404);
            }
            
            Logger.log('listFamilyMembers: Found ' + usersData.users.length + ' users');
            
            return createResponse({
              success: true,
              users: usersData.users
            });
          } else if (familyData) {
            Logger.log('listFamilyMembers: Family ID mismatch. Expected: ' + familyId + ', Got: ' + familyData.id);
            // Continue to next token
            continue;
          }
        } catch (error) {
          Logger.log('listFamilyMembers: Failed with token for ' + ownerEmail + ': ' + error.toString());
          // Continue to next token
          continue;
        }
      }
    }
    
    Logger.log('ERROR: Could not find family with ID: ' + familyId);
    return createResponse({ error: 'Family not found' }, 404);
    
  } catch (error) {
    Logger.log('Error in listFamilyMembers: ' + error.toString());
    Logger.log('Error stack: ' + (error.stack || 'no stack'));
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
    
    Logger.log('Getting family info (ownerEmail, folderId, accessToken)...');
    const familyInfo = getFamilyInfoForUser();
    
    if (!familyInfo) {
      Logger.log('ERROR: Could not get family info');
      return createResponse({ error: 'Family data not found or not accessible' }, 500);
    }
    
    const { ownerEmail, folderId, accessToken } = familyInfo;
    
    const usersData = loadJsonFile(FILE_NAMES.USERS, ownerEmail, folderId, accessToken);
    const familyData = loadJsonFile(FILE_NAMES.FAMILY, ownerEmail, folderId, accessToken);
    
    // Verify parent is authorized
    const parentUser = usersData.users.find(u => u.id === parentUserId);
    if (!parentUser || parentUser.role !== 'parent') {
      return createResponse({ error: 'Unauthorized' }, 403);
    }
    
    // Find target user
    const targetUser = usersData.users.find(u => u.id === targetUserId);
    if (!targetUser) {
      return createResponse({ error: 'User not found' }, 404);
    }
    
    // Generate new token and increment version
    const newToken = Utilities.getUuid();
    targetUser.authToken = newToken;
    targetUser.tokenVersion = targetUser.tokenVersion + 1;
    
    // Clear all devices (force re-authentication)
    targetUser.devices = [];
    
    // Save updates
    saveJsonFile(FILE_NAMES.USERS, usersData, ownerEmail, folderId, accessToken);
    
    // Update family data
    const familyMemberIndex = familyData.members.findIndex(m => m.id === targetUserId);
    if (familyMemberIndex !== -1) {
      familyData.members[familyMemberIndex] = targetUser;
      saveJsonFile(FILE_NAMES.FAMILY, familyData, ownerEmail, folderId, accessToken);
    }
    
    // Log QR regeneration
    logActivity({
      actorId: parentUserId,
      actorName: parentUser.name,
      actorRole: 'parent',
      actionType: 'qr_regenerated',
      targetUserId: targetUserId,
      targetUserName: targetUser.name,
      details: {
        reason: reason || 'Manual regeneration',
        newTokenVersion: targetUser.tokenVersion
      }
    }, ownerEmail, folderId, accessToken);
    
    // Generate new QR code data
    const qrData = {
      familyId: familyData.id,
      userId: targetUserId,
      token: newToken,
      version: targetUser.tokenVersion,
      appVersion: '1.0.0',
      timestamp: new Date().toISOString()
    };
    
    return createResponse({
      success: true,
      user: targetUser,
      qrData: qrData
    });
    
  } catch (error) {
    Logger.log('Error in regenerateQRCode: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}
