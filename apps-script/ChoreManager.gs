/**
 * ChoreManager.gs
 * Handles chore CRUD operations and completion logic
 */

/**
 * Helper function to get ownerEmail and folderId from family data
 * This function searches through all stored access tokens to find the family
 * that contains the specified userId (if provided) or returns the first family found
 */
function getFamilyInfo(userId) {
  try {
    Logger.log('getFamilyInfo: Attempting to get family info' + (userId ? ' for userId: ' + userId : ''));
    
    // Get all stored access tokens from user properties
    const userProps = PropertiesService.getUserProperties();
    const allProps = userProps.getProperties();
    
    // Try each stored access token until we find the family
    for (const key in allProps) {
      if (key.startsWith('ACCESS_TOKEN_')) {
        const ownerEmail = key.replace('ACCESS_TOKEN_', '');
        const accessToken = allProps[key];
        
        Logger.log('getFamilyInfo: Trying access token for: ' + ownerEmail);
        
        try {
          // Try to get the folder ID and load family data
          const folderId = getChoreQuestFolderV3(ownerEmail, accessToken);
          Logger.log('getFamilyInfo: Got folder ID: ' + folderId);
          
          // Load family data
          const familyData = loadJsonFileV3(FILE_NAMES.FAMILY, ownerEmail, folderId, accessToken);
          
          if (familyData) {
            // If userId is provided, verify the user belongs to this family
            if (userId) {
              const usersData = loadJsonFileV3(FILE_NAMES.USERS, ownerEmail, folderId, accessToken);
              if (usersData && usersData.users) {
                const user = usersData.users.find(u => u.id === userId);
                if (!user) {
                  Logger.log('getFamilyInfo: User ' + userId + ' not found in family ' + familyData.id);
                  // Continue to next token
                  continue;
                }
              } else {
                Logger.log('getFamilyInfo: Could not load users data for verification');
                // Continue to next token
                continue;
              }
            }
            
            Logger.log('getFamilyInfo: Found family! ownerEmail=' + familyData.ownerEmail + ', folderId=' + (familyData.driveFileId || folderId));
            
            return {
              ownerEmail: familyData.ownerEmail,
              folderId: familyData.driveFileId || folderId,
              familyId: familyData.id,
              accessToken: accessToken
            };
          }
        } catch (error) {
          Logger.log('getFamilyInfo: Failed with token for ' + ownerEmail + ': ' + error.toString());
          // Continue to next token
          continue;
        }
      }
    }
    
    Logger.log('getFamilyInfo: No family found' + (userId ? ' containing userId: ' + userId : ''));
    return null;
  } catch (error) {
    Logger.log('getFamilyInfo: Error: ' + error.toString());
    Logger.log('getFamilyInfo: Error stack: ' + (error.stack || 'no stack'));
    return null;
  }
}

/**
 * Handle chore data requests (GET)
 */
function handleChoresRequest(e) {
  const action = e.parameter.action;
  const familyId = e.parameter.familyId;
  const userId = e.parameter.userId;
  const choreId = e.parameter.choreId;
  
  if (action === 'list' && familyId) {
    return listChores(familyId, userId);
  } else if (action === 'get' && choreId) {
    return getChore(choreId);
  }
  
  return createResponse({ error: 'Invalid chores action' }, 400);
}

/**
 * Handle chore data requests (POST)
 */
function handleChoresPost(e, data) {
  const action = e.parameter.action;
  
  if (action === 'create') {
    return createChore(data);
  } else if (action === 'update') {
    return updateChore(data);
  } else if (action === 'complete') {
    return completeChore(data);
  } else if (action === 'verify') {
    return verifyChore(data);
  } else if (action === 'delete') {
    return deleteChore(data);
  }
  
  return createResponse({ error: 'Invalid chores action' }, 400);
}

/**
 * Create a new chore
 */
function createChore(data) {
  try {
    const { creatorId, title, description, assignedTo, pointValue, dueDate, recurring, subtasks, color, icon } = data;
    
    if (!creatorId || !title || !assignedTo || !pointValue) {
      return createResponse({ error: 'Missing required fields' }, 400);
    }
    
    // Get ownerEmail, folderId, and accessToken from family data
    // Pass creatorId to find the correct family
    const familyInfo = getFamilyInfo(creatorId);
    if (!familyInfo) {
      return createResponse({ error: 'Family data not found' }, 404);
    }
    
    const { ownerEmail, folderId, accessToken } = familyInfo;
    
    // Load data using ownerEmail, folderId, and accessToken
    const usersData = loadJsonFileV3(FILE_NAMES.USERS, ownerEmail, folderId, accessToken);
    if (!usersData || !usersData.users) {
      return createResponse({ error: 'Users data not found' }, 404);
    }
    
    const choresData = loadJsonFileV3(FILE_NAMES.CHORES, ownerEmail, folderId, accessToken) || { chores: [] };
    
    // Verify creator is authorized (must be parent)
    const creator = usersData.users.find(u => u.id === creatorId);
    if (!creator || creator.role !== 'parent') {
      return createResponse({ error: 'Unauthorized' }, 403);
    }
    
    // Create new chore
    const choreId = Utilities.getUuid();
    const newChore = {
      id: choreId,
      title: title,
      description: description || '',
      assignedTo: Array.isArray(assignedTo) ? assignedTo : [assignedTo],
      createdBy: creatorId,
      pointValue: pointValue,
      dueDate: dueDate || null,
      recurring: recurring || null,
      subtasks: subtasks || [],
      status: 'pending',
      photoProof: null,
      completedBy: null,
      completedAt: null,
      verifiedBy: null,
      verifiedAt: null,
      createdAt: new Date().toISOString(),
      color: color || null,
      icon: icon || null
    };
    
    // Add to chores data
    choresData.chores.push(newChore);
    saveJsonFileV3(FILE_NAMES.CHORES, choresData, ownerEmail, folderId, accessToken);
    
    // Log chore creation
    logActivity({
      actorId: creatorId,
      actorName: creator.name,
      actorRole: creator.role,
      actionType: 'chore_created',
      referenceId: choreId,
      referenceType: 'chore',
      details: {
        choreTitle: title,
        assignedTo: newChore.assignedTo.map(userId => {
          const user = usersData.users.find(u => u.id === userId);
          return user ? user.name : userId;
        }),
        pointValue: pointValue
      }
    }, ownerEmail, folderId, accessToken);
    
    return createResponse({
      success: true,
      chore: newChore
    });
    
  } catch (error) {
    Logger.log('Error in createChore: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}

/**
 * Update a chore
 */
function updateChore(data) {
  try {
    const { userId, choreId, updates } = data;
    
    if (!userId || !choreId || !updates) {
      return createResponse({ error: 'Missing required fields' }, 400);
    }
    
    // Get ownerEmail, folderId, and accessToken from family data
    // Pass userId to find the correct family
    const familyInfo = getFamilyInfo(userId);
    if (!familyInfo) {
      return createResponse({ error: 'Family data not found' }, 404);
    }
    
    const { ownerEmail, folderId, accessToken } = familyInfo;
    
    const usersData = loadJsonFileV3(FILE_NAMES.USERS, ownerEmail, folderId, accessToken);
    if (!usersData || !usersData.users) {
      return createResponse({ error: 'Users data not found' }, 404);
    }
    
    const choresData = loadJsonFileV3(FILE_NAMES.CHORES, ownerEmail, folderId, accessToken);
    if (!choresData || !choresData.chores) {
      return createResponse({ error: 'Chores data not found' }, 404);
    }
    
    // Verify user is authorized (must be parent or creator)
    const user = usersData.users.find(u => u.id === userId);
    if (!user) {
      return createResponse({ error: 'User not found' }, 404);
    }
    
    // Find chore
    const chore = choresData.chores.find(c => c.id === choreId);
    if (!chore) {
      return createResponse({ error: 'Chore not found' }, 404);
    }
    
    // Check authorization
    if (user.role !== 'parent' && chore.createdBy !== userId) {
      return createResponse({ error: 'Unauthorized' }, 403);
    }
    
    // Update allowed fields
    const allowedFields = ['title', 'description', 'assignedTo', 'pointValue', 'dueDate', 'recurring', 'subtasks', 'color', 'icon'];
    const updatedFields = [];
    
    allowedFields.forEach(field => {
      if (updates[field] !== undefined) {
        chore[field] = updates[field];
        updatedFields.push(field);
      }
    });
    
    // Save updates
    saveJsonFileV3(FILE_NAMES.CHORES, choresData, ownerEmail, folderId, accessToken);
    
    // Log update
    logActivity({
      actorId: userId,
      actorName: user.name,
      actorRole: user.role,
      actionType: 'chore_updated',
      referenceId: choreId,
      referenceType: 'chore',
      details: {
        choreTitle: chore.title,
        updatedFields: updatedFields
      }
    }, ownerEmail, folderId, accessToken);
    
    return createResponse({
      success: true,
      chore: chore
    });
    
  } catch (error) {
    Logger.log('Error in updateChore: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}

/**
 * Complete a chore
 */
function completeChore(data) {
  try {
    const { userId, choreId, photoProof } = data;
    
    if (!userId || !choreId) {
      return createResponse({ error: 'Missing required fields' }, 400);
    }
    
    // Get ownerEmail, folderId, and accessToken from family data
    // Pass userId to find the correct family
    const familyInfo = getFamilyInfo(userId);
    if (!familyInfo) {
      return createResponse({ error: 'Family data not found' }, 404);
    }
    
    const { ownerEmail, folderId, accessToken } = familyInfo;
    
    // Use Drive API v3 with access token
    const usersData = loadJsonFileV3(FILE_NAMES.USERS, ownerEmail, folderId, accessToken);
    if (!usersData || !usersData.users) {
      return createResponse({ error: 'Users data not found' }, 404);
    }
    
    const choresData = loadJsonFileV3(FILE_NAMES.CHORES, ownerEmail, folderId, accessToken);
    if (!choresData || !choresData.chores) {
      return createResponse({ error: 'Chores data not found' }, 404);
    }
    
    const transactionsData = loadJsonFileV3(FILE_NAMES.TRANSACTIONS, ownerEmail, folderId, accessToken) || { transactions: [] };
    const familyData = loadJsonFileV3(FILE_NAMES.FAMILY, ownerEmail, folderId, accessToken);
    if (!familyData) {
      return createResponse({ error: 'Family data not found' }, 404);
    }
    
    // Find user
    const user = usersData.users.find(u => u.id === userId);
    if (!user) {
      return createResponse({ error: 'User not found' }, 404);
    }
    
    // Find chore
    const chore = choresData.chores.find(c => c.id === choreId);
    if (!chore) {
      return createResponse({ error: 'Chore not found' }, 404);
    }
    
    // Check if user is assigned to this chore
    // If assignedTo is empty, anyone can complete it (unassigned chores)
    const isAssigned = Array.isArray(chore.assignedTo) && chore.assignedTo.length > 0;
    if (isAssigned && !chore.assignedTo.includes(userId)) {
      return createResponse({ error: 'You are not assigned to this chore' }, 403);
    }
    
    // Check if already completed
    if (chore.status === 'completed' || chore.status === 'verified') {
      return createResponse({ error: 'Chore already completed' }, 400);
    }
    
    // Check if all subtasks are completed
    const allSubtasksCompleted = chore.subtasks.every(st => st.completed);
    if (chore.subtasks.length > 0 && !allSubtasksCompleted) {
      return createResponse({ error: 'Please complete all subtasks first' }, 400);
    }
    
    // Update chore
    // If chore is unassigned, assign it to the user who completed it
    const isUnassigned = !Array.isArray(chore.assignedTo) || chore.assignedTo.length === 0;
    if (isUnassigned) {
      Logger.log('completeChore: Unassigned chore - assigning to user ' + userId);
      chore.assignedTo = [userId];
    }
    
    chore.status = familyData.settings.autoApproveChores ? 'verified' : 'completed';
    // Record who completed the chore
    chore.completedBy = userId;
    chore.completedAt = new Date().toISOString();
    chore.photoProof = photoProof || null;
    
    Logger.log('completeChore: Chore completed by userId=' + userId + ', wasUnassigned=' + isUnassigned);
    
    if (familyData.settings.autoApproveChores) {
      chore.verifiedBy = 'system';
      chore.verifiedAt = new Date().toISOString();
    }
    
    saveJsonFileV3(FILE_NAMES.CHORES, choresData, ownerEmail, folderId, accessToken);
    
    // Award points if user can earn points and chore is verified
    Logger.log('completeChore: Checking points award conditions');
    Logger.log('completeChore: user.canEarnPoints = ' + user.canEarnPoints);
    Logger.log('completeChore: chore.status = ' + chore.status);
    Logger.log('completeChore: familyData.settings.autoApproveChores = ' + familyData.settings.autoApproveChores);
    Logger.log('completeChore: chore.pointValue = ' + chore.pointValue);
    Logger.log('completeChore: familyData.settings.pointMultiplier = ' + (familyData.settings.pointMultiplier || 1));
    
    if (user.canEarnPoints && chore.status === 'verified') {
      const pointMultiplier = familyData.settings.pointMultiplier || 1;
      const pointsAwarded = Math.round(chore.pointValue * pointMultiplier);
      
      Logger.log('completeChore: Awarding ' + pointsAwarded + ' points to user ' + userId);
      Logger.log('completeChore: User current pointsBalance = ' + user.pointsBalance);
      
      // Find the user in the array and update directly to ensure the change persists
      const userIndex = usersData.users.findIndex(u => u.id === userId);
      if (userIndex !== -1) {
        // Update user points in the array
        usersData.users[userIndex].pointsBalance = (usersData.users[userIndex].pointsBalance || 0) + pointsAwarded;
        usersData.users[userIndex].stats = usersData.users[userIndex].stats || {};
        usersData.users[userIndex].stats.totalChoresCompleted = (usersData.users[userIndex].stats.totalChoresCompleted || 0) + 1;
        
        Logger.log('completeChore: User new pointsBalance = ' + usersData.users[userIndex].pointsBalance);
        
        // Also update the local user object for consistency
        user.pointsBalance = usersData.users[userIndex].pointsBalance;
        user.stats.totalChoresCompleted = usersData.users[userIndex].stats.totalChoresCompleted;
      } else {
        Logger.log('ERROR: User not found in users array when trying to award points');
      }
      
      saveJsonFileV3(FILE_NAMES.USERS, usersData, ownerEmail, folderId, accessToken);
      
      // Update family member data
      const familyMemberIndex = familyData.members.findIndex(m => m.id === userId);
      if (familyMemberIndex !== -1) {
        // Use the updated user from the array
        const updatedUser = usersData.users.find(u => u.id === userId);
        if (updatedUser) {
          familyData.members[familyMemberIndex] = updatedUser;
          saveJsonFileV3(FILE_NAMES.FAMILY, familyData, ownerEmail, folderId, accessToken);
        }
      }
      
      // Create transaction record
      const transaction = {
        id: Utilities.getUuid(),
        userId: userId,
        type: 'earn',
        points: pointsAwarded,
        reason: `Completed: ${chore.title}`,
        referenceId: choreId,
        timestamp: new Date().toISOString()
      };
      
      transactionsData.transactions.push(transaction);
      saveJsonFileV3(FILE_NAMES.TRANSACTIONS, transactionsData, ownerEmail, folderId, accessToken);
      
      Logger.log('completeChore: Points awarded successfully. Final user pointsBalance = ' + (usersData.users.find(u => u.id === userId)?.pointsBalance || 0));
    } else {
      Logger.log('completeChore: Points NOT awarded. canEarnPoints=' + user.canEarnPoints + ', status=' + chore.status);
    }
    
    // Get the updated user for the response
    const updatedUser = usersData.users.find(u => u.id === userId) || user;
    const pointMultiplier = familyData.settings.pointMultiplier || 1;
    const pointsAwarded = updatedUser.canEarnPoints && chore.status === 'verified' ? Math.round(chore.pointValue * pointMultiplier) : 0;
    
    // Log completion
    logActivity({
      actorId: userId,
      actorName: updatedUser.name,
      actorRole: updatedUser.role,
      actionType: 'chore_completed',
      referenceId: choreId,
      referenceType: 'chore',
      details: {
        choreTitle: chore.title,
        pointsEarned: pointsAwarded,
        hadPhotoProof: !!photoProof
      }
    }, ownerEmail, folderId, accessToken);
    
    // Handle recurring chores
    if (chore.recurring) {
      createNextRecurringChore(chore, usersData, choresData, ownerEmail, folderId, accessToken);
    }
    
    return createResponse({
      success: true,
      chore: chore,
      pointsAwarded: pointsAwarded,
      user: updatedUser // Return the updated user with new points balance
    });
    
  } catch (error) {
    Logger.log('Error in completeChore: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}

/**
 * Verify a completed chore (parent only)
 */
function verifyChore(data) {
  try {
    const { parentId, choreId, approved } = data;
    
    if (!parentId || !choreId || approved === undefined) {
      return createResponse({ error: 'Missing required fields' }, 400);
    }
    
    // Get ownerEmail, folderId, and accessToken from family data
    // Pass parentId to find the correct family
    const familyInfo = getFamilyInfo(parentId);
    if (!familyInfo) {
      return createResponse({ error: 'Family data not found' }, 404);
    }
    
    const { ownerEmail, folderId, accessToken } = familyInfo;
    
    const usersData = loadJsonFileV3(FILE_NAMES.USERS, ownerEmail, folderId, accessToken);
    if (!usersData || !usersData.users) {
      return createResponse({ error: 'Users data not found' }, 404);
    }
    
    const choresData = loadJsonFileV3(FILE_NAMES.CHORES, ownerEmail, folderId, accessToken);
    if (!choresData || !choresData.chores) {
      return createResponse({ error: 'Chores data not found' }, 404);
    }
    
    const transactionsData = loadJsonFileV3(FILE_NAMES.TRANSACTIONS, ownerEmail, folderId, accessToken) || { transactions: [] };
    const familyData = loadJsonFileV3(FILE_NAMES.FAMILY, ownerEmail, folderId, accessToken);
    if (!familyData) {
      return createResponse({ error: 'Family data not found' }, 404);
    }
    
    // Verify parent is authorized
    const parent = usersData.users.find(u => u.id === parentId);
    if (!parent || parent.role !== 'parent') {
      return createResponse({ error: 'Unauthorized' }, 403);
    }
    
    // Find chore
    const chore = choresData.chores.find(c => c.id === choreId);
    if (!chore) {
      return createResponse({ error: 'Chore not found' }, 404);
    }
    
    if (chore.status !== 'completed') {
      return createResponse({ error: 'Chore is not in completed status' }, 400);
    }
    
    if (approved) {
      // Approve and award points
      chore.status = 'verified';
      chore.verifiedBy = parentId;
      chore.verifiedAt = new Date().toISOString();
      
      // Award points to the user who completed the chore (from completedBy field)
      // This is especially important for unassigned chores where anyone can complete them
      const completedByUserId = chore.completedBy;
      const completedByUser = usersData.users.find(u => u.id === completedByUserId);
      
      const isUnassigned = !Array.isArray(chore.assignedTo) || chore.assignedTo.length === 0;
      Logger.log('verifyChore: Checking points award conditions');
      Logger.log('verifyChore: completedByUserId = ' + completedByUserId);
      Logger.log('verifyChore: isUnassigned = ' + isUnassigned);
      Logger.log('verifyChore: completedByUser found = ' + (completedByUser ? 'yes' : 'no'));
      if (isUnassigned && completedByUserId) {
        Logger.log('verifyChore: Verifying unassigned chore - awarding points to user who completed it: ' + completedByUserId);
      }
      Logger.log('verifyChore: completedByUser.canEarnPoints = ' + (completedByUser ? completedByUser.canEarnPoints : 'N/A'));
      Logger.log('verifyChore: chore.pointValue = ' + chore.pointValue);
      Logger.log('verifyChore: familyData.settings.pointMultiplier = ' + (familyData.settings.pointMultiplier || 1));
      
      if (completedByUser && completedByUser.canEarnPoints) {
        const pointMultiplier = familyData.settings.pointMultiplier || 1;
        const pointsAwarded = Math.round(chore.pointValue * pointMultiplier);
        
        Logger.log('verifyChore: Awarding ' + pointsAwarded + ' points to user ' + completedByUserId);
        Logger.log('verifyChore: User current pointsBalance = ' + (completedByUser.pointsBalance || 0));
        
        // Find the user in the array and update directly to ensure the change persists
        const userIndex = usersData.users.findIndex(u => u.id === completedByUserId);
        if (userIndex !== -1) {
          // Update user points in the array
          usersData.users[userIndex].pointsBalance = (usersData.users[userIndex].pointsBalance || 0) + pointsAwarded;
          usersData.users[userIndex].stats = usersData.users[userIndex].stats || {};
          usersData.users[userIndex].stats.totalChoresCompleted = (usersData.users[userIndex].stats.totalChoresCompleted || 0) + 1;
          
          Logger.log('verifyChore: User new pointsBalance = ' + usersData.users[userIndex].pointsBalance);
          
          // Update the local reference for consistency
          completedByUser.pointsBalance = usersData.users[userIndex].pointsBalance;
          completedByUser.stats.totalChoresCompleted = usersData.users[userIndex].stats.totalChoresCompleted;
        } else {
          Logger.log('ERROR: User not found in users array when trying to award points');
        }
        
        saveJsonFileV3(FILE_NAMES.USERS, usersData, ownerEmail, folderId, accessToken);
        
        // Update family member data - use the updated user from the array
        const updatedUser = usersData.users.find(u => u.id === completedByUserId);
        if (updatedUser) {
          const familyMemberIndex = familyData.members.findIndex(m => m.id === completedByUserId);
          if (familyMemberIndex !== -1) {
            familyData.members[familyMemberIndex] = updatedUser;
            saveJsonFileV3(FILE_NAMES.FAMILY, familyData, ownerEmail, folderId, accessToken);
          }
        }
        
        // Create transaction - use the updated user from the array
        const updatedUserForTransaction = usersData.users.find(u => u.id === completedByUserId);
        if (updatedUserForTransaction) {
          const transaction = {
            id: Utilities.getUuid(),
            userId: updatedUserForTransaction.id,
            type: 'earn',
            points: pointsAwarded,
            reason: `Completed & verified: ${chore.title}`,
            referenceId: choreId,
            timestamp: new Date().toISOString()
          };
          
          transactionsData.transactions.push(transaction);
          saveJsonFileV3(FILE_NAMES.TRANSACTIONS, transactionsData, ownerEmail, folderId, accessToken);
        }
        
        // Log verification - use the updated user from the array
        const updatedUserForLog = usersData.users.find(u => u.id === completedByUserId);
        if (updatedUserForLog) {
          logActivity({
            actorId: parentId,
            actorName: parent.name,
            actorRole: parent.role,
            actionType: 'chore_verified',
            targetUserId: updatedUserForLog.id,
            targetUserName: updatedUserForLog.name,
            referenceId: choreId,
            referenceType: 'chore',
            details: {
              choreTitle: chore.title,
              pointsAwarded: pointsAwarded
            }
          }, ownerEmail, folderId, accessToken);
        }
        
        Logger.log('verifyChore: Points awarded successfully. Final user pointsBalance = ' + (usersData.users.find(u => u.id === completedByUserId)?.pointsBalance || 0));
      } else {
        Logger.log('verifyChore: Points NOT awarded. completedByUser=' + (completedByUser ? 'found' : 'not found') + ', canEarnPoints=' + (completedByUser ? completedByUser.canEarnPoints : 'N/A'));
      }
    } else {
      // Reject - reset to pending
      chore.status = 'pending';
      chore.completedBy = null;
      chore.completedAt = null;
      chore.photoProof = null;
      
      // Log rejection
      logActivity({
        actorId: parentId,
        actorName: parent.name,
        actorRole: parent.role,
        actionType: 'chore_rejected',
        targetUserId: chore.completedBy,
        referenceId: choreId,
        referenceType: 'chore',
        details: {
          choreTitle: chore.title
        }
      }, ownerEmail, folderId, accessToken);
    }
    
    saveJsonFileV3(FILE_NAMES.CHORES, choresData, ownerEmail, folderId, accessToken);
    
    // Get the updated user if points were awarded
    let updatedUser = null;
    let pointsAwarded = 0;
    if (approved && chore.completedBy) {
      const completedByUserId = chore.completedBy;
      updatedUser = usersData.users.find(u => u.id === completedByUserId);
      if (updatedUser && updatedUser.canEarnPoints && chore.status === 'verified') {
        const pointMultiplier = familyData.settings.pointMultiplier || 1;
        pointsAwarded = Math.round(chore.pointValue * pointMultiplier);
      }
    }
    
    return createResponse({
      success: true,
      chore: chore,
      pointsAwarded: pointsAwarded,
      user: updatedUser // Return the updated user with new points balance if points were awarded
    });
    
  } catch (error) {
    Logger.log('Error in verifyChore: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}

/**
 * Delete a chore
 */
function deleteChore(data) {
  try {
    const { userId, choreId } = data;
    
    if (!userId || !choreId) {
      return createResponse({ error: 'Missing required fields' }, 400);
    }
    
    // Get ownerEmail, folderId, and accessToken from family data
    // Pass userId to find the correct family
    const familyInfo = getFamilyInfo(userId);
    if (!familyInfo) {
      return createResponse({ error: 'Family data not found' }, 404);
    }
    
    const { ownerEmail, folderId, accessToken } = familyInfo;
    
    const usersData = loadJsonFileV3(FILE_NAMES.USERS, ownerEmail, folderId, accessToken);
    if (!usersData || !usersData.users) {
      return createResponse({ error: 'Users data not found' }, 404);
    }
    
    const choresData = loadJsonFileV3(FILE_NAMES.CHORES, ownerEmail, folderId, accessToken);
    if (!choresData || !choresData.chores) {
      return createResponse({ error: 'Chores data not found' }, 404);
    }
    
    // Verify user is authorized (must be parent)
    const user = usersData.users.find(u => u.id === userId);
    if (!user || user.role !== 'parent') {
      return createResponse({ error: 'Unauthorized' }, 403);
    }
    
    // Find and remove chore
    const choreIndex = choresData.chores.findIndex(c => c.id === choreId);
    if (choreIndex === -1) {
      return createResponse({ error: 'Chore not found' }, 404);
    }
    
    const chore = choresData.chores[choreIndex];
    choresData.chores.splice(choreIndex, 1);
    saveJsonFileV3(FILE_NAMES.CHORES, choresData, ownerEmail, folderId, accessToken);
    
    // Log deletion
    logActivity({
      actorId: userId,
      actorName: user.name,
      actorRole: user.role,
      actionType: 'chore_deleted',
      referenceId: choreId,
      referenceType: 'chore',
      details: {
        choreTitle: chore.title
      }
    }, ownerEmail, folderId, accessToken);
    
    return createResponse({
      success: true,
      message: 'Chore deleted successfully'
    });
    
  } catch (error) {
    Logger.log('Error in deleteChore: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}

/**
 * List chores
 */
function listChores(familyId, userId) {
  try {
    // Search through all stored access tokens to find the family with matching familyId
    const userProps = PropertiesService.getUserProperties();
    const allProps = userProps.getProperties();
    
    let familyInfo = null;
    
    for (const key in allProps) {
      if (key.startsWith('ACCESS_TOKEN_')) {
        const ownerEmail = key.replace('ACCESS_TOKEN_', '');
        const accessToken = allProps[key];
        
        try {
          const folderId = getChoreQuestFolderV3(ownerEmail, accessToken);
          const familyData = loadJsonFileV3(FILE_NAMES.FAMILY, ownerEmail, folderId, accessToken);
          
          if (familyData && familyData.id === familyId) {
            familyInfo = {
              ownerEmail: ownerEmail,
              folderId: folderId,
              accessToken: accessToken,
              familyId: familyId
            };
            break;
          }
        } catch (error) {
          continue;
        }
      }
    }
    
    if (!familyInfo) {
      return createResponse({ error: 'Family not found' }, 404);
    }
    
    const { ownerEmail, folderId, accessToken } = familyInfo;
    
    const familyData = loadJsonFileV3(FILE_NAMES.FAMILY, ownerEmail, folderId, accessToken);
    const choresData = loadJsonFileV3(FILE_NAMES.CHORES, ownerEmail, folderId, accessToken) || { chores: [] };
    
    if (!familyData || familyData.id !== familyId) {
      return createResponse({ error: 'Family not found' }, 404);
    }
    
    // Filter chores if userId is provided
    let chores = choresData.chores;
    if (userId) {
      chores = chores.filter(c => 
        c.assignedTo.includes(userId) || c.createdBy === userId
      );
    }
    
    return createResponse({
      success: true,
      chores: chores
    });
    
  } catch (error) {
    Logger.log('Error in listChores: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}

/**
 * Get single chore
 * Searches through all families to find the chore
 */
function getChore(choreId) {
  try {
    // Search through all stored access tokens to find the chore
    const userProps = PropertiesService.getUserProperties();
    const allProps = userProps.getProperties();
    
    for (const key in allProps) {
      if (key.startsWith('ACCESS_TOKEN_')) {
        const ownerEmail = key.replace('ACCESS_TOKEN_', '');
        const accessToken = allProps[key];
        
        try {
          const folderId = getChoreQuestFolderV3(ownerEmail, accessToken);
          const choresData = loadJsonFileV3(FILE_NAMES.CHORES, ownerEmail, folderId, accessToken);
          
          if (choresData && choresData.chores) {
            const chore = choresData.chores.find(c => c.id === choreId);
            if (chore) {
              return createResponse({
                success: true,
                chore: chore
              });
            }
          }
        } catch (error) {
          continue;
        }
      }
    }
    
    return createResponse({ error: 'Chore not found' }, 404);
    
  } catch (error) {
    Logger.log('Error in getChore: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}

/**
 * Create next occurrence of recurring chore
 */
function createNextRecurringChore(originalChore, usersData, choresData, ownerEmail, folderId, accessToken) {
  try {
    if (!originalChore.recurring) return;
    
    const recurring = originalChore.recurring;
    const now = new Date();
    let nextDueDate = null;
    
    // Calculate next due date based on recurring type
    if (recurring.type === 'daily') {
      nextDueDate = new Date(now.getTime() + 24 * 60 * 60 * 1000);
    } else if (recurring.type === 'weekly') {
      nextDueDate = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000);
    } else if (recurring.type === 'monthly') {
      nextDueDate = new Date(now);
      nextDueDate.setMonth(nextDueDate.getMonth() + 1);
    } else if (recurring.type === 'custom' && recurring.interval) {
      nextDueDate = new Date(now.getTime() + recurring.interval * 24 * 60 * 60 * 1000);
    }
    
    // Check end date
    if (recurring.endDate && new Date(recurring.endDate) < nextDueDate) {
      return;
    }
    
    // Create new chore instance
    const newChore = {
      ...originalChore,
      id: Utilities.getUuid(),
      status: 'pending',
      dueDate: nextDueDate ? nextDueDate.toISOString() : null,
      completedBy: null,
      completedAt: null,
      photoProof: null,
      verifiedBy: null,
      verifiedAt: null,
      createdAt: new Date().toISOString(),
      subtasks: originalChore.subtasks.map(st => ({ ...st, completed: false }))
    };
    
    choresData.chores.push(newChore);
    saveJsonFileV3(FILE_NAMES.CHORES, choresData, ownerEmail, folderId, accessToken);
    
  } catch (error) {
    Logger.log('Error in createNextRecurringChore: ' + error.toString());
  }
}
