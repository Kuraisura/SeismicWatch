package com.gising.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gising.data.repository.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs [SettingsScreen] with persisted [SettingsStore] values. */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)

    val settings: StateFlow<SettingsStore.Snapshot> =
        store.settings.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsStore.Snapshot()
        )

    fun setMinMagnitude(value: Double) = viewModelScope.launch { store.setMinMagnitude(value) }
    fun setPollingSeconds(value: Int) = viewModelScope.launch { store.setPollingSeconds(value) }
    fun setVibration(value: Boolean) = viewModelScope.launch { store.setVibration(value) }
    fun setSound(value: Boolean) = viewModelScope.launch { store.setSound(value) }
    fun setFlashlight(value: Boolean) = viewModelScope.launch { store.setFlashlight(value) }

    // ── Emergency subsystem ──────────────────────────────────────────────────
    fun setPanicGestures(value: Boolean) = viewModelScope.launch {
        store.setPanicGestures(value)
        val ctx = getApplication<Application>()
        if (value) com.gising.emergency.PowerButtonService.start(ctx)
        else com.gising.emergency.PowerButtonService.stop(ctx)
    }

    fun setMeshRelay(value: Boolean) = viewModelScope.launch {
        store.setMeshRelay(value)
        // Accepting the relay implies accepting the Nearby disclosure; this also unlocks the
        // mesh channel for the outgoing SOS path. We never auto-clear consent on toggle-off so
        // an in-emergency SOS can still relay after the user later turns the standing relay off.
        if (value) store.setMeshConsent(true)
        val ctx = getApplication<Application>()
        if (value) com.gising.emergency.MeshService.startRelay(ctx)
        else com.gising.emergency.MeshService.stop(ctx)
    }

    fun setSmsFailback(value: Boolean) = viewModelScope.launch { store.setSmsFailback(value) }

    // ── Notification preferences ──────────────────────────────────────────────
    fun setAlertEarthquake(value: Boolean) = viewModelScope.launch { store.setAlertEarthquake(value) }
    fun setAlertTyphoon(value: Boolean) = viewModelScope.launch { store.setAlertTyphoon(value) }
    fun setAlertFlood(value: Boolean) = viewModelScope.launch { store.setAlertFlood(value) }
    fun setSoundVibration(value: Boolean) = viewModelScope.launch {
        store.setSound(value); store.setVibration(value)
    }
    fun setBackgroundSync(value: Boolean) = viewModelScope.launch { store.setBackgroundSync(value) }

    // ── Personal information ──────────────────────────────────────────────────
    fun setMobileNumber(value: String) = viewModelScope.launch { store.setMobileNumber(value) }
    fun setHomeAddress(value: String) = viewModelScope.launch { store.setHomeAddress(value) }

    // ── Appearance ────────────────────────────────────────────────────────────
    /** Persist the chosen theme and swap the OS launcher icon/cold-splash to match. */
    fun setAppTheme(theme: com.gising.ui.theme.AppTheme) = viewModelScope.launch {
        store.setAppTheme(theme.key)
        com.gising.ui.theme.ThemeAliasManager.apply(getApplication(), theme)
    }
    fun setThemeMode(mode: com.gising.ui.theme.ThemeMode) = viewModelScope.launch {
        store.setThemeMode(mode.key)
    }
}
