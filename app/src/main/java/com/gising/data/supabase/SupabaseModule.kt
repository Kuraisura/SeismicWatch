package com.gising.data.supabase

import com.gising.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

/**
 * Single, app-wide Supabase client.
 *
 * URL + key come from [BuildConfig] (sourced from gitignored `local.properties`), never
 * hardcoded here. The key MUST be the **publishable** ("anon") key — it is designed to be
 * shipped in client apps. It only protects you if Row Level Security (RLS) is enabled with a
 * public-read-only policy on the tables; without RLS anyone with the APK could read/write.
 * The service-role key must NEVER be placed here.
 *
 * The data is populated server-side by Supabase Edge Functions on a pg_cron schedule
 * (PHIVOLCS earthquakes + PAGASA typhoons); this client only ever READS.
 *
 * It installs:
 *  - [Postgrest] for table queries (see [SupabaseEarthquakeRepository.fetchLatestQuakes])
 *  - [Realtime]  for the 24/7 INSERT change stream that drives live alerts
 */
object SupabaseModule {

    val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        install(Postgrest)
        install(Realtime)
    }
}
