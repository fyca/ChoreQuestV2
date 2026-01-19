/**
 * PollingManager.gs
 * Handles efficient polling endpoints for data sync
 */

/**
 * Helper function to get ownerEmail, folderId, and accessToken from family data
 * Searches through all stored access tokens to find a family
 * @param {string} requestedFamilyId - Optional familyId to match. If provided, only returns matching family.
 */
function getFamilyInfoForSync(requestedFamilyId) {
  try {
    Logger.log('getFamilyInfoForSync: Attempting to get family info, requestedFamilyId=' + (requestedFamilyId || 'any'));
    
    // Get all stored access tokens from user properties
    const userProps = PropertiesService.getUserProperties();
    const allProps = userProps.getProperties();
    
    // Try each stored access token until we find a matching family
    for (const key in allProps) {
      if (key.startsWith('ACCESS_TOKEN_')) {
        const ownerEmail = key.replace('ACCESS_TOKEN_', '');
        const accessToken = allProps[key];
        
        Logger.log('getFamilyInfoForSync: Trying access token for: ' + ownerEmail);
        
        try {
          // Try to get the folder ID and load family data
          const folderId = getChoreQuestFolderV3(ownerEmail, accessToken);
          Logger.log('getFamilyInfoForSync: Got folder ID: ' + folderId);
          
          // Load family data
          const familyData = loadJsonFileV3(FILE_NAMES.FAMILY, ownerEmail, folderId, accessToken);
          
          if (familyData) {
            Logger.log('getFamilyInfoForSync: Found family! ownerEmail=' + familyData.ownerEmail + ', folderId=' + familyData.driveFileId + ', familyId=' + familyData.id);
            
            // If a specific familyId was requested, check if it matches
            if (requestedFamilyId && familyData.id !== requestedFamilyId) {
              Logger.log('getFamilyInfoForSync: Family ID mismatch. Requested: ' + requestedFamilyId + ', Found: ' + familyData.id + ', continuing search...');
              continue;
            }
            
            Logger.log('getFamilyInfoForSync: Returning family info for familyId=' + familyData.id);
            // Use driveFileId from family, fallback to folderId we just used to load family (ensures we use the same folder that works)
            return {
              ownerEmail: familyData.ownerEmail,
              folderId: familyData.driveFileId || folderId,
              familyId: familyData.id,
              accessToken: accessToken
            };
          }
        } catch (error) {
          Logger.log('getFamilyInfoForSync: Failed with token for ' + ownerEmail + ': ' + error.toString());
          // Continue to next token
          continue;
        }
      }
    }
    
    Logger.log('getFamilyInfoForSync: No family found' + (requestedFamilyId ? ' matching familyId=' + requestedFamilyId : ''));
    return null;
  } catch (error) {
    Logger.log('getFamilyInfoForSync: Error: ' + error.toString());
    Logger.log('getFamilyInfoForSync: Error stack: ' + (error.stack || 'no stack'));
    return null;
  }
}

/**
 * Handle sync requests (GET)
 */
function handleSyncRequest(e) {
  const action = e.parameter.action;
  
  if (action === 'status') {
    return getSyncStatus();
  } else if (action === 'changes') {
    return getChangesSince(e);
  }
  
  return createResponse({ error: 'Invalid sync action' }, 400);
}

/**
 * Get sync status (all file metadata)
 */
function getSyncStatus() {
  try {
    // Get ownerEmail, folderId, and accessToken from family data
    const familyInfo = getFamilyInfoForSync();
    if (!familyInfo) {
      return createResponse({ error: 'Family data not found' }, 404);
    }
    
    const { ownerEmail, folderId, accessToken } = familyInfo;
    const metadata = getAllFileMetadataV3(ownerEmail, folderId, accessToken);
    
    return createResponse({
      success: true,
      timestamp: new Date().toISOString(),
      files: metadata
    });
    
  } catch (error) {
    Logger.log('Error in getSyncStatus: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}

/**
 * Get changes since timestamp
 */
function getChangesSince(e) {
  try {
    // Get ownerEmail, folderId, and accessToken from family data
    const familyInfo = getFamilyInfoForSync();
    if (!familyInfo) {
      return createResponse({ error: 'Family data not found' }, 404);
    }
    
    const { ownerEmail, folderId, accessToken } = familyInfo;
    const since = e.parameter.since;
    const entityTypes = e.parameter.types ? e.parameter.types.split(',') : ['chores', 'rewards', 'users', 'transactions', 'activity_log'];
    
    if (!since) {
      return createResponse({ error: 'Missing since parameter' }, 400);
    }
    
    const changes = {};
    let hasAnyChanges = false;
    
    entityTypes.forEach(entityType => {
      const result = getIncrementalChanges(entityType, since, ownerEmail, folderId, accessToken);
      changes[entityType] = result;
      if (result.hasChanges) {
        hasAnyChanges = true;
      }
    });
    
    return createResponse({
      success: true,
      hasChanges: hasAnyChanges,
      timestamp: new Date().toISOString(),
      changes: changes
    });
    
  } catch (error) {
    Logger.log('Error in getChangesSince: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}

/**
 * Handle data requests (GET)
 */
function handleDataRequest(e) {
  const entityType = e.parameter.type;
  const action = e.parameter.action;
  
  if (action === 'get') {
    return getData(entityType, e);
  } else if (action === 'getSince') {
    const since = e.parameter.since;
    return getDataSince(entityType, since);
  }
  
  return createResponse({ error: 'Invalid data action' }, 400);
}

/**
 * Handle data requests (POST)
 */
function handleDataPost(e, data) {
  const entityType = e.parameter.type;
  const action = e.parameter.action || data.action;
  
  if (action === 'save') {
    return saveData(entityType, data);
  } else if (action === 'delete_all') {
    return deleteAllData(data);
  }
  
  return createResponse({ error: 'Invalid data action' }, 400);
}

/**
 * Get full data for entity type
 */
function getData(entityType, e) {
  try {
    // Get familyId from query parameter if provided
    const requestedFamilyId = e && e.parameter ? e.parameter.familyId : null;
    
    // Get ownerEmail, folderId, and accessToken from family data
    // If familyId is provided, use it to find the correct family
    const familyInfo = getFamilyInfoForSync(requestedFamilyId);
    if (!familyInfo) {
      return createResponse({ error: 'Family data not found' }, 404);
    }
    
    const { ownerEmail, folderId, accessToken } = familyInfo;
    
    // For chores, ensure recurring chore instances are up to date before fetching
    if (entityType === 'chores') {
      if (typeof ensureRecurringChoreInstances !== 'undefined') {
        ensureRecurringChoreInstances(ownerEmail, folderId, accessToken);
        if (typeof addDebugLog !== 'undefined') {
          addDebugLog('INFO', 'getData: Called for chores, ensured recurring instances');
        }
      }
    }
    
    const fileName = getFileNameForEntityType(entityType);
    
    if (!fileName) {
      return createResponse({ error: 'Invalid entity type' }, 400);
    }
    
    // Use Drive API v3 with access token
    const data = loadJsonFileV3(fileName, ownerEmail, folderId, accessToken);
    const metadata = getFileMetadataV3(fileName, ownerEmail, folderId, accessToken);
    
    return createResponse({
      success: true,
      data: data,
      metadata: metadata
    });
    
  } catch (error) {
    Logger.log('Error in getData: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}

/**
 * Get data if modified since timestamp
 */
function getDataSince(entityType, since) {
  try {
    // Get ownerEmail, folderId, and accessToken from family data
    const familyInfo = getFamilyInfoForSync();
    if (!familyInfo) {
      return createResponse({ error: 'Family data not found' }, 404);
    }
    
    const { ownerEmail, folderId, accessToken } = familyInfo;
    const fileName = getFileNameForEntityType(entityType);
    
    if (!fileName) {
      return createResponse({ error: 'Invalid entity type' }, 400);
    }
    
    if (!since) {
      return getData(entityType);
    }
    
    const result = getIncrementalChanges(entityType, since, ownerEmail, folderId, accessToken);
    
    if (result.hasChanges) {
      return createResponse({
        success: true,
        hasChanges: true,
        data: result.data,
        metadata: result.metadata
      });
    } else {
      return createResponse({
        success: true,
        hasChanges: false,
        metadata: result.metadata
      });
    }
    
  } catch (error) {
    Logger.log('Error in getDataSince: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}

/**
 * Save data with conflict detection
 */
function saveData(entityType, data) {
  try {
    // Get ownerEmail and folderId from family data
    const familyInfo = getFamilyInfoForSync();
    if (!familyInfo) {
      return createResponse({ error: 'Family data not found' }, 404);
    }
    
    const { ownerEmail, folderId } = familyInfo;
    const fileName = getFileNameForEntityType(entityType);
    
    if (!fileName) {
      return createResponse({ error: 'Invalid entity type' }, 400);
    }
    
    // Add metadata
    if (!data.metadata) {
      data.metadata = {};
    }
    
    data.metadata.lastModified = new Date().toISOString();
    data.metadata.lastModifiedBy = data.userId || 'system';
    
    if (!data.metadata.version) {
      data.metadata.version = 1;
    } else {
      data.metadata.version++;
    }
    
    // Save file
    const fileId = saveJsonFile(fileName, data, ownerEmail, folderId);
    const metadata = getFileMetadata(fileName, ownerEmail, folderId);
    
    return createResponse({
      success: true,
      fileId: fileId,
      metadata: metadata
    });
    
  } catch (error) {
    Logger.log('Error in saveData: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}

/**
 * Handle batch updates
 */
/**
 * Handle batch GET requests (read multiple files)
 */
function handleBatchRequest(e) {
  try {
    const action = e.parameter.action;
    const types = e.parameter.types; // Comma-separated list: "users,chores,rewards"
    const familyId = e.parameter.familyId;
    
    if (action === 'read' && types) {
      return getBatchData(types, familyId);
    }
    
    return createResponse({ error: 'Invalid batch action or missing types parameter' }, 400);
  } catch (error) {
    Logger.log('Error in handleBatchRequest: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}

/**
 * Handle batch POST requests (write multiple files)
 */
function handleBatchPost(e, data) {
  try {
    const updates = data.updates;
    
    if (!updates || !Array.isArray(updates)) {
      return createResponse({ error: 'Invalid updates array' }, 400);
    }
    
    const results = batchUpdate(updates);
    
    return createResponse({
      success: true,
      results: results
    });
    
  } catch (error) {
    Logger.log('Error in handleBatchPost: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}

/**
 * Get multiple data files in a single request
 * @param {string} types - Comma-separated list of entity types: "users,chores,rewards,family"
 * @param {string} familyId - Optional family ID to filter by
 * @returns {object} Response with all requested data
 */
function getBatchData(types, familyId) {
  try {
    Logger.log('getBatchData: types=' + types + ', familyId=' + (familyId || 'none'));
    
    // Get family info
    const familyInfo = getFamilyInfoForSync(familyId);
    if (!familyInfo) {
      return createResponse({ error: 'Family data not found' }, 404);
    }
    
    const { ownerEmail, folderId, accessToken } = familyInfo;
    
    // Parse types
    const typeList = types.split(',').map(t => t.trim()).filter(t => t.length > 0);
    Logger.log('getBatchData: Parsed types: ' + JSON.stringify(typeList));
    
    // If chores are in the batch, ensure recurring instances are up to date first
    if (typeList.includes('chores')) {
      if (typeof ensureRecurringChoreInstances !== 'undefined') {
        ensureRecurringChoreInstances(ownerEmail, folderId, accessToken);
        if (typeof addDebugLog !== 'undefined') {
          addDebugLog('INFO', 'getBatchData: Called with chores, ensured recurring instances');
        }
      }
    }
    
    // Map entity types to file names
    const typeToFile = {
      'family': FILE_NAMES.FAMILY,
      'users': FILE_NAMES.USERS,
      'chores': FILE_NAMES.CHORES,
      'rewards': FILE_NAMES.REWARDS,
      'reward_redemptions': FILE_NAMES.REWARD_REDEMPTIONS,
      'transactions': FILE_NAMES.TRANSACTIONS,
      'activity_log': FILE_NAMES.ACTIVITY_LOG
    };
    
    // Load all requested files
    const results = {};
    const errors = {};
    
    for (const type of typeList) {
      const fileName = typeToFile[type];
      if (!fileName) {
        Logger.log('getBatchData: Unknown type: ' + type);
        errors[type] = 'Unknown entity type';
        continue;
      }
      
      try {
        Logger.log('getBatchData: Loading ' + type + ' from ' + fileName);
        const data = loadJsonFileV3(fileName, ownerEmail, folderId, accessToken);
        results[type] = data;
        Logger.log('getBatchData: Successfully loaded ' + type);
      } catch (error) {
        Logger.log('getBatchData: Error loading ' + type + ': ' + error.toString());
        errors[type] = error.toString();
        // Continue loading other files even if one fails
      }
    }
    
    return createResponse({
      success: true,
      data: results,
      errors: Object.keys(errors).length > 0 ? errors : undefined
    });
    
  } catch (error) {
    Logger.log('Error in getBatchData: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}

/**
 * Optimize polling response (304 Not Modified)
 */
function checkNotModified(entityType, clientTimestamp, clientETag) {
  const metadata = getFileMetadata(getFileNameForEntityType(entityType));
  
  if (!metadata) {
    return false;
  }
  
  // Check if file hasn't been modified
  if (clientTimestamp && metadata.lastModified === clientTimestamp) {
    return true;
  }
  
  // Check ETag if provided
  if (clientETag) {
    const currentETag = Utilities.base64Encode(metadata.lastModified + metadata.size);
    if (currentETag === clientETag) {
      return true;
    }
  }
  
  return false;
}

/**
 * Delete all data (only accessible by primary parent)
 */
function deleteAllData(data) {
  try {
    const userId = data.userId;
    const familyId = data.familyId;
    
    if (!userId || !familyId) {
      return createResponse({ error: 'Missing userId or familyId' }, 400);
    }
    
    Logger.log('deleteAllData: Looking for family with ID: ' + familyId);
    Logger.log('deleteAllData: userId: ' + userId);
    
    // Get ownerEmail, folderId, and accessToken from family data using familyId
    const familyInfo = getFamilyInfoForSync(familyId);
    if (!familyInfo) {
      Logger.log('deleteAllData: Family info not found for familyId: ' + familyId);
      return createResponse({ error: 'Family data not found' }, 404);
    }
    
    const { ownerEmail, folderId, accessToken } = familyInfo;
    Logger.log('deleteAllData: Found family - ownerEmail: ' + ownerEmail + ', folderId: ' + folderId);
    
    // Load family data to verify user is primary parent
    const familyData = loadJsonFileV3(FILE_NAMES.FAMILY, ownerEmail, folderId, accessToken);
    if (!familyData) {
      Logger.log('deleteAllData: Family data file not found');
      return createResponse({ error: 'Family data not found' }, 404);
    }
    
    // Find user in family members
    const user = familyData.members.find(m => m.id === userId);
    if (!user) {
      Logger.log('deleteAllData: User not found in family members');
      return createResponse({ error: 'User not found' }, 404);
    }
    
    // Verify user is primary parent
    if (!user.isPrimaryParent || user.role !== 'parent') {
      Logger.log('deleteAllData: User is not primary parent');
      return createResponse({ error: 'Only primary parent can delete all data' }, 403);
    }
    
    Logger.log('deleteAllData: User verified as primary parent, proceeding with deletion');
    
    // Delete all data files except keep the primary parent user
    const primaryParent = user;
    
    Logger.log('deleteAllData: Clearing chores file...');
    const choresResult = saveJsonFileV3(FILE_NAMES.CHORES, { chores: [] }, ownerEmail, folderId, accessToken);
    Logger.log('deleteAllData: Chores file saved: ' + (choresResult ? 'SUCCESS' : 'FAILED'));
    
    Logger.log('deleteAllData: Clearing recurring chore templates file...');
    const templatesResult = saveJsonFileV3(FILE_NAMES.RECURRING_CHORE_TEMPLATES, { templates: [] }, ownerEmail, folderId, accessToken);
    Logger.log('deleteAllData: Recurring templates file saved: ' + (templatesResult ? 'SUCCESS' : 'FAILED'));
    
    Logger.log('deleteAllData: Clearing rewards file...');
    const rewardsResult = saveJsonFileV3(FILE_NAMES.REWARDS, { rewards: [] }, ownerEmail, folderId, accessToken);
    Logger.log('deleteAllData: Rewards file saved: ' + (rewardsResult ? 'SUCCESS' : 'FAILED'));
    
    Logger.log('deleteAllData: Clearing transactions file...');
    const transactionsResult = saveJsonFileV3(FILE_NAMES.TRANSACTIONS, { transactions: [] }, ownerEmail, folderId, accessToken);
    Logger.log('deleteAllData: Transactions file saved: ' + (transactionsResult ? 'SUCCESS' : 'FAILED'));
    
    Logger.log('deleteAllData: Clearing activity log file...');
    const activityLogResult = saveJsonFileV3(FILE_NAMES.ACTIVITY_LOG, { logs: [] }, ownerEmail, folderId, accessToken);
    Logger.log('deleteAllData: Activity log file saved: ' + (activityLogResult ? 'SUCCESS' : 'FAILED'));
    
    Logger.log('deleteAllData: Resetting users file to only include primary parent...');
    const usersResult = saveJsonFileV3(FILE_NAMES.USERS, { users: [primaryParent] }, ownerEmail, folderId, accessToken);
    Logger.log('deleteAllData: Users file saved: ' + (usersResult ? 'SUCCESS' : 'FAILED'));
    Logger.log('deleteAllData: Primary parent user ID: ' + primaryParent.id);
    Logger.log('deleteAllData: Primary parent user name: ' + primaryParent.name);
    
    // Reset family members to only include primary parent
    Logger.log('deleteAllData: Resetting family members...');
    familyData.members = [primaryParent];
    if (!familyData.metadata) {
      familyData.metadata = {};
    }
    familyData.metadata.lastModified = new Date().toISOString();
    familyData.metadata.lastModifiedBy = userId;
    familyData.metadata.version = (familyData.metadata.version || 0) + 1;
    const familyResult = saveJsonFileV3(FILE_NAMES.FAMILY, familyData, ownerEmail, folderId, accessToken);
    Logger.log('deleteAllData: Family file saved: ' + (familyResult ? 'SUCCESS' : 'FAILED'));
    Logger.log('deleteAllData: Family members count: ' + familyData.members.length);
    
    // Verify deletion by loading files back
    Logger.log('deleteAllData: Verifying deletion...');
    const verifyChores = loadJsonFileV3(FILE_NAMES.CHORES, ownerEmail, folderId, accessToken);
    const verifyRewards = loadJsonFileV3(FILE_NAMES.REWARDS, ownerEmail, folderId, accessToken);
    const verifyUsers = loadJsonFileV3(FILE_NAMES.USERS, ownerEmail, folderId, accessToken);
    Logger.log('deleteAllData: Verification - Chores count: ' + (verifyChores?.chores?.length || 0));
    Logger.log('deleteAllData: Verification - Rewards count: ' + (verifyRewards?.rewards?.length || 0));
    Logger.log('deleteAllData: Verification - Users count: ' + (verifyUsers?.users?.length || 0));
    
    Logger.log('deleteAllData: All data deleted successfully');
    
    // Log the deletion
    logActivity({
      actorId: userId,
      actorName: primaryParent.name,
      actorRole: primaryParent.role,
      actionType: 'settings_changed',
      details: {
        reason: 'All data deleted by primary parent'
      }
    }, ownerEmail, folderId, accessToken);
    
    return createResponse({
      success: true,
      message: 'All data deleted successfully. Primary parent account preserved.'
    });
    
  } catch (error) {
    Logger.log('Error in deleteAllData: ' + error.toString());
    Logger.log('Error stack: ' + (error.stack || 'no stack'));
    return createResponse({ error: error.toString() }, 500);
  }
}
