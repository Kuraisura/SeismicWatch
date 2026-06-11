package com.gising.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gising.data.gibs.GibsFrame
import com.gising.data.gibs.GibsTimelineRepository
import com.gising.data.model.CycloneTrack
import com.gising.data.supabase.CycloneRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CycloneUiState(
    val isLoading: Boolean = true,
    val track: CycloneTrack? = null,
    val error: String? = null,
    /** Animated satellite loop frames (oldest → newest). */
    val frames: List<GibsFrame> = emptyList(),
    /** True when no satellite frames could be resolved (show a note; base map still renders). */
    val imageryUnavailable: Boolean = false,
)

/**
 * Drives the Cyclone Tracker screen: the active storm's track (Supabase) plus the GIBS satellite
 * time-loop frames. Track + frames are fetched in parallel.
 */
class CycloneViewModel : ViewModel() {

    private val repo = CycloneRepository()
    private val timeline = GibsTimelineRepository()

    private val _ui = MutableStateFlow(CycloneUiState())
    val ui: StateFlow<CycloneUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true, error = null) }

            val trackDeferred = async { runCatching { repo.fetchActiveTrack() } }
            val framesDeferred = async { runCatching { timeline.loadSatelliteFrames() }.getOrDefault(emptyList()) }

            val trackResult = trackDeferred.await()
            val frames = framesDeferred.await()

            _ui.update { state ->
                trackResult.fold(
                    onSuccess = { track ->
                        state.copy(
                            isLoading = false,
                            track = track,
                            error = null,
                            frames = frames,
                            imageryUnavailable = frames.isEmpty(),
                        )
                    },
                    onFailure = {
                        state.copy(
                            isLoading = false,
                            error = "Couldn't reach the cyclone feed. Check your connection.",
                            frames = frames,
                            imageryUnavailable = frames.isEmpty(),
                        )
                    },
                )
            }
        }
    }
}
