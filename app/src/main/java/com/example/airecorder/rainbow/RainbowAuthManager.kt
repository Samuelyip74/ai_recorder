package com.example.airecorder.rainbow

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ale.infra.rest.listeners.Failure
import com.ale.infra.rest.listeners.onFailure
import com.ale.infra.rest.listeners.onSuccess
import com.ale.rainbowsdk.Connection
import com.ale.rainbowsdk.RainbowSdk
import com.example.airecorder.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class RainbowAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rainbowBubbleRepository: RainbowBubbleRepository,
) {

    companion object {
        private const val TAG = "RainbowAuthManager"
        private const val PREFS_NAME = "rainbow_auth"
        private const val KEY_LOGIN = "login"
        private const val KEY_PASSWORD = "password"
    }

    private val sdk by lazy { RainbowSdk.instance() }
    private val authPreferences: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
    private var initialized = false
    private var listenerRegistered = false
    private val authHost: String? = BuildConfig.RAINBOW_HOST.trim().ifBlank { null }
    private var pendingCredentials: CachedRainbowCredentials? = null

    private val _sessionState = MutableStateFlow<RainbowSessionState>(RainbowSessionState.SignedOut)
    val sessionState: StateFlow<RainbowSessionState> = _sessionState.asStateFlow()

    private val connectionListener = object : Connection.IConnectionListener {
        override fun onStateChanged(newState: Connection.ConnectionState) {
            Log.d(TAG, "Rainbow connection state changed to $newState")
            _sessionState.value = when (newState) {
                Connection.ConnectionState.DISCONNECTED -> {
                    rainbowBubbleRepository.clear()
                    RainbowSessionState.SignedOut
                }
                Connection.ConnectionState.AUTHENTICATING -> RainbowSessionState.SigningIn
                Connection.ConnectionState.AUTHENTICATED -> RainbowSessionState.Authenticated
                Connection.ConnectionState.READY -> {
                    pendingCredentials?.let(::saveCachedCredentials)
                    pendingCredentials = null
                    rainbowBubbleRepository.registerListenerIfNeeded()
                    rainbowBubbleRepository.refreshTrackedRooms()
                    RainbowSessionState.SignedIn
                }
            }
        }

        override fun onLoginError(error: Failure) {
            val message = error.message.ifBlank { "Rainbow sign-in failed: ${error.code}" }
            Log.e(TAG, "Rainbow login error: code=${error.code} details=${error.detailsCode} message=${error.message}")
            pendingCredentials = null
            _sessionState.value = RainbowSessionState.Error(message)
        }

        override fun onConnectionError(error: Exception) {
            val message = error.message ?: "Rainbow connection error."
            Log.e(TAG, "Rainbow connection error", error)
            pendingCredentials = null
            _sessionState.value = RainbowSessionState.Error(message)
        }

        override fun onUserLogoutForced(restart: Boolean) {
            rainbowBubbleRepository.clear()
            _sessionState.value = RainbowSessionState.Error("Rainbow session ended.")
        }
    }

    fun initialize(): Result<Unit> = runCatching {
        require(BuildConfig.RAINBOW_APP_ID.isNotBlank()) {
            "Missing Rainbow SDK app id. Set RAINBOW_APP_ID in local.properties or Gradle properties."
        }
        require(BuildConfig.RAINBOW_APP_SECRET.isNotBlank()) {
            "Missing Rainbow SDK app secret. Set RAINBOW_APP_SECRET in local.properties or Gradle properties."
        }
        if (!initialized) {
            sdk.initialize(
                context.applicationContext,
                BuildConfig.RAINBOW_APP_ID,
                BuildConfig.RAINBOW_APP_SECRET,
            )
            initialized = true
            Log.d(TAG, "Rainbow SDK initialized.")
        }
        if (!listenerRegistered) {
            sdk.connection().registerConnectionListener(connectionListener)
            listenerRegistered = true
        }
        rainbowBubbleRepository.registerListenerIfNeeded()
    }.onFailure {
        _sessionState.value = RainbowSessionState.Error(it.message ?: "Unable to initialize Rainbow SDK.")
    }

    suspend fun signIn(login: String, password: String): Result<Unit> {
        initialize().getOrElse { return Result.failure(it) }
        if (sdk.connection().state != Connection.ConnectionState.DISCONNECTED) {
            return Result.failure(IllegalStateException("Rainbow is already authenticating or connected."))
        }
        _sessionState.value = RainbowSessionState.SigningIn
        pendingCredentials = CachedRainbowCredentials(login = login.trim(), password = password)
        Log.d(TAG, "Signing in to Rainbow host=${authHost ?: "production"}")
        val result = sdk.connection().signIn(login = login, password = password, host = authHost)
        var failureMessage: String? = null
        result
            .onSuccess {
                Log.d(TAG, "Rainbow sign-in request accepted.")
            }
            .onFailure { failure ->
                failureMessage = failure.message.ifBlank { "Rainbow sign-in failed: ${failure.code}" }
                pendingCredentials = null
                _sessionState.value = RainbowSessionState.Error(failureMessage ?: "Rainbow sign-in failed.")
            }
        return failureMessage?.let { Result.failure(IllegalStateException(it)) } ?: Result.success(Unit)
    }

    fun getCachedCredentials(): CachedRainbowCredentials? {
        val login = authPreferences.getString(KEY_LOGIN, null)?.trim().orEmpty()
        val password = authPreferences.getString(KEY_PASSWORD, null).orEmpty()
        return if (login.isNotBlank() && password.isNotBlank()) {
            CachedRainbowCredentials(login = login, password = password)
        } else {
            null
        }
    }

    fun clearCachedCredentials() {
        authPreferences.edit().clear().apply()
    }

    private fun saveCachedCredentials(credentials: CachedRainbowCredentials) {
        authPreferences.edit()
            .putString(KEY_LOGIN, credentials.login)
            .putString(KEY_PASSWORD, credentials.password)
            .apply()
    }

    data class CachedRainbowCredentials(
        val login: String,
        val password: String,
    )

    sealed interface RainbowSessionState {
        data object SignedOut : RainbowSessionState
        data object SigningIn : RainbowSessionState
        data object Authenticated : RainbowSessionState
        data object SignedIn : RainbowSessionState
        data class Error(val message: String) : RainbowSessionState
    }
}
