package com.example.airecorder.ui.rainbow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.airecorder.rainbow.RainbowAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RainbowAuthUiState(
    val login: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val isSignedIn: Boolean = false,
    val errorMessage: String? = null,
    val hasPhonePermission: Boolean = false,
    val sdkReady: Boolean = false,
)

@HiltViewModel
class RainbowAuthViewModel @Inject constructor(
    private val rainbowAuthManager: RainbowAuthManager,
) : ViewModel() {

    private var autoSignInAttempted = false
    private val _uiState = MutableStateFlow(RainbowAuthUiState())
    val uiState: StateFlow<RainbowAuthUiState> = _uiState.asStateFlow()

    init {
        rainbowAuthManager.getCachedCredentials()?.let { credentials ->
            _uiState.update {
                it.copy(
                    login = credentials.login,
                    password = credentials.password,
                )
            }
        }
        viewModelScope.launch {
            rainbowAuthManager.sessionState.collect { state ->
                _uiState.update {
                    when (state) {
                        RainbowAuthManager.RainbowSessionState.SignedOut -> {
                            it.copy(isLoading = false, isSignedIn = false)
                        }

                        RainbowAuthManager.RainbowSessionState.SigningIn -> {
                            it.copy(isLoading = true, errorMessage = null)
                        }

                        RainbowAuthManager.RainbowSessionState.Authenticated -> {
                            it.copy(isLoading = true, errorMessage = null)
                        }

                        RainbowAuthManager.RainbowSessionState.SignedIn -> {
                            it.copy(isLoading = false, isSignedIn = true, errorMessage = null)
                        }

                        is RainbowAuthManager.RainbowSessionState.Error -> {
                            it.copy(isLoading = false, isSignedIn = false, errorMessage = state.message)
                        }
                    }
                }
            }
        }
    }

    fun onPhonePermissionChanged(granted: Boolean) {
        _uiState.update { it.copy(hasPhonePermission = granted) }
        if (granted && !_uiState.value.sdkReady) {
            initialize()
        } else if (granted) {
            attemptAutoSignIn()
        }
    }

    private fun initialize() {
        val result = rainbowAuthManager.initialize()
        val wasSuccessful = result.isSuccess
        _uiState.update {
            it.copy(
                sdkReady = wasSuccessful,
                errorMessage = result.exceptionOrNull()?.message,
            )
        }
        if (wasSuccessful) {
            attemptAutoSignIn()
        }
    }

    fun updateLogin(value: String) {
        _uiState.update { it.copy(login = value) }
    }

    fun updatePassword(value: String) {
        _uiState.update { it.copy(password = value) }
    }

    fun signIn() {
        val state = _uiState.value
        val login = state.login.trim()
        val password = state.password
        when {
            login.isBlank() -> _uiState.update { it.copy(errorMessage = "Enter your Rainbow login.") }
            password.isBlank() -> _uiState.update { it.copy(errorMessage = "Enter your Rainbow password.") }
            !state.hasPhonePermission -> _uiState.update {
                it.copy(errorMessage = "Phone permission is required to initialize Rainbow.")
            }
            !state.sdkReady -> _uiState.update {
                it.copy(errorMessage = "Rainbow SDK is not configured. Set RAINBOW_APP_ID and RAINBOW_APP_SECRET.")
            }
            else -> {
                autoSignInAttempted = true
                viewModelScope.launch {
                    rainbowAuthManager.signIn(login, password)
                        .onFailure { throwable ->
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = throwable.message ?: "Rainbow sign-in failed.",
                                )
                            }
                        }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun attemptAutoSignIn() {
        val state = _uiState.value
        if (autoSignInAttempted || !state.hasPhonePermission || !state.sdkReady) return
        val login = state.login.trim()
        val password = state.password
        if (login.isBlank() || password.isBlank()) return
        autoSignInAttempted = true
        signIn()
    }
}
