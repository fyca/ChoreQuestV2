/**
 * DriveManagerV3.gs
 * Handles all Google Drive file operations using Drive API v3
 * This works better with USER_ACCESSING deployment and mobile app API calls
 * 
 * NOTE: FOLDER_NAME and FILE_NAMES are defined in Code.gs
 */

/**
 * Get or create ChoreQuest folder in the user's Drive using Drive API v3
 * 
 * IMPORTANT: With USER_ACCESSING, if OAuth isn't working, this will access the script owner's Drive.
 * We use ownerEmail to verify we're accessing the correct user's Drive.
 * 
 * @param {string} ownerEmail - REQUIRED: Email of the owner (used to verify we're accessing the correct Drive)
 * @param {string} accessToken - Optional: OAuth access token for Drive API calls
 * @returns {string} Folder ID
 */
function getChoreQuestFolderV3(ownerEmail, accessToken) {
  if (!ownerEmail) {
    Logger.log('ERROR: ownerEmail is required for getChoreQuestFolderV3');
    throw new Error('ownerEmail is required to identify which user\'s Drive to access');
  }
  
  try {
    Logger.log('getChoreQuestFolderV3 called - Owner email: ' + ownerEmail);
    
    // CRITICAL: Get a valid access token (refreshes if expired)
    // This ensures tokens are always fresh, even if the Android app sends an expired token
    // However, if a fresh access token was just provided (e.g., from login), use it directly
    let validAccessToken = null;
    
    // First, try to use the provided access token if it exists (it might be fresh from login)
    if (accessToken) {
      Logger.log('Using provided access token (may be fresh from login)');
      Logger.log('Access token length: ' + accessToken.length);
      Logger.log('Access token preview: ' + accessToken.substring(0, 20) + '...');
      validAccessToken = accessToken;
      
      // Optionally verify it's still valid, but don't fail if verification fails
      // The token might be fresh and valid
    } else {
      // No access token provided, try to get/refresh one
      Logger.log('No access token provided, attempting to get/refresh one');
      validAccessToken = getValidAccessToken(ownerEmail);
      if (!validAccessToken) {
        Logger.log('WARNING: getValidAccessToken returned null - no access token available');
      } else {
        Logger.log('Using refreshed/valid access token from getValidAccessToken');
      }
    }
    
    // Get current user for logging (with ME deployment, this will be the script owner)
    let currentUserEmail = null;
    try {
      const currentUser = Session.getActiveUser();
      currentUserEmail = currentUser.getEmail();
      Logger.log('Current script user (Session.getActiveUser): ' + currentUserEmail);
      Logger.log('Target user (ownerEmail): ' + ownerEmail);
      
      // With ME deployment, the script runs as the owner, not the user
      // We MUST use access tokens to access the user's Drive
      if (currentUserEmail !== ownerEmail) {
        Logger.log('NOTE: Script is running as ' + currentUserEmail + ' (script owner)');
        Logger.log('To access ' + ownerEmail + '\'s Drive, we MUST use an access token');
        if (!validAccessToken) {
          Logger.log('ERROR: No access token available - cannot access user\'s Drive');
          throw new Error('Access token required: Script runs as ' + currentUserEmail + ' but needs to access ' + ownerEmail + '\'s Drive. An OAuth access token must be provided.');
        }
      }
    } catch (e) {
      Logger.log('Could not get current user email: ' + e.toString());
      // If we can't get the current user, we still need an access token
      if (!validAccessToken) {
        Logger.log('ERROR: No access token and cannot verify user context');
        throw new Error('Access token required: Cannot determine user context. An OAuth access token must be provided.');
      }
    }
    
    // Search for folder in user's Drive root
    // CRITICAL: With ME deployment, we MUST use access token for all Drive API calls
    const query = "name='" + FOLDER_NAME + "' and mimeType='application/vnd.google-apps.folder' and trashed=false and 'root' in parents";
    Logger.log('Searching for folder with query: ' + query);
    Logger.log('Using access token: ' + (validAccessToken ? 'yes (REQUIRED for ME deployment, length: ' + validAccessToken.length + ')' : 'no (will fail with ME deployment)'));
    
    // With ME deployment, access token is REQUIRED
    if (!validAccessToken) {
      Logger.log('ERROR: Access token is required with ME deployment');
      throw new Error('Access token required: With ME deployment, an OAuth access token must be provided to access the user\'s Drive.');
    }
    
    let response;
    if (validAccessToken) {
      // Use valid access token for Drive API call
      const url = 'https://www.googleapis.com/drive/v3/files?q=' + encodeURIComponent(query) + '&spaces=drive&fields=files(id,name,owners)';
      Logger.log('Drive API URL: ' + url);
      Logger.log('Authorization header: Bearer ' + validAccessToken.substring(0, 20) + '...');
      
      const options = {
        method: 'get',
        headers: {
          'Authorization': 'Bearer ' + validAccessToken
        },
        muteHttpExceptions: true
      };
      
      Logger.log('Making Drive API request...');
      const httpResponse = UrlFetchApp.fetch(url, options);
      const responseCode = httpResponse.getResponseCode();
      const responseText = httpResponse.getContentText();
      
      Logger.log('Drive API response code: ' + responseCode);
      Logger.log('Drive API response length: ' + responseText.length);
      Logger.log('Drive API response preview: ' + responseText.substring(0, 500));
      
      if (responseCode !== 200) {
        Logger.log('ERROR: Drive API call failed: ' + responseCode + ' - ' + responseText);
        
        // Parse error response if possible
        let errorMessage = 'Drive API call failed: ' + responseCode;
        try {
          const errorResponse = JSON.parse(responseText);
          Logger.log('Error response parsed: ' + JSON.stringify(errorResponse));
          
          // Handle different error response formats
          if (errorResponse.error) {
            if (typeof errorResponse.error === 'string') {
              errorMessage = 'Drive API error: ' + errorResponse.error;
            } else if (typeof errorResponse.error === 'object') {
              // Error is an object with code, message, status, etc.
              const errorObj = errorResponse.error;
              const errorDetails = [];
              
              if (errorObj.message) {
                errorDetails.push(errorObj.message);
              }
              if (errorObj.code) {
                errorDetails.push('code: ' + errorObj.code);
              }
              if (errorObj.status) {
                errorDetails.push('status: ' + errorObj.status);
              }
              if (errorObj.errors && Array.isArray(errorObj.errors)) {
                errorObj.errors.forEach((err, idx) => {
                  if (err.message) errorDetails.push('error[' + idx + ']: ' + err.message);
                  if (err.domain) errorDetails.push('domain[' + idx + ']: ' + err.domain);
                  if (err.reason) errorDetails.push('reason[' + idx + ']: ' + err.reason);
                });
              }
              
              if (errorDetails.length > 0) {
                errorMessage = 'Drive API error: ' + errorDetails.join(', ');
              } else {
                errorMessage = 'Drive API error: ' + JSON.stringify(errorObj);
              }
            }
            
            if (errorResponse.error_description) {
              errorMessage += ' - ' + errorResponse.error_description;
            }
            
            // Check for specific error codes
            const errorCode = typeof errorResponse.error === 'object' ? errorResponse.error.code : errorResponse.error;
            if (errorCode === 'invalid_grant' || errorCode === 401 || responseCode === 401) {
              errorMessage = 'Drive access not authorized. The access token may be invalid, expired, or missing required scopes.';
            }
          } else if (errorResponse.message) {
            errorMessage = 'Drive API error: ' + errorResponse.message;
          } else {
            // Fallback: use the raw response text
            errorMessage = 'Drive API error (code ' + responseCode + '): ' + responseText.substring(0, 500);
          }
        } catch (e) {
          Logger.log('Could not parse error response: ' + e.toString());
          // If response is HTML (like 401 errors), use the response code
          if (responseCode === 401) {
            errorMessage = 'Drive access not authorized. The access token may be invalid, expired, or missing required scopes.';
          } else if (responseCode === 403) {
            errorMessage = 'Drive access forbidden. The access token may not have required permissions.';
          }
        }
        
        Logger.log('Throwing error: ' + errorMessage);
        throw new Error(errorMessage);
      }
      
      response = JSON.parse(responseText);
    } else {
      // Use built-in Drive service (requires USER_ACCESSING context)
      response = Drive.Files.list({
        q: query,
        spaces: 'drive',
        fields: 'files(id, name, owners)'
      });
    }
    
    if (response.files && response.files.length > 0) {
      const folder = response.files[0];
      Logger.log('Found existing ChoreQuest folder: ' + folder.id);
      
      // Log folder owner and verify it matches expected user
      if (folder.owners && folder.owners.length > 0) {
        const folderOwnerEmail = folder.owners[0].emailAddress;
        Logger.log('Folder owner: ' + folderOwnerEmail);
        
        // CRITICAL: Verify folder owner matches expected user
        if (folderOwnerEmail !== ownerEmail) {
          Logger.log('ERROR: Folder owner (' + folderOwnerEmail + ') does not match ownerEmail (' + ownerEmail + ')');
          Logger.log('This confirms the script is accessing the wrong user\'s Drive!');
          Logger.log('The script is running as the script owner (' + (currentUserEmail || 'unknown') + '), not as the user (' + ownerEmail + ').');
          Logger.log('This happens because the Android app\'s HTTP requests don\'t have OAuth credentials.');
          throw new Error('Folder owner mismatch: Folder belongs to ' + folderOwnerEmail + ' but should belong to ' + ownerEmail + '. OAuth authorization required. Please visit: ' + ScriptApp.getService().getUrl());
        }
        
        // Also verify it matches current authenticated user (if we can get it)
        if (currentUserEmail && folderOwnerEmail !== currentUserEmail) {
          Logger.log('ERROR: Folder owner (' + folderOwnerEmail + ') does not match authenticated user (' + currentUserEmail + ')');
          Logger.log('This confirms OAuth is not working correctly!');
          throw new Error('OAuth mismatch: Folder belongs to ' + folderOwnerEmail + ' but authenticated user is ' + currentUserEmail);
        }
      }
      
      return folder.id;
    }
    
    // Folder doesn't exist, create it
    Logger.log('Folder not found, creating new ChoreQuest folder');
    const folderMetadata = {
      name: FOLDER_NAME,
      mimeType: 'application/vnd.google-apps.folder',
      description: 'ChoreQuest app data storage',
      parents: ['root'] // Create in root of authenticated user's Drive
    };
    
    let newFolder;
    if (validAccessToken) {
      // Use valid access token for Drive API call
      const url = 'https://www.googleapis.com/drive/v3/files';
      const options = {
        method: 'post',
        headers: {
          'Authorization': 'Bearer ' + validAccessToken,
          'Content-Type': 'application/json'
        },
        payload: JSON.stringify(folderMetadata),
        muteHttpExceptions: true
      };
      const httpResponse = UrlFetchApp.fetch(url, options);
      const responseCode = httpResponse.getResponseCode();
      const responseText = httpResponse.getContentText();
      
      if (responseCode !== 200) {
        Logger.log('ERROR: Drive API create failed: ' + responseCode + ' - ' + responseText);
        throw new Error('Drive API create failed: ' + responseCode + ' - ' + responseText);
      }
      
      newFolder = JSON.parse(responseText);
    } else {
      // Use built-in Drive service
      newFolder = Drive.Files.create(folderMetadata, {
        fields: 'id, name, owners'
      });
    }
    
    Logger.log('Created ChoreQuest folder: ' + newFolder.id);
    if (newFolder.owners && newFolder.owners.length > 0) {
      const newFolderOwnerEmail = newFolder.owners[0].emailAddress;
      Logger.log('New folder owner: ' + newFolderOwnerEmail);
      
      // CRITICAL: Verify new folder owner matches expected user
      if (newFolderOwnerEmail !== ownerEmail) {
        Logger.log('ERROR: New folder owner (' + newFolderOwnerEmail + ') does not match ownerEmail (' + ownerEmail + ')');
        Logger.log('This confirms the script is creating folders in the wrong user\'s Drive!');
        Logger.log('The script is running as the script owner (' + (currentUserEmail || 'unknown') + '), not as the user (' + ownerEmail + ').');
        throw new Error('Folder creation mismatch: Folder created for ' + newFolderOwnerEmail + ' but should be for ' + ownerEmail + '. OAuth authorization required. Please visit: ' + ScriptApp.getService().getUrl());
      }
    }
    
    return newFolder.id;
  } catch (error) {
    Logger.log('ERROR in getChoreQuestFolderV3: ' + error.toString());
    Logger.log('Error type: ' + typeof error);
    Logger.log('Error message: ' + (error.message || 'no message'));
    Logger.log('Error stack: ' + (error.stack || 'no stack'));
    
    // Try to extract error message properly
    let errorMessage = 'Cannot access Drive';
    if (error.message) {
      errorMessage = error.message;
    } else if (typeof error === 'string') {
      errorMessage = error;
    } else if (error.toString && error.toString() !== '[object Object]') {
      errorMessage = error.toString();
    } else {
      // Try to stringify the error
      try {
        errorMessage = JSON.stringify(error);
      } catch (e) {
        errorMessage = 'Unknown error: ' + typeof error;
      }
    }
    
    Logger.log('Extracted error message: ' + errorMessage);
    
    // Check if it's an authorization error
    const errorStr = errorMessage.toLowerCase();
    if (errorStr.includes('unauthorized') || errorStr.includes('401') || errorStr.includes('permission') || errorStr.includes('access denied') || errorStr.includes('insufficient permission')) {
      Logger.log('Authorization error detected - user needs to authorize Drive access');
      throw new Error('Drive access not authorized. Please visit the web app URL in a browser to authorize Drive access: ' + ScriptApp.getService().getUrl());
    }
    
    throw new Error(errorMessage);
  }
}

/**
 * Save JSON data to a file using Drive API v3
 * @param {string} fileName - Name of the file
 * @param {object} data - Data to save
 * @param {string} ownerEmail - Owner email to identify family folder (optional if folderId provided)
 * @param {string} folderId - Optional: Folder ID to access folder directly (for shared folders)
 * @param {string} accessToken - Optional: OAuth access token for Drive API calls
 * @returns {string} File ID
 */
function saveJsonFileV3(fileName, data, ownerEmail, folderId, accessToken) {
  try {
    // CRITICAL: Get a valid access token (refreshes if expired)
    // This ensures tokens are always fresh, even if the Android app sends an expired token
    let validAccessToken = null;
    if (ownerEmail) {
      validAccessToken = getValidAccessToken(ownerEmail);
      if (!validAccessToken) {
        Logger.log('WARNING: getValidAccessToken returned null, falling back to provided accessToken');
        validAccessToken = accessToken;
      } else {
        Logger.log('Using refreshed/valid access token from getValidAccessToken');
      }
    } else {
      validAccessToken = accessToken;
    }
    
    let targetFolderId;
    
    // If folderId is provided, use it directly (works for shared folders)
    if (folderId) {
      Logger.log('Using provided folderId: ' + folderId);
      targetFolderId = folderId;
    } else if (ownerEmail) {
      // Use ownerEmail to get folder
      Logger.log('Getting folder for owner: ' + ownerEmail);
      targetFolderId = getChoreQuestFolderV3(ownerEmail, validAccessToken);
    } else {
      // Use current user's folder
      Logger.log('Getting folder for current user');
      targetFolderId = getChoreQuestFolderV3(null, validAccessToken);
    }
    
    const jsonString = JSON.stringify(data, null, 2);
    
    // Search for existing file
    const query = "name='" + fileName + "' and '" + targetFolderId + "' in parents and trashed=false";
    Logger.log('Searching for file with query: ' + query);
    Logger.log('Using access token: ' + (validAccessToken ? 'yes (REQUIRED for ME deployment)' : 'no (will fail with ME deployment)'));
    
    // CRITICAL: With ME deployment, access token is REQUIRED
    if (!validAccessToken) {
      Logger.log('ERROR: Access token is required with ME deployment');
      throw new Error('Access token required: With ME deployment, an OAuth access token must be provided to access the user\'s Drive.');
    }
    
    let fileListResponse;
    if (validAccessToken) {
      // Use valid access token for Drive API call
      const url = 'https://www.googleapis.com/drive/v3/files?q=' + encodeURIComponent(query) + '&fields=files(id,name)';
      const options = {
        method: 'get',
        headers: {
          'Authorization': 'Bearer ' + validAccessToken
        },
        muteHttpExceptions: true
      };
      const httpResponse = UrlFetchApp.fetch(url, options);
      const responseCode = httpResponse.getResponseCode();
      const responseText = httpResponse.getContentText();
      
      if (responseCode !== 200) {
        Logger.log('ERROR: Drive API list call failed: ' + responseCode + ' - ' + responseText);
        if (responseCode === 401) {
          throw new Error('Drive access not authorized. Access token may be invalid or expired.');
        }
        throw new Error('Drive API call failed: ' + responseCode + ' - ' + responseText);
      }
      
      fileListResponse = JSON.parse(responseText);
    } else {
      // Use built-in Drive service (requires USER_ACCESSING context)
      fileListResponse = Drive.Files.list({
        q: query,
        fields: 'files(id, name)'
      });
    }
    
    if (fileListResponse.files && fileListResponse.files.length > 0) {
      // Update existing file
      // If multiple files found, delete duplicates and keep only the first one
      if (fileListResponse.files.length > 1) {
        Logger.log('WARNING: Multiple files found with name "' + fileName + '". Deleting duplicates...');
        for (let i = 1; i < fileListResponse.files.length; i++) {
          const duplicateFileId = fileListResponse.files[i].id;
          Logger.log('Deleting duplicate file: ' + duplicateFileId);
          try {
            if (validAccessToken) {
              const deleteUrl = 'https://www.googleapis.com/drive/v3/files/' + duplicateFileId;
              const deleteOptions = {
                method: 'delete',
                headers: {
                  'Authorization': 'Bearer ' + validAccessToken
                },
                muteHttpExceptions: true
              };
              UrlFetchApp.fetch(deleteUrl, deleteOptions);
            } else {
              DriveApp.getFileById(duplicateFileId).setTrashed(true);
            }
          } catch (e) {
            Logger.log('Error deleting duplicate file: ' + e.toString());
          }
        }
      }
      
      const fileId = fileListResponse.files[0].id;
      Logger.log('Updating existing file: ' + fileId);
      Logger.log('File content size: ' + jsonString.length + ' bytes');
      
      if (validAccessToken) {
        // Use valid access token to update file content
        // Use uploadType=media for simpler content-only updates
        try {
          const url = 'https://www.googleapis.com/upload/drive/v3/files/' + fileId + '?uploadType=media';
          
          const options = {
            method: 'patch',
            headers: {
              'Authorization': 'Bearer ' + validAccessToken,
              'Content-Type': 'application/json'
            },
            payload: jsonString,
            muteHttpExceptions: true
          };
          
          const httpResponse = UrlFetchApp.fetch(url, options);
          const responseCode = httpResponse.getResponseCode();
          const responseText = httpResponse.getContentText();
          
          if (responseCode !== 200) {
            Logger.log('ERROR: Drive API update failed: ' + responseCode);
            Logger.log('Response: ' + responseText);
            throw new Error('Failed to update file: ' + responseCode + ' - ' + responseText);
          }
          
          const updatedFile = JSON.parse(responseText);
          Logger.log('File updated successfully via API: ' + updatedFile.id);
          Logger.log('File name: ' + updatedFile.name);
          return updatedFile.id;
        } catch (error) {
          Logger.log('Error updating file with access token: ' + error.toString());
          Logger.log('Error stack: ' + (error.stack || 'no stack'));
          throw new Error('Cannot update file: ' + error.toString());
        }
      } else {
        // Use DriveApp (requires USER_ACCESSING context)
        try {
          const file = DriveApp.getFileById(fileId);
          file.setContent(jsonString);
          Logger.log('File updated successfully: ' + fileId);
          return fileId;
        } catch (error) {
          Logger.log('Error updating file: ' + error.toString());
          throw new Error('Cannot update file: ' + error.toString());
        }
      }
    } else {
      // Create new file
      Logger.log('Creating new file: ' + fileName);
      
      if (validAccessToken) {
        // Use valid access token to create file
        try {
          const blob = Utilities.newBlob(jsonString, 'application/json', fileName);
          const url = 'https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart';
          // Generate a random boundary string (Apps Script doesn't have getRandomString)
          const boundary = '----WebKitFormBoundary' + Utilities.getUuid().replace(/-/g, '').substring(0, 16);
          const payload = [
            '--' + boundary,
            'Content-Disposition: form-data; name="metadata"; filename=""',
            'Content-Type: application/json',
            '',
            JSON.stringify({ name: fileName, parents: [targetFolderId] }),
            '--' + boundary,
            'Content-Disposition: form-data; name="file"; filename="' + fileName + '"',
            'Content-Type: application/json',
            '',
            jsonString,
            '--' + boundary + '--'
          ].join('\r\n');
          
          const options = {
            method: 'post',
            headers: {
              'Authorization': 'Bearer ' + validAccessToken,
              'Content-Type': 'multipart/related; boundary=' + boundary
            },
            payload: payload,
            muteHttpExceptions: true
          };
          
          const httpResponse = UrlFetchApp.fetch(url, options);
          const responseCode = httpResponse.getResponseCode();
          
          if (responseCode !== 200) {
            Logger.log('ERROR: Drive API create failed: ' + responseCode + ' - ' + httpResponse.getContentText());
            throw new Error('Failed to create file: ' + responseCode);
          }
          
          const createdFile = JSON.parse(httpResponse.getContentText());
          Logger.log('File created successfully via API: ' + createdFile.id);
          return createdFile.id;
        } catch (error) {
          Logger.log('Error creating file with access token: ' + error.toString());
          throw new Error('Cannot create file: ' + error.toString());
        }
      } else {
        // Use DriveApp (requires USER_ACCESSING context)
        try {
          const folder = DriveApp.getFolderById(targetFolderId);
          const file = folder.createFile(fileName, jsonString, MimeType.PLAIN_TEXT);
          Logger.log('File created successfully: ' + file.getId());
          return file.getId();
        } catch (error) {
          Logger.log('Error creating file: ' + error.toString());
          throw new Error('Cannot create file: ' + error.toString());
        }
      }
    }
  } catch (error) {
    Logger.log('ERROR in saveJsonFileV3: ' + error.toString());
    Logger.log('Error details: ' + JSON.stringify(error));
    
    // Check if it's an authorization error
    const errorStr = error.toString().toLowerCase();
    if (errorStr.includes('unauthorized') || errorStr.includes('401') || errorStr.includes('permission') || errorStr.includes('access denied') || errorStr.includes('insufficient permission')) {
      Logger.log('Authorization error - user needs to authorize Drive access');
      throw new Error('Drive access not authorized. User must visit the web app URL to authorize Drive access.');
    }
    
    throw new Error('Cannot save file: ' + error.toString());
  }
}

/**
 * Upload image file to Drive using Drive API v3
 * @param {string} base64Data - Base64 encoded image data
 * @param {string} fileName - Name for the file
 * @param {string} mimeType - Image MIME type (image/jpeg, image/png, etc.)
 * @param {string} choreId - Optional: chore ID to organize files
 * @param {string} ownerEmail - REQUIRED: Owner email to identify family folder
 * @param {string} accessToken - REQUIRED: OAuth access token for Drive API calls
 * @returns {object} File metadata with URL and ID
 */
function uploadImageV3(base64Data, fileName, mimeType, choreId, ownerEmail, accessToken) {
  if (!ownerEmail) {
    Logger.log('ERROR: ownerEmail is required for uploadImageV3');
    throw new Error('ownerEmail is required');
  }
  
  try {
    Logger.log('uploadImageV3: Starting upload for owner: ' + ownerEmail);
    
    // CRITICAL: Get a valid access token (refreshes if expired)
    let validAccessToken = getValidAccessToken(ownerEmail);
    if (!validAccessToken) {
      Logger.log('WARNING: getValidAccessToken returned null, falling back to provided accessToken');
      validAccessToken = accessToken;
      if (!validAccessToken) {
        Logger.log('ERROR: No access token available for uploadImageV3');
        throw new Error('Access token required: With ME deployment, an OAuth access token must be provided to access the user\'s Drive.');
      }
    } else {
      Logger.log('Using refreshed/valid access token from getValidAccessToken');
    }
    
    // Get or create ChoreQuest folder
    const choreQuestFolderId = getChoreQuestFolderV3(ownerEmail, validAccessToken);
    Logger.log('uploadImageV3: Got ChoreQuest folder ID: ' + choreQuestFolderId);
    
    // Get or create Photos folder
    const photosFolderName = 'ChorePhotos';
    let photosFolderId = getOrCreateFolderV3(photosFolderName, choreQuestFolderId, validAccessToken);
    Logger.log('uploadImageV3: Got Photos folder ID: ' + photosFolderId);
    
    // Optionally organize by chore ID
    let targetFolderId = photosFolderId;
    if (choreId) {
      targetFolderId = getOrCreateFolderV3(choreId, photosFolderId, validAccessToken);
      Logger.log('uploadImageV3: Got chore folder ID: ' + targetFolderId);
    }
    
    // Decode base64 data
    const binaryData = Utilities.base64Decode(base64Data);
    Logger.log('uploadImageV3: Decoded image data, size: ' + binaryData.length + ' bytes');
    
    // Upload using multipart/form-data
    const url = 'https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart';
    const boundary = '----WebKitFormBoundary' + Utilities.getUuid().replace(/-/g, '').substring(0, 16);
    
    // Build multipart payload - need to construct as bytes
    const metadata = {
      name: fileName,
      parents: [targetFolderId]
    };
    
    const metadataPart = [
      '--' + boundary,
      'Content-Disposition: form-data; name="metadata"; filename=""',
      'Content-Type: application/json',
      '',
      JSON.stringify(metadata),
      '--' + boundary,
      'Content-Disposition: form-data; name="file"; filename="' + fileName + '"',
      'Content-Type: ' + mimeType,
      '',
      ''
    ].join('\r\n');
    
    const endBoundary = '\r\n--' + boundary + '--\r\n';
    
    // Convert strings to bytes and combine with binary data
    const metadataBytes = Utilities.newBlob(metadataPart).getBytes();
    const endBytes = Utilities.newBlob(endBoundary).getBytes();
    
    // Combine all parts: metadata (as bytes) + binary image data + end boundary (as bytes)
    const allBytes = metadataBytes.concat(binaryData).concat(endBytes);
    
    const options = {
      method: 'post',
      headers: {
        'Authorization': 'Bearer ' + validAccessToken,
        'Content-Type': 'multipart/related; boundary=' + boundary
      },
      payload: allBytes,
      muteHttpExceptions: true
    };
    
    Logger.log('uploadImageV3: Uploading to Drive API...');
    const httpResponse = UrlFetchApp.fetch(url, options);
    const responseCode = httpResponse.getResponseCode();
    const responseText = httpResponse.getContentText();
    
    if (responseCode !== 200) {
      Logger.log('ERROR: Drive API upload failed: ' + responseCode);
      Logger.log('Response: ' + responseText);
      throw new Error('Failed to upload image: ' + responseCode + ' - ' + responseText);
    }
    
    const fileData = JSON.parse(responseText);
    Logger.log('uploadImageV3: File uploaded successfully: ' + fileData.id);
    
    // Set file permissions to allow anyone with link to view
    try {
      const permissionsUrl = 'https://www.googleapis.com/drive/v3/files/' + fileData.id + '/permissions';
      const permissionsOptions = {
        method: 'post',
        headers: {
          'Authorization': 'Bearer ' + validAccessToken,
          'Content-Type': 'application/json'
        },
        payload: JSON.stringify({
          role: 'reader',
          type: 'anyone'
        }),
        muteHttpExceptions: true
      };
      
      const permissionsResponse = UrlFetchApp.fetch(permissionsUrl, permissionsOptions);
      if (permissionsResponse.getResponseCode() === 200) {
        Logger.log('uploadImageV3: File permissions set successfully');
      } else {
        Logger.log('WARNING: Could not set file permissions: ' + permissionsResponse.getContentText());
      }
    } catch (permError) {
      Logger.log('WARNING: Error setting file permissions: ' + permError.toString());
      // Continue anyway - file is uploaded
    }
    
    // Return file info
    return {
      success: true,
      fileId: fileData.id,
      fileName: fileData.name,
      url: 'https://drive.google.com/file/d/' + fileData.id + '/view',
      downloadUrl: 'https://drive.google.com/uc?export=download&id=' + fileData.id,
      thumbnailUrl: null, // Would require additional API call
      webViewLink: 'https://drive.google.com/file/d/' + fileData.id + '/view',
      size: fileData.size || binaryData.length,
      mimeType: fileData.mimeType || mimeType,
      createdDate: fileData.createdTime || new Date().toISOString()
    };
    
  } catch (error) {
    Logger.log('ERROR in uploadImageV3: ' + error.toString());
    Logger.log('Error stack: ' + (error.stack || 'no stack'));
    
    // Check if it's an authorization error
    const errorStr = error.toString().toLowerCase();
    if (errorStr.includes('unauthorized') || errorStr.includes('401') || errorStr.includes('permission') || errorStr.includes('access denied')) {
      Logger.log('Authorization error - user needs to authorize Drive access');
      throw new Error('Drive access not authorized. User must authorize Drive access.');
    }
    
    return {
      success: false,
      error: error.toString()
    };
  }
}

/**
 * Helper function to get or create a folder in Drive API v3
 * @param {string} folderName - Name of the folder
 * @param {string} parentFolderId - Parent folder ID
 * @param {string} accessToken - OAuth access token
 * @returns {string} Folder ID
 */
function getOrCreateFolderV3(folderName, parentFolderId, accessToken) {
  try {
    // Note: This function is called from uploadImageV3 which already has a valid token
    // We use the passed accessToken directly here since we don't have ownerEmail
    const validAccessToken = accessToken;
    
    // Search for existing folder
    const query = "name='" + folderName + "' and '" + parentFolderId + "' in parents and mimeType='application/vnd.google-apps.folder' and trashed=false";
    
    const url = 'https://www.googleapis.com/drive/v3/files?q=' + encodeURIComponent(query);
    const options = {
      method: 'get',
      headers: {
        'Authorization': 'Bearer ' + validAccessToken
      },
      muteHttpExceptions: true
    };
    
    const response = UrlFetchApp.fetch(url, options);
    const responseCode = response.getResponseCode();
    
    if (responseCode === 200) {
      const data = JSON.parse(response.getContentText());
      if (data.files && data.files.length > 0) {
        Logger.log('getOrCreateFolderV3: Found existing folder: ' + folderName);
        return data.files[0].id;
      }
    }
    
    // Folder doesn't exist, create it
    Logger.log('getOrCreateFolderV3: Creating new folder: ' + folderName);
    const createUrl = 'https://www.googleapis.com/drive/v3/files';
    const createOptions = {
      method: 'post',
      headers: {
        'Authorization': 'Bearer ' + validAccessToken,
        'Content-Type': 'application/json'
      },
      payload: JSON.stringify({
        name: folderName,
        mimeType: 'application/vnd.google-apps.folder',
        parents: [parentFolderId]
      }),
      muteHttpExceptions: true
    };
    
    const createResponse = UrlFetchApp.fetch(createUrl, createOptions);
    if (createResponse.getResponseCode() === 200) {
      const folderData = JSON.parse(createResponse.getContentText());
      Logger.log('getOrCreateFolderV3: Created folder: ' + folderData.id);
      return folderData.id;
    } else {
      throw new Error('Failed to create folder: ' + createResponse.getContentText());
    }
  } catch (error) {
    Logger.log('ERROR in getOrCreateFolderV3: ' + error.toString());
    throw new Error('Cannot get or create folder: ' + error.toString());
  }
}

/**
 * Load JSON data from a file using Drive API v3
 * @param {string} fileName - Name of the file
 * @param {string} ownerEmail - Owner email to identify family folder (optional if folderId provided)
 * @param {string} folderId - Optional: Folder ID to access folder directly (for shared folders)
 * @param {string} accessToken - Optional: OAuth access token for Drive API calls
 * @returns {object|null} Parsed JSON data or null if file doesn't exist
 */
function loadJsonFileV3(fileName, ownerEmail, folderId, accessToken) {
  try {
    // CRITICAL: Get a valid access token (refreshes if expired)
    // This ensures tokens are always fresh, even if the Android app sends an expired token
    let validAccessToken = null;
    if (ownerEmail) {
      validAccessToken = getValidAccessToken(ownerEmail);
      if (!validAccessToken) {
        Logger.log('WARNING: getValidAccessToken returned null, falling back to provided accessToken');
        validAccessToken = accessToken;
      } else {
        Logger.log('Using refreshed/valid access token from getValidAccessToken');
      }
    } else {
      validAccessToken = accessToken;
    }
    
    let targetFolderId;
    
    // If folderId is provided, use it directly (works for shared folders)
    if (folderId) {
      Logger.log('Using provided folderId: ' + folderId);
      targetFolderId = folderId;
    } else if (ownerEmail) {
      // Use ownerEmail to get folder (REQUIRED to verify OAuth)
      Logger.log('Getting folder for owner: ' + ownerEmail);
      targetFolderId = getChoreQuestFolderV3(ownerEmail, validAccessToken);
    } else {
      // ownerEmail is required to verify OAuth is working
      Logger.log('ERROR: ownerEmail is required for loadJsonFileV3 when folderId is not provided');
      throw new Error('ownerEmail is required to verify OAuth is working correctly');
    }
    
    // Search for file
    const query = "name='" + fileName + "' and '" + targetFolderId + "' in parents and trashed=false";
    Logger.log('Searching for file with query: ' + query);
    Logger.log('Using access token: ' + (validAccessToken ? 'yes' : 'no (using built-in Drive service)'));
    
    let fileListResponse;
    if (validAccessToken) {
      // Use valid access token for Drive API call
      const url = 'https://www.googleapis.com/drive/v3/files?q=' + encodeURIComponent(query) + '&fields=files(id,name)';
      const options = {
        method: 'get',
        headers: {
          'Authorization': 'Bearer ' + validAccessToken
        },
        muteHttpExceptions: true
      };
      const httpResponse = UrlFetchApp.fetch(url, options);
      const responseCode = httpResponse.getResponseCode();
      const responseText = httpResponse.getContentText();
      
      if (responseCode !== 200) {
        Logger.log('ERROR: Drive API list call failed: ' + responseCode + ' - ' + responseText);
        if (responseCode === 401) {
          throw new Error('Drive access not authorized. Access token may be invalid or expired.');
        }
        throw new Error('Drive API call failed: ' + responseCode + ' - ' + responseText);
      }
      
      fileListResponse = JSON.parse(responseText);
    } else {
      // Use built-in Drive service (requires USER_ACCESSING context)
      fileListResponse = Drive.Files.list({
        q: query,
        fields: 'files(id, name)'
      });
    }
    
    if (fileListResponse.files && fileListResponse.files.length > 0) {
      const fileId = fileListResponse.files[0].id;
      Logger.log('Found file: ' + fileId);
      
      // Get file content
      if (validAccessToken) {
        // Use valid access token to download file
        try {
          const downloadUrl = 'https://www.googleapis.com/drive/v3/files/' + fileId + '?alt=media';
          const options = {
            method: 'get',
            headers: {
              'Authorization': 'Bearer ' + validAccessToken
            },
            muteHttpExceptions: true
          };
          const httpResponse = UrlFetchApp.fetch(downloadUrl, options);
          const responseCode = httpResponse.getResponseCode();
          
          if (responseCode !== 200) {
            Logger.log('ERROR: Drive API download failed: ' + responseCode);
            throw new Error('Failed to download file: ' + responseCode);
          }
          
          const content = httpResponse.getContentText();
          return JSON.parse(content);
        } catch (error) {
          Logger.log('Error downloading file with access token: ' + error.toString());
          throw new Error('Cannot read file content: ' + error.toString());
        }
      } else {
        // Use DriveApp (requires USER_ACCESSING context)
        try {
          const file = DriveApp.getFileById(fileId);
          const content = file.getBlob().getDataAsString();
          return JSON.parse(content);
        } catch (error) {
          Logger.log('Error getting file content: ' + error.toString());
          throw new Error('Cannot read file content: ' + error.toString());
        }
      }
    }
    
    Logger.log('File not found: ' + fileName);
    return null;
  } catch (error) {
    Logger.log('ERROR in loadJsonFileV3: ' + error.toString());
    Logger.log('Error details: ' + JSON.stringify(error));
    
    // Check if it's an authorization error
    const errorStr = error.toString().toLowerCase();
    if (errorStr.includes('unauthorized') || errorStr.includes('401') || errorStr.includes('permission') || errorStr.includes('access denied') || errorStr.includes('insufficient permission')) {
      Logger.log('Authorization error - user needs to authorize Drive access');
      throw new Error('Drive access not authorized. User must visit the web app URL to authorize Drive access.');
    }
    
    // If file doesn't exist, return null instead of throwing
    if (errorStr.includes('not found') || errorStr.includes('404')) {
      Logger.log('File not found - returning null');
      return null;
    }
    
    // For other errors, return null to let caller handle missing data
    Logger.log('Error loading file - returning null');
    return null;
  }
}

/**
 * Get file metadata using Drive API v3
 * @param {string} fileName - Name of the file
 * @param {string} ownerEmail - Owner email to identify family folder (optional if folderId provided)
 * @param {string} folderId - Optional: Folder ID to access folder directly (for shared folders)
 * @returns {object|null} File metadata or null if file doesn't exist
 */
function getFileMetadataV3(fileName, ownerEmail, folderId, accessToken) {
  try {
    // CRITICAL: Get a valid access token (refreshes if expired)
    let validAccessToken = null;
    if (ownerEmail) {
      validAccessToken = getValidAccessToken(ownerEmail);
      if (!validAccessToken) {
        validAccessToken = accessToken;
      }
    } else {
      validAccessToken = accessToken;
    }
    
    let targetFolderId;
    
    if (folderId) {
      targetFolderId = folderId;
    } else if (ownerEmail) {
      targetFolderId = getChoreQuestFolderV3(ownerEmail, validAccessToken);
    } else {
      targetFolderId = getChoreQuestFolderV3(null, validAccessToken);
    }
    
    const query = "name='" + fileName + "' and '" + targetFolderId + "' in parents and trashed=false";
    const response = Drive.Files.list({
      q: query,
      fields: 'files(id, name, modifiedTime, size)'
    });
    
    if (response.files && response.files.length > 0) {
      const file = response.files[0];
      return {
        id: file.id,
        name: file.name,
        modifiedTime: file.modifiedTime,
        size: file.size || 0
      };
    }
    
    return null;
  } catch (error) {
    Logger.log('ERROR in getFileMetadataV3: ' + error.toString());
    return null;
  }
}

/**
 * Get all file metadata in the folder using Drive API v3
 * @param {string} ownerEmail - Owner email to identify family folder (optional if folderId provided)
 * @param {string} folderId - Optional: Folder ID to access folder directly (for shared folders)
 * @returns {Array} Array of file metadata objects
 */
function getAllFileMetadataV3(ownerEmail, folderId, accessToken) {
  try {
    // CRITICAL: Get a valid access token (refreshes if expired)
    let validAccessToken = null;
    if (ownerEmail) {
      validAccessToken = getValidAccessToken(ownerEmail);
      if (!validAccessToken) {
        validAccessToken = accessToken;
      }
    } else {
      validAccessToken = accessToken;
    }
    
    let targetFolderId;
    
    if (folderId) {
      targetFolderId = folderId;
    } else if (ownerEmail) {
      targetFolderId = getChoreQuestFolderV3(ownerEmail, validAccessToken);
    } else {
      targetFolderId = getChoreQuestFolderV3(null, validAccessToken);
    }
    
    const query = "'" + targetFolderId + "' in parents and trashed=false";
    const response = Drive.Files.list({
      q: query,
      fields: 'files(id, name, modifiedTime, size, mimeType)'
    });
    
    if (response.files) {
      return response.files.map(file => ({
        id: file.id,
        name: file.name,
        modifiedTime: file.modifiedTime,
        size: file.size || 0,
        mimeType: file.mimeType
      }));
    }
    
    return [];
  } catch (error) {
    Logger.log('ERROR in getAllFileMetadataV3: ' + error.toString());
    return [];
  }
}

/**
 * Delete a JSON file using Drive API v3
 * @param {string} fileName - Name of the file
 * @param {string} ownerEmail - Owner email to identify family folder (optional if folderId provided)
 * @param {string} folderId - Optional: Folder ID to access folder directly (for shared folders)
 * @returns {boolean} True if file was deleted, false otherwise
 */
function deleteJsonFileV3(fileName, ownerEmail, folderId, accessToken) {
  try {
    // CRITICAL: Get a valid access token (refreshes if expired)
    let validAccessToken = null;
    if (ownerEmail) {
      validAccessToken = getValidAccessToken(ownerEmail);
      if (!validAccessToken) {
        validAccessToken = accessToken;
      }
    } else {
      validAccessToken = accessToken;
    }
    
    let targetFolderId;
    
    if (folderId) {
      targetFolderId = folderId;
    } else if (ownerEmail) {
      targetFolderId = getChoreQuestFolderV3(ownerEmail, validAccessToken);
    } else {
      targetFolderId = getChoreQuestFolderV3(null, validAccessToken);
    }
    
    const query = "name='" + fileName + "' and '" + targetFolderId + "' in parents and trashed=false";
    const response = Drive.Files.list({
      q: query,
      fields: 'files(id)'
    });
    
    if (response.files && response.files.length > 0) {
      const fileId = response.files[0].id;
      Drive.Files.remove(fileId);
      Logger.log('File deleted: ' + fileId);
      return true;
    }
    
    return false;
  } catch (error) {
    Logger.log('ERROR in deleteJsonFileV3: ' + error.toString());
    return false;
  }
}
