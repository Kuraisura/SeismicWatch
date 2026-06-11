package com.gising.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gising.SeismicApplication
import com.gising.data.model.EmergencyContact
import com.gising.data.repository.SettingsStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Manages the user's saved family/friends and the family-alert channel toggles.
 */
class ContactsViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = (app as SeismicApplication).database.contactDao()
    private val settingsStore = SettingsStore(app)

    val contacts: StateFlow<List<EmergencyContact>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<SettingsStore.Snapshot> =
        settingsStore.settings.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsStore.Snapshot()
        )

    fun addContact(contact: EmergencyContact) = viewModelScope.launch {
        val id = dao.insert(contact)
        if (contact.isPrimary) dao.clearPrimaryExcept(id)
    }

    fun updateContact(contact: EmergencyContact) = viewModelScope.launch {
        dao.update(contact)
        if (contact.isPrimary) dao.clearPrimaryExcept(contact.id)
    }

    fun deleteContact(contact: EmergencyContact) = viewModelScope.launch {
        dao.delete(contact)
    }

    fun makePrimary(contact: EmergencyContact) = viewModelScope.launch {
        dao.update(contact.copy(isPrimary = true))
        dao.clearPrimaryExcept(contact.id)
    }

    // ── Channel toggles ────────────────────────────────────────────────────
    fun setFamilyAlerts(value: Boolean) = viewModelScope.launch { settingsStore.setFamilyAlerts(value) }
    fun setAutoSms(value: Boolean) = viewModelScope.launch { settingsStore.setAutoSms(value) }
    fun setAutoCall(value: Boolean) = viewModelScope.launch { settingsStore.setAutoCall(value) }
}
