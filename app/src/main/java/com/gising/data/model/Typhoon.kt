package com.gising.data.model

/**
 * An active tropical cyclone (typhoon / tropical storm) relevant to the Philippine area of
 * responsibility, sourced from GDACS (free, no API key — same no-key ethos as the rest of the app).
 */
data class Typhoon(
    val id: String,
    val name: String,            // storm name, e.g. "Mawar"
    val latitude: Double,
    val longitude: Double,
    val windKph: Double?,        // max sustained wind, when reported
    val alertLevel: TyphoonAlert,
    val fromMs: Long,
    val toMs: Long,
    /** Whether GDACS still flags this cyclone as active (false = historical/past event). */
    val isCurrent: Boolean = true,
) {
    /** PAGASA-style category derived from sustained wind; falls back to the GDACS alert level. */
    val category: TyphoonCategory
        get() = windKph?.let { TyphoonCategory.fromWindKph(it) }
            ?: when (alertLevel) {
                TyphoonAlert.RED -> TyphoonCategory.TYPHOON
                TyphoonAlert.ORANGE -> TyphoonCategory.SEVERE_TROPICAL_STORM
                else -> TyphoonCategory.TROPICAL_STORM
            }
}

/** GDACS alert level for the event. */
enum class TyphoonAlert(val label: String) {
    GREEN("Advisory"),
    ORANGE("Watch"),
    RED("Warning");

    companion object {
        fun from(raw: String?): TyphoonAlert = when (raw?.trim()?.lowercase()) {
            "red" -> RED
            "orange" -> ORANGE
            else -> GREEN
        }
    }
}

/** PAGASA tropical-cyclone categories, keyed to maximum sustained wind (km/h). */
enum class TyphoonCategory(val label: String) {
    TROPICAL_DEPRESSION("Tropical Depression"),
    TROPICAL_STORM("Tropical Storm"),
    SEVERE_TROPICAL_STORM("Severe Tropical Storm"),
    TYPHOON("Typhoon"),
    SUPER_TYPHOON("Super Typhoon");

    companion object {
        // PAGASA thresholds (10-min sustained wind, km/h).
        fun fromWindKph(kph: Double): TyphoonCategory = when {
            kph < 62 -> TROPICAL_DEPRESSION
            kph < 89 -> TROPICAL_STORM
            kph < 118 -> SEVERE_TROPICAL_STORM
            kph < 185 -> TYPHOON
            else -> SUPER_TYPHOON
        }
    }
}
