/**
 * SyncManager.gs
 * Handles conflict resolution and versioning
 */

/**
 * Resolve conflicts using last-write-wins strategy
 */
function resolveConflict(serverData, clientData) {
  const serverTime = new Date(serverData.metadata.lastModified).getTime();
  const clientTime = new Date(clientData.metadata.lastModified).getTime();
  
  // Last write wins
  if (clientTime > serverTime) {
    return {
      resolved: clientData,
      winner: 'client',
      serverVersion: serverData.metadata.version,
      clientVersion: clientData.metadata.version
    };
  } else {
    return {
      resolved: serverData,
      winner: 'server',
      serverVersion: serverData.metadata.version,
      clientVersion: clientData.metadata.version
    };
  }
}

/**
 * Merge array data (for collections like chores, rewards)
 */
function mergeArrayData(serverArray, clientArray, idField = 'id') {
  const merged = new Map();
  
  // Add server items
  serverArray.forEach(item => {
    merged.set(item[idField], {
      item: item,
      source: 'server',
      timestamp: item.updatedAt || item.createdAt
    });
  });
  
  // Merge client items (override if newer)
  clientArray.forEach(clientItem => {
    const id = clientItem[idField];
    const existing = merged.get(id);
    
    if (!existing) {
      merged.set(id, {
        item: clientItem,
        source: 'client',
        timestamp: clientItem.updatedAt || clientItem.createdAt
      });
    } else {
      const clientTime = new Date(clientItem.updatedAt || clientItem.createdAt).getTime();
      const existingTime = new Date(existing.timestamp).getTime();
      
      if (clientTime > existingTime) {
        merged.set(id, {
          item: clientItem,
          source: 'client',
          timestamp: clientItem.updatedAt || clientItem.createdAt
        });
      }
    }
  });
  
  // Convert back to array
  return Array.from(merged.values()).map(entry => entry.item);
}

/**
 * Validate data integrity
 */
function validateDataIntegrity(entityType, data) {
  const errors = [];
  
  switch (entityType) {
    case 'chores':
      if (!data.chores || !Array.isArray(data.chores)) {
        errors.push('Missing or invalid chores array');
      }
      break;
      
    case 'rewards':
      if (!data.rewards || !Array.isArray(data.rewards)) {
        errors.push('Missing or invalid rewards array');
      }
      break;
      
    case 'users':
      if (!data.users || !Array.isArray(data.users)) {
        errors.push('Missing or invalid users array');
      }
      break;
      
    case 'transactions':
      if (!data.transactions || !Array.isArray(data.transactions)) {
        errors.push('Missing or invalid transactions array');
      }
      break;
      
    case 'activity_log':
      if (!data.logs || !Array.isArray(data.logs)) {
        errors.push('Missing or invalid logs array');
      }
      break;
      
    case 'family':
      if (!data.id || !data.ownerId) {
        errors.push('Missing required family fields');
      }
      break;
  }
  
  return {
    valid: errors.length === 0,
    errors: errors
  };
}

/**
 * Calculate checksum for data integrity
 */
function calculateChecksum(data) {
  const jsonString = JSON.stringify(data);
  const hash = Utilities.computeDigest(
    Utilities.DigestAlgorithm.MD5,
    jsonString,
    Utilities.Charset.UTF_8
  );
  
  return Utilities.base64Encode(hash);
}

/**
 * Verify checksum
 */
function verifyChecksum(data, expectedChecksum) {
  const actualChecksum = calculateChecksum(data);
  return actualChecksum === expectedChecksum;
}

/**
 * Create version snapshot
 */
function createVersionSnapshot(entityType) {
  try {
    const fileName = getFileNameForEntityType(entityType);
    if (!fileName) {
      return { success: false, error: 'Invalid entity type' };
    }
    
    const data = loadJsonFile(fileName);
    const timestamp = Utilities.formatDate(
      new Date(),
      Session.getScriptTimeZone(),
      'yyyy-MM-dd_HHmmss'
    );
    
    const snapshotFileName = entityType + '_snapshot_' + timestamp + '.json';
    const folder = getChoreQuestFolder();
    
    // Create versions subfolder if it doesn't exist
    const versionsFolders = folder.getFoldersByName('Versions');
    let versionsFolder;
    
    if (versionsFolders.hasNext()) {
      versionsFolder = versionsFolders.next();
    } else {
      versionsFolder = folder.createFolder('Versions');
    }
    
    // Save snapshot
    const snapshotFile = versionsFolder.createFile(
      snapshotFileName,
      JSON.stringify(data, null, 2),
      MimeType.PLAIN_TEXT
    );
    
    return {
      success: true,
      fileName: snapshotFileName,
      fileId: snapshotFile.getId(),
      timestamp: timestamp
    };
    
  } catch (error) {
    return {
      success: false,
      error: error.toString()
    };
  }
}

/**
 * Rollback to previous version
 */
function rollbackToSnapshot(snapshotFileId) {
  try {
    const snapshotFile = DriveApp.getFileById(snapshotFileId);
    const content = snapshotFile.getBlob().getDataAsString();
    const data = JSON.parse(content);
    
    // Determine entity type from filename
    const fileName = snapshotFile.getName();
    let entityType;
    
    if (fileName.includes('chores')) entityType = 'chores';
    else if (fileName.includes('rewards')) entityType = 'rewards';
    else if (fileName.includes('users')) entityType = 'users';
    else if (fileName.includes('transactions')) entityType = 'transactions';
    else if (fileName.includes('activity_log')) entityType = 'activity_log';
    else if (fileName.includes('family')) entityType = 'family';
    else return { success: false, error: 'Cannot determine entity type' };
    
    const targetFileName = getFileNameForEntityType(entityType);
    saveJsonFile(targetFileName, data);
    
    return {
      success: true,
      entityType: entityType,
      restoredFrom: fileName
    };
    
  } catch (error) {
    return {
      success: false,
      error: error.toString()
    };
  }
}
