package com.gising.data.model

/**
 * Live environmental-hazard readings shown on the home "Hazard Monitors" grid, sourced from
 * Open-Meteo (free, no API key — same no-key ethos as the MapLibre/OpenStreetMap map).
 */

/** US Air Quality Index reading for the user's location. */
data class AirQualityReading(
    val usAqi: Int,
    val pm25: Double,
) {
    val category: AqiCategory get() = AqiCategory.from(usAqi)
}

enum class AqiCategory(val label: String) {
    GOOD("Good"),
    MODERATE("Moderate"),
    UNHEALTHY_SENSITIVE("Unhealthy (sensitive)"),
    UNHEALTHY("Unhealthy"),
    VERY_UNHEALTHY("Very unhealthy"),
    HAZARDOUS("Hazardous");

    companion object {
        fun from(aqi: Int): AqiCategory = when {
            aqi <= 50 -> GOOD
            aqi <= 100 -> MODERATE
            aqi <= 150 -> UNHEALTHY_SENSITIVE
            aqi <= 200 -> UNHEALTHY
            aqi <= 300 -> VERY_UNHEALTHY
            else -> HAZARDOUS
        }
    }
}

/**
 * Heat-index reading. We use Open-Meteo's `apparent_temperature` (feels-like, which folds in
 * humidity and wind) as the heat index — the most accurate single value available without a key.
 */
data class HeatIndexReading(
    val apparentC: Double,
    val actualC: Double,
) {
    val category: HeatCategory get() = HeatCategory.from(apparentC)
}

enum class HeatCategory(val label: String) {
    NORMAL("Normal"),
    CAUTION("Caution"),
    EXTREME_CAUTION("Extreme caution"),
    DANGER("Danger"),
    EXTREME_DANGER("Extreme danger");

    companion object {
        // PAGASA / NWS heat-index thresholds in °C.
        fun from(apparentC: Double): HeatCategory = when {
            apparentC < 27 -> NORMAL
            apparentC < 33 -> CAUTION
            apparentC < 42 -> EXTREME_CAUTION
            apparentC < 52 -> DANGER
            else -> EXTREME_DANGER
        }
    }
}
