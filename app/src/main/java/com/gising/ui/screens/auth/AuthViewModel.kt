package com.gising.ui.screens.auth

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gising.data.auth.AuthRepository
import com.gising.data.auth.AuthState
import com.gising.data.auth.GoogleAuthClient
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode { SignIn, SignUp }

data class AuthUiState(
    val mode: AuthMode = AuthMode.SignIn,
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
)

class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val authRepository = AuthRepository()
    private val googleAuthClient = GoogleAuthClient(app)

    // Bumped when we manually re-check email verification, so the gate re-evaluates even
    // though Firebase's AuthStateListener doesn't fire on a verification reload().
    private val refreshTick = MutableStateFlow(0)

    val authState: StateFlow<AuthState> =
        combine(authRepository.authState, refreshTick) { state, _ ->
            val user = FirebaseAuth.getInstance().currentUser
            when {
                state is AuthState.Loading -> AuthState.Loading
                user == null -> AuthState.SignedOut
                else -> AuthState.SignedIn(user, user.isEmailVerified)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthState.Loading)

    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()

    fun onEmailChange(v: String) = _ui.update { it.copy(email = v, error = null) }
    fun onPasswordChange(v: String) = _ui.update { it.copy(password = v, error = null) }
    fun onDisplayNameChange(v: String) = _ui.update { it.copy(displayName = v, error = null) }

    fun toggleMode() = _ui.update {
        it.copy(
            mode = if (it.mode == AuthMode.SignIn) AuthMode.SignUp else AuthMode.SignIn,
            error = null,
            info = null,
        )
    }

    fun consumeMessages() = _ui.update { it.copy(error = null, info = null) }

    fun submit() {
        val s = _ui.value
        val email = s.email.trim()
        if (!email.contains("@") || email.length < 5) {
            _ui.update { it.copy(error = "Enter a valid email address.") }
            return
        }
        if (s.password.length < 8) {
            _ui.update { it.copy(error = "Password must be at least 8 characters.") }
            return
        }
        if (s.mode == AuthMode.SignUp && s.displayName.isBlank()) {
            _ui.update { it.copy(error = "Please enter your name.") }
            return
        }

        _ui.update { it.copy(isLoading = true, error = null, info = null) }
        viewModelScope.launch {
            val result = if (s.mode == AuthMode.SignUp) {
                authRepository.signUpWithEmail(email, s.password, s.displayName)
            } else {
                authRepository.signInWithEmail(email, s.password)
            }
            result.fold(
                onSuccess = {
                    _ui.update {
                        it.copy(
                            isLoading = false,
                            info = if (s.mode == AuthMode.SignUp)
                                "Account created. We sent a verification link to your email."
                            else null,
                        )
                    }
                },
                onFailure = { e ->
                    _ui.update { it.copy(isLoading = false, error = friendly(e)) }
                },
            )
        }
    }

    fun signInWithGoogle(activityContext: Context) {
        _ui.update { it.copy(isLoading = true, error = null, info = null) }
        viewModelScope.launch {
            googleAuthClient.getIdToken(activityContext)
                .mapCatching { idToken -> authRepository.signInWithGoogle(idToken).getOrThrow() }
                .fold(
                    onSuccess = { _ui.update { it.copy(isLoading = false) } },
                    onFailure = { e -> _ui.update { it.copy(isLoading = false, error = friendly(e)) } },
                )
        }
    }

    fun sendPasswordReset() {
        val email = _ui.value.email.trim()
        if (!email.contains("@")) {
            _ui.update { it.copy(error = "Enter your email first, then tap reset.") }
            return
        }
        viewModelScope.launch {
            authRepository.sendPasswordReset(email).fold(
                onSuccess = { _ui.update { it.copy(info = "Password reset link sent to $email.") } },
                onFailure = { e -> _ui.update { it.copy(error = friendly(e)) } },
            )
        }
    }

    fun resendVerification() {
        viewModelScope.launch {
            authRepository.sendEmailVerification().fold(
                onSuccess = { _ui.update { it.copy(info = "Verification email re-sent.") } },
                onFailure = { e -> _ui.update { it.copy(error = friendly(e)) } },
            )
        }
    }

    /** Re-checks email verification; the gate recomposes when [authState] updates. */
    fun refreshVerification() {
        viewModelScope.launch {
            authRepository.reloadUser()
            refreshTick.update { it + 1 }
        }
    }

    fun signOut() = authRepository.signOut()

    /** Maps raw Firebase exceptions to short, user-safe messages (never leak internals). */
    private fun friendly(e: Throwable): String = when {
        e.message?.contains("password is invalid", true) == true ||
            e.message?.contains("INVALID_LOGIN_CREDENTIALS", true) == true ->
            "Incorrect email or password."
        e.message?.contains("no user record", true) == true ->
            "No account found for that email."
        e.message?.contains("email address is already in use", true) == true ->
            "That email is already registered. Try signing in."
        e.message?.contains("network", true) == true ->
            "Network error. Check your connection and try again."
        e.message?.contains("blocked all requests", true) == true ->
            "Too many attempts. Please wait a moment and retry."
        else -> "Something went wrong. Please try again."
    }
}
