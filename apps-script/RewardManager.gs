/**
 * RewardManager.gs
 * Handles reward CRUD operations and redemption logic
 */

/**
 * Handle reward requests (GET)
 */
function handleRewardsRequest(e) {
  const action = e.parameter.action;
  const familyId = e.parameter.familyId;
  const rewardId = e.parameter.rewardId;
  
  if (action === 'list' && familyId) {
    return listRewards(familyId);
  } else if (action === 'get' && rewardId) {
    return getReward(rewardId);
  }
  
  return createResponse({ error: 'Invalid rewards action' }, 400);
}

/**
 * Handle reward requests (POST)
 */
function handleRewardsPost(e, data) {
  const action = e.parameter.action;
  
  if (action === 'create') {
    return createReward(data);
  } else if (action === 'update') {
    return updateReward(data);
  } else if (action === 'redeem') {
    return redeemReward(data);
  } else if (action === 'delete') {
    return deleteReward(data);
  }
  
  return createResponse({ error: 'Invalid rewards action' }, 400);
}

/**
 * Create a new reward
 */
function createReward(data) {
  try {
    const { creatorId, title, description, pointCost, imageUrl, available, quantity } = data;
    
    if (!creatorId || !title || !pointCost) {
      return createResponse({ error: 'Missing required fields' }, 400);
    }
    
    const usersData = loadJsonFile(FILE_NAMES.USERS);
    const rewardsData = loadJsonFile(FILE_NAMES.REWARDS);
    
    // Verify creator is authorized (must be parent)
    const creator = usersData.users.find(u => u.id === creatorId);
    if (!creator || creator.role !== 'parent') {
      return createResponse({ error: 'Unauthorized' }, 403);
    }
    
    // Create new reward
    const rewardId = Utilities.getUuid();
    const newReward = {
      id: rewardId,
      title: title,
      description: description || '',
      pointCost: pointCost,
      imageUrl: imageUrl || null,
      available: available !== undefined ? available : true,
      quantity: quantity || null,
      createdBy: creatorId,
      redeemedCount: 0,
      createdAt: new Date().toISOString()
    };
    
    // Add to rewards data
    rewardsData.rewards.push(newReward);
    saveJsonFile(FILE_NAMES.REWARDS, rewardsData);
    
    // Log reward creation
    logActivity({
      actorId: creatorId,
      actorName: creator.name,
      actorRole: creator.role,
      actionType: 'reward_created',
      referenceId: rewardId,
      referenceType: 'reward',
      details: {
        rewardTitle: title,
        pointCost: pointCost
      }
    });
    
    return createResponse({
      success: true,
      reward: newReward
    });
    
  } catch (error) {
    Logger.log('Error in createReward: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}

/**
 * Update a reward
 */
function updateReward(data) {
  try {
    const { userId, rewardId, updates } = data;
    
    if (!userId || !rewardId || !updates) {
      return createResponse({ error: 'Missing required fields' }, 400);
    }
    
    const usersData = loadJsonFile(FILE_NAMES.USERS);
    const rewardsData = loadJsonFile(FILE_NAMES.REWARDS);
    
    // Verify user is authorized (must be parent)
    const user = usersData.users.find(u => u.id === userId);
    if (!user || user.role !== 'parent') {
      return createResponse({ error: 'Unauthorized' }, 403);
    }
    
    // Find reward
    const reward = rewardsData.rewards.find(r => r.id === rewardId);
    if (!reward) {
      return createResponse({ error: 'Reward not found' }, 404);
    }
    
    // Update allowed fields
    const allowedFields = ['title', 'description', 'pointCost', 'imageUrl', 'available', 'quantity'];
    const updatedFields = [];
    
    allowedFields.forEach(field => {
      if (updates[field] !== undefined) {
        reward[field] = updates[field];
        updatedFields.push(field);
      }
    });
    
    // Save updates
    saveJsonFile(FILE_NAMES.REWARDS, rewardsData);
    
    // Log update
    logActivity({
      actorId: userId,
      actorName: user.name,
      actorRole: user.role,
      actionType: 'reward_updated',
      referenceId: rewardId,
      referenceType: 'reward',
      details: {
        rewardTitle: reward.title,
        updatedFields: updatedFields
      }
    });
    
    return createResponse({
      success: true,
      reward: reward
    });
    
  } catch (error) {
    Logger.log('Error in updateReward: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}

/**
 * Redeem a reward
 */
function redeemReward(data) {
  try {
    const { userId, rewardId } = data;
    
    if (!userId || !rewardId) {
      return createResponse({ error: 'Missing required fields' }, 400);
    }
    
    const usersData = loadJsonFile(FILE_NAMES.USERS);
    const rewardsData = loadJsonFile(FILE_NAMES.REWARDS);
    const transactionsData = loadJsonFile(FILE_NAMES.TRANSACTIONS);
    const familyData = loadJsonFile(FILE_NAMES.FAMILY);
    
    // Find user
    const user = usersData.users.find(u => u.id === userId);
    if (!user) {
      return createResponse({ error: 'User not found' }, 404);
    }
    
    // Find reward
    const reward = rewardsData.rewards.find(r => r.id === rewardId);
    if (!reward) {
      return createResponse({ error: 'Reward not found' }, 404);
    }
    
    // Check if reward is available
    if (!reward.available) {
      return createResponse({ error: 'Reward is not currently available' }, 400);
    }
    
    // Check quantity
    if (reward.quantity !== null && reward.quantity <= 0) {
      return createResponse({ error: 'Reward is out of stock' }, 400);
    }
    
    // Check if user has enough points
    if (user.pointsBalance < reward.pointCost) {
      return createResponse({ 
        error: 'Insufficient points',
        required: reward.pointCost,
        current: user.pointsBalance,
        needed: reward.pointCost - user.pointsBalance
      }, 400);
    }
    
    // Deduct points from user
    user.pointsBalance -= reward.pointCost;
    saveJsonFile(FILE_NAMES.USERS, usersData);
    
    // Update family member data
    const familyMemberIndex = familyData.members.findIndex(m => m.id === userId);
    if (familyMemberIndex !== -1) {
      familyData.members[familyMemberIndex] = user;
      saveJsonFile(FILE_NAMES.FAMILY, familyData);
    }
    
    // Update reward stats
    reward.redeemedCount += 1;
    if (reward.quantity !== null) {
      reward.quantity -= 1;
      if (reward.quantity === 0) {
        reward.available = false;
      }
    }
    saveJsonFile(FILE_NAMES.REWARDS, rewardsData);
    
    // Create transaction record
    const transaction = {
      id: Utilities.getUuid(),
      userId: userId,
      type: 'spend',
      points: reward.pointCost,
      reason: `Redeemed: ${reward.title}`,
      referenceId: rewardId,
      timestamp: new Date().toISOString()
    };
    
    transactionsData.transactions.push(transaction);
    saveJsonFile(FILE_NAMES.TRANSACTIONS, transactionsData);
    
    // Log redemption
    logActivity({
      actorId: userId,
      actorName: user.name,
      actorRole: user.role,
      actionType: 'reward_redeemed',
      referenceId: rewardId,
      referenceType: 'reward',
      details: {
        rewardTitle: reward.title,
        pointsSpent: reward.pointCost,
        remainingBalance: user.pointsBalance
      }
    });
    
    return createResponse({
      success: true,
      reward: reward,
      transaction: transaction,
      newBalance: user.pointsBalance,
      message: `Successfully redeemed ${reward.title}!`
    });
    
  } catch (error) {
    Logger.log('Error in redeemReward: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}

/**
 * Delete a reward
 */
function deleteReward(data) {
  try {
    const { userId, rewardId } = data;
    
    if (!userId || !rewardId) {
      return createResponse({ error: 'Missing required fields' }, 400);
    }
    
    const usersData = loadJsonFile(FILE_NAMES.USERS);
    const rewardsData = loadJsonFile(FILE_NAMES.REWARDS);
    
    // Verify user is authorized (must be parent)
    const user = usersData.users.find(u => u.id === userId);
    if (!user || user.role !== 'parent') {
      return createResponse({ error: 'Unauthorized' }, 403);
    }
    
    // Find and remove reward
    const rewardIndex = rewardsData.rewards.findIndex(r => r.id === rewardId);
    if (rewardIndex === -1) {
      return createResponse({ error: 'Reward not found' }, 404);
    }
    
    const reward = rewardsData.rewards[rewardIndex];
    rewardsData.rewards.splice(rewardIndex, 1);
    saveJsonFile(FILE_NAMES.REWARDS, rewardsData);
    
    // Log deletion
    logActivity({
      actorId: userId,
      actorName: user.name,
      actorRole: user.role,
      actionType: 'reward_deleted',
      referenceId: rewardId,
      referenceType: 'reward',
      details: {
        rewardTitle: reward.title
      }
    });
    
    return createResponse({
      success: true,
      message: 'Reward deleted successfully'
    });
    
  } catch (error) {
    Logger.log('Error in deleteReward: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}

/**
 * List all rewards
 */
function listRewards(familyId) {
  try {
    const familyData = loadJsonFile(FILE_NAMES.FAMILY);
    const rewardsData = loadJsonFile(FILE_NAMES.REWARDS);
    
    if (!familyData || familyData.id !== familyId) {
      return createResponse({ error: 'Family not found' }, 404);
    }
    
    return createResponse({
      success: true,
      rewards: rewardsData.rewards
    });
    
  } catch (error) {
    Logger.log('Error in listRewards: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}

/**
 * Get single reward
 */
function getReward(rewardId) {
  try {
    const rewardsData = loadJsonFile(FILE_NAMES.REWARDS);
    const reward = rewardsData.rewards.find(r => r.id === rewardId);
    
    if (!reward) {
      return createResponse({ error: 'Reward not found' }, 404);
    }
    
    return createResponse({
      success: true,
      reward: reward
    });
    
  } catch (error) {
    Logger.log('Error in getReward: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}
