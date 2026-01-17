/**
 * RewardManager.gs
 * Handles reward CRUD operations and redemption logic
 */

/**
 * Helper function to get ownerEmail, folderId, and accessToken from family data
 * This function searches through all stored access tokens to find the family
 * that contains the specified userId (if provided) or returns the first family found
 */
function getFamilyInfoForReward(userId) {
  try {
    Logger.log('getFamilyInfoForReward: Attempting to get family info' + (userId ? ' for userId: ' + userId : ''));
    
    // Get all stored access tokens from user properties
    const userProps = PropertiesService.getUserProperties();
    const allProps = userProps.getProperties();
    
    // Try each stored access token until we find the family
    for (const key in allProps) {
      if (key.startsWith('ACCESS_TOKEN_')) {
        const ownerEmail = key.replace('ACCESS_TOKEN_', '');
        const accessToken = allProps[key];
        
        Logger.log('getFamilyInfoForReward: Trying access token for: ' + ownerEmail);
        
        try {
          // Try to get the folder ID and load family data
          const folderId = getChoreQuestFolderV3(ownerEmail, accessToken);
          const familyData = loadJsonFileV3(FILE_NAMES.FAMILY, ownerEmail, folderId, accessToken);
          
          if (familyData) {
            // If userId is provided, check if this family contains that user
            if (userId) {
              const userInFamily = familyData.members.find(m => m.id === userId);
              if (userInFamily) {
                Logger.log('getFamilyInfoForReward: Found family containing userId: ' + userId);
                return {
                  ownerEmail: ownerEmail,
                  folderId: folderId,
                  accessToken: accessToken
                };
              }
            } else {
              // No userId provided, return first family found
              Logger.log('getFamilyInfoForReward: Returning first family found');
              return {
                ownerEmail: ownerEmail,
                folderId: folderId,
                accessToken: accessToken
              };
            }
          }
        } catch (error) {
          Logger.log('getFamilyInfoForReward: Error with access token for ' + ownerEmail + ': ' + error.toString());
          continue;
        }
      }
    }
    
    Logger.log('getFamilyInfoForReward: No family found');
    return null;
  } catch (error) {
    Logger.log('Error in getFamilyInfoForReward: ' + error.toString());
    return null;
  }
}

/**
 * Handle reward requests (GET)
 */
function handleRewardsRequest(e) {
  const action = e.parameter.action;
  const familyId = e.parameter.familyId;
  const userId = e.parameter.userId;
  const rewardId = e.parameter.rewardId;
  
  if (action === 'list' && familyId) {
    return listRewards(familyId);
  } else if (action === 'get' && rewardId) {
    return getReward(rewardId);
  } else if (action === 'redemptions') {
    // userId is optional - if not provided, returns all pending redemptions for the family
    return getRewardRedemptions(userId, familyId);
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
  } else if (action === 'approve') {
    return approveRewardRedemption(data);
  } else if (action === 'deny') {
    return denyRewardRedemption(data);
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
    
    // Get ownerEmail, folderId, and accessToken from family data
    const familyInfo = getFamilyInfoForReward(creatorId);
    if (!familyInfo) {
      return createResponse({ error: 'Family data not found' }, 404);
    }
    
    const { ownerEmail, folderId, accessToken } = familyInfo;
    
    // Use Drive API v3 with access token
    const usersData = loadJsonFileV3(FILE_NAMES.USERS, ownerEmail, folderId, accessToken);
    if (!usersData || !usersData.users) {
      return createResponse({ error: 'Users data not found' }, 404);
    }
    
    const rewardsData = loadJsonFileV3(FILE_NAMES.REWARDS, ownerEmail, folderId, accessToken) || { rewards: [] };
    
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
    if (!rewardsData.rewards) {
      rewardsData.rewards = [];
    }
    rewardsData.rewards.push(newReward);
    saveJsonFileV3(FILE_NAMES.REWARDS, rewardsData, ownerEmail, folderId, accessToken);
    
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
    }, ownerEmail, folderId, accessToken);
    
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
    
    // Get ownerEmail, folderId, and accessToken from family data
    const familyInfo = getFamilyInfoForReward(userId);
    if (!familyInfo) {
      return createResponse({ error: 'Family data not found' }, 404);
    }
    
    const { ownerEmail, folderId, accessToken } = familyInfo;
    
    // Use Drive API v3 with access token
    const usersData = loadJsonFileV3(FILE_NAMES.USERS, ownerEmail, folderId, accessToken);
    if (!usersData || !usersData.users) {
      return createResponse({ error: 'Users data not found' }, 404);
    }
    
    const rewardsData = loadJsonFileV3(FILE_NAMES.REWARDS, ownerEmail, folderId, accessToken) || { rewards: [] };
    
    // Verify user is authorized (must be parent)
    const user = usersData.users.find(u => u.id === userId);
    if (!user || user.role !== 'parent') {
      return createResponse({ error: 'Unauthorized' }, 403);
    }
    
    // Find reward
    if (!rewardsData.rewards) {
      rewardsData.rewards = [];
    }
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
    saveJsonFileV3(FILE_NAMES.REWARDS, rewardsData, ownerEmail, folderId, accessToken);
    
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
    }, ownerEmail, folderId, accessToken);
    
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
 * Redeem a reward - creates a redemption request that requires parent approval
 */
function redeemReward(data) {
  try {
    const { userId, rewardId } = data;
    
    if (!userId || !rewardId) {
      return createResponse({ error: 'Missing required fields' }, 400);
    }
    
    // Get ownerEmail, folderId, and accessToken from family data
    const familyInfo = getFamilyInfoForReward(userId);
    if (!familyInfo) {
      return createResponse({ error: 'Family data not found' }, 404);
    }
    
    const { ownerEmail, folderId, accessToken } = familyInfo;
    
    // Use Drive API v3 with access token
    const usersData = loadJsonFileV3(FILE_NAMES.USERS, ownerEmail, folderId, accessToken);
    if (!usersData || !usersData.users) {
      return createResponse({ error: 'Users data not found' }, 404);
    }
    
    const rewardsData = loadJsonFileV3(FILE_NAMES.REWARDS, ownerEmail, folderId, accessToken) || { rewards: [] };
    const redemptionsData = loadJsonFileV3(FILE_NAMES.REWARD_REDEMPTIONS, ownerEmail, folderId, accessToken) || { redemptions: [] };
    
    // Find user
    const user = usersData.users.find(u => u.id === userId);
    if (!user) {
      return createResponse({ error: 'User not found' }, 404);
    }
    
    // Find reward
    if (!rewardsData.rewards) {
      rewardsData.rewards = [];
    }
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
    
    // Check if there's already a pending redemption for this reward by this user
    if (!redemptionsData.redemptions) {
      redemptionsData.redemptions = [];
    }
    const existingPending = redemptionsData.redemptions.find(r => 
      r.userId === userId && 
      r.rewardId === rewardId && 
      r.status === 'pending'
    );
    if (existingPending) {
      return createResponse({ error: 'You already have a pending redemption request for this reward' }, 400);
    }
    
    // Create redemption request (status: pending)
    const redemptionId = Utilities.getUuid();
    const redemption = {
      id: redemptionId,
      userId: userId,
      rewardId: rewardId,
      status: 'pending',
      requestedAt: new Date().toISOString(),
      approvedBy: null,
      approvedAt: null,
      deniedBy: null,
      deniedAt: null,
      completedAt: null,
      pointCost: reward.pointCost
    };
    
    redemptionsData.redemptions.push(redemption);
    saveJsonFileV3(FILE_NAMES.REWARD_REDEMPTIONS, redemptionsData, ownerEmail, folderId, accessToken);
    
    // Log redemption request
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
        status: 'pending_approval'
      }
    }, ownerEmail, folderId, accessToken);
    
    return createResponse({
      success: true,
      redemption: redemption,
      message: `Redemption request submitted! Waiting for parent approval.`
    });
    
  } catch (error) {
    Logger.log('Error in redeemReward: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}

/**
 * Approve a reward redemption request (parent only)
 */
function approveRewardRedemption(data) {
  try {
    const { parentId, redemptionId } = data;
    
    if (!parentId || !redemptionId) {
      return createResponse({ error: 'Missing required fields' }, 400);
    }
    
    // Get ownerEmail, folderId, and accessToken from family data
    const familyInfo = getFamilyInfoForReward(parentId);
    if (!familyInfo) {
      return createResponse({ error: 'Family data not found' }, 404);
    }
    
    const { ownerEmail, folderId, accessToken } = familyInfo;
    
    // Use Drive API v3 with access token
    const usersData = loadJsonFileV3(FILE_NAMES.USERS, ownerEmail, folderId, accessToken);
    if (!usersData || !usersData.users) {
      return createResponse({ error: 'Users data not found' }, 404);
    }
    
    const rewardsData = loadJsonFileV3(FILE_NAMES.REWARDS, ownerEmail, folderId, accessToken) || { rewards: [] };
    const redemptionsData = loadJsonFileV3(FILE_NAMES.REWARD_REDEMPTIONS, ownerEmail, folderId, accessToken) || { redemptions: [] };
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
    
    // Find redemption
    if (!redemptionsData.redemptions) {
      redemptionsData.redemptions = [];
    }
    const redemption = redemptionsData.redemptions.find(r => r.id === redemptionId);
    if (!redemption) {
      return createResponse({ error: 'Redemption not found' }, 404);
    }
    
    if (redemption.status !== 'pending') {
      return createResponse({ error: 'Redemption is not in pending status' }, 400);
    }
    
    // Find user and reward
    const user = usersData.users.find(u => u.id === redemption.userId);
    if (!user) {
      return createResponse({ error: 'User not found' }, 404);
    }
    
    if (!rewardsData.rewards) {
      rewardsData.rewards = [];
    }
    const reward = rewardsData.rewards.find(r => r.id === redemption.rewardId);
    if (!reward) {
      return createResponse({ error: 'Reward not found' }, 404);
    }
    
    // Check if user still has enough points
    if (user.pointsBalance < redemption.pointCost) {
      return createResponse({ 
        error: 'User no longer has enough points',
        required: redemption.pointCost,
        current: user.pointsBalance
      }, 400);
    }
    
    // Process the redemption: deduct points
    user.pointsBalance -= redemption.pointCost;
    saveJsonFileV3(FILE_NAMES.USERS, usersData, ownerEmail, folderId, accessToken);
    
    // Update family member data
    const familyMemberIndex = familyData.members.findIndex(m => m.id === redemption.userId);
    if (familyMemberIndex !== -1) {
      familyData.members[familyMemberIndex] = user;
      saveJsonFileV3(FILE_NAMES.FAMILY, familyData, ownerEmail, folderId, accessToken);
    }
    
    // Update reward stats
    reward.redeemedCount += 1;
    if (reward.quantity !== null) {
      reward.quantity -= 1;
      if (reward.quantity === 0) {
        reward.available = false;
      }
    }
    saveJsonFileV3(FILE_NAMES.REWARDS, rewardsData, ownerEmail, folderId, accessToken);
    
    // Update redemption status
    redemption.status = 'approved';
    redemption.approvedBy = parentId;
    redemption.approvedAt = new Date().toISOString();
    saveJsonFileV3(FILE_NAMES.REWARD_REDEMPTIONS, redemptionsData, ownerEmail, folderId, accessToken);
    
    // Create transaction record
    if (!transactionsData.transactions) {
      transactionsData.transactions = [];
    }
    const transaction = {
      id: Utilities.getUuid(),
      userId: redemption.userId,
      type: 'spend',
      points: redemption.pointCost,
      reason: `Redeemed: ${reward.title}`,
      referenceId: redemption.rewardId,
      timestamp: new Date().toISOString()
    };
    
    transactionsData.transactions.push(transaction);
    saveJsonFileV3(FILE_NAMES.TRANSACTIONS, transactionsData, ownerEmail, folderId, accessToken);
    
    // Log approval
    logActivity({
      actorId: parentId,
      actorName: parent.name,
      actorRole: parent.role,
      actionType: 'reward_approved',
      targetUserId: redemption.userId,
      targetUserName: user.name,
      referenceId: redemption.rewardId,
      referenceType: 'reward',
      details: {
        rewardTitle: reward.title,
        pointsSpent: redemption.pointCost,
        remainingBalance: user.pointsBalance
      }
    }, ownerEmail, folderId, accessToken);
    
    return createResponse({
      success: true,
      redemption: redemption,
      transaction: transaction,
      newBalance: user.pointsBalance,
      message: `Reward redemption approved!`
    });
    
  } catch (error) {
    Logger.log('Error in approveRewardRedemption: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}

/**
 * Deny a reward redemption request (parent only)
 */
function denyRewardRedemption(data) {
  try {
    const { parentId, redemptionId } = data;
    
    if (!parentId || !redemptionId) {
      return createResponse({ error: 'Missing required fields' }, 400);
    }
    
    // Get ownerEmail, folderId, and accessToken from family data
    const familyInfo = getFamilyInfoForReward(parentId);
    if (!familyInfo) {
      return createResponse({ error: 'Family data not found' }, 404);
    }
    
    const { ownerEmail, folderId, accessToken } = familyInfo;
    
    // Use Drive API v3 with access token
    const usersData = loadJsonFileV3(FILE_NAMES.USERS, ownerEmail, folderId, accessToken);
    if (!usersData || !usersData.users) {
      return createResponse({ error: 'Users data not found' }, 404);
    }
    
    const redemptionsData = loadJsonFileV3(FILE_NAMES.REWARD_REDEMPTIONS, ownerEmail, folderId, accessToken) || { redemptions: [] };
    const rewardsData = loadJsonFileV3(FILE_NAMES.REWARDS, ownerEmail, folderId, accessToken) || { rewards: [] };
    
    // Verify parent is authorized
    const parent = usersData.users.find(u => u.id === parentId);
    if (!parent || parent.role !== 'parent') {
      return createResponse({ error: 'Unauthorized' }, 403);
    }
    
    // Find redemption
    if (!redemptionsData.redemptions) {
      redemptionsData.redemptions = [];
    }
    const redemption = redemptionsData.redemptions.find(r => r.id === redemptionId);
    if (!redemption) {
      return createResponse({ error: 'Redemption not found' }, 404);
    }
    
    if (redemption.status !== 'pending') {
      return createResponse({ error: 'Redemption is not in pending status' }, 400);
    }
    
    // Find user and reward for logging
    const user = usersData.users.find(u => u.id === redemption.userId);
    if (!rewardsData.rewards) {
      rewardsData.rewards = [];
    }
    const reward = rewardsData.rewards.find(r => r.id === redemption.rewardId);
    
    // Update redemption status
    redemption.status = 'denied';
    redemption.deniedBy = parentId;
    redemption.deniedAt = new Date().toISOString();
    saveJsonFileV3(FILE_NAMES.REWARD_REDEMPTIONS, redemptionsData, ownerEmail, folderId, accessToken);
    
    // Log denial
    logActivity({
      actorId: parentId,
      actorName: parent.name,
      actorRole: parent.role,
      actionType: 'reward_denied',
      targetUserId: redemption.userId,
      targetUserName: user ? user.name : 'Unknown',
      referenceId: redemption.rewardId,
      referenceType: 'reward',
      details: {
        rewardTitle: reward ? reward.title : 'Unknown',
        pointCost: redemption.pointCost
      }
    }, ownerEmail, folderId, accessToken);
    
    return createResponse({
      success: true,
      redemption: redemption,
      message: `Reward redemption denied.`
    });
    
  } catch (error) {
    Logger.log('Error in denyRewardRedemption: ' + error.toString());
    return createResponse({ error: error.toString() }, 500);
  }
}

/**
 * Get reward redemptions for a user or all pending redemptions for a family
 */
function getRewardRedemptions(userId, familyId) {
  try {
    // Get ownerEmail, folderId, and accessToken from family data
    let familyInfo;
    
    if (userId) {
      // If userId is provided, use it to find the family
      familyInfo = getFamilyInfoForReward(userId);
    } else if (familyId) {
      // For parent dashboard - find family by familyId
      const userProps = PropertiesService.getUserProperties();
      const allProps = userProps.getProperties();
      
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
                folderId: familyData.driveFileId || folderId,
                familyId: familyData.id,
                accessToken: accessToken
              };
              break;
            }
          } catch (error) {
            continue;
          }
        }
      }
    }
    
    if (!familyInfo) {
      return createResponse({ error: 'Family data not found' }, 404);
    }
    
    const { ownerEmail, folderId, accessToken } = familyInfo;
    
    // Load redemptions data
    const redemptionsData = loadJsonFileV3(FILE_NAMES.REWARD_REDEMPTIONS, ownerEmail, folderId, accessToken) || { redemptions: [] };
    
    // If userId is provided, filter for that user; otherwise return all pending redemptions
    let redemptions;
    if (userId) {
      redemptions = (redemptionsData.redemptions || []).filter(r => r.userId === userId);
    } else {
      // Return all pending redemptions for the family (for parent dashboard)
      redemptions = (redemptionsData.redemptions || []).filter(r => r.status === 'pending');
    }
    
    return createResponse({
      success: true,
      redemptions: redemptions
    });
    
  } catch (error) {
    Logger.log('Error in getRewardRedemptions: ' + error.toString());
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
    
    // Get ownerEmail, folderId, and accessToken from family data
    const familyInfo = getFamilyInfoForReward(userId);
    if (!familyInfo) {
      return createResponse({ error: 'Family data not found' }, 404);
    }
    
    const { ownerEmail, folderId, accessToken } = familyInfo;
    
    // Use Drive API v3 with access token
    const usersData = loadJsonFileV3(FILE_NAMES.USERS, ownerEmail, folderId, accessToken);
    if (!usersData || !usersData.users) {
      return createResponse({ error: 'Users data not found' }, 404);
    }
    
    const rewardsData = loadJsonFileV3(FILE_NAMES.REWARDS, ownerEmail, folderId, accessToken) || { rewards: [] };
    
    // Verify user is authorized (must be parent)
    const user = usersData.users.find(u => u.id === userId);
    if (!user || user.role !== 'parent') {
      return createResponse({ error: 'Unauthorized' }, 403);
    }
    
    // Find and remove reward
    if (!rewardsData.rewards) {
      rewardsData.rewards = [];
    }
    const rewardIndex = rewardsData.rewards.findIndex(r => r.id === rewardId);
    if (rewardIndex === -1) {
      return createResponse({ error: 'Reward not found' }, 404);
    }
    
    const reward = rewardsData.rewards[rewardIndex];
    rewardsData.rewards.splice(rewardIndex, 1);
    saveJsonFileV3(FILE_NAMES.REWARDS, rewardsData, ownerEmail, folderId, accessToken);
    
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
    }, ownerEmail, folderId, accessToken);
    
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
