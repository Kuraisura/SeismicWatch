package com.gising.data.supabase

import com.gising.data.model.CyclonePoint
import com.gising.data.model.CycloneType
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

/**
 * Row of the Supabase `cyclone_track` table (populated by the backend scraper).
 * Nullable everywhere so a partial row never breaks decoding; conversion happens in [toDomain].
 */
@Serializable
data class SupabaseCyclonePoint(
    val id: Long? = null,
    val cyclone_name: String? = null,
    val observed_at: String? = null,
    val cyclone_type: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radius: Int? = null,
    val status: String? = null,
    val scraped_at: String? = null,
)

/** Parse an ISO-8601 timestamptz (e.g. "2026-06-17T08:19:00+00:00") into epoch millis. */
private fun parseIsoMillis(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return try {
        OffsetDateTime.parse(value.trim()).toInstant().toEpochMilli()
    } catch (_: Exception) {
        null
    }
}

/**
 * Map a row to the domain [CyclonePoint], or null if it lacks the essentials
 * (name + coordinates + a usable observation time).
 */
fun SupabaseCyclonePoint.toDomain(): CyclonePoint? {
    val name = cyclone_name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val lat = latitude ?: return null
    val lon = longitude ?: return null
    val observedMs = parseIsoMillis(observed_at) ?: parseIsoMillis(scraped_at) ?: return null
    return CyclonePoint(
        cycloneName = name,
        latitude = lat,
        longitude = lon,
        type = CycloneType.fromCode(cyclone_type),
        observedAtMs = observedMs,
        radiusKm = radius ?: 0,
        status = status?.trim()?.lowercase() ?: "active",
    )
}
