package com.gising.data.repository

import com.gising.data.model.Typhoon
import com.gising.data.model.TyphoonAlert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Fetches active tropical cyclones from GDACS (Global Disaster Alert and Coordination System).
 * The EVENTS4APP feed is the same one GDACS' own mobile app uses — free, no API key, GeoJSON.
 * We keep only current TC events in/near the Western Pacific (Philippine area of responsibility).
 */
class TyphoonRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private suspend fun getBody(url: String, attempts: Int = 2): String? = withContext(Dispatchers.IO) {
        repeat(attempts) { attempt ->
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "SeismicWatch-PH/1.0 (Philippine typhoon alerts)")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) return@withContext response.body?.string()
                }
            } catch (e: Exception) {
                if (attempt == attempts - 1) return@withContext null
            }
        }
        null
    }

    private val GDACS_FEED =
        "https://www.gdacs.org/gdacsapi/api/events/geteventlist/EVENTS4APP"

    // Philippine Area of Responsibility (PAR) box — typhoons in/approaching the Philippines.
    private val PAR_MIN_LAT = 4.0
    private val PAR_MAX_LAT = 26.0
    private val PAR_MIN_LON = 114.0
    private val PAR_MAX_LON = 136.0

    private val windRegex = Regex("""(\d+(?:\.\d+)?)\s*km/?h""", RegexOption.IGNORE_CASE)

    /** Current Philippine-area tropical cyclones, newest first. Empty if the feed can't be reached. */
    suspend fun fetchActiveTyphoons(): List<Typhoon> {
        val body = getBody(GDACS_FEED) ?: return emptyList()
        return parse(body, currentOnly = true).sortedByDescending { it.fromMs }
    }

    /**
     * Recent (past ~90 days) Philippine-area tropical cyclones that are no longer active, newest
     * first. Drives the Reports "past typhoons" history. Empty if the feed can't be reached.
     */
    suspend fun fetchRecentTyphoons(): List<Typhoon> {
        val body = getBody(GDACS_FEED) ?: return emptyList()
        val cutoff = System.currentTimeMillis() - 90L * 24 * 3600_000
        return parse(body, currentOnly = false)
            .filter { !it.isCurrent && it.fromMs >= cutoff }
            .sortedByDescending { it.fromMs }
    }

    private fun parse(json: String, currentOnly: Boolean): List<Typhoon> {
        val out = mutableListOf<Typhoon>()
        try {
            val features = JSONObject(json).optJSONArray("features") ?: return emptyList()
            for (i in 0 until features.length()) {
                val feature = features.optJSONObject(i) ?: continue
                val props = feature.optJSONObject("properties") ?: continue

                if (!props.optString("eventtype").equals("TC", ignoreCase = true)) continue
                val current = isCurrent(props)
                if (currentOnly && !current) continue

                val coords = feature.optJSONObject("geometry")?.optJSONArray("coordinates")
                val lon = coords?.optDouble(0, Double.NaN) ?: Double.NaN
                val lat = coords?.optDouble(1, Double.NaN) ?: Double.NaN
                if (lat.isNaN() || lon.isNaN()) continue
                if (lat < PAR_MIN_LAT || lat > PAR_MAX_LAT || lon < PAR_MIN_LON || lon > PAR_MAX_LON) continue

                val name = props.optString("eventname").ifBlank { props.optString("name") }
                    .ifBlank { "Tropical Cyclone" }

                out.add(
                    Typhoon(
                        id = "TC-" + props.optString("eventid", name),
                        name = name.trim(),
                        latitude = lat,
                        longitude = lon,
                        windKph = extractWindKph(props),
                        alertLevel = TyphoonAlert.from(props.optString("alertlevel")),
                        fromMs = parseIso(props.optString("fromdate")),
                        toMs = parseIso(props.optString("todate")),
                        isCurrent = current,
                    )
                )
            }
        } catch (_: Exception) {}
        return out
    }

    /** GDACS reports `iscurrent` as the string "true"/"false" (occasionally a boolean). */
    private fun isCurrent(props: JSONObject): Boolean {
        val raw = props.opt("iscurrent")
        return when (raw) {
            is Boolean -> raw
            is String -> raw.equals("true", ignoreCase = true)
            else -> true // if the field is absent, don't discard the event
        }
    }

    /** Prefer the numeric severity (when it's a wind speed in km/h), else scrape the text. */
    private fun extractWindKph(props: JSONObject): Double? {
        val sev = props.optJSONObject("severitydata") ?: return null
        val unit = sev.optString("severityunit").lowercase()
        if (unit.contains("km")) {
            val v = sev.optDouble("severity", Double.NaN)
            if (!v.isNaN() && v > 0) return v
        }
        val text = sev.optString("severitytext")
        return windRegex.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    }

    private fun parseIso(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        return try {
            java.time.Instant.parse(raw).toEpochMilli()
        } catch (_: Exception) {
            try {
                // GDACS sometimes omits the zone; treat as UTC.
                java.time.LocalDateTime.parse(raw)
                    .toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
            } catch (_: Exception) {
                0L
            }
        }
    }
}
