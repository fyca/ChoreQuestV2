package com.lostsierra.chorequest.presentation.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostsierra.chorequest.data.repository.ActivityLogRepository
import com.lostsierra.chorequest.domain.models.ActivityLog
import com.lostsierra.chorequest.utils.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for activity log screen
 */
@HiltViewModel
class ActivityLogViewModel @Inject constructor(
    private val activityLogRepository: ActivityLogRepository
) : ViewModel() {

    private val _activityLogsState = MutableStateFlow<ActivityLogsState>(ActivityLogsState.Loading)
    val activityLogsState: StateFlow<ActivityLogsState> = _activityLogsState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var currentPage = 1
    private val pageSize = 50

    init {
        loadActivityLogs()
    }

    /**
     * Load activity logs
     */
    fun loadActivityLogs(refresh: Boolean = false) {
        if (refresh) {
            currentPage = 1
            _isRefreshing.value = true
        }
        
        viewModelScope.launch {
            activityLogRepository.fetchActivityLogs(
                page = currentPage,
                pageSize = pageSize
            ).collect { result ->
                when (result) {
                    is Result.Success -> {
                        _activityLogsState.value = ActivityLogsState.Success(result.data)
                        _isRefreshing.value = false
                    }
                    is Result.Error -> {
                        // Try loading from local cache on error
                        activityLogRepository.getRecentLogs(pageSize).firstOrNull()?.let { localLogs ->
                            if (localLogs.isNotEmpty()) {
                                _activityLogsState.value = ActivityLogsState.Success(localLogs)
                            } else {
                                _activityLogsState.value = ActivityLogsState.Error(result.message)
                            }
                        } ?: run {
                            _activityLogsState.value = ActivityLogsState.Error(result.message)
                        }
                        _isRefreshing.value = false
                    }
                    is Result.Loading -> {
                        if (!refresh) {
                            _activityLogsState.value = ActivityLogsState.Loading
                        }
                    }
                }
            }
        }
    }

    /**
     * Load more logs (pagination)
     */
    fun loadMore() {
        currentPage++
        viewModelScope.launch {
            activityLogRepository.fetchActivityLogs(
                page = currentPage,
                pageSize = pageSize
            ).collect { result ->
                when (result) {
                    is Result.Success -> {
                        val currentState = _activityLogsState.value
                        if (currentState is ActivityLogsState.Success) {
                            val combinedLogs = currentState.logs + result.data
                            _activityLogsState.value = ActivityLogsState.Success(combinedLogs)
                        }
                    }
                    is Result.Error -> {
                        // Keep current state on error
                    }
                    is Result.Loading -> {
                        // Keep current state while loading more
                    }
                }
            }
        }
    }

    /**
     * Refresh logs
     */
    fun refresh() {
        loadActivityLogs(refresh = true)
    }

    /**
     * Filter logs by user
     */
    fun filterByUser(userId: String) {
        viewModelScope.launch {
            activityLogRepository.getLogsForUser(userId).collect { logs ->
                _activityLogsState.value = ActivityLogsState.Success(logs)
            }
        }
    }

    /**
     * Filter logs by action type
     */
    fun filterByActionType(actionType: String) {
        viewModelScope.launch {
            activityLogRepository.getLogsByType(actionType).collect { logs ->
                _activityLogsState.value = ActivityLogsState.Success(logs)
            }
        }
    }

    /**
     * Clear filters
     */
    fun clearFilters() {
        loadActivityLogs(refresh = true)
    }
}

/**
 * UI state for activity logs
 */
sealed class ActivityLogsState {
    object Loading : ActivityLogsState()
    data class Success(val logs: List<ActivityLog>) : ActivityLogsState()
    data class Error(val message: String) : ActivityLogsState()
}
