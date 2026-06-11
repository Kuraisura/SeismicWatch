package com.gising.data

/**
 * Static geological reference data for the Philippines, used by the map overlay.
 *
 * IMPORTANT: The fault traces below are SIMPLIFIED, APPROXIMATE polylines intended for
 * visual/educational context only. They are hand-digitised from publicly described
 * trends of major Philippine active faults (Philippine Fault Zone, Valley Fault System,
 * Cotabato Fault, etc.). They are NOT survey-grade and must not be used for engineering
 * or hazard-zonation decisions. For authoritative data see PHIVOLCS
 * (https://www.phivolcs.dost.gov.ph) and its "Faultfinder" service.
 */
object PhilippineGeo {

    /** A point as (latitude, longitude). */
    data class LatLng(val lat: Double, val lon: Double)

    data class FaultLine(
        val name: String,
        val description: String,
        /** true = densely populated / frequently studied segment we highlight as higher concern. */
        val highConcern: Boolean,
        val points: List<LatLng>
    )

    data class Region(
        val name: String,
        val center: LatLng,
        val recommendedZoom: Double
    )

    // Whole-country camera target (roughly the geographic center of the archipelago).
    val PHILIPPINES_CENTER = LatLng(12.4, 122.6)
    const val PHILIPPINES_ZOOM = 5.6

    val REGIONS = listOf(
        Region("Luzon", LatLng(16.3, 121.1), 6.4),
        Region("Visayas", LatLng(10.7, 123.9), 7.0),
        Region("Mindanao", LatLng(7.6, 124.9), 6.6),
    )

    val FAULTS: List<FaultLine> = listOf(
        FaultLine(
            name = "Philippine Fault Zone",
            description = "Major ~1,200 km left-lateral fault running the length of the archipelago.",
            highConcern = true,
            points = listOf(
                LatLng(18.40, 121.55), // N. Luzon (Ilocos–Cagayan)
                LatLng(17.20, 121.75),
                LatLng(16.40, 121.40), // Digdig (1990 M7.8 source area)
                LatLng(15.30, 121.30),
                LatLng(14.20, 122.20), // Quezon
                LatLng(13.20, 123.40), // Bicol
                LatLng(12.40, 123.70), // Masbate
                LatLng(11.20, 124.60), // Leyte
                LatLng(10.20, 125.00),
                LatLng(9.00, 125.55),  // Agusan, Mindanao
                LatLng(7.80, 126.05),
                LatLng(6.60, 126.20),  // Davao Oriental
            )
        ),
        FaultLine(
            name = "West Valley Fault",
            description = "Runs through Metro Manila; capable of a ~M7.2 \"Big One\".",
            highConcern = true,
            points = listOf(
                LatLng(14.95, 121.10), // Rodriguez/Montalban, Rizal
                LatLng(14.70, 121.13),
                LatLng(14.58, 121.15), // Marikina / Pasig
                LatLng(14.40, 121.16),
                LatLng(14.18, 121.18), // Cavite / Laguna
            )
        ),
        FaultLine(
            name = "East Valley Fault",
            description = "Eastern strand of the Valley Fault System near Metro Manila.",
            highConcern = false,
            points = listOf(
                LatLng(14.88, 121.18),
                LatLng(14.72, 121.20),
                LatLng(14.62, 121.22),
            )
        ),
        FaultLine(
            name = "Cotabato Fault",
            description = "Active fault system in western Mindanao.",
            highConcern = false,
            points = listOf(
                LatLng(7.60, 124.20),
                LatLng(7.10, 124.40),
                LatLng(6.70, 124.60),
                LatLng(6.20, 124.80),
            )
        ),
        FaultLine(
            name = "Negros–Cebu Fault Trend",
            description = "Approximate active fault trend through the central Visayas.",
            highConcern = false,
            points = listOf(
                LatLng(10.60, 123.20),
                LatLng(10.10, 123.40),
                LatLng(9.60, 123.30),
            )
        ),
    )

    /**
     * Geofenced "risk zones" we draw as translucent circles — populated areas sitting
     * close to a highlighted fault. Radius is in metres.
     */
    data class RiskZone(
        val name: String,
        val center: LatLng,
        val radiusMeters: Double,
        val note: String
    )

    val RISK_ZONES = listOf(
        RiskZone("Metro Manila", LatLng(14.60, 121.00), 35_000.0, "On/near the West Valley Fault"),
        RiskZone("Davao Region", LatLng(7.07, 125.61), 30_000.0, "Near the Philippine Fault Zone"),
        RiskZone("Surigao / Agusan", LatLng(9.10, 125.55), 30_000.0, "Philippine Fault Zone segment"),
        RiskZone("Leyte", LatLng(11.00, 124.60), 28_000.0, "Philippine Fault Zone segment"),
    )
}
