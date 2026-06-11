package com.gising.data.model

/** One hour of the short-term forecast (Open-Meteo `hourly`). */
data class HourPoint(
    val timeIso: String,      // e.g. "2026-07-03T15:00" (local to the location)
    val tempC: Double,
    val precipProb: Int,      // % chance of precipitation
    val weatherCode: Int,
    val windKph: Double,
)

/** One day of the multi-day forecast (Open-Meteo `daily`). */
data class DayPoint(
    val dateIso: String,      // e.g. "2026-07-03"
    val minC: Double,
    val maxC: Double,
    val precipMm: Double,
    val precipProb: Int,
    val weatherCode: Int,
    val windKph: Double,
)

/**
 * The full detail behind a weather card: rich current conditions plus a 24-hour and 7-day forecast.
 * Everything is live from Open-Meteo (free, no key). Optional fields are null when the API omits them.
 */
data class WeatherDetail(
    val current: LiveWeather,
    val apparentC: Double?,   // "feels like"
    val pressureHpa: Double?,
    val cloudCover: Int?,     // %
    val uvIndexMax: Double?,  // today's max UV
    val sunrise: String?,     // ISO local
    val sunset: String?,      // ISO local
    val hourly: List<HourPoint>,
    val daily: List<DayPoint>,
)
