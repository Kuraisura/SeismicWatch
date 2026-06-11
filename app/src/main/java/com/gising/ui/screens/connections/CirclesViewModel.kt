package com.gising.ui.screens.connections

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gising.data.model.firebase.Circle
import com.gising.data.model.firebase.CircleMember
import com.gising.data.model.firebase.Invite
import com.gising.data.repository.CircleRepository
import com.gising.data.repository.SettingsStore
import com.gising.service.LocationSharingService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CirclesViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = CircleRepository()
    private val settings = SettingsStore(app)

    val myUid: String? get() = FirebaseAuth.getInstance().currentUser?.uid

    val circles: StateFlow<List<Circle>> =
        repo.observeMyCircles()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedCircleId = MutableStateFlow<String?>(null)
    val selectedCircleId: StateFlow<String?> = _selectedCircleId.asStateFlow()

    val members: StateFlow<List<CircleMember>> =
        _selectedCircleId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repo.observeMembers(id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val masterSharing: StateFlow<Boolean> =
        settings.settings.map { it.locationSharing }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Whether the user has accepted the background-location prominent disclosure. */
    val bgConsentGiven: StateFlow<Boolean> =
        settings.settings.map { it.bgLocationConsent }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setBgConsent(value: Boolean) = viewModelScope.launch { settings.setBgLocationConsent(value) }

    /** Family members (across all circles) who currently have a shareable location — for the map. */
    val familyPins: StateFlow<List<FamilyPin>> =
        repo.observeAllMembers().map { all ->
            all.filter { it.uid != myUid && it.sharingEnabled && it.lastLocation != null }
                .map {
                    val l = it.lastLocation!!
                    FamilyPin(it.displayName, l.lat, l.lng, l.updatedAt, l.batteryPct)
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** One-shot user messages (errors / confirmations). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    /**
     * Fires when a new person joins one of the caller's circles while the app is open.
     * Purely client-side — driven by the existing Firestore live member listener, so it
     * needs no server, push, or Cloud Function. The UI collects this to show a banner.
     */
    private val _memberJoined = MutableSharedFlow<MemberJoinEvent>(extraBufferCapacity = 4)
    val memberJoined: SharedFlow<MemberJoinEvent> = _memberJoined.asSharedFlow()

    init {
        observeJoins()
    }

    /**
     * Watches the live roster across all circles and emits a [MemberJoinEvent] the moment a
     * brand-new member appears. Two guards keep it honest:
     *  • the first emission only seeds the baseline (the existing roster never notifies), and
     *  • a fresh [CircleMember.joinedAt] is required, so joining a circle that already has
     *    long-standing members doesn't spam a banner for each of them.
     */
    private fun observeJoins() = viewModelScope.launch {
        val seen = mutableSetOf<String>()
        var seeded = false
        repo.observeAllMembers().collect { members ->
            if (!seeded) {
                members.forEach { seen.add(it.uid) }
                seeded = true
                return@collect
            }
            val now = System.currentTimeMillis()
            for (m in members) {
                if (m.uid == myUid || !seen.add(m.uid)) continue // skip me + already-seen
                val joinedMs = m.joinedAt?.time ?: continue
                if (now - joinedMs <= JOIN_RECENCY_WINDOW_MS) {
                    _memberJoined.emit(
                        MemberJoinEvent(m.displayName.ifBlank { "A new member" }, m.photoUrl)
                    )
                }
            }
        }
    }


    /** The freshly created invite to surface in a share sheet, if any. */
    private val _pendingInvite = MutableStateFlow<Invite?>(null)
    val pendingInvite: StateFlow<Invite?> = _pendingInvite.asStateFlow()
    fun consumeInvite() { _pendingInvite.value = null }

    fun select(circleId: String) { _selectedCircleId.value = circleId }

    fun createCircle(name: String) = viewModelScope.launch {
        repo.createCircle(name).fold(
            onSuccess = { id -> _selectedCircleId.value = id; _message.value = "Circle created." },
            onFailure = { e -> _message.value = e.message ?: "Couldn't create circle. Try again." },
        )
    }

    fun joinByCode(code: String) = viewModelScope.launch {
        repo.joinByCode(code).fold(
            onSuccess = { id -> _selectedCircleId.value = id; _message.value = "You've joined the circle!" },
            onFailure = { e -> _message.value = e.message ?: "Couldn't join with that code." },
        )
    }

    fun createInvite(circleId: String) = viewModelScope.launch {
        repo.createInvite(circleId).fold(
            onSuccess = { _pendingInvite.value = it },
            onFailure = { e -> _message.value = e.message ?: "Couldn't create an invite." },
        )
    }

    fun leaveCircle(circleId: String) = viewModelScope.launch {
        repo.leaveCircle(circleId).onSuccess {
            if (_selectedCircleId.value == circleId) _selectedCircleId.value = null
            _message.value = "You left the circle."
        }.onFailure { _message.value = "Couldn't leave the circle." }
    }

    fun removeMember(circleId: String, uid: String) = viewModelScope.launch {
        repo.removeMember(circleId, uid).onFailure { _message.value = "Couldn't remove member." }
    }

    /** Per-circle ghost mode. */
    fun setCircleSharing(circleId: String, enabled: Boolean) = viewModelScope.launch {
        repo.setSharingEnabled(circleId, enabled)
    }

    /**
     * Master location-sharing switch. Turning it on starts the foreground service; off stops
     * it. The caller is responsible for ensuring location permission/consent first.
     */
    fun setMasterSharing(enabled: Boolean) = viewModelScope.launch {
        settings.setLocationSharing(enabled)
        val ctx = getApplication<Application>()
        if (enabled) LocationSharingService.start(ctx) else LocationSharingService.stop(ctx)
    }

    companion object {
        /** Only a join newer than this (vs. its server timestamp) raises an in-app banner. */
        private const val JOIN_RECENCY_WINDOW_MS = 120_000L // 2 minutes
    }
}

/** A person who just joined one of the user's circles, surfaced as an in-app banner. */
data class MemberJoinEvent(val displayName: String, val photoUrl: String)
