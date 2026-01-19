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
import com.chorequest.domain.models.RewardRedemption
import com.chorequest.domain.models.RewardRedemptionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for child dashboard
 */
@HiltViewModel
class ChildDashboardViewModel @Inject constructor(
    private val choreRepository: ChoreRepository,
    private val rewardRepository: RewardRepository,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
    private val authRepository: AuthRepository,
    private val syncRepository: com.chorequest.data.repository.SyncRepository,
    val syncManager: com.chorequest.workers.SyncManager,
    private val networkObserver: com.chorequest.utils.NetworkConnectivityObserver
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChildDashboardState>(ChildDashboardState.Loading)
    val uiState: StateFlow<ChildDashboardState> = _uiState.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()

    val networkStatus: StateFlow<com.chorequest.utils.NetworkStatus> = networkObserver.observeConnectivity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.chorequest.utils.NetworkStatus.Available)

    init {
        // Load dashboard data directly when screen opens (on-demand)
        viewModelScope.launch {
            loadDashboardData()
        }
    }
    
    // Sync observation removed - data loads on-demand when screen opens

    /**
     * Load dashboard data for child
     */
    private fun loadDashboardData() {
        viewModelScope.launch {
            try {
                val session = sessionManager.loadSession()
                if (session == null) {
                    _uiState.value = ChildDashboardState.Error("No session found")
                    return@launch
                }

                // Use local redemptions Flow for real-time updates (local-first)
                val localRedemptionsFlow = rewardRepository.getLocalRedemptions(session.userId)
                
                // Trigger background sync (non-blocking)
                launch {
                    rewardRepository.getRewardRedemptions(session.userId)
                }

                // Combine chores, user data, and redemptions flows to get real-time updates
                combine(
                    choreRepository.getAllChores(),
                    userRepository.getAllUsers(),
                    rewardRepository.getAllRewards(),
                    localRedemptionsFlow
                ) { allChores, allUsers, allRewards, allRedemptions ->
                    val pendingRedemptions = allRedemptions.filter { 
                        it.status == RewardRedemptionStatus.PENDING 
                    }
                    // Find current user
                    val user = allUsers.find { it.id == session.userId }
                    val totalPoints = user?.pointsBalance ?: 0
                    
                    val myChores = allChores.filter { 
                        it.assignedTo.contains(session.userId) 
                    }
                    // Include both PENDING and IN_PROGRESS chores in "to do"
                    val pendingChores = myChores.filter { 
                        it.status == com.chorequest.domain.models.ChoreStatus.PENDING ||
                        it.status == com.chorequest.domain.models.ChoreStatus.IN_PROGRESS
                    }
                    val completedToday = myChores.filter { 
                        it.status == com.chorequest.domain.models.ChoreStatus.COMPLETED ||
                        it.status == com.chorequest.domain.models.ChoreStatus.VERIFIED
                    }.size

                    // Get unassigned chores (available for any user to complete) - "Earn Extra Points"
                    val unassignedChores = allChores.filter { 
                        it.assignedTo.isEmpty() &&
                        (it.status == com.chorequest.domain.models.ChoreStatus.PENDING ||
                         it.status == com.chorequest.domain.models.ChoreStatus.IN_PROGRESS)
                    }

                    // Match pending redemptions with their rewards
                    val pendingRewardsWithDetails = pendingRedemptions.mapNotNull { redemption ->
                        val reward = allRewards.find { it.id == redemption.rewardId }
                        if (reward != null) {
                            Pair(redemption, reward)
                        } else {
                            null
                        }
                    }

                    ChildDashboardState.Success(
                        userName = session.userName,
                        totalPoints = totalPoints,
                        pendingChoresCount = pendingChores.size,
                        completedTodayCount = completedToday,
                        myChores = pendingChores.take(5),
                        extraPointsChores = unassignedChores.take(5),
                        pendingRewards = pendingRewardsWithDetails
                    )
                }.collect { state ->
                    _uiState.value = state
                }
            } catch (e: Exception) {
                _uiState.value = ChildDashboardState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Refresh dashboard
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
        viewModelScope.launch {
            syncManager.triggerImmediateSync()
            // Reload dashboard after sync
            kotlinx.coroutines.delay(2000) // Give sync time to complete
            loadDashboardData()
            loadLastSyncTime()
        }
    }

    /**
     * Logout - cancels sync work, clears all local data and session, then triggers navigation callback
     */
    fun logout(onLogoutComplete: () -> Unit) {
        viewModelScope.launch {
            // Cancel any pending sync work FIRST to prevent sync from running during/after logout
            syncManager.cancelSyncWork()
            android.util.Log.i("ChildDashboardViewModel", "Cancelled sync work before logout")
            
            // Now logout (clears session and local data)
            authRepository.logout()
            
            onLogoutComplete()
        }
    }
}

/**
 * UI state for child dashboard
 */
sealed class ChildDashboardState {
    object Loading : ChildDashboardState()
    data class Success(
        val userName: String,
        val totalPoints: Int,
        val pendingChoresCount: Int,
        val completedTodayCount: Int,
        val myChores: List<Chore>,
        val extraPointsChores: List<Chore> = emptyList(), // Unassigned chores available for any user
        val pendingRewards: List<Pair<RewardRedemption, com.chorequest.domain.models.Reward>> = emptyList() // Pending reward redemptions
    ) : ChildDashboardState()
    data class Error(val message: String) : ChildDashboardState()
}
