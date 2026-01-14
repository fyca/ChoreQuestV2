package com.chorequest.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.chorequest.data.local.SessionManager
import com.chorequest.data.repository.ChoreRepository
import com.chorequest.domain.models.Chore
import com.chorequest.domain.models.User
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
    private val sessionManager: SessionManager,
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

                // Get all chores
                choreRepository.getAllChores().collect { chores ->
                    val pendingChores = chores.filter { 
                        it.status == com.chorequest.domain.models.ChoreStatus.PENDING 
                    }
                    val completedChores = chores.filter { 
                        it.status == com.chorequest.domain.models.ChoreStatus.COMPLETED 
                    }
                    val verifiedChores = chores.filter { 
                        it.status == com.chorequest.domain.models.ChoreStatus.VERIFIED 
                    }

                    _uiState.value = ParentDashboardState.Success(
                        userName = session.userName,
                        pendingChoresCount = pendingChores.size,
                        completedChoresCount = completedChores.size + verifiedChores.size,
                        awaitingVerificationCount = completedChores.size,
                        totalFamilyPoints = 0, // TODO: Calculate from users
                        recentChores = chores.take(5)
                    )
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
        val recentChores: List<Chore>
    ) : ParentDashboardState()
    data class Error(val message: String) : ParentDashboardState()
}
