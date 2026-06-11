package com.gising.emergency

import org.json.JSONObject

/**
 * The wire format for an SOS bounced over the offline Nearby-Connections mesh. Kept tiny (a flat
 * JSON object) because payloads hop device-to-device over BLE/Wi-Fi where bandwidth is scarce.
 *
 * Multi-hop relay is hand-rolled: each forwarder decrements [ttl] and increments [hopCount].
 * A packet with `ttl == 0` is delivered locally but not re-forwarded, which (together with id
 * de-duplication in [NearbyMeshManager]) prevents broadcast storms.
 */
data class SosPacket(
    val id: String,
    val originUid: String,
    val lat: Double,
    val lon: Double,
    val timeMs: Long,
    val category: String,
    val ttl: Int,
    val hopCount: Int,
) {
    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("o", originUid)
        put("la", lat)
        put("lo", lon)
        put("t", timeMs)
        put("c", category)
        put("ttl", ttl)
        put("h", hopCount)
    }.toString()

    fun toBytes(): ByteArray = toJson().toByteArray(Charsets.UTF_8)

    /** A copy advanced one hop, or null if it must not be forwarded further. */
    fun forwarded(): SosPacket? =
        if (ttl <= 0) null else copy(ttl = ttl - 1, hopCount = hopCount + 1)

    companion object {
        const val DEFAULT_TTL = 5

        fun origin(originUid: String, lat: Double, lon: Double, category: String): SosPacket =
            SosPacket(
                id = java.util.UUID.randomUUID().toString(),
                originUid = originUid,
                lat = lat, lon = lon,
                timeMs = System.currentTimeMillis(),
                category = category,
                ttl = DEFAULT_TTL,
                hopCount = 0,
            )

        fun fromBytes(bytes: ByteArray): SosPacket? = runCatching {
            val o = JSONObject(String(bytes, Charsets.UTF_8))
            SosPacket(
                id = o.getString("id"),
                originUid = o.optString("o", "anon"),
                lat = o.getDouble("la"),
                lon = o.getDouble("lo"),
                timeMs = o.optLong("t", 0L),
                category = o.optString("c", "SOS"),
                ttl = o.optInt("ttl", 0),
                hopCount = o.optInt("h", 0),
            )
        }.getOrNull()
    }
}
