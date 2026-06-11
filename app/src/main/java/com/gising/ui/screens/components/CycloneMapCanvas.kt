package com.gising.ui.screens.components

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.gising.data.gibs.GibsFrame
import com.gising.data.model.CycloneTrack
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

// OpenFreeMap "dark" vector basemap (detailed Philippine land / coastlines / place labels) — the
// same free, no-key style used across the app. Replaces the old satellite-only basemap which showed
// clouds but almost no land, so users couldn't read the Philippines or the storm's position.
private const val MAP_STYLE = "https://tiles.openfreemap.org/styles/dark"
private val MAP_LABEL_FONT = arrayOf("Noto Sans Regular")

private const val SRC_PAR = "par-area"
private const val SRC_PAR_LABEL = "par-label"
private const val LAYER_PAR_FILL = "par-fill"
private const val LAYER_PAR_LINE = "par-line"
private const val LAYER_PAR_LABEL = "par-label-layer"
private const val SRC_TRACK_LINE = "cyclone-track-line"
private const val SRC_TRACK_PTS = "cyclone-track-pts"
private const val LAYER_TRACK_LINE = "cyclone-track-line-layer"
private const val LAYER_TRACK_PTS = "cyclone-track-pts-layer"
private const val SRC_FORECAST_LINE = "cyclone-forecast-line"
private const val SRC_FORECAST_PTS = "cyclone-forecast-pts"
private const val LAYER_FORECAST_LINE = "cyclone-forecast-line-layer"
private const val LAYER_FORECAST_PTS = "cyclone-forecast-pts-layer"
private const val LAYER_FORECAST_LABEL = "cyclone-forecast-label-layer"

// 7-day projection styling — a distinct amber dashed path, set apart from the white observed track.
private const val FORECAST_COLOR = "#F5C518"
private const val FORECAST_DAYS = 7

// Satellite cloud loop renders ABOVE the detailed map but semi-transparent, so land + coastline
// always read through the imagery (Windy-style clouds over a real map).
private const val CLOUD_OPACITY = 0.62f

private fun frameSrcId(i: Int) = "gibs-frame-src-$i"
private fun frameLayerId(i: Int) = "gibs-frame-layer-$i"

/**
 * Official PAGASA Philippine Area of Responsibility, as a closed ring (lon, lat). Drawn as a faint
 * fill + dashed boundary so the user can see where a cyclone is relative to the PAR.
 */
private val PAR_RING: List<Point> = listOf(
    Point.fromLngLat(120.0, 25.0),
    Point.fromLngLat(135.0, 25.0),
    Point.fromLngLat(135.0, 5.0),
    Point.fromLngLat(115.0, 5.0),
    Point.fromLngLat(115.0, 15.0),
    Point.fromLngLat(120.0, 21.0),
    Point.fromLngLat(120.0, 25.0),
)

/** Camera box a little wider than the PAR, so a storm tracking anywhere around the PAR stays in view. */
private val VIEW_BOUNDS: LatLngBounds = LatLngBounds.from(
    /* latNorth = */ 27.0, /* lonEast = */ 137.0, /* latSouth = */ 3.0, /* lonWest = */ 113.0,
)

private fun Int.toCssRgba(): String {
    val a = (this ushr 24) and 0xFF
    val r = (this ushr 16) and 0xFF
    val g = (this ushr 8) and 0xFF
    val b = this and 0xFF
    return "rgba($r, $g, $b, ${a / 255f})"
}

private fun CycloneTrack?.toLineString(): LineString =
    LineString.fromLngLats(
        this?.points?.map { Point.fromLngLat(it.longitude, it.latitude) } ?: emptyList(),
    )

private fun CycloneTrack?.toPointFeatures(): FeatureCollection {
    val pts = this?.points ?: emptyList()
    return FeatureCollection.fromFeatures(
        pts.mapIndexed { index, p ->
            val props = com.google.gson.JsonObject().apply {
                addProperty("color", p.type.colorInt.toCssRgba())
            }
            Feature.fromGeometry(Point.fromLngLat(p.longitude, p.latitude), props, index.toString())
        },
    )
}

/** Forecast path as a line that starts at the latest observed point and runs through the projection. */
private fun CycloneTrack?.toForecastLine(): LineString {
    val track = this ?: return LineString.fromLngLats(emptyList())
    val anchor = track.latest ?: return LineString.fromLngLats(emptyList())
    val fc = track.forecast(FORECAST_DAYS)
    if (fc.isEmpty()) return LineString.fromLngLats(emptyList())
    val pts = buildList {
        add(Point.fromLngLat(anchor.longitude, anchor.latitude))
        fc.forEach { add(Point.fromLngLat(it.longitude, it.latitude)) }
    }
    return LineString.fromLngLats(pts)
}

private fun CycloneTrack?.toForecastFeatures(): FeatureCollection {
    val fc = this?.forecast(FORECAST_DAYS) ?: emptyList()
    return FeatureCollection.fromFeatures(
        fc.map { p ->
            val props = com.google.gson.JsonObject().apply { addProperty("label", "+${p.dayIndex}d") }
            Feature.fromGeometry(Point.fromLngLat(p.longitude, p.latitude), props)
        },
    )
}

private fun framesSignature(frames: List<GibsFrame>): String =
    if (frames.isEmpty()) "" else "${frames.size}:${frames.first().timeMs}:${frames.last().timeMs}"

/**
 * MapLibre canvas for the Cyclone Tracker. A detailed OpenFreeMap dark basemap with the PAR boundary,
 * the cyclone [track] (dashed line + category-colored points), and an OPTIONAL NASA-GIBS satellite
 * **time-loop** overlaid translucently on top (one raster layer per [frames] entry, all at opacity 0
 * except [currentFrame]). Handles the MapView lifecycle + low-memory like [MapLibreCanvas].
 */
@Composable
fun CycloneMapCanvas(
    track: CycloneTrack?,
    frames: List<GibsFrame>,
    currentFrame: Int,
    modifier: Modifier = Modifier,
    onPointClick: (Int) -> Unit = {},
    onMapReady: (MapLibreMap) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context)
    }
    val styleHolder = remember { arrayOfNulls<Style>(1) }
    val latestTrack = remember { arrayOfNulls<CycloneTrack>(1) }
    val latestFrames = remember { arrayOfNulls<List<GibsFrame>>(1) }
    val addedFrameCount = remember { intArrayOf(0) }
    val addedFramesSig = remember { arrayOfNulls<String>(1) }
    val shownFrame = remember { intArrayOf(-1) }
    val currentFrameHolder = remember { intArrayOf(0) }
    val animatedFor = remember { arrayOfNulls<String>(1) }

    latestTrack[0] = track
    latestFrames[0] = frames
    currentFrameHolder[0] = currentFrame

    fun rebuildFrameLayers(style: Style, fr: List<GibsFrame>) {
        // Drop previous frame layers/sources.
        for (i in 0 until addedFrameCount[0]) {
            style.getLayer(frameLayerId(i))?.let { style.removeLayer(it) }
            style.getSource(frameSrcId(i))?.let { style.removeSource(it) }
        }
        // Add fresh frame layers, all transparent, just BELOW the PAR overlay so the boundary + track
        // always stay on top of the clouds.
        fr.forEachIndexed { i, frame ->
            style.addSource(
                RasterSource(frameSrcId(i), TileSet("2.1.0", frame.urlTemplate).apply { maxZoom = frame.maxNativeZoom }, 256),
            )
            val layer = RasterLayer(frameLayerId(i), frameSrcId(i)).withProperties(PropertyFactory.rasterOpacity(0f))
            if (style.getLayer(LAYER_PAR_FILL) != null) style.addLayerBelow(layer, LAYER_PAR_FILL) else style.addLayer(layer)
        }
        addedFrameCount[0] = fr.size
        addedFramesSig[0] = framesSignature(fr)
        shownFrame[0] = -1
    }

    fun redraw() {
        val style = styleHolder[0] ?: return

        val fr = latestFrames[0] ?: emptyList()
        if (framesSignature(fr) != addedFramesSig[0]) rebuildFrameLayers(style, fr)

        // Show only the current frame, translucently so the map shows through.
        if (fr.isNotEmpty()) {
            val idx = currentFrameHolder[0].coerceIn(0, fr.lastIndex)
            if (idx != shownFrame[0]) {
                (style.getLayerAs<RasterLayer>(frameLayerId(idx)))?.setProperties(PropertyFactory.rasterOpacity(CLOUD_OPACITY))
                val prev = shownFrame[0]
                if (prev in fr.indices && prev != idx) {
                    (style.getLayerAs<RasterLayer>(frameLayerId(prev)))?.setProperties(PropertyFactory.rasterOpacity(0f))
                }
                shownFrame[0] = idx
            }
        }

        // Track overlay.
        val t = latestTrack[0]
        style.getSourceAs<GeoJsonSource>(SRC_TRACK_LINE)?.setGeoJson(t.toLineString())
        style.getSourceAs<GeoJsonSource>(SRC_TRACK_PTS)?.setGeoJson(t.toPointFeatures())
        style.getSourceAs<GeoJsonSource>(SRC_FORECAST_LINE)?.setGeoJson(t.toForecastLine())
        style.getSourceAs<GeoJsonSource>(SRC_FORECAST_PTS)?.setGeoJson(t.toForecastFeatures())

        // When a new storm loads, frame its whole path (so you see how it moves), else center on it.
        val points = t?.points
        if (t != null && !points.isNullOrEmpty() && animatedFor[0] != t.name) {
            animatedFor[0] = t.name
            val forecastPts = t.forecast(FORECAST_DAYS)
            mapView.getMapAsync { map ->
                runCatching {
                    if (points.size >= 2) {
                        val b = LatLngBounds.Builder()
                        points.forEach { b.include(LatLng(it.latitude, it.longitude)) }
                        // Include the projection so the user sees where the storm is heading.
                        forecastPts.forEach { b.include(LatLng(it.latitude, it.longitude)) }
                        map.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 140))
                    } else {
                        val p = points.first()
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.latitude, p.longitude), 6.0))
                    }
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        mapView.onCreate(null)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        // Forward system memory pressure to the MapView (frees tile caches).
        val memoryCallback = object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) {}
            override fun onLowMemory() { mapView.onLowMemory() }
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) mapView.onLowMemory()
            }
        }
        context.registerComponentCallbacks(memoryCallback)

        mapView.getMapAsync { map ->
            map.cameraPosition = CameraPosition.Builder().target(LatLng(13.5, 123.5)).zoom(4.3).build()
            map.setMinZoomPreference(3.5)
            map.setMaxZoomPreference(9.0) // satellite frames over-zoom (upscale) past their native zoom
            map.setLatLngBoundsForCameraTarget(VIEW_BOUNDS) // keep the view around the PAR
            map.uiSettings.isRotateGesturesEnabled = false
            map.uiSettings.isTiltGesturesEnabled = false
            // Minimal, branded look: drop the MapLibre logo, the "ⓘ" attribution button and compass.
            map.uiSettings.isLogoEnabled = false
            map.uiSettings.isAttributionEnabled = false
            map.uiSettings.isCompassEnabled = false

            map.setStyle(Style.Builder().fromUri(MAP_STYLE)) { style ->
                styleHolder[0] = style

                // ── PAR boundary: faint fill + dashed line + label ───────────────────
                style.addSource(GeoJsonSource(SRC_PAR, Polygon.fromLngLats(listOf(PAR_RING))))
                style.addLayer(
                    FillLayer(LAYER_PAR_FILL, SRC_PAR).withProperties(
                        PropertyFactory.fillColor("#FF6A00"),
                        PropertyFactory.fillOpacity(0.05f),
                    ),
                )
                style.addLayer(
                    LineLayer(LAYER_PAR_LINE, SRC_PAR).withProperties(
                        PropertyFactory.lineColor("#FF6A00"),
                        PropertyFactory.lineWidth(1.6f),
                        PropertyFactory.lineOpacity(0.65f),
                        PropertyFactory.lineDasharray(arrayOf(3f, 2f)),
                    ),
                )
                style.addSource(
                    GeoJsonSource(
                        SRC_PAR_LABEL,
                        Feature.fromGeometry(Point.fromLngLat(132.5, 23.5)),
                    ),
                )
                style.addLayer(
                    SymbolLayer(LAYER_PAR_LABEL, SRC_PAR_LABEL).withProperties(
                        PropertyFactory.textField("PAR"),
                        PropertyFactory.textFont(MAP_LABEL_FONT),
                        PropertyFactory.textSize(11f),
                        PropertyFactory.textColor("#FF6A00"),
                        PropertyFactory.textHaloColor("#0A0608"),
                        PropertyFactory.textHaloWidth(1.4f),
                        PropertyFactory.textLetterSpacing(0.2f),
                    ),
                )

                // ── Forecast: 7-day projection (amber dashed path + hollow daily points) ──
                // Added before the observed track so the white track + colored points sit on top.
                style.addSource(GeoJsonSource(SRC_FORECAST_LINE, (latestTrack[0]).toForecastLine()))
                style.addLayer(
                    LineLayer(LAYER_FORECAST_LINE, SRC_FORECAST_LINE).withProperties(
                        PropertyFactory.lineColor(FORECAST_COLOR),
                        PropertyFactory.lineWidth(2f),
                        PropertyFactory.lineOpacity(0.85f),
                        PropertyFactory.lineDasharray(arrayOf(1.5f, 2.5f)),
                    ),
                )
                style.addSource(GeoJsonSource(SRC_FORECAST_PTS, (latestTrack[0]).toForecastFeatures()))
                style.addLayer(
                    CircleLayer(LAYER_FORECAST_PTS, SRC_FORECAST_PTS).withProperties(
                        PropertyFactory.circleRadius(4f),
                        PropertyFactory.circleColor("#0A0608"),
                        PropertyFactory.circleStrokeColor(FORECAST_COLOR),
                        PropertyFactory.circleStrokeWidth(1.6f),
                    ),
                )
                style.addLayer(
                    SymbolLayer(LAYER_FORECAST_LABEL, SRC_FORECAST_PTS).withProperties(
                        PropertyFactory.textField(Expression.get("label")),
                        PropertyFactory.textFont(MAP_LABEL_FONT),
                        PropertyFactory.textSize(9.5f),
                        PropertyFactory.textColor(FORECAST_COLOR),
                        PropertyFactory.textHaloColor("#0A0608"),
                        PropertyFactory.textHaloWidth(1.2f),
                        PropertyFactory.textOffset(arrayOf(0f, -1.2f)),
                        PropertyFactory.textAllowOverlap(true),
                    ),
                )

                // ── Track: dashed path + category-colored points (always on top) ─────
                style.addSource(GeoJsonSource(SRC_TRACK_LINE, (latestTrack[0]).toLineString()))
                style.addLayer(
                    LineLayer(LAYER_TRACK_LINE, SRC_TRACK_LINE).withProperties(
                        PropertyFactory.lineColor("#FFFFFF"),
                        PropertyFactory.lineWidth(2.5f),
                        PropertyFactory.lineDasharray(arrayOf(2f, 2f)),
                    ),
                )
                style.addSource(GeoJsonSource(SRC_TRACK_PTS, (latestTrack[0]).toPointFeatures()))
                style.addLayer(
                    CircleLayer(LAYER_TRACK_PTS, SRC_TRACK_PTS).withProperties(
                        PropertyFactory.circleRadius(6f),
                        PropertyFactory.circleColor(Expression.get("color")),
                        PropertyFactory.circleStrokeColor("#FFFFFF"),
                        PropertyFactory.circleStrokeWidth(1.5f),
                    ),
                )

                redraw()
            }

            map.addOnMapClickListener { latLng ->
                val screen = map.projection.toScreenLocation(latLng)
                val id = map.queryRenderedFeatures(screen, LAYER_TRACK_PTS).firstOrNull()?.id()
                val index = id?.toIntOrNull()
                if (index != null) { onPointClick(index); true } else false
            }

            onMapReady(map)
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            context.unregisterComponentCallbacks(memoryCallback)
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { redraw() },
    )
}
