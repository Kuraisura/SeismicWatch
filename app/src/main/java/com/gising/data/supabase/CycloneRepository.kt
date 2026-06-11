package com.gising.data.supabase

import com.gising.data.model.CycloneTrack
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order

/**
 * Reads the Supabase `cyclone_track` table and surfaces the currently-active storm's track.
 * Mirrors [SupabaseEarthquakeRepository]; uses the same shared [SupabaseModule.client].
 */
class CycloneRepository {

    private val client = SupabaseModule.client

    /**
     * The track of the currently-active tropical cyclone, points in chronological order,
     * or null when nothing is active (no `status='active'` rows → empty state).
     *
     * Filters `status='active'` server-side (so the whole, ever-growing table is never
     * downloaded) and orders by `observed_at` ascending. If more than one storm is active,
     * the one whose newest observation is most recent wins.
     */
    suspend fun fetchActiveTrack(): CycloneTrack? {
        val points = client.postgrest["cyclone_track"]
            .select {
                filter { eq("status", "active") }
                order("observed_at", Order.ASCENDING)
            }
            .decodeList<SupabaseCyclonePoint>()
            .mapNotNull { it.toDomain() }

        if (points.isEmpty()) return null

        // One storm = one cyclone_name. Each group keeps ascending order from the query.
        val activeStorm = points
            .groupBy { it.cycloneName }
            // If several are active, pick the one whose newest point is most recent.
            .maxByOrNull { (_, pts) -> pts.last().observedAtMs }
            ?: return null

        return CycloneTrack(name = activeStorm.key, points = activeStorm.value)
    }
}
