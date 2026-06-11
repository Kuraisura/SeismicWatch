package com.gising.data.supabase

import com.gising.data.model.Earthquake
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Row of the Supabase `earthquakes` table (populated server-side by the PHIVOLCS-scraping
 * Edge Function on a pg_cron schedule).
 *
 * Field types mirror the table's column types EXACTLY: numeric/boolean columns
 * (`double precision`, `integer`, `boolean`, `bigint`) come back as JSON numbers/booleans,
 * so decoding them into Kotlin String would throw. Only the genuine `text` columns
 * (`date_time`, `location`, `bulletin_link`, `scraped_at`) are [String]. Everything stays
 * nullable so a partial row never breaks decoding; defaulting happens in [toDomain].
 *
 * Named [SupabaseEarthquake] (not `Earthquake`) on purpose: the app's domain model
 * [com.gising.data.model.Earthquake] is a Room entity with a different shape.
 */
@Serializable
data class SupabaseEarthquake(
    val id: Long? = null,
    val date_time: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val depth_km: Int? = null,
    val magnitude: Double? = null,
    val location: String? = null,
    val bulletin_link: String? = null,
    val expecting_damage: Boolean? = null,
    val expecting_aftershocks: Boolean? = null,
    val scraped_at: String? = null,
)

fun SupabaseEarthquake.expectsDamage(): Boolean = expecting_damage == true
fun SupabaseEarthquake.expectsAftershocks(): Boolean = expecting_aftershocks == true

/**
 * PHIVOLCS bulletin time, e.g. "17 June 2026 - 04:16 PM" (sometimes with seconds / short month).
 *
 * These [DateTimeFormatter]s are immutable + thread-safe, so they're built ONCE here and reused for
 * every row — the old code created a fresh `SimpleDateFormat` per pattern per row (up to thousands of
 * allocations on a full fetch), which was the main reason the feed took ~30s to appear on old phones.
 */
private val PH_ZONE: ZoneId = ZoneId.of("Asia/Manila")
private val PH_DATE_FORMATTERS: List<DateTimeFormatter> = listOf(
    "d MMMM yyyy - hh:mm:ss a",
    "d MMMM yyyy - hh:mm a",
    "d MMM yyyy - hh:mm:ss a",
    "d MMM yyyy - hh:mm a",
).map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }

/**
 * Best-effort parse of the bulletin time into epoch millis. Tries the known PHIVOLCS
 * patterns first, falls back to the ISO `scraped_at` timestamp, then to "now".
 */
fun parsePhDateTime(dateTime: String?, scrapedAt: String?): Long {
    val raw = dateTime?.trim()
    if (!raw.isNullOrEmpty()) {
        for (fmt in PH_DATE_FORMATTERS) {
            try {
                return LocalDateTime.parse(raw, fmt).atZone(PH_ZONE).toInstant().toEpochMilli()
            } catch (_: Exception) { /* try next pattern */ }
        }
    }
    if (!scrapedAt.isNullOrBlank()) {
        try {
            return OffsetDateTime.parse(scrapedAt.trim()).toInstant().toEpochMilli()
        } catch (_: Exception) { /* fall through */ }
    }
    return System.currentTimeMillis()
}

/**
 * Map a Supabase row into the app's domain [Earthquake] so the entire UI + alert stack
 * keeps working unchanged. `expecting_*` flags are deliberately not carried here — the
 * realtime path reads them off the raw row before mapping.
 */
fun SupabaseEarthquake.toDomain(): Earthquake {
    val eventTime = parsePhDateTime(date_time, scraped_at)
    return Earthquake(
        // bulletin_link is `unique` → the most stable dedupe key; fall back to the row id.
        id = bulletin_link?.takeIf { it.isNotBlank() }
            ?: id?.toString()
            ?: "phivolcs-$eventTime",
        magnitude = magnitude ?: 0.0,
        place = location?.trim().orEmpty(),
        timeMs = eventTime,
        updatedMs = eventTime,
        latitude = latitude ?: 0.0,
        longitude = longitude ?: 0.0,
        depth = depth_km?.toDouble() ?: 0.0,
        felt = null,
        tsunami = 0,
        status = "PHIVOLCS",
        type = "earthquake",
    )
}
