package com.gising

import android.app.Application
import android.util.Base64
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.map.Mapper
import coil.request.Options
import com.gising.data.repository.SeismicDatabase
import com.google.firebase.FirebaseApp
import org.maplibre.android.MapLibre
import java.nio.ByteBuffer

class SeismicApplication : Application(), ImageLoaderFactory {

    val database by lazy { SeismicDatabase.getInstance(this) }

    /**
     * App-wide Coil loader that can render `data:image/...;base64,...` strings, so profile photos
     * stored as base64 in Firestore (no paid Firebase Storage) work through the existing
     * `AsyncImage(photoUrl)` call sites with no per-screen changes. Normal http(s) URLs fall through.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(Base64DataUriMapper()) }
            .crossfade(true)
            .build()

    override fun onCreate() {
        super.onCreate()

        // ── Firebase + App Check ─────────────────────────────────────────────
        // Firebase auto-initializes from google-services.json. App Check then ensures only
        // genuine, untampered instances of THIS app can reach Firestore/Auth. The provider
        // differs per build type (Play Integrity for release, debug provider for dev), so it
        // is installed from build-type-specific source sets — see src/{debug,release}.
        FirebaseApp.initializeApp(this)
        AppCheckInstaller.install()

        // Pre-warm the encrypted database
        database.earthquakeDao()

        // Pre-warm MapLibre's native library at startup so the FIRST map screen (Live Map / Cyclone
        // Tracker) opens without the cold-init stall that made the first tap feel laggy.
        runCatching { MapLibre.getInstance(this) }
    }
}

/**
 * Coil mapper: decodes a `data:image/...;base64,<payload>` string into a [ByteBuffer], which Coil's
 * built-in ByteBuffer fetcher then decodes. Returns null for anything that isn't a base64 data URI
 * (http URLs, file URIs, empty strings) so those continue down Coil's normal path.
 */
private class Base64DataUriMapper : Mapper<String, ByteBuffer> {
    override fun map(data: String, options: Options): ByteBuffer? {
        if (!data.startsWith("data:image")) return null
        val payload = data.substringAfter("base64,", "")
        if (payload.isEmpty()) return null
        return try {
            ByteBuffer.wrap(Base64.decode(payload, Base64.DEFAULT))
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
