package com.gising.ui.screens.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** A single plottable point on the map: a magnitude/severity-colored circle with an optional label. */
data class MapPin(
    val id: String,
    val lat: Double,
    val lon: Double,
    /** ARGB color int for the circle fill/stroke. */
    val colorInt: Int,
    /** Circle radius in dp (screen-space, constant across zoom). */
    val radiusDp: Float,
    /** Optional text drawn under the circle (used for family pins). */
    val label: String? = null,
)

private const val SRC_PINS = "gising-pins"
private const val LAYER_GLOW = "gising-pin-glow"
private const val LAYER_CIRCLES = "gising-pin-circles"
private const val LAYER_LABELS = "gising-pin-labels"

// OpenFreeMap "dark" — free, no API key, no usage limits, MIT-licensed vector tiles built from
// OpenStreetMap (detailed streets) with a muted dark cartography that matches the tactical theme.
// The MapLibre logo + attribution UI are hidden in onMapReady; required OSM/OpenFreeMap credit is
// shown as a small overlay instead.
private const val MAP_STYLE = "https://tiles.openfreemap.org/styles/dark"

// Lightweight fallback for weak / no signal: a bundled raster-OSM style (assets/maplibre_style.json).
// Raster 256px PNG tiles are far cheaper to fetch than a full vector style + glyphs, cache well, and
// keep the same dark tactical background — so the map still comes up when the vector style times out.
private const val MAP_STYLE_FALLBACK_ASSET = "maplibre_style.json"

// A font shipped by the OpenFreeMap glyph server, used to render the pin labels (family names).
private val MAP_LABEL_FONT = arrayOf("Noto Sans Regular")

private fun Int.toCssRgba(): String {
    val a = (this ushr 24) and 0xFF
    val r = (this ushr 16) and 0xFF
    val g = (this ushr 8) and 0xFF
    val b = this and 0xFF
    return "rgba($r, $g, $b, ${a / 255f})"
}

private fun List<MapPin>.toFeatureCollection(): FeatureCollection =
    FeatureCollection.fromFeatures(
        map { pin ->
            val props = com.google.gson.JsonObject().apply {
                addProperty("color", pin.colorInt.toCssRgba())
                addProperty("stroke", (pin.colorInt or (0xFF shl 24)).toCssRgba())
                addProperty("radius", pin.radiusDp)
                addProperty("glowRadius", pin.radiusDp * 2.6f)
                addProperty("label", pin.label ?: "")
            }
            Feature.fromGeometry(Point.fromLngLat(pin.lon, pin.lat), props, pin.id)
        },
    )

/**
 * Reusable open-source (MapLibre + OpenStreetMap raster) map canvas. Renders [pins] as colored
 * circles (optionally labeled) and reports taps on them via [onPinClick]. Handles the MapView
 * lifecycle so callers stay declarative. Used by the Live Map and the family People map.
 */
@Composable
fun MapLibreCanvas(
    pins: List<MapPin>,
    initialLat: Double,
    initialLon: Double,
    initialZoom: Double,
    modifier: Modifier = Modifier,
    minZoom: Double = 2.0,
    maxZoom: Double = 16.0,
    bounds: LatLngBounds? = null,
    showLabels: Boolean = false,
    glow: Boolean = false,
    onPinClick: (String) -> Unit = {},
    onMapReady: (MapLibreMap) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // MapLibre must be initialized before any MapView is created.
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context)
    }
    val mapHolder = remember { arrayOfNulls<MapLibreMap>(1) }
    val styleHolder = remember { arrayOfNulls<Style>(1) }
    val latestPins = remember { arrayOfNulls<List<MapPin>>(1) }
    latestPins[0] = pins

    fun redraw() {
        val style = styleHolder[0] ?: return
        val data = latestPins[0] ?: emptyList()
        (style.getSourceAs<GeoJsonSource>(SRC_PINS))?.setGeoJson(data.toFeatureCollection())
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

        mapView.getMapAsync { map ->
            mapHolder[0] = map
            map.cameraPosition = CameraPosition.Builder()
                .target(LatLng(initialLat, initialLon))
                .zoom(initialZoom)
                .build()
            map.setMinZoomPreference(minZoom)
            map.setMaxZoomPreference(maxZoom)
            bounds?.let { map.setLatLngBoundsForCameraTarget(it) }
            map.uiSettings.isRotateGesturesEnabled = false
            map.uiSettings.isTiltGesturesEnabled = false
            // Minimal, branded look: drop the default MapLibre logo, the "ⓘ" attribution button and
            // the compass. (Attribution is re-added as a small custom overlay by the map screens.)
            map.uiSettings.isLogoEnabled = false
            map.uiSettings.isAttributionEnabled = false
            map.uiSettings.isCompassEnabled = false

            // Adds our GeoJSON pin source + circle/label layers on top of whichever base style loaded.
            fun applyPins(style: Style) {
                styleHolder[0] = style
                style.addSource(GeoJsonSource(SRC_PINS, (latestPins[0] ?: emptyList()).toFeatureCollection()))
                // Optional soft "heat-glow" halo beneath each pin (weather map look).
                if (glow) {
                    style.addLayer(
                        CircleLayer(LAYER_GLOW, SRC_PINS).withProperties(
                            PropertyFactory.circleRadius(Expression.get("glowRadius")),
                            PropertyFactory.circleColor(Expression.get("color")),
                            PropertyFactory.circleOpacity(0.28f),
                            PropertyFactory.circleBlur(0.85f),
                        ),
                    )
                }
                style.addLayer(
                    CircleLayer(LAYER_CIRCLES, SRC_PINS).withProperties(
                        PropertyFactory.circleRadius(Expression.get("radius")),
                        PropertyFactory.circleColor(Expression.get("color")),
                        PropertyFactory.circleStrokeColor(Expression.get("stroke")),
                        PropertyFactory.circleStrokeWidth(2f),
                    ),
                )
                if (showLabels) {
                    style.addLayer(
                        SymbolLayer(LAYER_LABELS, SRC_PINS).withProperties(
                            PropertyFactory.textField(Expression.get("label")),
                            PropertyFactory.textFont(MAP_LABEL_FONT),
                            PropertyFactory.textSize(11f),
                            PropertyFactory.textColor("#F7F2F4"),
                            PropertyFactory.textHaloColor("#0A0608"),
                            PropertyFactory.textHaloWidth(1.4f),
                            PropertyFactory.textOffset(arrayOf(0f, 1.4f)),
                            PropertyFactory.textAllowOverlap(true),
                        ),
                    )
                }
            }

            // Weak/no signal resilience: if the vector style fails to load (timeout, DNS, captive
            // portal), swap in the bundled raster-OSM fallback exactly once instead of a blank map.
            val triedFallback = booleanArrayOf(false)
            fun loadFallbackStyle() {
                if (triedFallback[0]) return
                triedFallback[0] = true
                val json = runCatching {
                    context.assets.open(MAP_STYLE_FALLBACK_ASSET).bufferedReader().use { it.readText() }
                }.getOrNull() ?: return
                map.setStyle(Style.Builder().fromJson(json)) { style -> applyPins(style) }
            }
            mapView.addOnDidFailLoadingMapListener { loadFallbackStyle() }

            map.setStyle(Style.Builder().fromUri(MAP_STYLE)) { style -> applyPins(style) }

            map.addOnMapClickListener { latLng ->
                val screen = map.projection.toScreenLocation(latLng)
                val hits = map.queryRenderedFeatures(screen, LAYER_CIRCLES)
                val id = hits.firstOrNull()?.id()
                if (id != null) { onPinClick(id); true } else false
            }

            onMapReady(map)
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
