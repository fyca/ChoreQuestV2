package com.chorequest.presentation.rewards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chorequest.data.local.SessionManager
import com.chorequest.data.repository.RewardRepository
import com.chorequest.data.repository.UserRepository
import com.chorequest.domain.models.Reward
import com.chorequest.domain.models.RewardRedemption
import com.chorequest.domain.models.RewardRedemptionStatus
import com.chorequest.utils.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.*
import javax.inject.Inject

/**
 * ViewModel for reward management
 */
@HiltViewModel
class RewardViewModel @Inject constructor(
    private val rewardRepository: RewardRepository,
    private val sessionManager: SessionManager,
    val userRepository: UserRepository
) : ViewModel() {

    private val _allRewards = MutableStateFlow<List<Reward>>(emptyList())
    val allRewards: StateFlow<List<Reward>> = _allRewards.asStateFlow()
    
    // User points balance - observe user data to keep it updated
    val userPointsBalance: StateFlow<Int> = userRepository.getAllUsers()
        .map { users ->
            val session = sessionManager.loadSession()
            if (session != null) {
                users.find { it.id == session.userId }?.pointsBalance ?: 0
            } else {
                0
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _rewardDetailState = MutableStateFlow<RewardDetailState>(RewardDetailState.Loading)
    val rewardDetailState: StateFlow<RewardDetailState> = _rewardDetailState.asStateFlow()

    private val _createEditState = MutableStateFlow<CreateEditState>(CreateEditState.Idle)
    val createEditState: StateFlow<CreateEditState> = _createEditState.asStateFlow()

    private val _redeemState = MutableStateFlow<RedeemState>(RedeemState.Idle)
    val redeemState: StateFlow<RedeemState> = _redeemState.asStateFlow()

    private val _previousRedemptions = MutableStateFlow<List<RewardRedemption>>(emptyList())
    val previousRedemptions: StateFlow<List<RewardRedemption>> = _previousRedemptions.asStateFlow()

    val currentUserId: String? 
        get() = sessionManager.loadSession()?.userId

    init {
        loadAllRewards()
        loadPreviousRedemptions()
    }

    /**
     * Load all rewards
     * Uses local-first approach with Flow for real-time updates
     * Triggers background sync (non-blocking) to ensure data is up-to-date
     */
    fun loadAllRewards() {
        viewModelScope.launch {
            // Use local rewards Flow for real-time updates (local-first)
            rewardRepository.getAllRewards().collect { rewards ->
                _allRewards.value = rewards
            }
        }
        
        // Trigger background sync (non-blocking, separate coroutine)
        // This ensures rewards are synced using Drive API first when screen opens
        viewModelScope.launch {
            rewardRepository.syncRewards()
        }
    }

    /**
     * Load reward details
     */
    fun loadRewardDetail(rewardId: String) {
        viewModelScope.launch {
            _rewardDetailState.value = RewardDetailState.Loading
            val reward = rewardRepository.getRewardById(rewardId)
            if (reward != null) {
                _rewardDetailState.value = RewardDetailState.Success(reward)
            } else {
                _rewardDetailState.value = RewardDetailState.Error("Reward not found")
            }
        }
    }

    /**
     * Create new reward
     */
    fun createReward(
        title: String,
        description: String,
        pointsCost: Int,
        icon: String?,
        stock: Int?
    ) {
        viewModelScope.launch {
            val session = sessionManager.loadSession() ?: return@launch

            val newReward = Reward(
                id = UUID.randomUUID().toString(),
                title = title,
                description = description,
                pointCost = pointsCost,
                imageUrl = icon,
                available = true,
                quantity = stock,
                createdBy = session.userId,
                redeemedCount = 0,
                createdAt = Instant.now().toString()
            )

            _createEditState.value = CreateEditState.Loading

            rewardRepository.createReward(newReward).collect { result ->
                when (result) {
                    is Result.Success -> {
                        _createEditState.value = CreateEditState.Success("Reward created successfully!")
                    }
                    is Result.Error -> {
                        _createEditState.value = CreateEditState.Error(result.message)
                    }
                    is Result.Loading -> {
                        _createEditState.value = CreateEditState.Loading
                    }
                }
            }
        }
    }

    /**
     * Update existing reward
     */
    fun updateReward(reward: Reward) {
        viewModelScope.launch {
            _createEditState.value = CreateEditState.Loading

            rewardRepository.updateReward(reward).collect { result ->
                when (result) {
                    is Result.Success -> {
                        _createEditState.value = CreateEditState.Success("Reward updated successfully!")
                    }
                    is Result.Error -> {
                        _createEditState.value = CreateEditState.Error(result.message)
                    }
                    is Result.Loading -> {
                        _createEditState.value = CreateEditState.Loading
                    }
                }
            }
        }
    }

    /**
     * Delete reward
     */
    fun deleteReward(reward: Reward) {
        viewModelScope.launch {
            rewardRepository.deleteReward(reward).collect { result ->
                when (result) {
                    is Result.Success -> {
                        _rewardDetailState.value = RewardDetailState.Deleted
                    }
                    is Result.Error -> {
                        _rewardDetailState.value = RewardDetailState.Error(result.message)
                    }
                    is Result.Loading -> {
                        // Keep current state during deletion
                    }
                }
            }
        }
    }

    /**
     * Redeem reward
     */
    fun redeemReward(rewardId: String) {
        viewModelScope.launch {
            val session = sessionManager.loadSession() ?: return@launch

            _redeemState.value = RedeemState.Loading

            rewardRepository.redeemReward(rewardId, session.userId).collect { result ->
                when (result) {
                    is Result.Success -> {
                        _redeemState.value = RedeemState.Success(result.data)
                        // Refresh redemptions list after successful redemption
                        refreshRedemptions()
                    }
                    is Result.Error -> {
                        _redeemState.value = RedeemState.Error(result.message)
                    }
                    is Result.Loading -> {
                        _redeemState.value = RedeemState.Loading
                    }
                }
            }
        }
    }

    /**
     * Reset create/edit state
     */
    fun resetCreateEditState() {
        _createEditState.value = CreateEditState.Idle
    }

    /**
     * Reset redeem state
     */
    fun resetRedeemState() {
        _redeemState.value = RedeemState.Idle
    }

    /**
     * Load previous redemptions (non-pending) for the current user
     * Uses local-first approach with Flow for real-time updates
     */
    fun loadPreviousRedemptions() {
        viewModelScope.launch {
            val session = sessionManager.loadSession() ?: return@launch
            
            // Use local redemptions Flow for real-time updates
            rewardRepository.getLocalRedemptions(session.userId)
                .map { redemptions ->
                    // Filter out pending redemptions - only show approved, denied, or completed
                    redemptions.filter { 
                        it.status != RewardRedemptionStatus.PENDING 
                    }.sortedByDescending { 
                        it.requestedAt 
                    }
                }
                .collect { previous ->
                    _previousRedemptions.value = previous
                }
        }
        
        // Trigger background sync (non-blocking, separate coroutine)
        viewModelScope.launch {
            val session = sessionManager.loadSession() ?: return@launch
            rewardRepository.getRewardRedemptions(userId = session.userId)
        }
    }

    /**
     * Refresh redemptions (called after redeeming a reward)
     */
    fun refreshRedemptions() {
        loadPreviousRedemptions()
    }
}

/**
 * State for reward detail screen
 */
sealed class RewardDetailState {
    object Loading : RewardDetailState()
    data class Success(val reward: Reward) : RewardDetailState()
    data class Error(val message: String) : RewardDetailState()
    object Deleted : RewardDetailState()
}

/**
 * State for create/edit operations
 */
sealed class CreateEditState {
    object Idle : CreateEditState()
    object Loading : CreateEditState()
    data class Success(val message: String) : CreateEditState()
    data class Error(val message: String) : CreateEditState()
}

/**
 * State for redeem operations
 */
sealed class RedeemState {
    object Idle : RedeemState()
    object Loading : RedeemState()
    data class Success(val message: String) : RedeemState()
    data class Error(val message: String) : RedeemState()
}
