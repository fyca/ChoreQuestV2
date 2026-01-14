package com.chorequest.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chorequest.data.repository.AuthRepository
import com.chorequest.domain.models.QRCodePayload
import com.chorequest.domain.models.User
import com.chorequest.utils.QRCodeUtils
import com.chorequest.utils.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for QR code scanner
 * Handles QR code validation and authentication
 */
@HiltViewModel
class QRScannerViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _scannerState = MutableStateFlow<QRScannerState>(QRScannerState.Scanning)
    val scannerState: StateFlow<QRScannerState> = _scannerState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<QRNavigationEvent>()
    val navigationEvent: SharedFlow<QRNavigationEvent> = _navigationEvent.asSharedFlow()

    /**
     * Process scanned QR code data
     */
    fun processQRCode(qrData: String) {
        viewModelScope.launch {
            _scannerState.value = QRScannerState.Processing

            // Parse QR code payload
            val payload = QRCodeUtils.parseQRCodeData(qrData)
            
            if (payload == null) {
                _scannerState.value = QRScannerState.Error("Invalid QR code format")
                return@launch
            }

            // Validate payload
            if (!QRCodeUtils.validateQRCodePayload(payload)) {
                _scannerState.value = QRScannerState.Error("QR code is invalid or expired")
                return@launch
            }

            // Authenticate with QR code
            authenticateWithQRCode(payload)
        }
    }

    /**
     * Authenticate user with QR code payload
     */
    private fun authenticateWithQRCode(payload: QRCodePayload) {
        viewModelScope.launch {
            authRepository.authenticateWithQR(
                familyId = payload.familyId,
                userId = payload.userId,
                token = payload.token,
                tokenVersion = payload.version
            ).collect { result ->
                when (result) {
                    is Result.Success -> {
                        _scannerState.value = QRScannerState.Success(result.data)
                        // Navigate based on user role
                        _navigationEvent.emit(
                            if (result.data.role == com.chorequest.domain.models.UserRole.PARENT) {
                                QRNavigationEvent.NavigateToParentDashboard
                            } else {
                                QRNavigationEvent.NavigateToChildDashboard
                            }
                        )
                    }
                    is Result.Error -> {
                        _scannerState.value = QRScannerState.Error(result.message)
                    }
                    is Result.Loading -> {
                        _scannerState.value = QRScannerState.Processing
                    }
                }
            }
        }
    }

    /**
     * Reset scanner to retry
     */
    fun resetScanner() {
        _scannerState.value = QRScannerState.Scanning
    }
    
    /**
     * Set error state
     */
    fun setError(message: String) {
        _scannerState.value = QRScannerState.Error(message)
    }

    /**
     * Navigate back to login
     */
    fun navigateBack() {
        viewModelScope.launch {
            _navigationEvent.emit(QRNavigationEvent.NavigateBack)
        }
    }
}

/**
 * UI state for QR scanner
 */
sealed class QRScannerState {
    object Scanning : QRScannerState()
    object Processing : QRScannerState()
    data class Success(val user: User) : QRScannerState()
    data class Error(val message: String) : QRScannerState()
}

/**
 * Navigation events for QR scanner
 */
sealed class QRNavigationEvent {
    object NavigateToParentDashboard : QRNavigationEvent()
    object NavigateToChildDashboard : QRNavigationEvent()
    object NavigateBack : QRNavigationEvent()
}
