package com.chorequest.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.chorequest.data.local.SessionManager
import com.chorequest.data.repository.ChoreRepository
import com.chorequest.domain.models.Chore
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
    private val sessionManager: SessionManager,
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
        // Trigger sync first, then load dashboard data (match Parent dashboard behavior)
        viewModelScope.launch {
            syncManager.triggerImmediateSync()
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

                // Get user's chores
                choreRepository.getChoresForUser(session.userId).collect { chores ->
                    val myChores = chores.filter { 
                        it.assignedTo.contains(session.userId) 
                    }
                    val pendingChores = myChores.filter { 
                        it.status == com.chorequest.domain.models.ChoreStatus.PENDING 
                    }
                    val completedToday = myChores.filter { 
                        it.status == com.chorequest.domain.models.ChoreStatus.COMPLETED ||
                        it.status == com.chorequest.domain.models.ChoreStatus.VERIFIED
                    }.size

                    // Calculate total points (from mock or session)
                    val totalPoints = 0 // TODO: Get from user profile

                    _uiState.value = ChildDashboardState.Success(
                        userName = session.userName,
                        totalPoints = totalPoints,
                        pendingChoresCount = pendingChores.size,
                        completedTodayCount = completedToday,
                        myChores = pendingChores.take(5)
                    )
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
     * Logout - clears session and triggers navigation callback
     */
    fun logout(onLogoutComplete: () -> Unit) {
        viewModelScope.launch {
            sessionManager.clearSession()
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
        val myChores: List<Chore>
    ) : ChildDashboardState()
    data class Error(val message: String) : ChildDashboardState()
}
