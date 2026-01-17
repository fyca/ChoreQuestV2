/**
 * DriveManager.gs
 * Handles all Google Drive file operations
 * 
 * NOTE: This file now acts as a wrapper that uses DriveManagerV3.gs functions
 * for better compatibility with USER_ACCESSING deployment and mobile app API calls
 */

/**
 * Save JSON data to a file
 * Uses Drive API v3 for better compatibility with mobile app API calls
 * @param {string} fileName - Name of the file
 * @param {object} data - Data to save
 * @param {string} ownerEmail - Owner email to identify family folder (optional if folderId provided)
 * @param {string} folderId - Optional: Folder ID to access folder directly (for shared folders)
 */
function saveJsonFile(fileName, data, ownerEmail, folderId, accessToken) {
  // Use Drive API v3 version
  try {
    return saveJsonFileV3(fileName, data, ownerEmail, folderId, accessToken);
  } catch (error) {
    Logger.log('Error in saveJsonFileV3, falling back to legacy: ' + error.toString());
    // Fallback to legacy implementation
    return saveJsonFileLegacy(fileName, data, ownerEmail, folderId);
  }
}

/**
 * Legacy implementation using DriveApp (kept for fallback)
 */
function saveJsonFileLegacy(fileName, data, ownerEmail, folderId) {
  let folder;
  
  // If folderId is provided, use it directly (works for shared folders)
  if (folderId) {
    try {
      folder = DriveApp.getFolderById(folderId);
    } catch (error) {
      Logger.log('ERROR: Cannot access folder by ID: ' + error.toString());
      throw new Error('Cannot access folder by ID: ' + error.toString());
    }
  } else if (ownerEmail) {
    // Use ownerEmail to get folder
    folder = getChoreQuestFolder(ownerEmail);
  } else {
    // Try to save to current user's Drive (for parent operations)
    // With USER_ACCESSING, this accesses the authenticated user's Drive
    try {
      const rootFolder = DriveApp.getRootFolder();
      Logger.log('Accessing root folder for authenticated user');
      const folders = rootFolder.getFoldersByName(FOLDER_NAME);
      if (folders.hasNext()) {
        folder = folders.next();
        Logger.log('Found folder in authenticated user\'s Drive: ' + folder.getId());
      } else {
        // Folder doesn't exist, create it
        Logger.log('Folder not found, creating in authenticated user\'s Drive');
        folder = rootFolder.createFolder(FOLDER_NAME);
        folder.setDescription('ChoreQuest app data storage');
        Logger.log('Created folder: ' + folder.getId());
      }
    } catch (error) {
      Logger.log('ERROR accessing authenticated user\'s Drive: ' + error.toString());
      const errorStr = error.toString().toLowerCase();
      
      // Check if it's an authorization error
      if (errorStr.includes('unauthorized') || errorStr.includes('401') || errorStr.includes('permission') || errorStr.includes('access denied')) {
        Logger.log('Authorization error - user needs to authorize Drive access');
        throw new Error('Drive access not authorized. User must visit the web app URL to authorize Drive access.');
      }
      
      Logger.log('This might indicate the deployment is not set to USER_ACCESSING');
      throw new Error('Cannot access Drive: ' + error.toString());
    }
  }
  
  const files = folder.getFilesByName(fileName);
  const jsonString = JSON.stringify(data, null, 2);
  
  if (files.hasNext()) {
    // Update existing file
    const file = files.next();
    file.setContent(jsonString);
    return file.getId();
  } else {
    // Create new file
    const file = folder.createFile(fileName, jsonString, MimeType.PLAIN_TEXT);
    return file.getId();
  }
}

/**
 * Load JSON data from a file
 * Uses Drive API v3 for better compatibility with mobile app API calls
 * @param {string} fileName - Name of the file
 * @param {string} ownerEmail - Owner email to identify family folder (optional if folderId provided)
 * @param {string} folderId - Optional: Folder ID to access folder directly (for shared folders)
 */
function loadJsonFile(fileName, ownerEmail, folderId, accessToken) {
  // Use Drive API v3 version
  try {
    return loadJsonFileV3(fileName, ownerEmail, folderId, accessToken);
  } catch (error) {
    Logger.log('Error in loadJsonFileV3, falling back to legacy: ' + error.toString());
    // Fallback to legacy implementation
    return loadJsonFileLegacy(fileName, ownerEmail, folderId);
  }
}

/**
 * Legacy implementation using DriveApp (kept for fallback)
 */
function loadJsonFileLegacy(fileName, ownerEmail, folderId) {
  let folder;
  
  // If folderId is provided, use it directly (works for shared folders)
  if (folderId) {
    try {
      folder = DriveApp.getFolderById(folderId);
    } catch (error) {
      Logger.log('ERROR: Cannot access folder by ID: ' + error.toString());
      throw new Error('Cannot access folder by ID: ' + error.toString());
    }
  } else if (ownerEmail) {
    // Use ownerEmail to get folder
    folder = getChoreQuestFolder(ownerEmail);
  } else {
    // Try to load from current user's Drive (for parent operations)
    // With USER_ACCESSING, this accesses the authenticated user's Drive
    try {
      const rootFolder = DriveApp.getRootFolder();
      Logger.log('Accessing root folder for authenticated user');
      const folders = rootFolder.getFoldersByName(FOLDER_NAME);
      if (folders.hasNext()) {
        folder = folders.next();
        Logger.log('Found folder in authenticated user\'s Drive: ' + folder.getId());
      } else {
        Logger.log('Folder not found in authenticated user\'s Drive - returning null');
        // Return null instead of throwing error - folder might not exist yet
        return null;
      }
    } catch (error) {
      Logger.log('ERROR accessing authenticated user\'s Drive: ' + error.toString());
      const errorStr = error.toString().toLowerCase();
      
      // Check if it's an authorization error
      if (errorStr.includes('unauthorized') || errorStr.includes('401') || errorStr.includes('permission') || errorStr.includes('access denied')) {
        Logger.log('Authorization error - user needs to authorize Drive access');
        // Throw a specific error that can be caught and handled
        throw new Error('Drive access not authorized. User must visit the web app URL to authorize Drive access.');
      }
      
      Logger.log('This might indicate the deployment is not set to USER_ACCESSING');
      // Return null instead of throwing for other errors - let caller handle missing data
      return null;
    }
  }
  
  const files = folder.getFilesByName(fileName);
  
  if (files.hasNext()) {
    const file = files.next();
    const content = file.getBlob().getDataAsString();
    return JSON.parse(content);
  }
  
  return null;
}

/**
 * Get file metadata (for polling)
 * Uses Drive API v3 for better compatibility with mobile app API calls
 * @param {string} fileName - Name of the file
 * @param {string} ownerEmail - Owner email to identify family folder (optional if folderId provided)
 * @param {string} folderId - Optional: Folder ID to access folder directly (for shared folders)
 */
function getFileMetadata(fileName, ownerEmail, folderId, accessToken) {
  // Use Drive API v3 version
  try {
    const metadata = getFileMetadataV3(fileName, ownerEmail, folderId, accessToken);
    if (!metadata) return null;
    // Convert to legacy format for backward compatibility
    return {
      fileName: metadata.name,
      lastModified: metadata.modifiedTime,
      size: metadata.size,
      id: metadata.id
    };
  } catch (error) {
    Logger.log('Error in getFileMetadataV3, falling back to legacy: ' + error.toString());
    // Fallback to legacy implementation
    return getFileMetadataLegacy(fileName, ownerEmail, folderId);
  }
}

/**
 * Legacy implementation using DriveApp (kept for fallback)
 */
function getFileMetadataLegacy(fileName, ownerEmail, folderId) {
  let folder;
  
  // If folderId is provided, use it directly (works for shared folders)
  if (folderId) {
    try {
      folder = DriveApp.getFolderById(folderId);
    } catch (error) {
      Logger.log('ERROR: Cannot access folder by ID: ' + error.toString());
      throw new Error('Cannot access folder by ID: ' + error.toString());
    }
  } else if (ownerEmail) {
    // Use ownerEmail to get folder
    folder = getChoreQuestFolder(ownerEmail);
  } else {
    // Try to load from current user's Drive (for parent operations)
    try {
      const rootFolder = DriveApp.getRootFolder();
      const folders = rootFolder.getFoldersByName(FOLDER_NAME);
      if (folders.hasNext()) {
        folder = folders.next();
      } else {
        Logger.log('ERROR: ownerEmail or folderId is required for getFileMetadata');
        throw new Error('ownerEmail or folderId is required');
      }
    } catch (error) {
      Logger.log('ERROR: ownerEmail or folderId is required for getFileMetadata');
      throw new Error('ownerEmail or folderId is required');
    }
  }
  
  const files = folder.getFilesByName(fileName);
  
  if (files.hasNext()) {
    const file = files.next();
    return {
      fileName: fileName,
      lastModified: file.getLastUpdated().toISOString(),
      size: file.getSize(),
      id: file.getId()
    };
  }
  
  return null;
}

/**
 * Get all file metadata (for sync status)
 * Uses Drive API v3 for better compatibility with mobile app API calls
 * @param {string} ownerEmail - Owner email to identify family folder (optional if folderId provided)
 * @param {string} folderId - Optional: Folder ID to access folder directly (for shared folders)
 */
function getAllFileMetadata(ownerEmail, folderId, accessToken) {
  // Use Drive API v3 version
  try {
    const allFiles = getAllFileMetadataV3(ownerEmail, folderId, accessToken);
    const metadata = {};
    
    // Convert to legacy format
    allFiles.forEach(file => {
      const fileName = file.name;
      if (Object.values(FILE_NAMES).includes(fileName)) {
        metadata[fileName] = {
          fileName: file.name,
          lastModified: file.modifiedTime,
          size: file.size,
          id: file.id
        };
      }
    });
    
    return metadata;
  } catch (error) {
    Logger.log('Error in getAllFileMetadataV3, falling back to legacy: ' + error.toString());
    // Fallback to legacy implementation
    const metadata = {};
    Object.values(FILE_NAMES).forEach(fileName => {
      const fileMeta = getFileMetadataLegacy(fileName, ownerEmail, folderId);
      if (fileMeta) {
        metadata[fileName] = fileMeta;
      }
    });
    return metadata;
  }
}

/**
 * Delete a file
 * @param {string} fileName - Name of the file
 * @param {string} ownerEmail - Owner email to identify family folder (required)
 */
function deleteJsonFile(fileName, ownerEmail) {
  if (!ownerEmail) {
    Logger.log('ERROR: ownerEmail is required for deleteJsonFile');
    throw new Error('ownerEmail is required');
  }
  const folder = getChoreQuestFolder(ownerEmail);
  const files = folder.getFilesByName(fileName);
  
  if (files.hasNext()) {
    const file = files.next();
    file.setTrashed(true);
    return true;
  }
  
  return false;
}

/**
 * Check if file has been modified since timestamp
 * @param {string} fileName - Name of the file
 * @param {string} sinceTimestamp - Timestamp to check against
 * @param {string} ownerEmail - Owner email to identify family folder (required)
 */
function isFileModifiedSince(fileName, sinceTimestamp, ownerEmail, folderId, accessToken) {
  const metadata = accessToken 
    ? getFileMetadataV3(fileName, ownerEmail, folderId, accessToken)
    : getFileMetadata(fileName, ownerEmail, folderId);
  
  if (!metadata) {
    return false;
  }
  
  const fileTime = new Date(metadata.lastModified).getTime();
  const sinceTime = new Date(sinceTimestamp).getTime();
  
  return fileTime > sinceTime;
}

/**
 * Get incremental changes since timestamp
 * @param {string} entityType - Type of entity
 * @param {string} sinceTimestamp - Timestamp to check against
 * @param {string} ownerEmail - Owner email to identify family folder (required)
 */
function getIncrementalChanges(entityType, sinceTimestamp, ownerEmail, folderId, accessToken) {
  if (!ownerEmail) {
    Logger.log('ERROR: ownerEmail is required for getIncrementalChanges');
    throw new Error('ownerEmail is required');
  }
  const fileName = getFileNameForEntityType(entityType);
  
  if (!isFileModifiedSince(fileName, sinceTimestamp, ownerEmail, folderId, accessToken)) {
    return {
      hasChanges: false,
      data: null,
      metadata: accessToken 
        ? getFileMetadataV3(fileName, ownerEmail, folderId, accessToken)
        : getFileMetadata(fileName, ownerEmail, folderId)
    };
  }
  
  const data = accessToken 
    ? loadJsonFileV3(fileName, ownerEmail, folderId, accessToken)
    : loadJsonFile(fileName, ownerEmail, folderId);
  
  return {
    hasChanges: true,
    data: data,
    metadata: accessToken 
      ? getFileMetadataV3(fileName, ownerEmail, folderId, accessToken)
      : getFileMetadata(fileName, ownerEmail, folderId)
  };
}

/**
 * Map entity type to file name
 */
function getFileNameForEntityType(entityType) {
  const mapping = {
    'family': FILE_NAMES.FAMILY,
    'users': FILE_NAMES.USERS,
    'chores': FILE_NAMES.CHORES,
    'rewards': FILE_NAMES.REWARDS,
    'transactions': FILE_NAMES.TRANSACTIONS,
    'activity_log': FILE_NAMES.ACTIVITY_LOG
  };
  
  return mapping[entityType] || null;
}

/**
 * Batch update multiple files
 * @param {Array} updates - Array of update objects with entityType and data
 * @param {string} ownerEmail - Owner email to identify family folder (required)
 */
function batchUpdate(updates, ownerEmail) {
  if (!ownerEmail) {
    Logger.log('ERROR: ownerEmail is required for batchUpdate');
    throw new Error('ownerEmail is required');
  }
  const results = {};
  
  updates.forEach(update => {
    const fileName = getFileNameForEntityType(update.entityType);
    if (fileName) {
      try {
        const fileId = saveJsonFile(fileName, update.data, ownerEmail);
        results[update.entityType] = {
          success: true,
          fileId: fileId,
          metadata: getFileMetadata(fileName, ownerEmail)
        };
      } catch (error) {
        results[update.entityType] = {
          success: false,
          error: error.toString()
        };
      }
    }
  });
  
  return results;
}

/**
 * Create backup of all data
 * @param {string} ownerEmail - Owner email to identify family folder (required)
 */
function createBackup(ownerEmail) {
  if (!ownerEmail) {
    Logger.log('ERROR: ownerEmail is required for createBackup');
    throw new Error('ownerEmail is required');
  }
  const folder = getChoreQuestFolder(ownerEmail);
  const timestamp = Utilities.formatDate(new Date(), Session.getScriptTimeZone(), 'yyyy-MM-dd_HHmmss');
  const backupFolderName = 'Backup_' + timestamp;
  
  const backupFolder = folder.createFolder(backupFolderName);
  
  Object.values(FILE_NAMES).forEach(fileName => {
    const files = folder.getFilesByName(fileName);
    if (files.hasNext()) {
      const file = files.next();
      file.makeCopy(fileName, backupFolder);
    }
  });
  
  return {
    success: true,
    backupFolder: backupFolderName,
    backupId: backupFolder.getId(),
    timestamp: timestamp
  };
}

/**
 * Upload image file to Drive
 * @param {string} base64Data - Base64 encoded image data
 * @param {string} fileName - Name for the file
 * @param {string} mimeType - Image MIME type (image/jpeg, image/png, etc.)
 * @param {string} choreId - Optional: chore ID to organize files
 * @returns {object} File metadata with URL and ID
 */
function uploadImage(base64Data, fileName, mimeType, choreId, ownerEmail) {
  if (!ownerEmail) {
    Logger.log('ERROR: ownerEmail is required for uploadImage');
    throw new Error('ownerEmail is required');
  }
  try {
    // Get or create Photos folder inside ChoreQuest folder
    const choreQuestFolder = getChoreQuestFolder(ownerEmail);
    const photosFolderName = 'ChorePhotos';
    
    let photosFolder;
    const folders = choreQuestFolder.getFoldersByName(photosFolderName);
    if (folders.hasNext()) {
      photosFolder = folders.next();
    } else {
      photosFolder = choreQuestFolder.createFolder(photosFolderName);
    }
    
    // Optionally organize by chore ID
    let targetFolder = photosFolder;
    if (choreId) {
      const choreFolders = photosFolder.getFoldersByName(choreId);
      if (choreFolders.hasNext()) {
        targetFolder = choreFolders.next();
      } else {
        targetFolder = photosFolder.createFolder(choreId);
      }
    }
    
    // Decode base64 and create blob
    const blob = Utilities.newBlob(
      Utilities.base64Decode(base64Data),
      mimeType,
      fileName
    );
    
    // Create file in Drive
    const file = targetFolder.createFile(blob);
    
    // Make file accessible via link (anyone with link can view)
    file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
    
    // Return file info
    return {
      success: true,
      fileId: file.getId(),
      fileName: file.getName(),
      url: file.getUrl(),
      downloadUrl: file.getDownloadUrl(),
      thumbnailUrl: file.getThumbnailLink(),
      webViewLink: 'https://drive.google.com/file/d/' + file.getId() + '/view',
      size: file.getSize(),
      mimeType: file.getMimeType(),
      createdDate: file.getDateCreated().toISOString()
    };
    
  } catch (error) {
    Logger.log('Error uploading image: ' + error.toString());
    return {
      success: false,
      error: error.toString()
    };
  }
}

/**
 * Delete image file from Drive
 * @param {string} fileId - Drive file ID
 */
function deleteImage(fileId) {
  try {
    const file = DriveApp.getFileById(fileId);
    file.setTrashed(true);
    return { success: true };
  } catch (error) {
    Logger.log('Error deleting image: ' + error.toString());
    return { success: false, error: error.toString() };
  }
}

/**
 * Get image URL by file ID
 * @param {string} fileId - Drive file ID
 */
function getImageUrl(fileId) {
  try {
    const file = DriveApp.getFileById(fileId);
    return {
      success: true,
      url: file.getUrl(),
      downloadUrl: file.getDownloadUrl(),
      thumbnailUrl: file.getThumbnailLink(),
      webViewLink: 'https://drive.google.com/file/d/' + fileId + '/view'
    };
  } catch (error) {
    Logger.log('Error getting image URL: ' + error.toString());
    return { success: false, error: error.toString() };
  }
}
