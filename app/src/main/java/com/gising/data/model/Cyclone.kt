package com.gising.data.model

/**
 * Tropical-cyclone category as reported by PHIVOLCS/PAGASA, weakest → strongest.
 * Colors mirror the Windy-style cyclone palette used on the tracker map.
 */
enum class CycloneType(val code: String, val label: String, val colorInt: Int) {
    TD("TD", "Tropical Depression", 0xFF3BB2D0.toInt()),
    TS("TS", "Tropical Storm", 0xFF2ECC71.toInt()),
    STS("STS", "Severe Tropical Storm", 0xFFF1C40F.toInt()),
    TY("TY", "Typhoon", 0xFFE67E22.toInt()),
    STY("STY", "Super Typhoon", 0xFFE74C3C.toInt()),
    UNKNOWN("?", "Tropical Cyclone", 0xFFBBBBBB.toInt());

    companion object {
        fun fromCode(raw: String?): CycloneType =
            entries.firstOrNull { it.code.equals(raw?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

/** A single observed position along a cyclone's track. */
data class CyclonePoint(
    val cycloneName: String,
    val latitude: Double,
    val longitude: Double,
    val type: CycloneType,
    val observedAtMs: Long,
    val radiusKm: Int,
    /** 'active' or 'ended' as of the last scrape. */
    val status: String,
)

/**
 * A projected future position of the storm, extrapolated from its recent observed motion.
 * Not an official forecast — a straight-line dead-reckoning of the last leg's velocity, surfaced
 * as a dashed "where it's heading" path so the user gets a sense of direction over the coming days.
 */
data class CycloneForecastPoint(
    val latitude: Double,
    val longitude: Double,
    /** Estimated time of arrival (epoch millis) at this position. */
    val etaMs: Long,
    /** Days ahead of the latest observation (1..n). */
    val dayIndex: Int,
)

/**
 * One storm's full track — its points in chronological (observed_at ascending) order.
 * Built by [com.gising.data.supabase.CycloneRepository]; only the currently-active storm
 * is surfaced to the UI.
 */
data class CycloneTrack(
    val name: String,
    val points: List<CyclonePoint>,
) {
    /** Most recent observation (points are ascending). */
    val latest: CyclonePoint? get() = points.lastOrNull()

    /** Current category = the latest point's type. */
    val category: CycloneType get() = latest?.type ?: CycloneType.UNKNOWN

    /**
     * A [days]-day movement projection extrapolated from the storm's recent translation velocity.
     * Estimates deg/hour from the leg between the latest point and the most recent observation at
     * least 12h earlier (falling back to the previous point), then dead-reckons one point per day.
     * Returns an empty list when there isn't enough motion history to project from.
     */
    fun forecast(days: Int = 7): List<CycloneForecastPoint> {
        if (days <= 0 || points.size < 2) return emptyList()
        val last = points.last()

        // Reference point: the most recent observation between 12h and 72h before the latest, so a
        // single noisy 1-hour hop doesn't dominate the heading. Fall back to the prior point.
        val ref = points.dropLast(1).lastOrNull {
            val age = last.observedAtMs - it.observedAtMs
            age in (12L * 3_600_000)..(72L * 3_600_000)
        } ?: points[points.size - 2]

        val dtHours = (last.observedAtMs - ref.observedAtMs) / 3_600_000.0
        if (dtHours <= 0.0) return emptyList()

        val vLat = (last.latitude - ref.latitude) / dtHours   // degrees per hour
        val vLon = (last.longitude - ref.longitude) / dtHours

        // Ignore essentially-stationary noise (< ~0.01°/h ≈ 1 km/h).
        if (kotlin.math.hypot(vLat, vLon) < 0.01) return emptyList()

        val stepHours = 24.0
        return (1..days).map { d ->
            val h = d * stepHours
            CycloneForecastPoint(
                latitude = (last.latitude + vLat * h).coerceIn(-10.0, 60.0),
                longitude = (last.longitude + vLon * h).coerceIn(100.0, 180.0),
                etaMs = last.observedAtMs + (h * 3_600_000).toLong(),
                dayIndex = d,
            )
        }
    }
}
