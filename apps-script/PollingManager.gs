/**
 * PollingManager.gs
 * Handles efficient polling endpoints for data sync
 */

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
    const metadata = getAllFileMetadata();
    
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
    const since = e.parameter.since;
    const entityTypes = e.parameter.types ? e.parameter.types.split(',') : ['chores', 'rewards', 'users', 'transactions', 'activity_log'];
    
    if (!since) {
      return createResponse({ error: 'Missing since parameter' }, 400);
    }
    
    const changes = {};
    let hasAnyChanges = false;
    
    entityTypes.forEach(entityType => {
      const result = getIncrementalChanges(entityType, since);
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
    return getData(entityType);
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
function getData(entityType) {
  try {
    const fileName = getFileNameForEntityType(entityType);
    
    if (!fileName) {
      return createResponse({ error: 'Invalid entity type' }, 400);
    }
    
    const data = loadJsonFile(fileName);
    const metadata = getFileMetadata(fileName);
    
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
    const fileName = getFileNameForEntityType(entityType);
    
    if (!fileName) {
      return createResponse({ error: 'Invalid entity type' }, 400);
    }
    
    if (!since) {
      return getData(entityType);
    }
    
    const result = getIncrementalChanges(entityType, since);
    
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
    const fileId = saveJsonFile(fileName, data);
    const metadata = getFileMetadata(fileName);
    
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
    
    // Load family data to verify user is primary parent
    const familyData = loadJsonFile(FILE_NAMES.FAMILY);
    if (!familyData) {
      return createResponse({ error: 'Family data not found' }, 404);
    }
    
    // Find user in family members
    const user = familyData.members.find(m => m.id === userId);
    if (!user) {
      return createResponse({ error: 'User not found' }, 404);
    }
    
    // Verify user is primary parent
    if (!user.isPrimaryParent || user.role !== 'parent') {
      return createResponse({ error: 'Only primary parent can delete all data' }, 403);
    }
    
    // Delete all data files except keep the primary parent user
    const primaryParent = user;
    
    // Clear all data files
    saveJsonFile(FILE_NAMES.CHORES, { chores: [] });
    saveJsonFile(FILE_NAMES.REWARDS, { rewards: [] });
    saveJsonFile(FILE_NAMES.TRANSACTIONS, { transactions: [] });
    saveJsonFile(FILE_NAMES.ACTIVITY_LOG, { logs: [] });
    
    // Reset users to only include primary parent
    saveJsonFile(FILE_NAMES.USERS, { users: [primaryParent] });
    
    // Reset family members to only include primary parent
    familyData.members = [primaryParent];
    familyData.metadata.lastModified = new Date().toISOString();
    familyData.metadata.lastModifiedBy = userId;
    familyData.metadata.version = (familyData.metadata.version || 0) + 1;
    saveJsonFile(FILE_NAMES.FAMILY, familyData);
    
    // Log the deletion
    logActivity({
      actorId: userId,
      actorName: primaryParent.name,
      actorRole: primaryParent.role,
      actionType: 'settings_changed',
      details: {
        reason: 'All data deleted by primary parent'
      }
    });
    
    return createResponse({
      success: true,
      message: 'All data deleted successfully. Primary parent account preserved.'
    });
    
  } catch (error) {
    Logger.log('Error in deleteAllData: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}
