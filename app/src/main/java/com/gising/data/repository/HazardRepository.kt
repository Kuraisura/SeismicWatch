package com.gising.data.repository

import com.gising.data.model.AirQualityReading
import com.gising.data.model.HeatIndexReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Fetches live environmental-hazard readings (air quality, heat index) from Open-Meteo.
 * Both endpoints are free and require NO API key — consistent with the app's no-key map.
 */
class HazardRepository {

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
                    .header("User-Agent", "SeismicWatch-PH/1.0 (Philippine hazard alerts)")
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

    /** US AQI + PM2.5 at the given location, or null if the feed can't be reached. */
    suspend fun fetchAirQuality(lat: Double, lon: Double): AirQualityReading? {
        val url = "https://air-quality-api.open-meteo.com/v1/air-quality" +
            "?latitude=$lat&longitude=$lon&current=us_aqi,pm2_5"
        val body = getBody(url) ?: return null
        return try {
            val current = JSONObject(body).getJSONObject("current")
            if (current.isNull("us_aqi")) return null
            AirQualityReading(
                usAqi = current.getDouble("us_aqi").roundToInt(),
                pm25 = current.optDouble("pm2_5", 0.0),
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Apparent ("feels-like") and actual temperature at the location, or null on failure. */
    suspend fun fetchHeatIndex(lat: Double, lon: Double): HeatIndexReading? {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lon&current=temperature_2m,apparent_temperature"
        val body = getBody(url) ?: return null
        return try {
            val current = JSONObject(body).getJSONObject("current")
            if (current.isNull("apparent_temperature")) return null
            HeatIndexReading(
                apparentC = current.getDouble("apparent_temperature"),
                actualC = current.optDouble("temperature_2m", 0.0),
            )
        } catch (_: Exception) {
            null
        }
    }
}
