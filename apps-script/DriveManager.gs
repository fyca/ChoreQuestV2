/**
 * DriveManager.gs
 * Handles all Google Drive file operations
 */

/**
 * Save JSON data to a file
 */
function saveJsonFile(fileName, data) {
  const folder = getChoreQuestFolder();
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
 */
function loadJsonFile(fileName) {
  const folder = getChoreQuestFolder();
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
 */
function getFileMetadata(fileName) {
  const folder = getChoreQuestFolder();
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
 */
function getAllFileMetadata() {
  const metadata = {};
  
  Object.values(FILE_NAMES).forEach(fileName => {
    const fileMeta = getFileMetadata(fileName);
    if (fileMeta) {
      metadata[fileName] = fileMeta;
    }
  });
  
  return metadata;
}

/**
 * Delete a file
 */
function deleteJsonFile(fileName) {
  const folder = getChoreQuestFolder();
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
 */
function isFileModifiedSince(fileName, sinceTimestamp) {
  const metadata = getFileMetadata(fileName);
  
  if (!metadata) {
    return false;
  }
  
  const fileTime = new Date(metadata.lastModified).getTime();
  const sinceTime = new Date(sinceTimestamp).getTime();
  
  return fileTime > sinceTime;
}

/**
 * Get incremental changes since timestamp
 */
function getIncrementalChanges(entityType, sinceTimestamp) {
  const fileName = getFileNameForEntityType(entityType);
  
  if (!isFileModifiedSince(fileName, sinceTimestamp)) {
    return {
      hasChanges: false,
      data: null,
      metadata: getFileMetadata(fileName)
    };
  }
  
  const data = loadJsonFile(fileName);
  
  return {
    hasChanges: true,
    data: data,
    metadata: getFileMetadata(fileName)
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
 */
function batchUpdate(updates) {
  const results = {};
  
  updates.forEach(update => {
    const fileName = getFileNameForEntityType(update.entityType);
    if (fileName) {
      try {
        const fileId = saveJsonFile(fileName, update.data);
        results[update.entityType] = {
          success: true,
          fileId: fileId,
          metadata: getFileMetadata(fileName)
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
 */
function createBackup() {
  const folder = getChoreQuestFolder();
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
function uploadImage(base64Data, fileName, mimeType, choreId) {
  try {
    // Get or create Photos folder inside ChoreQuest folder
    const choreQuestFolder = getChoreQuestFolder();
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
