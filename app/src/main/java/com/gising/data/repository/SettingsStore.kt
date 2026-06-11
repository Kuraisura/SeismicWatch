package com.gising.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gising_settings")

/**
 * Persisted user preferences for SeismicWatch — alert threshold and which family-alert
 * channels fire automatically. Backed by Jetpack DataStore so both the UI and the
 * background [com.gising.service.EarthquakeMonitorService] read the same source.
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val MIN_MAGNITUDE = doublePreferencesKey("min_magnitude")
        val POLLING_SECONDS = intPreferencesKey("polling_seconds")
        val ENABLE_VIBRATION = booleanPreferencesKey("enable_vibration")
        val ENABLE_SOUND = booleanPreferencesKey("enable_sound")
        val ENABLE_FLASHLIGHT = booleanPreferencesKey("enable_flashlight")
        val FAMILY_ALERTS = booleanPreferencesKey("family_alerts")
        val AUTO_SMS = booleanPreferencesKey("auto_sms")
        val AUTO_CALL = booleanPreferencesKey("auto_call")
        // Master switch for Life360-style live location sharing. Opt-in (default off).
        val LOCATION_SHARING = booleanPreferencesKey("location_sharing")
        // Whether the user has accepted the background-location prominent disclosure.
        val BG_LOCATION_CONSENT = booleanPreferencesKey("bg_location_consent")
        // ── Emergency subsystem ──────────────────────────────────────────────
        // Opt-in power-button (screen-toggle) panic gesture.
        val PANIC_GESTURES = booleanPreferencesKey("panic_gestures")
        // Keep the Nearby mesh relay running to forward neighbours' SOS packets.
        val MESH_RELAY = booleanPreferencesKey("mesh_relay")
        // Whether the user has accepted the Nearby-mesh prominent disclosure.
        val MESH_CONSENT = booleanPreferencesKey("mesh_consent")
        // Always send a backup SMS even when the cloud write succeeds.
        val SMS_FAILBACK = booleanPreferencesKey("sms_failback")
        // ── Notification preferences (per-hazard) ────────────────────────────
        val ALERT_EARTHQUAKE = booleanPreferencesKey("alert_earthquake")
        val ALERT_TYPHOON = booleanPreferencesKey("alert_typhoon")
        val ALERT_FLOOD = booleanPreferencesKey("alert_flood")
        // Keep background polling running to stay up to date.
        val BACKGROUND_SYNC = booleanPreferencesKey("background_sync")
        // ── Personal information ─────────────────────────────────────────────
        val MOBILE_NUMBER = stringPreferencesKey("mobile_number")
        val HOME_ADDRESS = stringPreferencesKey("home_address")
        // Keys of the regions the user is actively monitoring.
        val MONITORED_REGIONS = stringSetPreferencesKey("monitored_regions")
        // ── Appearance ───────────────────────────────────────────────────────
        // Selected color theme (AppTheme.key) + light/dark/system mode (ThemeMode.key).
        val APP_THEME = stringPreferencesKey("app_theme")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    data class Snapshot(
        val minMagnitude: Double = 4.0,
        val pollingSeconds: Int = 30,
        val enableVibration: Boolean = true,
        val enableSound: Boolean = true,
        val enableFlashlight: Boolean = false,
        val familyAlerts: Boolean = false,
        val autoSms: Boolean = true,
        val autoCall: Boolean = false,
        val locationSharing: Boolean = false,
        val bgLocationConsent: Boolean = false,
        val panicGestures: Boolean = false,
        val meshRelay: Boolean = false,
        val meshConsent: Boolean = false,
        val smsFailback: Boolean = true,
        val alertEarthquake: Boolean = true,
        val alertTyphoon: Boolean = true,
        val alertFlood: Boolean = false,
        val backgroundSync: Boolean = true,
        val mobileNumber: String = "",
        val homeAddress: String = "",
        val monitoredRegions: Set<String> = setOf("ncr", "calabarzon"),
        // Appearance — stored as keys; resolved to AppTheme/ThemeMode by the UI layer.
        val appTheme: String = "deep_ocean",
        val themeMode: String = "system",
    )

    private fun Preferences.toSnapshot() = Snapshot(
        minMagnitude = this[Keys.MIN_MAGNITUDE] ?: 4.0,
        pollingSeconds = this[Keys.POLLING_SECONDS] ?: 30,
        enableVibration = this[Keys.ENABLE_VIBRATION] ?: true,
        enableSound = this[Keys.ENABLE_SOUND] ?: true,
        enableFlashlight = this[Keys.ENABLE_FLASHLIGHT] ?: false,
        familyAlerts = this[Keys.FAMILY_ALERTS] ?: false,
        autoSms = this[Keys.AUTO_SMS] ?: true,
        autoCall = this[Keys.AUTO_CALL] ?: false,
        locationSharing = this[Keys.LOCATION_SHARING] ?: false,
        bgLocationConsent = this[Keys.BG_LOCATION_CONSENT] ?: false,
        panicGestures = this[Keys.PANIC_GESTURES] ?: false,
        meshRelay = this[Keys.MESH_RELAY] ?: false,
        meshConsent = this[Keys.MESH_CONSENT] ?: false,
        smsFailback = this[Keys.SMS_FAILBACK] ?: true,
        alertEarthquake = this[Keys.ALERT_EARTHQUAKE] ?: true,
        alertTyphoon = this[Keys.ALERT_TYPHOON] ?: true,
        alertFlood = this[Keys.ALERT_FLOOD] ?: false,
        backgroundSync = this[Keys.BACKGROUND_SYNC] ?: true,
        mobileNumber = this[Keys.MOBILE_NUMBER] ?: "",
        homeAddress = this[Keys.HOME_ADDRESS] ?: "",
        monitoredRegions = this[Keys.MONITORED_REGIONS] ?: setOf("ncr", "calabarzon"),
        appTheme = this[Keys.APP_THEME] ?: "deep_ocean",
        themeMode = this[Keys.THEME_MODE] ?: "system",
    )

    val settings: Flow<Snapshot> = context.dataStore.data.map { it.toSnapshot() }

    /** Blocking-friendly read for the background service. */
    suspend fun current(): Snapshot = context.dataStore.data.first().toSnapshot()

    suspend fun setMinMagnitude(value: Double) = edit { it[Keys.MIN_MAGNITUDE] = value }
    suspend fun setPollingSeconds(value: Int) = edit { it[Keys.POLLING_SECONDS] = value }
    suspend fun setVibration(value: Boolean) = edit { it[Keys.ENABLE_VIBRATION] = value }
    suspend fun setSound(value: Boolean) = edit { it[Keys.ENABLE_SOUND] = value }
    suspend fun setFlashlight(value: Boolean) = edit { it[Keys.ENABLE_FLASHLIGHT] = value }
    suspend fun setFamilyAlerts(value: Boolean) = edit { it[Keys.FAMILY_ALERTS] = value }
    suspend fun setAutoSms(value: Boolean) = edit { it[Keys.AUTO_SMS] = value }
    suspend fun setAutoCall(value: Boolean) = edit { it[Keys.AUTO_CALL] = value }
    suspend fun setLocationSharing(value: Boolean) = edit { it[Keys.LOCATION_SHARING] = value }
    suspend fun setBgLocationConsent(value: Boolean) = edit { it[Keys.BG_LOCATION_CONSENT] = value }
    suspend fun setPanicGestures(value: Boolean) = edit { it[Keys.PANIC_GESTURES] = value }
    suspend fun setMeshRelay(value: Boolean) = edit { it[Keys.MESH_RELAY] = value }
    suspend fun setMeshConsent(value: Boolean) = edit { it[Keys.MESH_CONSENT] = value }
    suspend fun setSmsFailback(value: Boolean) = edit { it[Keys.SMS_FAILBACK] = value }
    suspend fun setAlertEarthquake(value: Boolean) = edit { it[Keys.ALERT_EARTHQUAKE] = value }
    suspend fun setAlertTyphoon(value: Boolean) = edit { it[Keys.ALERT_TYPHOON] = value }
    suspend fun setAlertFlood(value: Boolean) = edit { it[Keys.ALERT_FLOOD] = value }
    suspend fun setBackgroundSync(value: Boolean) = edit { it[Keys.BACKGROUND_SYNC] = value }
    suspend fun setMobileNumber(value: String) = edit { it[Keys.MOBILE_NUMBER] = value }
    suspend fun setHomeAddress(value: String) = edit { it[Keys.HOME_ADDRESS] = value }
    suspend fun setMonitoredRegions(value: Set<String>) = edit { it[Keys.MONITORED_REGIONS] = value }
    suspend fun setAppTheme(value: String) = edit { it[Keys.APP_THEME] = value }
    suspend fun setThemeMode(value: String) = edit { it[Keys.THEME_MODE] = value }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
