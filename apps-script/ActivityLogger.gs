/**
 * ActivityLogger.gs
 * Utility functions for logging activities
 */

/**
 * Log an activity
 */
function logActivity(activity) {
  try {
    const activityLogData = loadJsonFile(FILE_NAMES.ACTIVITY_LOG) || { logs: [] };
    
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
    
    // Keep only last 1000 entries to prevent file from growing too large
    if (activityLogData.logs.length > 1000) {
      activityLogData.logs = activityLogData.logs.slice(0, 1000);
    }
    
    // Update metadata
    if (!activityLogData.metadata) {
      activityLogData.metadata = {};
    }
    activityLogData.metadata.lastModified = new Date().toISOString();
    activityLogData.metadata.lastModifiedBy = activity.actorId;
    activityLogData.metadata.version = (activityLogData.metadata.version || 0) + 1;
    
    // Save
    saveJsonFile(FILE_NAMES.ACTIVITY_LOG, activityLogData);
    
    return true;
    
  } catch (error) {
    Logger.log('Error in logActivity: ' + error.toString());
    return false;
  }
}

/**
 * Get activity logs with filtering
 */
function getActivityLogs(options = {}) {
  try {
    const activityLogData = loadJsonFile(FILE_NAMES.ACTIVITY_LOG) || { logs: [] };
    let logs = activityLogData.logs;
    
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
