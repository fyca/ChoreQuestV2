/**
 * UserManager.gs
 * Handles user account creation and management
 */

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
    
    const { parentUserId, name, role, avatarUrl } = data;
    
    if (!parentUserId || !name || !role) {
      Logger.log('ERROR: Missing required fields');
      Logger.log('parentUserId: ' + parentUserId);
      Logger.log('name: ' + name);
      Logger.log('role: ' + role);
      return createResponse({ error: 'Missing required fields' }, 400);
    }
    
    Logger.log('Loading users and family data...');
    const usersData = loadJsonFile(FILE_NAMES.USERS);
    const familyData = loadJsonFile(FILE_NAMES.FAMILY);
    
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
      role: role,
      isPrimaryParent: false,
      avatarUrl: avatarUrl || null,
      pointsBalance: 0,
      canEarnPoints: role === 'child',
      authToken: authToken,
      tokenVersion: 1,
      devices: [],
      createdAt: new Date().toISOString(),
      createdBy: parentUserId,
      settings: {
        notifications: true,
        theme: role === 'child' ? 'colorful' : 'light',
        celebrationStyle: 'fireworks',
        soundEffects: true
      },
      stats: {
        totalChoresCompleted: 0,
        currentStreak: 0
      }
    };
    
    // Add to users data
    Logger.log('Adding user to users array...');
    usersData.users.push(newUser);
    const usersSaveResult = saveJsonFile(FILE_NAMES.USERS, usersData);
    Logger.log('Users file saved: ' + (usersSaveResult ? 'SUCCESS' : 'FAILED'));
    
    // Add to family members
    Logger.log('Adding user to family members...');
    familyData.members.push(newUser);
    // Update family metadata
    if (!familyData.metadata) {
      familyData.metadata = {};
    }
    familyData.metadata.lastModified = new Date().toISOString();
    familyData.metadata.lastModifiedBy = parentUserId;
    familyData.metadata.version = (familyData.metadata.version || 0) + 1;
    const familySaveResult = saveJsonFile(FILE_NAMES.FAMILY, familyData);
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
    });
    
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
    
    const usersData = loadJsonFile(FILE_NAMES.USERS);
    
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
    
    // Save updates
    saveJsonFile(FILE_NAMES.USERS, usersData);
    
    // Update family data as well
    const familyData = loadJsonFile(FILE_NAMES.FAMILY);
    const familyMemberIndex = familyData.members.findIndex(m => m.id === targetUserId);
    if (familyMemberIndex !== -1) {
      familyData.members[familyMemberIndex] = targetUser;
      saveJsonFile(FILE_NAMES.FAMILY, familyData);
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
    });
    
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
    
    const usersData = loadJsonFile(FILE_NAMES.USERS);
    
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
    saveJsonFile(FILE_NAMES.USERS, usersData);
    
    // Remove from family
    const familyData = loadJsonFile(FILE_NAMES.FAMILY);
    familyData.members = familyData.members.filter(m => m.id !== targetUserId);
    saveJsonFile(FILE_NAMES.FAMILY, familyData);
    
    // Log removal
    logActivity({
      actorId: parentUserId,
      actorName: parentUser.name,
      actorRole: 'parent',
      actionType: 'user_removed',
      targetUserId: targetUserId,
      targetUserName: targetUser.name
    });
    
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
 */
function listFamilyMembers(familyId) {
  try {
    const familyData = loadJsonFile(FILE_NAMES.FAMILY);
    
    if (!familyData || familyData.id !== familyId) {
      return createResponse({ error: 'Family not found' }, 404);
    }
    
    const usersData = loadJsonFile(FILE_NAMES.USERS);
    
    return createResponse({
      success: true,
      users: usersData.users
    });
    
  } catch (error) {
    Logger.log('Error in listFamilyMembers: ' + error.toString());
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
    const familyData = loadJsonFile(FILE_NAMES.FAMILY);
    
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
    saveJsonFile(FILE_NAMES.USERS, usersData);
    
    // Update family data
    const familyMemberIndex = familyData.members.findIndex(m => m.id === targetUserId);
    if (familyMemberIndex !== -1) {
      familyData.members[familyMemberIndex] = targetUser;
      saveJsonFile(FILE_NAMES.FAMILY, familyData);
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
    });
    
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
