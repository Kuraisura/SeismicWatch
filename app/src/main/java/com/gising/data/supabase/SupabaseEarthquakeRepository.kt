package com.gising.data.supabase

import com.gising.data.model.Earthquake
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/**
 * Reads the Supabase `earthquakes` table and exposes a live stream of newly inserted
 * rows for 24/7 alerting. Replaces the USGS [com.gising.data.repository.EarthquakeRepository]
 * as the app's data source.
 */
class SupabaseEarthquakeRepository {

    private val client = SupabaseModule.client

    /**
     * The most recent events as the app's domain [Earthquake] model, so callers
     * (MainViewModel, the monitor service) need no changes.
     *
     * Ordered by `id` DESCENDING: it's an identity column (always present, monotonic
     * with insert order), so it's a reliable recency proxy — unlike `date_time`, which
     * is unsortable formatted text.
     *
     * @param minMagnitude when non-null, filter server-side to rows with `magnitude >= it`.
     */
    suspend fun fetchLatestQuakes(
        limit: Long = 100,
        minMagnitude: Double? = null,
    ): List<Earthquake> = withContext(Dispatchers.IO) {
        // Decode + date-parse off the main thread so a large batch never blocks/janks the UI.
        client.postgrest["earthquakes"]
            .select {
                filter { minMagnitude?.let { gte("magnitude", it) } }
                order("id", Order.DESCENDING)
                limit(limit)
            }
            .decodeList<SupabaseEarthquake>()
            .map { it.toDomain() }
    }

    /**
     * Cold [Flow] of rows INSERTed into `earthquakes` after collection starts. Emits the
     * RAW [SupabaseEarthquake] (not the mapped domain model) so the caller can read the
     * `expecting_*` flags for the alert decision.
     *
     * New rows are inserted server-side by the PHIVOLCS Edge Function (pg_cron), and this
     * stream surfaces them the instant they land.
     *
     * The channel is joined in [onStart] (when collection begins) rather than eagerly —
     * postgresChangeFlow must be built before `subscribe()`, or it throws.
     */
    fun observeInserts(): Flow<SupabaseEarthquake> {
        val channel = client.channel("public:earthquakes")
        val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "earthquakes"
        }
        return inserts
            .onStart { channel.subscribe() }
            .map { it.decodeRecord<SupabaseEarthquake>() }
    }
}
