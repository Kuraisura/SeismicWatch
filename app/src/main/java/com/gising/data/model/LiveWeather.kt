package com.gising.data.model

/**
 * Live local weather "power" for the user's location, sourced from Open-Meteo (free, no key —
 * same no-key ethos as the rest of the app). Drives the home weather card: instead of only
 * surfacing a typhoon when one exists, this always shows the current strength of the weather
 * (wind, gusts, rain, sky condition).
 */
data class LiveWeather(
    val windKph: Double,
    val gustKph: Double,
    val precipMm: Double,
    val weatherCode: Int,
    /** Air temperature in °C (Open-Meteo `temperature_2m`); null when unavailable. */
    val tempC: Double? = null,
    /** Relative humidity in % (Open-Meteo `relative_humidity_2m`); null when unavailable. */
    val humidity: Int? = null,
) {
    /** Human-readable sky condition from the WMO weather code. */
    val condition: String get() = wmoLabel(weatherCode)

    /** Overall severity of the current weather, the stronger of wind- and storm-driven tiers. */
    val power: WeatherPower
        get() = WeatherPower.from(maxOf(gustKph, windKph), precipMm, weatherCode)
}

/** Strength tiers for everyday weather, keyed to wind speed / rain / thunderstorm codes. */
enum class WeatherPower(val label: String) {
    CALM("Calm"),
    BREEZY("Breezy"),
    WINDY("Windy"),
    BLUSTERY("Blustery"),
    STORMY("Stormy"),
    SEVERE("Severe");

    companion object {
        fun from(windKph: Double, precipMm: Double, code: Int): WeatherPower {
            // PAGASA tropical-cyclone wind thresholds reused as everyday wind tiers (km/h).
            val byWind = when {
                windKph >= 118 -> SEVERE
                windKph >= 89 -> STORMY
                windKph >= 62 -> BLUSTERY
                windKph >= 39 -> WINDY
                windKph >= 20 -> BREEZY
                else -> CALM
            }
            // Thunderstorms and heavy rain bump the tier regardless of wind.
            val byStorm = when {
                code in 95..99 -> STORMY        // thunderstorm (with/without hail)
                precipMm >= 7.6 -> STORMY        // violent rain rate (mm/h)
                precipMm >= 2.5 -> BLUSTERY      // heavy rain
                precipMm >= 0.5 -> WINDY         // moderate rain
                else -> CALM
            }
            // Enums are Comparable by ordinal, so maxOf picks the more severe tier.
            return maxOf(byWind, byStorm)
        }
    }
}

/** Maps a WMO weather-interpretation code to a short label (Open-Meteo `weather_code`). */
fun wmoLabel(code: Int): String = when (code) {
    0 -> "Clear sky"
    1, 2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    51, 53, 55 -> "Drizzle"
    56, 57 -> "Freezing drizzle"
    61, 63, 65 -> "Rain"
    66, 67 -> "Freezing rain"
    71, 73, 75, 77 -> "Snow"
    80, 81, 82 -> "Rain showers"
    85, 86 -> "Snow showers"
    95 -> "Thunderstorm"
    96, 99 -> "Thunderstorm w/ hail"
    else -> "—"
}

/**
 * Official PAGASA tropical-cyclone signal, best-effort scraped from the Severe Weather Bulletin.
 * Present only while a cyclone is active over the Philippines; otherwise null. Fragile by nature
 * (PAGASA publishes no API), so callers must treat every field as optional.
 */
data class PagasaCyclone(
    val name: String,
    val category: String?,
    val maxWindsKph: Int?,
    val gustKph: Int?,
    /** Highest Tropical Cyclone Wind Signal currently raised (1–5), if any. */
    val highestSignal: Int?,
)
