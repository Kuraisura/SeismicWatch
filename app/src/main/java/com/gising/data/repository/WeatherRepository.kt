package com.gising.data.repository

import com.gising.data.model.DayPoint
import com.gising.data.model.HourPoint
import com.gising.data.model.LiveWeather
import com.gising.data.model.PagasaCyclone
import com.gising.data.model.WeatherDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Live weather for the home "weather power" card.
 *
 * Two sources, both best-effort:
 *  - Open-Meteo `forecast` current block → realtime wind/gust/rain/sky for the user's spot
 *    (free, no key). This always returns something, so the card is never empty.
 *  - PAGASA Severe Weather Bulletin HTML → the official Tropical Cyclone Wind Signal when a
 *    storm is active. PAGASA publishes no API, so this is a defensive scrape that returns null
 *    the moment anything looks off (no storm, markup change, unreachable).
 */
class WeatherRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        // Let the many parallel city lookups actually run concurrently (OkHttp caps at 5/host by
        // default, which would make ~80 city calls crawl). Open-Meteo tolerates short bursts fine.
        .dispatcher(Dispatcher().apply { maxRequests = 64; maxRequestsPerHost = 20 })
        .build()

    /** The last network failure reason (for surfacing in the UI when weather won't load). */
    @Volatile
    var lastError: String? = null
        private set

    private suspend fun getBody(url: String, attempts: Int = 2): String? = withContext(Dispatchers.IO) {
        repeat(attempts) { attempt ->
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "SeismicWatch-PH/1.0 (Philippine weather alerts)")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        lastError = null
                        return@withContext response.body?.string()
                    } else {
                        lastError = "HTTP ${response.code}"
                    }
                }
            } catch (e: Exception) {
                lastError = e.javaClass.simpleName + (e.message?.let { ": $it" }.orEmpty())
                if (attempt == attempts - 1) return@withContext null
            }
        }
        null
    }

    private val CURRENT_FIELDS =
        "weather_code,temperature_2m,relative_humidity_2m,wind_speed_10m,wind_gusts_10m,precipitation"

    /** Build a [LiveWeather] from an Open-Meteo `current` block. */
    private fun parseCurrent(current: JSONObject): LiveWeather = LiveWeather(
        windKph = current.optDouble("wind_speed_10m", 0.0),
        gustKph = current.optDouble("wind_gusts_10m", 0.0),
        precipMm = current.optDouble("precipitation", 0.0),
        weatherCode = current.optInt("weather_code", -1),
        tempC = if (current.has("temperature_2m")) current.optDouble("temperature_2m") else null,
        humidity = if (current.has("relative_humidity_2m")) current.optInt("relative_humidity_2m") else null,
    )

    /** Current wind/gust/rain/sky at the location (Open-Meteo, km/h), or null if unreachable. */
    suspend fun fetchLiveWeather(lat: Double, lon: Double): LiveWeather? {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lon" +
            "&current=$CURRENT_FIELDS" +
            "&wind_speed_unit=kmh"
        val body = getBody(url) ?: return null
        return try {
            parseCurrent(JSONObject(body).getJSONObject("current"))
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Rich detail for a single location: enriched current conditions + a 24-hour and 7-day forecast,
     * all live from Open-Meteo (free, no key). Returns null only if the location is unreachable.
     */
    suspend fun fetchWeatherDetail(lat: Double, lon: Double): WeatherDetail? {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lon" +
            "&current=$CURRENT_FIELDS,apparent_temperature,surface_pressure,cloud_cover" +
            "&hourly=temperature_2m,precipitation_probability,weather_code,wind_speed_10m" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max,wind_speed_10m_max,sunrise,sunset,uv_index_max" +
            "&forecast_days=7&forecast_hours=24&timezone=auto&wind_speed_unit=kmh"
        val body = getBody(url) ?: return null
        return try {
            val root = JSONObject(body)
            val current = root.getJSONObject("current")

            val hourly = root.optJSONObject("hourly")?.let { h ->
                val times = h.optJSONArray("time")
                val temps = h.optJSONArray("temperature_2m")
                val probs = h.optJSONArray("precipitation_probability")
                val codes = h.optJSONArray("weather_code")
                val winds = h.optJSONArray("wind_speed_10m")
                val n = times?.length() ?: 0
                (0 until n).map { i ->
                    HourPoint(
                        timeIso = times!!.optString(i),
                        tempC = temps?.optDouble(i, 0.0) ?: 0.0,
                        precipProb = probs?.optInt(i, 0) ?: 0,
                        weatherCode = codes?.optInt(i, -1) ?: -1,
                        windKph = winds?.optDouble(i, 0.0) ?: 0.0,
                    )
                }
            }.orEmpty()

            val dailyObj = root.optJSONObject("daily")
            val daily = dailyObj?.let { d ->
                val dates = d.optJSONArray("time")
                val mins = d.optJSONArray("temperature_2m_min")
                val maxs = d.optJSONArray("temperature_2m_max")
                val sums = d.optJSONArray("precipitation_sum")
                val probs = d.optJSONArray("precipitation_probability_max")
                val codes = d.optJSONArray("weather_code")
                val winds = d.optJSONArray("wind_speed_10m_max")
                val n = dates?.length() ?: 0
                (0 until n).map { i ->
                    DayPoint(
                        dateIso = dates!!.optString(i),
                        minC = mins?.optDouble(i, 0.0) ?: 0.0,
                        maxC = maxs?.optDouble(i, 0.0) ?: 0.0,
                        precipMm = sums?.optDouble(i, 0.0) ?: 0.0,
                        precipProb = probs?.optInt(i, 0) ?: 0,
                        weatherCode = codes?.optInt(i, -1) ?: -1,
                        windKph = winds?.optDouble(i, 0.0) ?: 0.0,
                    )
                }
            }.orEmpty()

            fun firstStr(key: String) = dailyObj?.optJSONArray(key)?.optString(0)?.takeIf { it.isNotBlank() }
            fun firstNum(key: String) = dailyObj?.optJSONArray(key)?.let { if (it.length() > 0 && !it.isNull(0)) it.optDouble(0) else null }

            WeatherDetail(
                current = parseCurrent(current),
                apparentC = if (current.has("apparent_temperature")) current.optDouble("apparent_temperature") else null,
                pressureHpa = if (current.has("surface_pressure")) current.optDouble("surface_pressure") else null,
                cloudCover = if (current.has("cloud_cover")) current.optInt("cloud_cover") else null,
                uvIndexMax = firstNum("uv_index_max"),
                sunrise = firstStr("sunrise"),
                sunset = firstStr("sunset"),
                hourly = hourly,
                daily = daily,
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Live weather for MANY locations, returned positionally aligned to [points] (null where a point
     * is unreachable, so the UI shows "Unavailable" for just that one, not the whole list).
     *
     * Uses the SAME proven single-location endpoint as [fetchLiveWeather], fired in parallel. This is
     * deliberately NOT the comma-separated multi-location endpoint: that one returns an error *object*
     * (not an array) whenever it dislikes a request, which silently turned every card "Unavailable".
     * OkHttp throttles the fan-out to the dispatcher's per-host limit, so it's fast and safe.
     */
    suspend fun fetchBulkWeather(points: List<Pair<Double, Double>>): List<LiveWeather?> {
        if (points.isEmpty()) return emptyList()
        return coroutineScope {
            points
                .map { (lat, lon) -> async { fetchLiveWeather(lat, lon) } }
                .awaitAll()
        }
    }

    private val PAGASA_BULLETIN =
        "https://www.pagasa.dost.gov.ph/tropical-cyclone/severe-weather-bulletin"

    // The bulletin headline reads e.g. "Severe Tropical Storm KRISTINE (TRAMI)".
    private val nameRegex = Regex(
        """(Tropical Depression|Tropical Storm|Severe Tropical Storm|Typhoon|Super Typhoon)\s+([A-ZÑ][A-ZÑ'\-]+)""",
    )
    private val signalRegex = Regex("""Signal\s*(?:No\.?|Number)?\s*#?\s*([1-5])""", RegexOption.IGNORE_CASE)
    private val windRegex = Regex("""(\d{2,3})\s*km/?h""", RegexOption.IGNORE_CASE)
    private val gustRegex = Regex("""gust(?:s|iness)?(?:\s+of)?(?:\s+up\s+to)?\s*(\d{2,3})\s*km/?h""", RegexOption.IGNORE_CASE)

    /**
     * Best-effort official cyclone signal from PAGASA's bulletin page. Returns null when no
     * cyclone is active or the page can't be parsed — never throws.
     */
    suspend fun fetchPagasaCyclone(): PagasaCyclone? {
        val html = getBody(PAGASA_BULLETIN) ?: return null
        return try {
            // When nothing is active the page says so — phrasing is "No Active Tropical Cyclone
            // within the Philippine Area of Responsibility" (note the "Active"). Match both the
            // current and older wordings; bail rather than risk mis-parsing.
            val quiet = Regex("""no\s+(?:active\s+)?tropical\s+cyclone""", RegexOption.IGNORE_CASE)
            if (quiet.containsMatchIn(html)) return null

            // Drop the "Archive" section before scanning — it lists *past* storms (e.g. their
            // TCB#1–#6 bulletin links) whose names would otherwise be misread as the active one.
            val live = html.split(Regex("""(?i)<[^>]*>\s*Archive""")).first()

            val text = live.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ")
            val nameMatch = nameRegex.find(text) ?: return null
            val category = nameMatch.groupValues[1].trim()
            val name = nameMatch.groupValues[2].trim().lowercase()
                .replaceFirstChar { it.uppercase() }

            val highestSignal = signalRegex.findAll(text)
                .mapNotNull { it.groupValues[1].toIntOrNull() }
                .maxOrNull()
            val maxWinds = windRegex.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val gust = gustRegex.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()

            PagasaCyclone(
                name = name,
                category = category,
                maxWindsKph = maxWinds,
                gustKph = gust,
                highestSignal = highestSignal,
            )
        } catch (_: Exception) {
            null
        }
    }
}
