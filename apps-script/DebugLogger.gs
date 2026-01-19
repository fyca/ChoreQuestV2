/**
 * DebugLogger.gs
 * In-memory log buffer that can be retrieved via API for debugging
 */

// In-memory log buffer (max 100 entries)
const DEBUG_LOG_BUFFER = [];
const MAX_LOG_ENTRIES = 100;

/**
 * Add a log entry to the buffer
 */
function addDebugLog(level, message, data) {
  try {
    const logEntry = {
      timestamp: new Date().toISOString(),
      level: level || 'INFO',
      message: message || '',
      data: data || null
    };
    
    DEBUG_LOG_BUFFER.push(logEntry);
    
    // Keep only last MAX_LOG_ENTRIES
    if (DEBUG_LOG_BUFFER.length > MAX_LOG_ENTRIES) {
      DEBUG_LOG_BUFFER.shift();
    }
  } catch (error) {
    // Silently fail - don't break the app if logging fails
  }
}

/**
 * Get all logs from buffer
 */
function getDebugLogs(limit) {
  const logs = DEBUG_LOG_BUFFER.slice();
  if (limit && limit > 0) {
    return logs.slice(-limit);
  }
  return logs;
}

/**
 * Clear the log buffer
 */
function clearDebugLogs() {
  DEBUG_LOG_BUFFER.length = 0;
}

/**
 * Handle debug log requests
 */
function handleDebugLogsRequest(e) {
  try {
    const action = e.parameter.action || 'get';
    const limit = parseInt(e.parameter.limit || '50');
    
    if (action === 'get') {
      return createResponse({
        success: true,
        logs: getDebugLogs(limit),
        count: DEBUG_LOG_BUFFER.length
      });
    } else if (action === 'clear') {
      clearDebugLogs();
      return createResponse({
        success: true,
        message: 'Logs cleared'
      });
    }
    
    return createResponse({ error: 'Invalid action' }, 400);
  } catch (error) {
    return createResponse({ error: error.toString() }, 500);
  }
}
