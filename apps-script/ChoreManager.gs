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
  } else if (action === 'delete_template') {
    return deleteRecurringChoreTemplate(data);
  }
  
  return createResponse({ error: 'Invalid chores action' }, 400);
}

/**
 * Create a new chore
 */
function createChore(data) {
  try {
    const { creatorId, title, description, assignedTo, pointValue, dueDate, recurring, subtasks, color, icon, requirePhotoProof } = data;
    
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
    
    // If recurring, save as template and create initial instance
    if (recurring) {
      const templateId = Utilities.getUuid();
      const template = {
        id: templateId,
        title: title,
        description: description || '',
        assignedTo: Array.isArray(assignedTo) ? assignedTo : [assignedTo],
        createdBy: creatorId,
        pointValue: pointValue,
        dueDate: dueDate || null,
        recurring: recurring,
        subtasks: subtasks || [],
        createdAt: new Date().toISOString(),
        color: color || null,
        icon: icon || null
      };
      
      // Save template
      const templatesData = loadJsonFileV3(FILE_NAMES.RECURRING_CHORE_TEMPLATES, ownerEmail, folderId, accessToken) || { templates: [] };
      templatesData.templates.push(template);
      saveJsonFileV3(FILE_NAMES.RECURRING_CHORE_TEMPLATES, templatesData, ownerEmail, folderId, accessToken);
      
      // Create initial instance for current cycle
      const initialInstance = createChoreInstanceFromTemplate(template, ownerEmail, folderId, accessToken);
      if (initialInstance) {
        choresData.chores.push(initialInstance);
        saveJsonFileV3(FILE_NAMES.CHORES, choresData, ownerEmail, folderId, accessToken);
        
        // Update template with initial due date and cycle ID
        const templateIndex = templatesData.templates.findIndex(t => t.id === template.id);
        if (templateIndex !== -1) {
          templatesData.templates[templateIndex].lastDueDate = initialInstance.dueDate;
          templatesData.templates[templateIndex].lastCycleId = initialInstance.cycleId;
          saveJsonFileV3(FILE_NAMES.RECURRING_CHORE_TEMPLATES, templatesData, ownerEmail, folderId, accessToken);
        }
        
        // Log chore creation
        logActivity({
          actorId: creatorId,
          actorName: creator.name,
          actorRole: creator.role,
          actionType: 'chore_created',
          referenceId: templateId,
          referenceType: 'recurring_chore_template',
          details: {
            choreTitle: title,
            assignedTo: template.assignedTo.map(userId => {
              const user = usersData.users.find(u => u.id === userId);
              return user ? user.name : userId;
            }),
            pointValue: pointValue,
            recurring: true
          }
        }, ownerEmail, folderId, accessToken);
        
        return createResponse({
          success: true,
          chore: initialInstance,
          templateId: templateId
        });
      }
    }
    
    // Create new non-recurring chore
    const choreId = Utilities.getUuid();
    const newChore = {
      id: choreId,
      title: title,
      description: description || '',
      assignedTo: Array.isArray(assignedTo) ? assignedTo : [assignedTo],
      createdBy: creatorId,
      pointValue: pointValue,
      dueDate: dueDate || null,
      recurring: null,
      subtasks: subtasks || [],
      status: 'pending',
      photoProof: null,
      requirePhotoProof: requirePhotoProof || false,
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
    // Allow parents, creators, or assigned children (for subtask updates)
    const isParent = user.role === 'parent';
    const isCreator = chore.createdBy === userId;
    const isAssigned = Array.isArray(chore.assignedTo) && chore.assignedTo.includes(userId);
    const canUpdate = isParent || isCreator || (isAssigned && updates.subtasks !== undefined);
    
    if (!canUpdate) {
      return createResponse({ error: 'Unauthorized' }, 403);
    }
    
    // Update allowed fields
    const allowedFields = ['title', 'description', 'assignedTo', 'pointValue', 'dueDate', 'recurring', 'subtasks', 'color', 'icon', 'requirePhotoProof'];
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
    
    // Handle recurring chores - don't create new instance immediately
    // New instances will be created when the next cycle starts via ensureRecurringChoreInstances
    // This is handled in listChores to ensure instances are created when needed
    
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
 * Delete a recurring chore template
 */
function deleteRecurringChoreTemplate(data) {
  try {
    const { userId, templateId } = data;
    
    if (!userId || !templateId) {
      return createResponse({ error: 'Missing required fields' }, 400);
    }
    
    // Get ownerEmail, folderId, and accessToken from family data
    const familyInfo = getFamilyInfo(userId);
    if (!familyInfo) {
      return createResponse({ error: 'Family data not found' }, 404);
    }
    
    const { ownerEmail, folderId, accessToken } = familyInfo;
    
    const usersData = loadJsonFileV3(FILE_NAMES.USERS, ownerEmail, folderId, accessToken);
    if (!usersData || !usersData.users) {
      return createResponse({ error: 'Users data not found' }, 404);
    }
    
    const templatesData = loadJsonFileV3(FILE_NAMES.RECURRING_CHORE_TEMPLATES, ownerEmail, folderId, accessToken);
    if (!templatesData || !templatesData.templates) {
      return createResponse({ error: 'Templates data not found' }, 404);
    }
    
    // Verify user is authorized (must be parent)
    const user = usersData.users.find(u => u.id === userId);
    if (!user || user.role !== 'parent') {
      return createResponse({ error: 'Unauthorized' }, 403);
    }
    
    // Find and remove template
    const templateIndex = templatesData.templates.findIndex(t => t.id === templateId);
    if (templateIndex === -1) {
      return createResponse({ error: 'Template not found' }, 404);
    }
    
    const template = templatesData.templates[templateIndex];
    templatesData.templates.splice(templateIndex, 1);
    saveJsonFileV3(FILE_NAMES.RECURRING_CHORE_TEMPLATES, templatesData, ownerEmail, folderId, accessToken);
    
    // Also delete all chore instances created from this template
    const choresData = loadJsonFileV3(FILE_NAMES.CHORES, ownerEmail, folderId, accessToken);
    if (choresData && choresData.chores) {
      const templateChores = choresData.chores.filter(c => c.templateId === templateId);
      if (templateChores.length > 0) {
        choresData.chores = choresData.chores.filter(c => c.templateId !== templateId);
        saveJsonFileV3(FILE_NAMES.CHORES, choresData, ownerEmail, folderId, accessToken);
      }
    }
    
    // Log deletion
    logActivity({
      actorId: userId,
      actorName: user.name,
      actorRole: user.role,
      actionType: 'recurring_chore_template_deleted',
      referenceId: templateId,
      referenceType: 'recurring_chore_template',
      details: {
        templateTitle: template.title
      }
    }, ownerEmail, folderId, accessToken);
    
    return createResponse({
      success: true,
      message: 'Recurring chore template deleted successfully'
    });
    
  } catch (error) {
    Logger.log('Error in deleteRecurringChoreTemplate: ' + error.toString());
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
    
    if (!familyData || familyData.id !== familyId) {
      return createResponse({ error: 'Family not found' }, 404);
    }
    
    // Ensure all recurring chore templates have instances for current cycle
    // This also handles expired chores by creating instances for current cycle if needed
    ensureRecurringChoreInstances(ownerEmail, folderId, accessToken);
    
    // Add a log entry to indicate listChores was called
    if (typeof addDebugLog !== 'undefined') {
      addDebugLog('INFO', 'listChores: Called for familyId=' + familyId + ', userId=' + (userId || 'all'));
    }
    
    // Reload chores data after ensuring instances
    const choresData = loadJsonFileV3(FILE_NAMES.CHORES, ownerEmail, folderId, accessToken) || { chores: [] };
    
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
 * Get cycle identifier for a given date and frequency
 * Returns a string like "2024-01-15" for daily, "2024-W03" for weekly, "2024-01" for monthly
 */
function getCycleIdentifier(date, frequency) {
  const d = new Date(date);
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  
  if (frequency === 'daily' || frequency === 'DAILY') {
    return `${year}-${month}-${day}`;
  } else if (frequency === 'weekly' || frequency === 'WEEKLY') {
    // Get week number (ISO week)
    const week = getWeekNumber(d);
    return `${year}-W${String(week).padStart(2, '0')}`;
  } else if (frequency === 'monthly' || frequency === 'MONTHLY') {
    return `${year}-${month}`;
  }
  return null;
}

/**
 * Get ISO week number for a date
 */
function getWeekNumber(date) {
  const d = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
  const dayNum = d.getUTCDay() || 7;
  d.setUTCDate(d.getUTCDate() + 4 - dayNum);
  const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1));
  return Math.ceil((((d - yearStart) / 86400000) + 1) / 7);
}

/**
 * Get current cycle identifier based on frequency
 */
function getCurrentCycleIdentifier(frequency) {
  return getCycleIdentifier(new Date(), frequency);
}

/**
 * Create a chore instance from a template for a specific cycle
 */
function createChoreInstanceFromTemplate(template, ownerEmail, folderId, accessToken) {
  try {
    if (!template.recurring) return null;
    
    const recurring = template.recurring;
    const frequency = (recurring.frequency || recurring.type || '').toLowerCase(); // Support both field names, normalize to lowercase
    if (!frequency) return null;
    const now = new Date();
    let dueDate = null;
    
    // If template has a dueDate, use it (for initial instance with custom due date)
    // Check if this is the initial instance by seeing if template doesn't have lastCycleId yet
    if (template.dueDate && !template.lastCycleId) {
      // This is the initial instance - use the template's dueDate as specified
      const templateDueDate = new Date(template.dueDate);
      templateDueDate.setHours(0, 0, 0, 0);
      dueDate = templateDueDate;
    }
    
    // If no dueDate from template or this is a subsequent instance, calculate based on frequency
    if (!dueDate) {
      // Calculate due date based on frequency (date only, no time)
      if (frequency === 'daily' || frequency === 'DAILY') {
        // Due today
        dueDate = new Date(now.getFullYear(), now.getMonth(), now.getDate());
      } else if (frequency === 'weekly' || frequency === 'WEEKLY') {
        // Due at end of week (Sunday)
        const dayOfWeek = now.getDay();
        const daysUntilSunday = dayOfWeek === 0 ? 0 : 7 - dayOfWeek;
        dueDate = new Date(now.getFullYear(), now.getMonth(), now.getDate() + daysUntilSunday);
      } else if (frequency === 'monthly' || frequency === 'MONTHLY') {
        // Use specified day of month, or default to end of month
        const targetDay = recurring.dayOfMonth || null;
        if (targetDay !== null && targetDay >= 1 && targetDay <= 31) {
          try {
            // Try to set the day in the current month
            const daysInCurrentMonth = new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate();
            const dayToUse = Math.min(targetDay, daysInCurrentMonth);
            const targetDate = new Date(now.getFullYear(), now.getMonth(), dayToUse);
            targetDate.setHours(0, 0, 0, 0);
            
            // If the day has already passed this month, move to next month
            const nowDateOnly = new Date(now.getFullYear(), now.getMonth(), now.getDate());
            nowDateOnly.setHours(0, 0, 0, 0);
            
            if (targetDate < nowDateOnly) {
              // Move to next month
              const nextMonth = new Date(now.getFullYear(), now.getMonth() + 1, 1);
              const daysInNextMonth = new Date(nextMonth.getFullYear(), nextMonth.getMonth() + 1, 0).getDate();
              const nextDayToUse = Math.min(targetDay, daysInNextMonth);
              dueDate = new Date(nextMonth.getFullYear(), nextMonth.getMonth(), nextDayToUse);
            } else {
              dueDate = targetDate;
            }
          } catch (e) {
            // If day doesn't exist in current month, use last day
            dueDate = new Date(now.getFullYear(), now.getMonth() + 1, 0);
          }
        } else {
          // Default to end of month if no dayOfMonth specified
          dueDate = new Date(now.getFullYear(), now.getMonth() + 1, 0);
        }
      } else {
        return null; // Unknown frequency
      }
    }
    
    // Check end date (compare dates only)
    if (recurring.endDate) {
      const endDate = new Date(recurring.endDate);
      endDate.setHours(0, 0, 0, 0);
      const dueDateOnly = new Date(dueDate);
      dueDateOnly.setHours(0, 0, 0, 0);
      if (endDate < dueDateOnly) {
        return null;
      }
    }
    
    // Calculate cycle ID based on the due date (not current date)
    // This ensures the cycle ID matches the due date's cycle
    const cycleId = getCycleIdentifier(dueDate, frequency);
    
    // Format dueDate as date-only string (YYYY-MM-DD)
    const year = dueDate.getFullYear();
    const month = String(dueDate.getMonth() + 1).padStart(2, '0');
    const day = String(dueDate.getDate()).padStart(2, '0');
    const dueDateString = `${year}-${month}-${day}`;
    
    // Create new chore instance
    const instance = {
      id: Utilities.getUuid(),
      templateId: template.id,
      cycleId: cycleId,
      title: template.title,
      description: template.description || '',
      assignedTo: template.assignedTo || [],
      createdBy: template.createdBy,
      pointValue: template.pointValue,
      dueDate: dueDateString,
      recurring: template.recurring, // Preserve recurring info so app can identify recurring chores
      subtasks: (template.subtasks || []).map(st => ({
        id: st.id || Utilities.getUuid(),
        title: st.title,
        completed: false,
        completedBy: null,
        completedAt: null
      })),
      status: 'pending',
      photoProof: null,
      completedBy: null,
      completedAt: null,
      verifiedBy: null,
      verifiedAt: null,
      createdAt: new Date().toISOString(),
      color: template.color || null,
      icon: template.icon || null
    };
    
    return instance;
    
  } catch (error) {
    Logger.log('Error in createChoreInstanceFromTemplate: ' + error.toString());
    return null;
  }
}

/**
 * Check if a chore instance exists for a template in the current cycle
 */
function hasInstanceForCurrentCycle(templateId, cycleId, choresData) {
  if (!choresData || !choresData.chores) return false;
  return choresData.chores.some(c => 
    c.templateId === templateId && 
    c.cycleId === cycleId
  );
}

/**
 * Check if a chore instance is completed for a given cycle
 */
function isCompletedForCycle(templateId, cycleId, choresData) {
  if (!choresData || !choresData.chores) return false;
  const instance = choresData.chores.find(c => 
    c.templateId === templateId && 
    c.cycleId === cycleId
  );
  return instance && (instance.status === 'completed' || instance.status === 'verified');
}

/**
 * Ensure all recurring chore templates have instances for the current cycle
 * Also creates instances for expired chores if not already completed for current cycle
 */
function ensureRecurringChoreInstances(ownerEmail, folderId, accessToken) {
  try {
    const logMsg = 'ensureRecurringChoreInstances: Starting';
    Logger.log(logMsg);
    if (typeof addDebugLog !== 'undefined') {
      addDebugLog('INFO', logMsg);
    }
    
    const templatesData = loadJsonFileV3(FILE_NAMES.RECURRING_CHORE_TEMPLATES, ownerEmail, folderId, accessToken);
    if (!templatesData || !templatesData.templates) {
      const msg = 'ensureRecurringChoreInstances: No templates found';
      Logger.log(msg);
      if (typeof addDebugLog !== 'undefined') {
        addDebugLog('WARN', msg);
      }
      return;
    }
    
    const templatesMsg = 'ensureRecurringChoreInstances: Found ' + templatesData.templates.length + ' templates';
    Logger.log(templatesMsg);
    if (typeof addDebugLog !== 'undefined') {
      addDebugLog('INFO', templatesMsg);
    }
    
    const usersData = loadJsonFileV3(FILE_NAMES.USERS, ownerEmail, folderId, accessToken);
    if (!usersData || !usersData.users) {
      const msg = 'ensureRecurringChoreInstances: No users data found';
      Logger.log(msg);
      addDebugLog('WARN', msg);
      return;
    }
    
    const choresData = loadJsonFileV3(FILE_NAMES.CHORES, ownerEmail, folderId, accessToken) || { chores: [] };
    const choresMsg = 'ensureRecurringChoreInstances: Found ' + (choresData.chores ? choresData.chores.length : 0) + ' chores';
    Logger.log(choresMsg);
    if (typeof addDebugLog !== 'undefined') {
      addDebugLog('INFO', choresMsg);
    }
    
    let hasChanges = false;
    let templatesNeedSaving = false;
    const now = new Date();
    
    for (const template of templatesData.templates) {
      if (!template.recurring) continue;
      
      const frequency = (template.recurring.frequency || template.recurring.type || '').toLowerCase();
      if (!frequency) continue;
      
      const currentCycleId = getCurrentCycleIdentifier(frequency);
      
      // Check for expired instances that need to be removed
      // These are instances with past due dates that are not completed/verified
      // Normalize dates for comparison (date only, no time)
      const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
      today.setHours(0, 0, 0, 0); // Ensure it's normalized
      // Debug: Log all chores for this template
      const allTemplateChores = choresData.chores.filter(c => c.templateId === template.id);
      const templateInfoMsg = 'Template ' + template.id + ' (' + template.title + '): Found ' + allTemplateChores.length + ' total instances';
      Logger.log(templateInfoMsg);
      if (typeof addDebugLog !== 'undefined') {
        addDebugLog('INFO', templateInfoMsg, { templateId: template.id, templateTitle: template.title, instanceCount: allTemplateChores.length });
      }
      
      allTemplateChores.forEach(c => {
        const choreInfo = '  - Chore: id=' + c.id + ', dueDate=' + c.dueDate + ', cycleId=' + (c.cycleId || 'none') + ', status=' + c.status;
        Logger.log(choreInfo);
        if (typeof addDebugLog !== 'undefined') {
          addDebugLog('DEBUG', choreInfo, { choreId: c.id, dueDate: c.dueDate, cycleId: c.cycleId, status: c.status });
        }
      });
      
      const expiredInstances = choresData.chores.filter(c => {
        // Must have templateId matching this template
        if (!c.templateId || c.templateId !== template.id) return false;
        // Must have a due date
        if (!c.dueDate) {
          const msg = 'Chore ' + c.id + ' has no dueDate, skipping';
          Logger.log(msg);
          if (typeof addDebugLog !== 'undefined') {
            addDebugLog('DEBUG', msg, { choreId: c.id });
          }
          return false;
        }
        // Skip completed/verified chores
        if (c.status === 'completed' || c.status === 'verified') {
          const msg = 'Chore ' + c.id + ' is completed/verified, skipping';
          Logger.log(msg);
          if (typeof addDebugLog !== 'undefined') {
            addDebugLog('DEBUG', msg, { choreId: c.id, status: c.status });
          }
          return false;
        }
        
        // Parse and normalize the due date
        // Handle both YYYY-MM-DD format and full ISO date strings
        let choreDueDate;
        if (typeof c.dueDate === 'string' && c.dueDate.match(/^\d{4}-\d{2}-\d{2}$/)) {
          // YYYY-MM-DD format - parse as local date
          const parts = c.dueDate.split('-');
          choreDueDate = new Date(parseInt(parts[0]), parseInt(parts[1]) - 1, parseInt(parts[2]));
        } else {
          // Try parsing as ISO date string
          choreDueDate = new Date(c.dueDate);
        }
        
        if (isNaN(choreDueDate.getTime())) {
          const msg = 'Chore ' + c.id + ' has invalid dueDate: ' + c.dueDate;
          Logger.log(msg);
          if (typeof addDebugLog !== 'undefined') {
            addDebugLog('WARN', msg, { choreId: c.id, dueDate: c.dueDate });
          }
          return false;
        }
        
        // Normalize to date only (no time component)
        choreDueDate.setHours(0, 0, 0, 0);
        
        // A chore is expired ONLY if dueDate is strictly less than today (<)
        // If dueDate equals today or is in the future, it's NOT expired
        const choreTime = choreDueDate.getTime();
        const todayTime = today.getTime();
        const isExpired = choreTime < todayTime;
        
        // CRITICAL: Don't remove if it's not expired at all (regardless of cycle)
        // This is the most important check - if dueDate >= today, never remove it
        if (!isExpired) {
          const msg = 'Chore ' + c.id + ' is NOT expired (dueDate >= today), keeping it. dueDate=' + c.dueDate + ' (' + choreDueDate.toISOString() + '), today=' + today.toISOString() + ', choreTime=' + choreTime + ', todayTime=' + todayTime;
          Logger.log(msg);
          if (typeof addDebugLog !== 'undefined') {
            addDebugLog('INFO', msg, { 
              choreId: c.id, 
              dueDate: c.dueDate, 
              choreDueDateISO: choreDueDate.toISOString(), 
              todayISO: today.toISOString(),
              choreTime: choreTime,
              todayTime: todayTime,
              timeDiff: todayTime - choreTime
            });
          }
          return false; // NEVER remove non-expired chores
        }
        
        // Only remove if expired AND not for current cycle
        // OR if expired for current cycle, we'll handle it separately
        const isCurrentCycle = c.cycleId === currentCycleId;
        
        // Don't remove if it's for current cycle and not expired - it's still valid
        // (This check is redundant now but kept for safety)
        if (isCurrentCycle && !isExpired) {
          const msg = 'Chore ' + c.id + ' is for current cycle and not expired, keeping it';
          Logger.log(msg);
          if (typeof addDebugLog !== 'undefined') {
            addDebugLog('DEBUG', msg, { choreId: c.id, cycleId: c.cycleId, currentCycleId: currentCycleId });
          }
          return false;
        }
        
        // Log for debugging - only reached if chore is actually expired
        const checkMsg = 'Chore ' + c.id + ' IS expired: dueDate=' + c.dueDate + ' (' + choreDueDate.toISOString() + '), today=' + today.toISOString() + ', cycleId=' + (c.cycleId || 'none') + ', currentCycleId=' + currentCycleId;
        Logger.log(checkMsg);
        if (typeof addDebugLog !== 'undefined') {
          addDebugLog('INFO', checkMsg, { 
          choreId: c.id, 
          dueDate: c.dueDate, 
          choreDueDateISO: choreDueDate.toISOString(), 
          todayISO: today.toISOString(), 
          isExpired: isExpired,
          cycleId: c.cycleId,
          currentCycleId: currentCycleId,
          choreTime: choreTime,
          todayTime: todayTime
          });
        }
        
        return true; // Only return true if actually expired
      });
      
      const expiredMsg = 'Template ' + template.id + ' (' + template.title + '): Found ' + expiredInstances.length + ' expired instances to remove';
      Logger.log(expiredMsg);
      if (typeof addDebugLog !== 'undefined') {
        addDebugLog('INFO', expiredMsg, { templateId: template.id, expiredCount: expiredInstances.length });
      }
      
      // Track if we removed an expired instance for the current cycle
      let removedCurrentCycleInstance = false;
      // Track if we removed any expired instances (for logging/debugging)
      let removedAnyExpired = false;
      
      // Remove expired instances and log the removal
      for (const expiredInstance of expiredInstances) {
        // Double-check that this instance is actually expired before removing
        // A chore is expired ONLY if dueDate is strictly less than today (<)
        // If dueDate equals today or is in the future, it's NOT expired
        const instanceDueDate = new Date(expiredInstance.dueDate);
        instanceDueDate.setHours(0, 0, 0, 0);
        const isActuallyExpired = instanceDueDate.getTime() < today.getTime();
        const isCurrentCycle = expiredInstance.cycleId === currentCycleId;
        
        // Safety check: Don't remove if it's for current cycle and not actually expired
        if (isCurrentCycle && !isActuallyExpired) {
          const skipMsg = 'Skipping removal of chore ' + expiredInstance.id + ' - it is for current cycle and not expired';
          Logger.log(skipMsg);
          if (typeof addDebugLog !== 'undefined') {
            addDebugLog('WARN', skipMsg, { 
            choreId: expiredInstance.id, 
            dueDate: expiredInstance.dueDate, 
            cycleId: expiredInstance.cycleId,
            currentCycleId: currentCycleId
            });
          }
          continue; // Skip this one
        }
        
        const index = choresData.chores.findIndex(c => c.id === expiredInstance.id);
        if (index !== -1) {
          // Check if this expired instance is for the current cycle
          if (isCurrentCycle) {
            removedCurrentCycleInstance = true;
          }
          removedAnyExpired = true;
          
          choresData.chores.splice(index, 1);
          hasChanges = true;
          
          // Log expired chore removal
          logActivity({
            actorId: 'system',
            actorName: 'System',
            actorRole: 'system',
            actionType: 'chore_deleted',
            referenceId: expiredInstance.id,
            referenceType: 'chore',
            details: {
              choreTitle: expiredInstance.title,
              dueDate: expiredInstance.dueDate,
              cycleId: expiredInstance.cycleId,
              reason: 'Expired (system cleanup)'
            }
          }, ownerEmail, folderId, accessToken);
        }
      }
      
      // Check if instance exists for current cycle (after removals)
      const templateLastCycleId = template.lastCycleId || null;
      const instanceExists = hasInstanceForCurrentCycle(template.id, currentCycleId, choresData);
      
      // Additional safety check: verify there's actually a valid (non-expired) instance for current cycle
      let hasValidCurrentCycleInstance = false;
      if (instanceExists) {
        const currentCycleInstances = choresData.chores.filter(c => 
          c.templateId === template.id && 
          c.cycleId === currentCycleId
        );
        for (const instance of currentCycleInstances) {
          if (instance.dueDate) {
            const instanceDueDate = new Date(instance.dueDate);
            instanceDueDate.setHours(0, 0, 0, 0);
            if (instanceDueDate >= today) {
              hasValidCurrentCycleInstance = true;
              break;
            }
          }
        }
      }
      
      // Only create a new instance if:
      // 1. We removed an expired instance for current cycle (need to recreate), OR
      // 2. No instance exists for current cycle AND template's lastCycleId doesn't match (new cycle or first time)
      // But NOT if a valid (non-expired) instance already exists for current cycle (it's up to date)
      const shouldCreateInstance = (removedCurrentCycleInstance || 
                                   (!instanceExists && templateLastCycleId !== currentCycleId)) &&
                                   !hasValidCurrentCycleInstance; // Don't create if valid instance exists
      
      // Only create if not already completed for current cycle
      if (shouldCreateInstance && !isCompletedForCycle(template.id, currentCycleId, choresData)) {
        const instance = createChoreInstanceFromTemplate(template, ownerEmail, folderId, accessToken);
        if (instance) {
          choresData.chores.push(instance);
          hasChanges = true;
          
          // Update template with current due date and cycle ID (save at end of loop)
          const templateIndex = templatesData.templates.findIndex(t => t.id === template.id);
          if (templateIndex !== -1) {
            templatesData.templates[templateIndex].lastDueDate = instance.dueDate;
            templatesData.templates[templateIndex].lastCycleId = instance.cycleId;
            templatesNeedSaving = true;
          }
          
          // Log recurring instance creation
          logActivity({
            actorId: 'system',
            actorName: 'System',
            actorRole: 'system',
            actionType: 'chore_created',
            referenceId: instance.id,
            referenceType: 'recurring_chore_instance',
            details: {
              choreTitle: instance.title,
              assignedTo: instance.assignedTo.map(userId => {
                const user = usersData.users.find(u => u.id === userId);
                return user ? user.name : userId;
              }),
              pointValue: instance.pointValue,
              dueDate: instance.dueDate,
              cycleId: instance.cycleId,
              recurring: true
            }
          }, ownerEmail, folderId, accessToken);
        }
      }
      
      // Check for expired instances from previous cycles that need new instances
      const expiredFromPreviousCycle = choresData.chores.filter(c => 
        c.templateId === template.id &&
        c.dueDate &&
        new Date(c.dueDate) < now &&
        c.cycleId !== currentCycleId &&
        (c.status === 'completed' || c.status === 'verified')
      );
      
      for (const expiredInstance of expiredFromPreviousCycle) {
        // If expired from previous cycle and not completed for current cycle, create instance for current cycle
        // Also check if template's lastCycleId doesn't match current cycle to prevent duplicates
        const templateLastCycleId = template.lastCycleId || null;
        if (!isCompletedForCycle(template.id, currentCycleId, choresData) && 
            templateLastCycleId !== currentCycleId) {
          const newInstance = createChoreInstanceFromTemplate(template, ownerEmail, folderId, accessToken);
          if (newInstance) {
            choresData.chores.push(newInstance);
            hasChanges = true;
            
            // Update template with current due date and cycle ID (save at end of loop)
            const templateIndex = templatesData.templates.findIndex(t => t.id === template.id);
            if (templateIndex !== -1) {
              templatesData.templates[templateIndex].lastDueDate = newInstance.dueDate;
              templatesData.templates[templateIndex].lastCycleId = newInstance.cycleId;
              templatesNeedSaving = true;
            }
            
            // Log recurring instance creation
            logActivity({
              actorId: 'system',
              actorName: 'System',
              actorRole: 'system',
              actionType: 'chore_created',
              referenceId: newInstance.id,
              referenceType: 'recurring_chore_instance',
              details: {
                choreTitle: newInstance.title,
                assignedTo: newInstance.assignedTo.map(userId => {
                  const user = usersData.users.find(u => u.id === userId);
                  return user ? user.name : userId;
                }),
                pointValue: newInstance.pointValue,
                dueDate: newInstance.dueDate,
                cycleId: newInstance.cycleId,
                recurring: true
              }
            }, ownerEmail, folderId, accessToken);
          }
        }
      }
    }
    
    // Save templates file once at the end if any templates were updated
    if (templatesNeedSaving) {
      saveJsonFileV3(FILE_NAMES.RECURRING_CHORE_TEMPLATES, templatesData, ownerEmail, folderId, accessToken);
    }
    
    if (hasChanges) {
      const saveMsg = 'ensureRecurringChoreInstances: Saving changes to chores file';
      Logger.log(saveMsg);
      if (typeof addDebugLog !== 'undefined') {
        addDebugLog('INFO', saveMsg);
      }
      saveJsonFileV3(FILE_NAMES.CHORES, choresData, ownerEmail, folderId, accessToken);
      const savedMsg = 'ensureRecurringChoreInstances: Changes saved successfully';
      Logger.log(savedMsg);
      if (typeof addDebugLog !== 'undefined') {
        addDebugLog('INFO', savedMsg);
      }
    } else {
      const noChangesMsg = 'ensureRecurringChoreInstances: No changes to save';
      Logger.log(noChangesMsg);
      if (typeof addDebugLog !== 'undefined') {
        addDebugLog('INFO', noChangesMsg);
      }
    }
    
    const completedMsg = 'ensureRecurringChoreInstances: Completed';
    Logger.log(completedMsg);
    if (typeof addDebugLog !== 'undefined') {
      addDebugLog('INFO', completedMsg);
    }
    
  } catch (error) {
    const errorMsg = 'Error in ensureRecurringChoreInstances: ' + error.toString();
    Logger.log(errorMsg);
    Logger.log('Error stack: ' + (error.stack || 'no stack'));
    if (typeof addDebugLog !== 'undefined') {
      addDebugLog('ERROR', errorMsg, { error: error.toString(), stack: error.stack || 'no stack' });
    }
  }
}
