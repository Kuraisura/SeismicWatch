package com.gising.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gising.data.model.*
import com.gising.data.repository.EarthquakeRepository
import com.gising.data.repository.SeismicDatabase
import com.gising.data.repository.toDomain
import com.gising.data.repository.toEntity
import com.gising.data.supabase.SupabaseEarthquakeRepository
import com.gising.data.repository.HazardRepository
import com.gising.data.repository.TyphoonRepository
import com.gising.data.repository.WeatherRepository
import com.gising.emergency.ConnectivityObserver
import com.gising.emergency.ConnectivityProbe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.absoluteValue

data class HomeUiState(
    val isLoading: Boolean = false,
    val earthquakes: List<Earthquake> = emptyList(),
    val nearestSevere: Earthquake? = null,
    /** Closest recent event by distance, any magnitude (drives the home earthquake card). */
    val nearestRecent: Earthquake? = null,
    val lastUpdated: String = "",
    val error: String? = null,
    val filterMag: Double = 0.0,
    val isMonitoringActive: Boolean = true,
    val locationLabel: String = "",
    val airQuality: AirQualityReading? = null,
    val heatIndex: HeatIndexReading? = null,
    val typhoons: List<Typhoon> = emptyList(),
    /** Recent (past ~90 days) Philippine-area tropical cyclones, for the Reports history view. */
    val pastTyphoons: List<Typhoon> = emptyList(),
    /** Closest active tropical cyclone to the user, if any. */
    val nearestTyphoon: Typhoon? = null,
    /** Live local weather strength for the user's location (drives the weather card). */
    val liveWeather: LiveWeather? = null,
    /** Official PAGASA cyclone signal when a storm is active (best-effort). */
    val pagasaCyclone: PagasaCyclone? = null,
    /** No validated internet path right now → the app is serving locally-saved reports only. */
    val isOffline: Boolean = false,
    /** The currently-shown reports came from the on-disk cache, not a live fetch. */
    val usingCachedData: Boolean = false,
)

/** Earthquakes farther than this from the user don't drive the home hero. */
const val NEARBY_RADIUS_KM = 500.0

// On-disk retention for the offline cache.
private const val QUAKE_CACHE_RETENTION_MS = 30L * 24 * 3600_000   // 30 days of quakes
private const val TYPHOON_CACHE_RETENTION_MS = 120L * 24 * 3600_000 // ~4 months of cyclones

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = EarthquakeRepository()                 // retained for shaking prediction / attenuation math
    private val supabaseRepo = SupabaseEarthquakeRepository()  // primary data source (PHIVOLCS via Supabase)
    private val hazardRepo = HazardRepository()
    private val typhoonRepo = TyphoonRepository()
    private val weatherRepo = WeatherRepository()

    private val appContext = app.applicationContext
    private val db by lazy { SeismicDatabase.getInstance(appContext) }
    private val eqDao get() = db.earthquakeDao()
    private val tyDao get() = db.typhoonDao()

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Default location: Manila
    var userLat = 14.5995
    var userLon = 120.9842

    init {
        // Show whatever we saved last time first, so a cold start with no signal isn't blank.
        seedFromCache()
        // React to connectivity changes: flip the Offline-Mode flag and re-pull when signal returns.
        viewModelScope.launch {
            ConnectivityObserver.observe(appContext).collect { online ->
                val wasOffline = _uiState.value.isOffline
                _uiState.update { it.copy(isOffline = !online) }
                if (online && wasOffline) {
                    refresh()
                    fetchHazards()
                    fetchWeather()
                }
            }
        }
        refresh()
        fetchHazards()
        fetchTyphoons()
        fetchWeather()
    }

    /** Populate the UI from the encrypted Room cache (earthquakes + typhoons) without any network. */
    private fun seedFromCache() {
        viewModelScope.launch {
            val cachedQuakes = runCatching { eqDao.getAll() }.getOrDefault(emptyList())
                .sortedByDescending { it.timeMs }
            val cachedTyphoons = runCatching { tyDao.getAll().map { it.toDomain() } }.getOrDefault(emptyList())
            _uiState.update { st ->
                if (st.earthquakes.isNotEmpty() || st.typhoons.isNotEmpty()) return@update st
                val active = cachedTyphoons.filter { it.isCurrent }
                st.copy(
                    earthquakes = cachedQuakes,
                    nearestSevere = nearestSevereOf(cachedQuakes),
                    nearestRecent = nearestRecentOf(cachedQuakes),
                    typhoons = active,
                    pastTyphoons = cachedTyphoons,
                    nearestTyphoon = active.minByOrNull { haversine(it.latitude, it.longitude, userLat, userLon) },
                    usingCachedData = cachedQuakes.isNotEmpty() || cachedTyphoons.isNotEmpty(),
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Offline: serve the saved bulletins straight from disk, no doomed network attempt.
            if (!ConnectivityProbe.isOnline(appContext)) {
                val cached = runCatching { eqDao.getAll() }.getOrDefault(emptyList())
                    .sortedByDescending { it.timeMs }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isOffline = true,
                        usingCachedData = cached.isNotEmpty() || it.earthquakes.isNotEmpty(),
                        earthquakes = cached.ifEmpty { it.earthquakes },
                        nearestSevere = nearestSevereOf(cached.ifEmpty { it.earthquakes }),
                        nearestRecent = nearestRecentOf(cached.ifEmpty { it.earthquakes }),
                    )
                }
                loadTyphoonCache()
                return@launch
            }

            try {
                // Recent PHIVOLCS bulletins from Supabase, newest first. 400 covers well over a week of
                // activity while keeping the initial load + decode fast on low-end devices.
                val all = supabaseRepo.fetchLatestQuakes(limit = 400)
                    .distinctBy { it.id }
                    .sortedByDescending { it.timeMs }

                // Persist for offline use, then trim the cache to its retention window.
                runCatching {
                    val now = System.currentTimeMillis()
                    eqDao.upsertAll(all.map { it.copy(savedAt = now) })
                    eqDao.purgeOlderThan(now - QUAKE_CACHE_RETENTION_MS)
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isOffline = false,
                        usingCachedData = false,
                        earthquakes = all,
                        nearestSevere = nearestSevereOf(all),
                        nearestRecent = nearestRecentOf(all),
                        lastUpdated = formatNow()
                    )
                }
            } catch (e: Exception) {
                // Weak/flaky signal: don't wipe the screen — keep showing saved data and say so.
                val cached = runCatching { eqDao.getAll() }.getOrDefault(emptyList())
                    .sortedByDescending { it.timeMs }
                _uiState.update {
                    val quakes = if (it.earthquakes.isEmpty()) cached else it.earthquakes
                    it.copy(
                        isLoading = false,
                        error = "Weak connection — showing saved reports.",
                        usingCachedData = quakes.isNotEmpty(),
                        earthquakes = quakes,
                        nearestSevere = nearestSevereOf(quakes),
                        nearestRecent = nearestRecentOf(quakes),
                    )
                }
            }
        }
        // Keep typhoons + live weather fresh on the same cadence as quakes.
        fetchTyphoons()
        fetchWeather()
    }

    private fun nearestSevereOf(list: List<Earthquake>): Earthquake? =
        list.filter { it.magnitude >= 5.0 }
            .minByOrNull { haversine(it.latitude, it.longitude, userLat, userLon) }

    private fun nearestRecentOf(list: List<Earthquake>): Earthquake? =
        list.minByOrNull { haversine(it.latitude, it.longitude, userLat, userLon) }

    /** Load the saved cyclones when offline / after a failed fetch, splitting active vs. history. */
    private suspend fun loadTyphoonCache() {
        val cached = runCatching { tyDao.getAll().map { it.toDomain() } }.getOrDefault(emptyList())
        if (cached.isEmpty()) return
        val active = cached.filter { it.isCurrent }
        _uiState.update {
            it.copy(
                typhoons = active,
                pastTyphoons = cached,
                nearestTyphoon = active.minByOrNull { t -> haversine(t.latitude, t.longitude, userLat, userLon) },
            )
        }
    }

    /** Pull live local weather strength + best-effort PAGASA cyclone signal. */
    fun fetchWeather() {
        viewModelScope.launch {
            val weather = runCatching { weatherRepo.fetchLiveWeather(userLat, userLon) }.getOrNull()
            val pagasa = runCatching { weatherRepo.fetchPagasaCyclone() }.getOrNull()
            _uiState.update {
                it.copy(
                    liveWeather = weather ?: it.liveWeather,
                    pagasaCyclone = pagasa,
                )
            }
        }
    }

    /** Pull active + recent tropical cyclones (best-effort) and recompute the nearest one. */
    fun fetchTyphoons() {
        viewModelScope.launch {
            // Offline → serve saved cyclones instead of failing the fetch.
            if (!ConnectivityProbe.isOnline(appContext)) {
                loadTyphoonCache()
                return@launch
            }
            val typhoons = runCatching { typhoonRepo.fetchActiveTyphoons() }.getOrNull()
            val past = runCatching { typhoonRepo.fetchRecentTyphoons() }.getOrNull()

            // Both calls failed (weak signal) → keep whatever we have, backfilled from cache.
            if (typhoons == null && past == null) {
                loadTyphoonCache()
                return@launch
            }

            // Persist for offline use. Active storms win over their historical copy on id clash.
            runCatching {
                val now = System.currentTimeMillis()
                val byId = LinkedHashMap<String, com.gising.data.repository.TyphoonEntity>()
                past?.forEach { byId[it.id] = it.toEntity(now) }
                typhoons?.forEach { byId[it.id] = it.copy(isCurrent = true).toEntity(now) }
                if (byId.isNotEmpty()) {
                    tyDao.upsertAll(byId.values.toList())
                    tyDao.purgeOlderThan(now - TYPHOON_CACHE_RETENTION_MS)
                }
            }

            _uiState.update {
                val active = typhoons ?: it.typhoons
                it.copy(
                    typhoons = active,
                    pastTyphoons = past ?: it.pastTyphoons,
                    nearestTyphoon = active.minByOrNull { t ->
                        haversine(t.latitude, t.longitude, userLat, userLon)
                    },
                )
            }
        }
    }

    fun setFilter(minMag: Double) {
        _uiState.update { it.copy(filterMag = minMag) }
    }

    /** Update the user's location (from the device) and recompute the nearest severe event. */
    fun updateLocation(lat: Double, lon: Double) {
        if (lat == userLat && lon == userLon) return
        userLat = lat
        userLon = lon
        _uiState.update { st ->
            val nearest = st.earthquakes
                .filter { it.magnitude >= 5.0 }
                .minByOrNull { haversine(it.latitude, it.longitude, userLat, userLon) }
            val nearestRecent = st.earthquakes
                .minByOrNull { haversine(it.latitude, it.longitude, userLat, userLon) }
            val nearestTyphoon = st.typhoons
                .minByOrNull { haversine(it.latitude, it.longitude, userLat, userLon) }
            st.copy(
                nearestSevere = nearest,
                nearestRecent = nearestRecent,
                nearestTyphoon = nearestTyphoon,
            )
        }
        fetchHazards()
        fetchWeather()
    }

    /** Pull live air-quality and heat-index readings for the current location (best-effort). */
    fun fetchHazards() {
        viewModelScope.launch {
            val aqi = runCatching { hazardRepo.fetchAirQuality(userLat, userLon) }.getOrNull()
            val heat = runCatching { hazardRepo.fetchHeatIndex(userLat, userLon) }.getOrNull()
            _uiState.update {
                it.copy(
                    airQuality = aqi ?: it.airQuality,
                    heatIndex = heat ?: it.heatIndex,
                )
            }
        }
    }

    fun filteredQuakes(): List<Earthquake> {
        val state = _uiState.value
        return state.earthquakes.filter { it.magnitude >= state.filterMag }
    }

    /** Great-circle distance from the user to an event, in km. */
    fun distanceTo(eq: Earthquake): Double =
        haversine(eq.latitude, eq.longitude, userLat, userLon)

    /** Great-circle distance from the user to a tropical cyclone's current center, in km. */
    fun distanceTo(t: Typhoon): Double =
        haversine(t.latitude, t.longitude, userLat, userLon)

    /**
     * The nearest recent earthquake, but only if it is close enough to matter to the user.
     * Drives the home hero: a nearby event shows as "active", otherwise the screen is "all clear".
     */
    fun nearbyQuake(): Earthquake? =
        _uiState.value.nearestRecent?.takeIf { distanceTo(it) <= NEARBY_RADIUS_KM }

    /** How many events in the last 7 days share the broad region of [eq] (by place suffix). */
    fun weeklyCountNear(eq: Earthquake): Int {
        val region = eq.place.substringAfterLast(", ").trim().lowercase()
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 3600_000
        return _uiState.value.earthquakes.count {
            it.timeMs >= weekAgo &&
                (region.isBlank() || it.place.lowercase().contains(region))
        }
    }

    /** Set a human-readable label for the user's current area (shown on the home top bar). */
    fun setLocationLabel(label: String) {
        if (label.isNotBlank()) _uiState.update { it.copy(locationLabel = label) }
    }

    fun computePrediction(eq: Earthquake): ShakingPrediction =
        repo.computeShakingPrediction(eq, userLat, userLon)

    private fun formatNow(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return 2 * r * Math.asin(Math.sqrt(a))
    }
}
