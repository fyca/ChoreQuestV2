/**
 * ChoreQuest Google Apps Script Connector
 * Main entry point and routing
 */

// Configuration
const FOLDER_NAME = 'ChoreQuest_Data';
const FILE_NAMES = {
  FAMILY: 'family.json',
  USERS: 'users.json',
  CHORES: 'chores.json',
  REWARDS: 'rewards.json',
  TRANSACTIONS: 'transactions.json',
  ACTIVITY_LOG: 'activity_log.json'
};

/**
 * Handle GET requests
 */
function doGet(e) {
  try {
    const path = e.parameter.path || '';
    const action = e.parameter.action || '';
    
    // Route to appropriate handler
    if (path === 'auth') {
      return handleAuthRequest(e);
    } else if (path === 'data') {
      return handleDataRequest(e);
    } else if (path === 'sync') {
      return handleSyncRequest(e);
    } else if (path === 'users') {
      return handleUsersRequest(e);
    } else if (path === 'chores') {
      return handleChoresRequest(e);
    } else if (path === 'rewards') {
      return handleRewardsRequest(e);
    } else if (path === 'activity') {
      return handleActivityLogsRequest(e);
    }
    
    return createResponse({ error: 'Invalid path' }, 400);
  } catch (error) {
    Logger.log('Error in doGet: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}

/**
 * Handle POST requests
 */
function doPost(e) {
  try {
    // Log incoming request for debugging
    Logger.log('doPost called');
    Logger.log('Parameters: ' + JSON.stringify(e.parameter));
    Logger.log('Post data: ' + (e.postData ? e.postData.contents : 'none'));
    
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
    }
    
    // If we get here, path was invalid
    Logger.log('No matching path found. Available paths: auth, data, users, chores, rewards, batch, photos');
    return createResponse({ 
      success: false,
      error: 'Invalid path: "' + path + '"',
      availablePaths: ['auth', 'data', 'users', 'chores', 'rewards', 'batch', 'photos']
    }, 400);
  } catch (error) {
    Logger.log('Error in doPost: ' + error.toString());
    Logger.log('Error stack: ' + error.stack);
    return createResponse({ 
      success: false,
      error: error.toString(),
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
 * Get or create ChoreQuest folder
 * Uses PropertiesService to cache folder ID to avoid searching all folders
 * This allows it to work with drive.file scope
 */
function getChoreQuestFolder() {
  const PROPERTY_KEY = 'CHOREQUEST_FOLDER_ID';
  const properties = PropertiesService.getScriptProperties();
  
  // Try to get cached folder ID
  const cachedFolderId = properties.getProperty(PROPERTY_KEY);
  
  if (cachedFolderId) {
    try {
      const folder = DriveApp.getFolderById(cachedFolderId);
      // Verify folder still exists and has correct name
      if (folder.getName() === FOLDER_NAME) {
        return folder;
      }
    } catch (error) {
      // Folder doesn't exist or was deleted, clear cache
      Logger.log('Cached folder ID invalid, clearing cache: ' + error.toString());
      properties.deleteProperty(PROPERTY_KEY);
    }
  }
  
  // Folder not found in cache or invalid, try to find it
  // First try using root folder (requires full Drive scope)
  try {
    const rootFolder = DriveApp.getRootFolder();
    const folders = rootFolder.getFoldersByName(FOLDER_NAME);
    
    if (folders.hasNext()) {
      const folder = folders.next();
      // Cache the folder ID for future use
      properties.setProperty(PROPERTY_KEY, folder.getId());
      return folder;
    }
  } catch (error) {
    // If root folder access fails, try searching all folders (requires full Drive scope)
    Logger.log('Root folder access failed, trying search: ' + error.toString());
    try {
      const folders = DriveApp.getFoldersByName(FOLDER_NAME);
      if (folders.hasNext()) {
        const folder = folders.next();
        properties.setProperty(PROPERTY_KEY, folder.getId());
        return folder;
      }
    } catch (searchError) {
      Logger.log('Folder search also failed: ' + searchError.toString());
    }
  }
  
  // Folder doesn't exist, create it in root
  let folder;
  try {
    const rootFolder = DriveApp.getRootFolder();
    folder = rootFolder.createFolder(FOLDER_NAME);
  } catch (error) {
    // If root folder access fails, create in Drive root (requires full Drive scope)
    Logger.log('Cannot access root folder, creating in Drive root: ' + error.toString());
    folder = DriveApp.createFolder(FOLDER_NAME);
  }
  
  folder.setDescription('ChoreQuest app data storage');
  
  // Cache the folder ID
  properties.setProperty(PROPERTY_KEY, folder.getId());
  
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
 * Initialize family data structure
 * @param {string} userEmail - User's email address
 * @param {string} userName - User's display name from Google account
 * @param {string} userPicture - User's profile picture URL from Google account (optional)
 */
function initializeFamilyData(userEmail, userName, userPicture) {
  const folder = getChoreQuestFolder();
  const familyId = Utilities.getUuid();
  const userId = Utilities.getUuid();
  
  // Use provided userName or fall back to email prefix
  const displayName = userName || userEmail.split('@')[0];
  
  const familyData = {
    id: familyId,
    name: 'My Family',
    ownerId: userEmail,
    ownerEmail: userEmail,
    driveFileId: folder.getId(),
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
  
  // Save to Drive
  saveJsonFile(FILE_NAMES.FAMILY, familyData);
  saveJsonFile(FILE_NAMES.USERS, { users: [primaryUser] });
  saveJsonFile(FILE_NAMES.CHORES, { chores: [] });
  saveJsonFile(FILE_NAMES.REWARDS, { rewards: [] });
  saveJsonFile(FILE_NAMES.TRANSACTIONS, { transactions: [] });
  saveJsonFile(FILE_NAMES.ACTIVITY_LOG, { logs: [] });
  
  return { familyData, primaryUser };
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
    const { base64Data, fileName, mimeType, choreId } = data;
    
    if (!base64Data || !fileName || !mimeType) {
      return createResponse({ 
        success: false,
        error: 'Missing required fields: base64Data, fileName, mimeType' 
      }, 400);
    }
    
    // Validate MIME type
    if (!mimeType.startsWith('image/')) {
      return createResponse({ 
        success: false,
        error: 'Invalid MIME type. Must be an image.' 
      }, 400);
    }
    
    const result = uploadImage(base64Data, fileName, mimeType, choreId);
    
    if (result.success) {
      // Log upload activity
      try {
        logActivity({
          type: 'photo_uploaded',
          userId: data.userId || 'unknown',
          choreId: choreId || null,
          details: {
            fileName: fileName,
            fileId: result.fileId,
            size: result.size
          },
          timestamp: new Date().toISOString()
        });
      } catch (logError) {
        Logger.log('Failed to log activity: ' + logError.toString());
      }
      
      return createResponse(result, 200);
    } else {
      return createResponse(result, 500);
    }
  } catch (error) {
    Logger.log('Error in uploadPhotoHandler: ' + error.toString());
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
 * Handle activity log GET requests
 */
function handleActivityLogsRequest(e) {
  try {
    const options = {
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
