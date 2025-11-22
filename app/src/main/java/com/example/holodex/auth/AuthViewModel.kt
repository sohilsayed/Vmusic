// File: java/com/example/holodex/auth/AuthViewModel.kt
// (Create this new file)

package com.example.holodex.auth

import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.holodex.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import org.json.JSONObject
import timber.log.Timber
import java.nio.charset.StandardCharsets
import javax.inject.Inject

// Represents the different states of the authentication flow for the UI to observe.
sealed class AuthState {
    object LoggedOut : AuthState()
    object InProgress : AuthState()
    object LoggedIn : AuthState()
    data class Error(val message: String) : AuthState()
}
private fun getUserIdFromJwt(jwt: String): String? {
    return try {
        val parts = jwt.split(".")
        if (parts.size < 2) return null
        val payload = parts[1]
        val decodedBytes = Base64.decode(payload, Base64.URL_SAFE)
        val decodedString = String(decodedBytes, StandardCharsets.UTF_8)
        val json = JSONObject(decodedString)
        json.optInt("i", -1).takeIf { it != -1 }?.toString()
    } catch (e: Exception) {
        Timber.e(e, "Failed to decode JWT payload")
        null
    }
}
@HiltViewModel
class AuthViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.LoggedOut)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val authService = AuthorizationService(appContext)

    // Define the endpoints for Discord's OAuth2 service
    private val serviceConfig = AuthorizationServiceConfiguration(
        "https://discord.com/api/oauth2/authorize".toUri(),
        "https://discord.com/api/oauth2/token".toUri()
    )

    init {
        // On ViewModel creation, check if we are already logged in
        if (tokenManager.getJwt() != null) {
            _authState.value = AuthState.LoggedIn
        }
    }

    /**
     * Creates an intent to launch the Discord login flow in a Custom Tab.
     * This is called by the UI when the user taps the "Login" button.
     */
    fun getAuthorizationRequestIntent(): Intent {
        val authRequest = AuthorizationRequest.Builder(
            serviceConfig,
            BuildConfig.DISCORD_CLIENT_ID,
            ResponseTypeValues.CODE,
            BuildConfig.DISCORD_REDIRECT_URI.toUri()
        )
            .setScope("identify email")
            // AppAuth automatically generates and includes the PKCE parameters
            .build()

        Timber.d("Created authorization request for Discord.")
        return authService.getAuthorizationRequestIntent(authRequest)
    }

    /**
     * Handles the redirect intent received from the Custom Tab after the user
     * authorizes the app on Discord.
     */
    fun onAuthorizationResponse(intent: Intent) {
        _authState.value = AuthState.InProgress

        val resp = net.openid.appauth.AuthorizationResponse.fromIntent(intent)
        val ex = net.openid.appauth.AuthorizationException.fromIntent(intent)

        if (resp == null) {
            val errorMessage = "Authorization failed: ${ex?.errorDescription ?: "Unknown error"}"
            Timber.e(ex, errorMessage)
            _authState.value = AuthState.Error(errorMessage)
            return
        }

        // The authorization was successful, now exchange the code for a token.
        // AppAuth automatically includes the PKCE code_verifier it generated earlier.
        viewModelScope.launch {
            try {
                Timber.d("Exchanging authorization code for Discord token...")
                val tokenResponse = authRepository.exchangeDiscordCodeForToken(resp.createTokenExchangeRequest())

                val discordAccessToken = tokenResponse.accessToken
                if (discordAccessToken == null) {
                    throw IllegalStateException("Discord access token was null")
                }

                Timber.d("Successfully received Discord access token. Now logging into Holodex...")
                val holodexJwt = authRepository.loginToHolodex(discordAccessToken)
                tokenManager.saveJwt(holodexJwt)
                val userId = getUserIdFromJwt(holodexJwt)
                if (userId != null) {
                    tokenManager.saveUserId(userId)
                    Timber.i("Successfully logged in, saved Holodex JWT, and extracted User ID: $userId")
                } else {
                    Timber.w("Successfully logged in but could not extract User ID from JWT.")
                }
                _authState.value = AuthState.LoggedIn
                Timber.i("Successfully logged in and saved Holodex JWT.")

            } catch (e: Exception) {
                val errorMessage = "Full login flow failed: ${e.message}"
                Timber.e(e, errorMessage)
                _authState.value = AuthState.Error(errorMessage)
            }
        }
    }

    fun logout() {
        tokenManager.clearJwt()
        _authState.value = AuthState.LoggedOut
        Timber.i("User logged out and JWT cleared.")
    }

    override fun onCleared() {
        super.onCleared()
        authService.dispose()
    }
}