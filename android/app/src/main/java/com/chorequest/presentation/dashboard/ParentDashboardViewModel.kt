package com.chorequest.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.chorequest.data.local.SessionManager
import com.chorequest.data.repository.AuthRepository
import com.chorequest.data.repository.ChoreRepository
import com.chorequest.data.repository.RewardRepository
import com.chorequest.data.repository.UserRepository
import com.chorequest.domain.models.Chore
import com.chorequest.domain.models.User
import com.chorequest.domain.models.RewardRedemption
import com.chorequest.domain.models.RewardRedemptionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for parent dashboard
 */
@HiltViewModel
class ParentDashboardViewModel @Inject constructor(
    private val choreRepository: ChoreRepository,
    private val rewardRepository: RewardRepository,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
    private val authRepository: AuthRepository,
    private val syncRepository: com.chorequest.data.repository.SyncRepository,
    val syncManager: com.chorequest.workers.SyncManager,
    private val networkObserver: com.chorequest.utils.NetworkConnectivityObserver
) : ViewModel() {

    private val _uiState = MutableStateFlow<ParentDashboardState>(ParentDashboardState.Loading)
    val uiState: StateFlow<ParentDashboardState> = _uiState.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()

    val networkStatus: StateFlow<com.chorequest.utils.NetworkStatus> = networkObserver.observeConnectivity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.chorequest.utils.NetworkStatus.Available)

    val currentUserId: String?
        get() = sessionManager.loadSession()?.userId

    init {
        // Trigger sync first, then load dashboard data
        viewModelScope.launch {
            // Trigger immediate sync on dashboard load
            syncManager.triggerImmediateSync()
            // Wait a bit for sync to start, then load data
            kotlinx.coroutines.delay(1000)
            loadDashboardData()
            loadLastSyncTime()
        }
        
        // Observe sync completion and refresh data
        viewModelScope.launch {
            observeSyncCompletion()
        }
    }
    
    /**
     * Observe sync work completion and refresh data when sync succeeds
     */
    private fun observeSyncCompletion() {
        viewModelScope.launch {
            // Convert LiveData to Flow using callbackFlow
            callbackFlow {
                val observer = androidx.lifecycle.Observer<List<WorkInfo>> { workInfos ->
                    trySend(workInfos)
                }
                syncManager.getSyncWorkInfoLiveData().observeForever(observer)
                awaitClose {
                    syncManager.getSyncWorkInfoLiveData().removeObserver(observer)
                }
            }
            .distinctUntilChanged()
            .collect { workInfos ->
                val latestWork = workInfos.firstOrNull()
                val state = latestWork?.state
                
                // When sync completes successfully, refresh the timestamp
                if (state == WorkInfo.State.SUCCEEDED) {
                    loadLastSyncTime()
                    loadDashboardData()
                }
            }
        }
    }

    /**
     * Load all dashboard data
     */
    private fun loadDashboardData() {
        viewModelScope.launch {
            try {
                val session = sessionManager.loadSession()
                if (session == null) {
                    _uiState.value = ParentDashboardState.Error("No session found")
                    return@launch
                }

                // Use local redemptions Flow for real-time updates (local-first)
                val localRedemptionsFlow = rewardRepository.getLocalRedemptions(userId = null)
                
                // Trigger background sync (non-blocking)
                launch {
                    rewardRepository.getRewardRedemptions(userId = null, familyId = session.familyId)
                }

                // Combine chores, rewards, users, and redemptions flows to get real-time updates
                combine(
                    choreRepository.getAllChores(),
                    rewardRepository.getAllRewards(),
                    userRepository.getAllUsers(),
                    localRedemptionsFlow
                ) { chores, rewards, users, allRedemptions ->
                    val pendingRedemptions = allRedemptions.filter { 
                        it.status == RewardRedemptionStatus.PENDING 
                    }
                    
                    val pendingChores = chores.filter { 
                        it.status == com.chorequest.domain.models.ChoreStatus.PENDING 
                    }
                    val completedChores = chores.filter { 
                        it.status == com.chorequest.domain.models.ChoreStatus.COMPLETED 
                    }
                    val verifiedChores = chores.filter { 
                        it.status == com.chorequest.domain.models.ChoreStatus.VERIFIED 
                    }

                    // Calculate total family points
                    val totalFamilyPoints = users.sumOf { it.pointsBalance }

                    // Match pending redemptions with their rewards and users
                    val pendingRewardsWithDetails = pendingRedemptions.mapNotNull { redemption ->
                        val reward = rewards.find { it.id == redemption.rewardId }
                        val user = users.find { it.id == redemption.userId }
                        if (reward != null && user != null) {
                            Triple(redemption, reward, user)
                        } else {
                            null
                        }
                    }

                    ParentDashboardState.Success(
                        userName = session.userName,
                        pendingChoresCount = pendingChores.size,
                        completedChoresCount = completedChores.size + verifiedChores.size,
                        awaitingVerificationCount = completedChores.size,
                        totalFamilyPoints = totalFamilyPoints,
                        activeChores = pendingChores.sortedByDescending { it.createdAt },
                        awaitingApprovalChores = completedChores.sortedByDescending { it.completedAt ?: it.createdAt },
                        completedChores = (completedChores + verifiedChores).sortedByDescending { 
                            it.completedAt ?: it.verifiedAt ?: it.createdAt 
                        },
                        pendingRewards = pendingRewardsWithDetails
                    )
                }.collect { state ->
                    _uiState.value = state
                }
            } catch (e: Exception) {
                _uiState.value = ParentDashboardState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Refresh dashboard data
     */
    fun refresh() {
        loadDashboardData()
    }

    /**
     * Load last sync time
     */
    private fun loadLastSyncTime() {
        viewModelScope.launch {
            _lastSyncTime.value = syncRepository.getLastSyncTime()
        }
    }

    /**
     * Trigger manual sync
     */
    fun triggerSync() {
        syncManager.triggerImmediateSync()
        // Reload dashboard after a delay to allow sync to complete
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000) // Give sync time to complete
            loadDashboardData()
            loadLastSyncTime()
        }
    }

    /**
     * Approve a reward redemption
     */
    fun approveReward(redemptionId: String) {
        viewModelScope.launch {
            val session = sessionManager.loadSession() ?: return@launch
            rewardRepository.approveRewardRedemption(session.userId, redemptionId).collect { result ->
                when (result) {
                    is com.chorequest.utils.Result.Success -> {
                        // Refresh dashboard to update pending rewards list
                        loadDashboardData()
                    }
                    is com.chorequest.utils.Result.Error -> {
                        // TODO: Show error message
                        android.util.Log.e("ParentDashboardViewModel", "Failed to approve reward: ${result.message}")
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * Deny a reward redemption
     */
    fun denyReward(redemptionId: String) {
        viewModelScope.launch {
            val session = sessionManager.loadSession() ?: return@launch
            rewardRepository.denyRewardRedemption(session.userId, redemptionId).collect { result ->
                when (result) {
                    is com.chorequest.utils.Result.Success -> {
                        // Refresh dashboard to update pending rewards list
                        loadDashboardData()
                    }
                    is com.chorequest.utils.Result.Error -> {
                        // TODO: Show error message
                        android.util.Log.e("ParentDashboardViewModel", "Failed to deny reward: ${result.message}")
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * Logout - cancels sync work, clears all local data and session, then triggers navigation callback
     */
    fun logout(onLogoutComplete: () -> Unit) {
        viewModelScope.launch {
            // Cancel any pending sync work FIRST to prevent sync from running during/after logout
            syncManager.cancelSyncWork()
            android.util.Log.i("ParentDashboardViewModel", "Cancelled sync work before logout")
            
            // Now logout (clears session and local data)
            authRepository.logout()
            
            onLogoutComplete()
        }
    }
}

/**
 * UI state for parent dashboard
 */
sealed class ParentDashboardState {
    object Loading : ParentDashboardState()
    data class Success(
        val userName: String,
        val pendingChoresCount: Int,
        val completedChoresCount: Int,
        val awaitingVerificationCount: Int,
        val totalFamilyPoints: Int,
        val activeChores: List<Chore> = emptyList(),
        val awaitingApprovalChores: List<Chore> = emptyList(),
        val completedChores: List<Chore> = emptyList(),
        val pendingRewards: List<Triple<RewardRedemption, com.chorequest.domain.models.Reward, User>> = emptyList() // Pending reward redemptions with details
    ) : ParentDashboardState()
    data class Error(val message: String) : ParentDashboardState()
}
