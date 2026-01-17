/**
 * ActivityLogger.gs
 * Utility functions for logging activities
 */

/**
 * Log an activity
 * @param {object} activity - Activity data to log
 * @param {string} ownerEmail - Owner email to identify family folder (optional if folderId provided)
 * @param {string} folderId - Optional: Folder ID to access folder directly (for shared folders)
 * @param {string} accessToken - Optional: OAuth access token for Drive API v3 calls
 */
function logActivity(activity, ownerEmail, folderId, accessToken) {
  try {
    Logger.log('logActivity: Starting - actionType: ' + activity.actionType + ', actorId: ' + activity.actorId);
    
    // If neither ownerEmail nor folderId provided, try to get from family data
    if (!ownerEmail && !folderId) {
      Logger.log('logActivity: No ownerEmail or folderId provided, attempting to get from family data');
      const familyInfo = getFamilyInfo();
      if (familyInfo) {
        ownerEmail = familyInfo.ownerEmail;
        folderId = familyInfo.folderId;
        accessToken = familyInfo.accessToken;
        Logger.log('logActivity: Got family info - ownerEmail: ' + ownerEmail + ', folderId: ' + folderId);
      } else {
        Logger.log('ERROR: ownerEmail or folderId is required for logActivity');
        throw new Error('ownerEmail or folderId is required');
      }
    }
    
    if (!ownerEmail && !folderId) {
      Logger.log('ERROR: Still missing ownerEmail or folderId after fallback');
      throw new Error('ownerEmail or folderId is required');
    }
    
    Logger.log('logActivity: Loading activity log - ownerEmail: ' + ownerEmail + ', folderId: ' + folderId + ', hasAccessToken: ' + (!!accessToken));
    
    // Use Drive API v3 if accessToken is provided, otherwise fall back to legacy
    let activityLogData;
    try {
      activityLogData = accessToken 
        ? loadJsonFileV3(FILE_NAMES.ACTIVITY_LOG, ownerEmail, folderId, accessToken) || { logs: [] }
        : loadJsonFile(FILE_NAMES.ACTIVITY_LOG, ownerEmail, folderId) || { logs: [] };
      Logger.log('logActivity: Loaded activity log - existing logs count: ' + (activityLogData.logs ? activityLogData.logs.length : 0));
    } catch (loadError) {
      Logger.log('ERROR: Failed to load activity log: ' + loadError.toString());
      // Create empty log data if load fails
      activityLogData = { logs: [] };
    }
    
    if (!activityLogData.logs) {
      activityLogData.logs = [];
    }
    
    const logEntry = {
      id: Utilities.getUuid(),
      timestamp: new Date().toISOString(),
      actorId: activity.actorId,
      actorName: activity.actorName,
      actorRole: activity.actorRole,
      actionType: activity.actionType,
      targetUserId: activity.targetUserId || null,
      targetUserName: activity.targetUserName || null,
      details: activity.details || {},
      referenceId: activity.referenceId || null,
      referenceType: activity.referenceType || null,
      metadata: {
        deviceType: activity.deviceType || 'unknown',
        appVersion: activity.appVersion || '1.0.0'
      }
    };
    
    // Add to beginning of logs array (newest first)
    activityLogData.logs.unshift(logEntry);
    Logger.log('logActivity: Added log entry - new logs count: ' + activityLogData.logs.length);
    
    // Keep only last 1000 entries to prevent file from growing too large
    if (activityLogData.logs.length > 1000) {
      activityLogData.logs = activityLogData.logs.slice(0, 1000);
      Logger.log('logActivity: Trimmed logs to 1000 entries');
    }
    
    // Update metadata
    if (!activityLogData.metadata) {
      activityLogData.metadata = {};
    }
    activityLogData.metadata.lastModified = new Date().toISOString();
    activityLogData.metadata.lastModifiedBy = activity.actorId;
    activityLogData.metadata.version = (activityLogData.metadata.version || 0) + 1;
    
    // Save (using ownerEmail or folderId to ensure we save to the correct family's Drive)
    Logger.log('logActivity: Saving activity log - hasAccessToken: ' + (!!accessToken));
    try {
      if (accessToken) {
        const fileId = saveJsonFileV3(FILE_NAMES.ACTIVITY_LOG, activityLogData, ownerEmail, folderId, accessToken);
        Logger.log('logActivity: Saved successfully via Drive API v3 - fileId: ' + fileId);
      } else {
        const fileId = saveJsonFile(FILE_NAMES.ACTIVITY_LOG, activityLogData, ownerEmail, folderId);
        Logger.log('logActivity: Saved successfully via legacy method - fileId: ' + fileId);
      }
    } catch (saveError) {
      Logger.log('ERROR: Failed to save activity log: ' + saveError.toString());
      Logger.log('ERROR: Save error stack: ' + (saveError.stack || 'no stack'));
      throw saveError;
    }
    
    Logger.log('logActivity: Completed successfully');
    return true;
    
  } catch (error) {
    Logger.log('ERROR in logActivity: ' + error.toString());
    Logger.log('ERROR: Activity data: ' + JSON.stringify(activity));
    Logger.log('ERROR: ownerEmail: ' + ownerEmail + ', folderId: ' + folderId + ', hasAccessToken: ' + (!!accessToken));
    Logger.log('ERROR: Error stack: ' + (error.stack || 'no stack'));
    return false;
  }
}

/**
 * Get activity logs with filtering
 * Requires familyId in options to load from the correct family's Drive (uses getFamilyInfoForSync and loadJsonFileV3)
 */
function getActivityLogs(options = {}) {
  try {
    const familyId = options.familyId;
    
    if (!familyId) {
      Logger.log('getActivityLogs: familyId is required to load from correct Drive');
      return {
        success: false,
        error: 'familyId is required'
      };
    }
    
    // Get ownerEmail, folderId, accessToken for this family's Drive
    const familyInfo = getFamilyInfoForSync(familyId);
    if (!familyInfo) {
      Logger.log('getActivityLogs: Family not found for familyId=' + familyId);
      return {
        success: true,
        logs: [],
        totalCount: 0,
        page: options.page || 1,
        pageSize: options.pageSize || 50,
        hasMore: false
      };
    }
    
    const { ownerEmail, folderId, accessToken } = familyInfo;
    Logger.log('getActivityLogs: Loading from ownerEmail=' + ownerEmail + ', folderId=' + folderId);
    
    let activityLogData = loadJsonFileV3(FILE_NAMES.ACTIVITY_LOG, ownerEmail, folderId, accessToken);
    
    // If activity_log.json does not exist, create it (e.g. family created before we added it to init, or logActivity has not yet run)
    if (!activityLogData) {
      Logger.log('getActivityLogs: activity_log.json not found, creating empty file');
      try {
        saveJsonFileV3(FILE_NAMES.ACTIVITY_LOG, { logs: [] }, ownerEmail, folderId, accessToken);
        Logger.log('getActivityLogs: Created empty activity_log.json');
      } catch (createErr) {
        Logger.log('getActivityLogs: Failed to create activity_log.json: ' + createErr.toString());
      }
      activityLogData = { logs: [] };
    }
    
    let logs = activityLogData.logs || [];
    Logger.log('getActivityLogs: Loaded ' + logs.length + ' logs from Drive');
    
    // Filter by user
    if (options.userId) {
      logs = logs.filter(log => 
        log.actorId === options.userId || log.targetUserId === options.userId
      );
    }
    
    // Filter by action type
    if (options.actionType) {
      logs = logs.filter(log => log.actionType === options.actionType);
    }
    
    // Filter by date range
    if (options.startDate) {
      const startTime = new Date(options.startDate).getTime();
      logs = logs.filter(log => new Date(log.timestamp).getTime() >= startTime);
    }
    
    if (options.endDate) {
      const endTime = new Date(options.endDate).getTime();
      logs = logs.filter(log => new Date(log.timestamp).getTime() <= endTime);
    }
    
    // Pagination
    const page = options.page || 1;
    const pageSize = options.pageSize || 50;
    const startIndex = (page - 1) * pageSize;
    const endIndex = startIndex + pageSize;
    
    const paginatedLogs = logs.slice(startIndex, endIndex);
    
    return {
      success: true,
      logs: paginatedLogs,
      totalCount: logs.length,
      page: page,
      pageSize: pageSize,
      hasMore: endIndex < logs.length
    };
    
  } catch (error) {
    Logger.log('Error in getActivityLogs: ' + error.toString());
    return {
      success: false,
      error: error.toString()
    };
  }
}

/**
 * Clear old activity logs
 */
function clearOldLogs(olderThanDays = 365) {
  try {
    const activityLogData = loadJsonFile(FILE_NAMES.ACTIVITY_LOG) || { logs: [] };
    const cutoffDate = new Date();
    cutoffDate.setDate(cutoffDate.getDate() - olderThanDays);
    const cutoffTime = cutoffDate.getTime();
    
    const originalCount = activityLogData.logs.length;
    
    // Keep only logs newer than cutoff
    activityLogData.logs = activityLogData.logs.filter(log => {
      return new Date(log.timestamp).getTime() > cutoffTime;
    });
    
    const removedCount = originalCount - activityLogData.logs.length;
    
    // Update metadata
    if (!activityLogData.metadata) {
      activityLogData.metadata = {};
    }
    activityLogData.metadata.lastModified = new Date().toISOString();
    activityLogData.metadata.lastModifiedBy = 'system';
    activityLogData.metadata.version = (activityLogData.metadata.version || 0) + 1;
    
    // Save
    saveJsonFile(FILE_NAMES.ACTIVITY_LOG, activityLogData);
    
    return {
      success: true,
      removedCount: removedCount,
      remainingCount: activityLogData.logs.length
    };
    
  } catch (error) {
    Logger.log('Error in clearOldLogs: ' + error.toString());
    return {
      success: false,
      error: error.toString()
    };
  }
}

/**
 * Export activity logs to CSV
 */
function exportActivityLogsToCsv() {
  try {
    const activityLogData = loadJsonFile(FILE_NAMES.ACTIVITY_LOG) || { logs: [] };
    
    // Create CSV header
    let csv = 'Timestamp,Actor,Action,Target,Details\n';
    
    // Add rows
    activityLogData.logs.forEach(log => {
      const timestamp = log.timestamp;
      const actor = log.actorName + ' (' + log.actorRole + ')';
      const action = log.actionType;
      const target = log.targetUserName || '-';
      const details = JSON.stringify(log.details).replace(/"/g, '""'); // Escape quotes
      
      csv += `"${timestamp}","${actor}","${action}","${target}","${details}"\n`;
    });
    
    // Create file
    const folder = getChoreQuestFolder();
    const timestamp = Utilities.formatDate(
      new Date(),
      Session.getScriptTimeZone(),
      'yyyy-MM-dd_HHmmss'
    );
    const fileName = 'activity_log_export_' + timestamp + '.csv';
    
    const file = folder.createFile(fileName, csv, MimeType.CSV);
    
    return {
      success: true,
      fileName: fileName,
      fileId: file.getId(),
      url: file.getUrl()
    };
    
  } catch (error) {
    Logger.log('Error in exportActivityLogsToCsv: ' + error.toString());
    return {
      success: false,
      error: error.toString()
    };
  }
}
