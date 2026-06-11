package com.gising.emergency

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import com.gising.ui.theme.SeismicPalette
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gising.MainActivity
import com.gising.data.model.Earthquake
import com.gising.data.model.Typhoon
import com.gising.data.repository.TyphoonRepository
import com.gising.data.supabase.SupabaseEarthquakeRepository
import java.util.concurrent.TimeUnit

/**
 * "Nearest Threat" home-screen widget (Jetpack Glance): the latest earthquake (magnitude colour-coded,
 * place, time-ago) and any active typhoon, at a glance. Tapping it opens the app.
 *
 * Data is fetched on each render (and on each [HazardWidgetWorker] tick) from the same sources the app
 * uses — Supabase for quakes, GDACS for typhoons — so the widget needs no separate backend. Refresh is
 * driven by a ~15-minute WorkManager job (the OS minimum for periodic work); the in-app monitor service
 * remains the instant alert path.
 */
class HazardMonitorWidget : GlanceAppWidget() {

    private val quakes = SupabaseEarthquakeRepository()
    private val typhoons = TyphoonRepository()

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val palette = widgetPalette(context)
        val summary = runCatching { loadSummary() }.getOrNull()
        provideContent { Content(palette, summary) }
    }

    private suspend fun loadSummary(): Summary {
        val quake = runCatching { quakes.fetchLatestQuakes(limit = 1) }.getOrNull()?.firstOrNull()
        val typhoon = runCatching { typhoons.fetchActiveTyphoons() }.getOrNull()?.firstOrNull()
        return Summary(quake, typhoon)
    }

    @Composable
    private fun Content(p: SeismicPalette, summary: Summary?) {
        val context = LocalContext.current
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(p.card)
                .cornerRadius(22.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
                .padding(16.dp),
        ) {
            // ── Header: live dot + wordmark ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = GlanceModifier.size(7.dp).cornerRadius(4.dp).background(p.accent),
                ) {}
                Spacer(GlanceModifier.width(7.dp))
                Text(
                    "SEISMICWATCH",
                    style = TextStyle(color = ColorProvider(p.label), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                )
            }
            Spacer(GlanceModifier.height(12.dp))

            // ── Earthquake block ──
            val quake = summary?.quake
            if (quake != null) {
                val mag = magColor(quake.magnitude, p)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Magnitude chip on a tinted rounded plate.
                    Box(
                        modifier = GlanceModifier
                            .background(mag.copy(alpha = 0.16f))
                            .cornerRadius(14.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "M%.1f".format(quake.magnitude),
                            style = TextStyle(color = ColorProvider(mag), fontSize = 26.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                    Spacer(GlanceModifier.width(12.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            quake.place.ifBlank { "Earthquake" },
                            maxLines = 1,
                            style = TextStyle(color = ColorProvider(p.white), fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        )
                        Spacer(GlanceModifier.height(2.dp))
                        Text(
                            timeAgo(quake.timeMs),
                            style = TextStyle(color = ColorProvider(p.label), fontSize = 11.sp),
                        )
                    }
                }
            } else {
                Text(
                    "No recent earthquakes",
                    style = TextStyle(color = ColorProvider(p.label), fontSize = 13.sp),
                )
            }

            Spacer(GlanceModifier.height(12.dp))
            // Hairline divider.
            Box(modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(p.border)) {}
            Spacer(GlanceModifier.height(12.dp))

            // ── Typhoon row ──
            val typhoon = summary?.typhoon
            if (typhoon != null) {
                Text(
                    "🌀  ${typhoon.name}${typhoon.windKph?.let { " · ${it.toInt()} km/h" } ?: ""}",
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(p.high), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = GlanceModifier.size(7.dp).cornerRadius(4.dp).background(p.safe)) {}
                    Spacer(GlanceModifier.width(7.dp))
                    Text(
                        "No active typhoon",
                        style = TextStyle(color = ColorProvider(p.safe), fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    )
                }
            }
        }
    }

    private data class Summary(val quake: Earthquake?, val typhoon: Typhoon?)

    private companion object {
        fun magColor(mag: Double, p: SeismicPalette): Color = when {
            mag >= 6.0 -> p.critical
            mag >= 5.0 -> p.high
            mag >= 4.0 -> p.moderate
            mag >= 3.0 -> p.signal
            else -> p.safe
        }

        fun timeAgo(timeMs: Long): String {
            val mins = ((System.currentTimeMillis() - timeMs) / 60_000L).coerceAtLeast(0)
            return when {
                mins < 1 -> "just now"
                mins < 60 -> "${mins}m ago"
                mins < 1440 -> "${mins / 60}h ago"
                else -> "${mins / 1440}d ago"
            }
        }
    }
}

/** Re-renders the widget on a ~15-minute cadence (the OS minimum for periodic work). */
class HazardWidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { HazardMonitorWidget().updateAll(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "hazard_widget_refresh"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<HazardWidgetWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

class HazardMonitorReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HazardMonitorWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        HazardWidgetWorker.enqueue(context)
    }

    override fun onDisabled(context: Context) {
        HazardWidgetWorker.cancel(context)
        super.onDisabled(context)
    }
}
