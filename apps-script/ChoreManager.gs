/**
 * ChoreManager.gs
 * Handles chore CRUD operations and completion logic
 */

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
    
    const usersData = loadJsonFile(FILE_NAMES.USERS);
    const choresData = loadJsonFile(FILE_NAMES.CHORES);
    
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
    saveJsonFile(FILE_NAMES.CHORES, choresData);
    
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
    });
    
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
    
    const usersData = loadJsonFile(FILE_NAMES.USERS);
    const choresData = loadJsonFile(FILE_NAMES.CHORES);
    
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
    saveJsonFile(FILE_NAMES.CHORES, choresData);
    
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
    });
    
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
    
    const usersData = loadJsonFile(FILE_NAMES.USERS);
    const choresData = loadJsonFile(FILE_NAMES.CHORES);
    const transactionsData = loadJsonFile(FILE_NAMES.TRANSACTIONS);
    const familyData = loadJsonFile(FILE_NAMES.FAMILY);
    
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
    if (!chore.assignedTo.includes(userId)) {
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
    chore.status = familyData.settings.autoApproveChores ? 'verified' : 'completed';
    chore.completedBy = userId;
    chore.completedAt = new Date().toISOString();
    chore.photoProof = photoProof || null;
    
    if (familyData.settings.autoApproveChores) {
      chore.verifiedBy = 'system';
      chore.verifiedAt = new Date().toISOString();
    }
    
    saveJsonFile(FILE_NAMES.CHORES, choresData);
    
    // Award points if user can earn points and chore is verified
    if (user.canEarnPoints && chore.status === 'verified') {
      const pointsAwarded = Math.round(chore.pointValue * familyData.settings.pointMultiplier);
      
      // Update user points
      user.pointsBalance += pointsAwarded;
      user.stats.totalChoresCompleted = (user.stats.totalChoresCompleted || 0) + 1;
      saveJsonFile(FILE_NAMES.USERS, usersData);
      
      // Update family member data
      const familyMemberIndex = familyData.members.findIndex(m => m.id === userId);
      if (familyMemberIndex !== -1) {
        familyData.members[familyMemberIndex] = user;
        saveJsonFile(FILE_NAMES.FAMILY, familyData);
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
      saveJsonFile(FILE_NAMES.TRANSACTIONS, transactionsData);
    }
    
    // Log completion
    logActivity({
      actorId: userId,
      actorName: user.name,
      actorRole: user.role,
      actionType: 'chore_completed',
      referenceId: choreId,
      referenceType: 'chore',
      details: {
        choreTitle: chore.title,
        pointsEarned: user.canEarnPoints && chore.status === 'verified' ? Math.round(chore.pointValue * familyData.settings.pointMultiplier) : 0,
        hadPhotoProof: !!photoProof
      }
    });
    
    // Handle recurring chores
    if (chore.recurring) {
      createNextRecurringChore(chore, usersData, choresData);
    }
    
    return createResponse({
      success: true,
      chore: chore,
      pointsAwarded: user.canEarnPoints && chore.status === 'verified' ? Math.round(chore.pointValue * familyData.settings.pointMultiplier) : 0
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
    
    const usersData = loadJsonFile(FILE_NAMES.USERS);
    const choresData = loadJsonFile(FILE_NAMES.CHORES);
    const transactionsData = loadJsonFile(FILE_NAMES.TRANSACTIONS);
    const familyData = loadJsonFile(FILE_NAMES.FAMILY);
    
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
      
      // Award points
      const completedByUser = usersData.users.find(u => u.id === chore.completedBy);
      if (completedByUser && completedByUser.canEarnPoints) {
        const pointsAwarded = Math.round(chore.pointValue * familyData.settings.pointMultiplier);
        
        completedByUser.pointsBalance += pointsAwarded;
        completedByUser.stats.totalChoresCompleted = (completedByUser.stats.totalChoresCompleted || 0) + 1;
        saveJsonFile(FILE_NAMES.USERS, usersData);
        
        // Update family member data
        const familyMemberIndex = familyData.members.findIndex(m => m.id === completedByUser.id);
        if (familyMemberIndex !== -1) {
          familyData.members[familyMemberIndex] = completedByUser;
          saveJsonFile(FILE_NAMES.FAMILY, familyData);
        }
        
        // Create transaction
        const transaction = {
          id: Utilities.getUuid(),
          userId: completedByUser.id,
          type: 'earn',
          points: pointsAwarded,
          reason: `Completed & verified: ${chore.title}`,
          referenceId: choreId,
          timestamp: new Date().toISOString()
        };
        
        transactionsData.transactions.push(transaction);
        saveJsonFile(FILE_NAMES.TRANSACTIONS, transactionsData);
        
        // Log verification
        logActivity({
          actorId: parentId,
          actorName: parent.name,
          actorRole: parent.role,
          actionType: 'chore_verified',
          targetUserId: completedByUser.id,
          targetUserName: completedByUser.name,
          referenceId: choreId,
          referenceType: 'chore',
          details: {
            choreTitle: chore.title,
            pointsAwarded: pointsAwarded
          }
        });
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
      });
    }
    
    saveJsonFile(FILE_NAMES.CHORES, choresData);
    
    return createResponse({
      success: true,
      chore: chore
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
    
    const usersData = loadJsonFile(FILE_NAMES.USERS);
    const choresData = loadJsonFile(FILE_NAMES.CHORES);
    
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
    saveJsonFile(FILE_NAMES.CHORES, choresData);
    
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
    });
    
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
    const familyData = loadJsonFile(FILE_NAMES.FAMILY);
    const choresData = loadJsonFile(FILE_NAMES.CHORES);
    
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
 */
function getChore(choreId) {
  try {
    const choresData = loadJsonFile(FILE_NAMES.CHORES);
    const chore = choresData.chores.find(c => c.id === choreId);
    
    if (!chore) {
      return createResponse({ error: 'Chore not found' }, 404);
    }
    
    return createResponse({
      success: true,
      chore: chore
    });
    
  } catch (error) {
    Logger.log('Error in getChore: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}

/**
 * Create next occurrence of recurring chore
 */
function createNextRecurringChore(originalChore, usersData, choresData) {
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
    saveJsonFile(FILE_NAMES.CHORES, choresData);
    
  } catch (error) {
    Logger.log('Error in createNextRecurringChore: ' + error.toString());
  }
}
