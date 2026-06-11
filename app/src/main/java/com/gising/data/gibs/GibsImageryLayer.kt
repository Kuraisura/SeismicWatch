package com.gising.data.gibs

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Describes a time-enabled NASA GIBS imagery layer servable as EPSG:3857 WMTS REST tiles
 * (no API key). Tiles overlay directly on the MapLibre mercator map.
 *
 * @param maxNativeZoom highest zoom the layer's TileMatrixSet provides (cap the map there).
 * @param subDaily true → frames are timestamped to the second (ISO-Z), false → daily date.
 */
data class GibsImageryLayer(
    val id: String,
    val tileMatrixSet: String,
    val ext: String,
    val maxNativeZoom: Float,
    val subDaily: Boolean,
    val tileSize: Int = 256,
) {
    /** WMTS REST tile template for a given {TIME}. z/y/x order (GIBS row/col = y/x). */
    fun tileUrl(time: String): String =
        "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/$id/default/$time/$tileMatrixSet/{z}/{y}/{x}.$ext"

    /** Format an instant into this layer's {TIME} token. */
    fun timeKey(epochMs: Long): String {
        val instant = Instant.ofEpochMilli(epochMs)
        return if (subDaily) SUBDAILY_FMT.format(instant) else DAILY_FMT.format(instant)
    }

    companion object {
        /** Asia-Pacific geostationary IR — 24/7, ~10-min frames; ideal for cyclones. */
        val HIMAWARI_B13 = GibsImageryLayer(
            id = "Himawari_AHI_Band13_Clean_Infrared",
            // Confirmed/overridden at runtime via GetCapabilities (GibsTimelineRepository).
            tileMatrixSet = "GoogleMapsCompatible_Level6",
            ext = "png",
            maxNativeZoom = 6f,
            subDaily = true,
        )

        /** Guaranteed daily true-color fallback when no geostationary frames are available. */
        val MODIS_FALLBACK = GibsImageryLayer(
            id = "MODIS_Terra_CorrectedReflectance_TrueColor",
            tileMatrixSet = "GoogleMapsCompatible_Level9",
            ext = "jpg",
            maxNativeZoom = 9f,
            subDaily = false,
        )

        private val SUBDAILY_FMT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)
        private val DAILY_FMT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)
    }
}

/** One animation frame: its observation time (epoch ms, UTC) + the resolved tile-URL template. */
data class GibsFrame(
    val timeMs: Long,
    val urlTemplate: String,
    /** Native max zoom of the source layer, so the canvas can cap tile requests. */
    val maxNativeZoom: Float,
)
