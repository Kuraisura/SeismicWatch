package com.gising.data.gibs

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Resolves the list of GIBS animation frames for the satellite loop.
 *
 * Strategy (network-resilient, bounded):
 *  1. Best-effort: discover the chosen layer's TileMatrixSet/format/latest-time from the WMTS
 *     GetCapabilities document (parsed incrementally, aborting once the layer is found; cached).
 *  2. If discovery fails: use the hardcoded [GibsImageryLayer.HIMAWARI_B13] descriptor with a
 *     computed latest time (now − latency, floored to the cadence).
 *  3. Last resort: daily [GibsImageryLayer.MODIS_FALLBACK] frames, so the loop always has imagery.
 */
class GibsTimelineRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Cached discovery result for the session so we don't refetch the large capabilities doc. */
    @Volatile private var cached: Discovered? = null

    private data class Discovered(
        val layer: GibsImageryLayer,
        val latestMs: Long,
        val cadenceMinutes: Long,
    )

    /** The newest `count` frames (oldest → newest). Never throws; always returns playable frames. */
    suspend fun loadSatelliteFrames(count: Int = 12): List<GibsFrame> = withContext(Dispatchers.IO) {
        val n = count.coerceIn(1, 12)

        // 1 + 2: Himawari IR via discovery, else computed timestamps.
        val discovered = cached ?: runCatching { discover(GibsImageryLayer.HIMAWARI_B13) }
            .getOrNull()
            ?.also { cached = it }

        if (discovered != null) {
            return@withContext buildSubDailyFrames(discovered.layer, discovered.latestMs, discovered.cadenceMinutes, n)
        }

        // Computed Himawari fallback (no network needed for the timestamps themselves).
        val layer = GibsImageryLayer.HIMAWARI_B13
        val cadence = 10L
        val latest = floorTo(System.currentTimeMillis() - 90 * 60_000L, cadence)
        val frames = buildSubDailyFrames(layer, latest, cadence, n)
        if (frames.isNotEmpty()) return@withContext frames

        // 3: daily MODIS true-color.
        buildDailyFrames(GibsImageryLayer.MODIS_FALLBACK, n)
    }

    private fun buildSubDailyFrames(layer: GibsImageryLayer, latestMs: Long, cadenceMin: Long, n: Int): List<GibsFrame> {
        val stepMs = cadenceMin.coerceAtLeast(1) * 60_000L
        // Oldest → newest so the scrubber moves left→right through time.
        return (n - 1 downTo 0).map { back ->
            val t = latestMs - back * stepMs
            GibsFrame(timeMs = t, urlTemplate = layer.tileUrl(layer.timeKey(t)), maxNativeZoom = layer.maxNativeZoom)
        }
    }

    private fun buildDailyFrames(layer: GibsImageryLayer, n: Int): List<GibsFrame> {
        val dayMs = 24 * 60 * 60_000L
        val latest = System.currentTimeMillis() - dayMs // yesterday UTC (today's mosaic incomplete)
        return (n - 1 downTo 0).map { back ->
            val t = latest - back * dayMs
            GibsFrame(timeMs = t, urlTemplate = layer.tileUrl(layer.timeKey(t)), maxNativeZoom = layer.maxNativeZoom)
        }
    }

    private fun floorTo(epochMs: Long, cadenceMin: Long): Long {
        val stepMs = cadenceMin * 60_000L
        return (epochMs / stepMs) * stepMs
    }

    // ── GetCapabilities discovery ────────────────────────────────────────────────
    private val capabilitiesUrl =
        "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/1.0.0/WMTSCapabilities.xml"

    /** Stream-parse the capabilities doc for [target]'s time dimension + TileMatrixSet/format. */
    private fun discover(target: GibsImageryLayer): Discovered? {
        val request = Request.Builder()
            .url(capabilitiesUrl)
            .header("User-Agent", "SeismicWatch-PH/1.0 (Philippine cyclone tracker)")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            return body.byteStream().use { parseCapabilities(it, target.id) }
        }
    }

    private fun parseCapabilities(input: InputStream, targetId: String): Discovered? {
        val parser = Xml.newPullParser()
        parser.setInput(input, "UTF-8")

        var inLayer = false
        var layerId: String? = null
        var format: String? = null
        var tms: String? = null
        var timeDefault: String? = null
        var timeExtent: String? = null
        var seenFirstIdentifier = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name.localName()) {
                    "Layer" -> {
                        inLayer = true
                        layerId = null; format = null; tms = null
                        timeDefault = null; timeExtent = null; seenFirstIdentifier = false
                    }
                    "Identifier" -> if (inLayer && !seenFirstIdentifier) {
                        layerId = parser.nextText().trim()
                        seenFirstIdentifier = true
                    }
                    "Format" -> if (inLayer && format == null) format = parser.nextText().trim()
                    "TileMatrixSet" -> if (inLayer && tms == null) tms = parser.nextText().trim()
                    "Default" -> if (inLayer) timeDefault = parser.nextText().trim()
                    "Value" -> if (inLayer && timeExtent == null) timeExtent = parser.nextText().trim()
                }
                XmlPullParser.END_TAG -> if (parser.name.localName() == "Layer") {
                    if (layerId == targetId) {
                        return buildDiscovered(targetId, format, tms, timeDefault, timeExtent)
                    }
                    inLayer = false
                }
            }
            event = parser.next()
        }
        return null
    }

    private fun buildDiscovered(
        id: String,
        format: String?,
        tms: String?,
        timeDefault: String?,
        timeExtent: String?,
    ): Discovered? {
        val matrixSet = tms ?: return null
        val ext = when {
            format?.contains("png", true) == true -> "png"
            format?.contains("jpeg", true) == true || format?.contains("jpg", true) == true -> "jpg"
            else -> "png"
        }
        // Latest available time = Dimension Default (fall back to the extent's end token).
        val latestIso = timeDefault?.takeIf { it.isNotBlank() }
            ?: timeExtent?.substringBefore("/")?.takeIf { timeExtent.contains("/") }
            ?: return null
        val latestMs = runCatching { Instant.parse(latestIso).toEpochMilli() }.getOrNull() ?: return null

        // Cadence from the extent period (e.g. ".../PT10M"); default 10 minutes.
        val cadence = timeExtent?.substringAfterLast("/")
            ?.let { runCatching { Duration.parse(it).toMinutes() }.getOrNull() }
            ?.takeIf { it > 0 } ?: 10L

        val nativeZoom = matrixSet.substringAfterLast("Level").toFloatOrNull() ?: 6f
        val layer = GibsImageryLayer(
            id = id,
            tileMatrixSet = matrixSet,
            ext = ext,
            maxNativeZoom = nativeZoom,
            subDaily = true,
        )
        return Discovered(layer = layer, latestMs = latestMs, cadenceMinutes = cadence)
    }

    /** Local tag name without any namespace prefix (namespace processing is off). */
    private fun String.localName(): String = substringAfterLast(':')
}
