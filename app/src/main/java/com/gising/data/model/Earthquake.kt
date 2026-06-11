package com.gising.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Core earthquake event from USGS / PHIVOLCS API
 */
@Entity(tableName = "earthquakes")
data class Earthquake(
    @PrimaryKey val id: String,
    val magnitude: Double,
    val place: String,
    val timeMs: Long,                  // epoch millis
    val updatedMs: Long,
    val latitude: Double,
    val longitude: Double,
    val depth: Double,                 // km
    val felt: Int?,
    val tsunami: Int,
    val status: String,
    val type: String = "earthquake",
    val isAlerted: Boolean = false,
    val savedAt: Long = System.currentTimeMillis()
)

/** Severity tier derived from magnitude */
enum class MagnitudeTier(
    val label: String,
    val range: ClosedFloatingPointRange<Double>
) {
    MICRO("Micro", 0.0..1.99),
    MINOR("Minor", 2.0..3.99),
    LIGHT("Light", 4.0..4.99),
    MODERATE("Moderate", 5.0..5.99),
    STRONG("Strong", 6.0..6.99),
    MAJOR("Major", 7.0..7.99),
    GREAT("Great", 8.0..12.0);

    companion object {
        fun from(magnitude: Double): MagnitudeTier =
            entries.firstOrNull { magnitude in it.range } ?: MICRO
    }
}

/** User alert settings stored in DataStore */
data class AlertSettings(
    val minimumMagnitude: Double = 4.0,
    val enableVibration: Boolean = true,
    val enableSound: Boolean = true,
    val enableFlashlight: Boolean = true,        // unique feature
    val enableBuddyPing: Boolean = false,        // unique: ping saved contacts
    val buddyPhones: List<String> = emptyList(),
    val monitoringRadiusKm: Int = 500,           // filter by distance
    val pollingIntervalSeconds: Int = 30,        // faster than default
    val theme: AppTheme = AppTheme.DARK
)

enum class AppTheme { LIGHT, DARK, SYSTEM }

/**
 * Predicted shaking intensity at user's location — our unique computed field
 * Based on magnitude + epicentral distance using attenuation model
 */
data class ShakingPrediction(
    val earthquake: Earthquake,
    val distanceKm: Double,
    val estimatedMMI: Double,      // Modified Mercalli Intensity 1–12
    val peakGroundAccel: Double,   // g units
    val arrivalEstimateSec: Int,   // seconds until S-wave arrives
    val safetyAction: SafetyAction
)

enum class SafetyAction(val instruction: String, val icon: String) {
    DROP_COVER_HOLD("Drop, Cover, Hold On", "🛡"),
    MOVE_TO_OPEN("Move away from buildings", "🏃"),
    EVACUATE_COAST("Evacuate coastal areas now", "🌊"),
    SHELTER_INDOORS("Stay indoors, away from windows", "🏠"),
    NO_ACTION("No action needed", "✅")
}
