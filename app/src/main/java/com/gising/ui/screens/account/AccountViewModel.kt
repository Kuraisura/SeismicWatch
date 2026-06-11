package com.gising.ui.screens.account

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gising.data.auth.AuthRepository
import com.gising.data.repository.PrivacyRepository
import com.gising.data.repository.SettingsStore
import com.gising.service.LocationSharingService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Backs the Privacy & Account section: identity, sign-out, and full data deletion. */
class AccountViewModel(app: Application) : AndroidViewModel(app) {

    private val authRepository = AuthRepository()
    private val privacyRepository = PrivacyRepository()
    private val settings = SettingsStore(app)

    val email: String? get() = FirebaseAuth.getInstance().currentUser?.email
    val displayName: String? get() = FirebaseAuth.getInstance().currentUser?.displayName

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    fun signOut() {
        stopSharing()
        authRepository.signOut()
    }

    fun deleteAccount() {
        _busy.value = true
        viewModelScope.launch {
            stopSharing()
            settings.setLocationSharing(false)
            when (val result = privacyRepository.deleteAccountAndData()) {
                is PrivacyRepository.DeleteResult.Success ->
                    authRepository.signOut() // ensure the gate returns to the auth screen
                is PrivacyRepository.DeleteResult.NeedsRecentLogin ->
                    _message.value = "For your security, please sign out and sign in again, then delete."
                is PrivacyRepository.DeleteResult.Failed ->
                    _message.value = result.message
            }
            _busy.value = false
        }
    }

    private fun stopSharing() {
        LocationSharingService.stop(getApplication())
    }
}
