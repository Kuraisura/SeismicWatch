package com.gising.data

/**
 * A comprehensive set of Philippine cities/municipalities across Luzon, Visayas, and Mindanao — one
 * entry per province (its capital) plus every major highly-urbanized city — used to show live weather
 * for "all places" in the country. Coordinates are city centres. Open-Meteo (free, no key) returns a
 * live current-conditions block for any of these, so no backend / storage is needed.
 *
 * Grouped by island so the Weather section can filter by Luzon / Visayas / Mindanao. Extend freely —
 * every entry is just one more point in the parallel Open-Meteo fetch.
 */
object PhilippineWeatherPoints {

    data class Place(val name: String, val island: String, val lat: Double, val lon: Double)

    const val LUZON = "Luzon"
    const val VISAYAS = "Visayas"
    const val MINDANAO = "Mindanao"

    val PLACES: List<Place> = listOf(
        // ── LUZON ────────────────────────────────────────────────────────────
        // Metro Manila / NCR
        Place("Manila", LUZON, 14.5995, 120.9842),
        Place("Quezon City", LUZON, 14.6760, 121.0437),
        Place("Caloocan", LUZON, 14.6499, 120.9673),
        Place("Taguig", LUZON, 14.5176, 121.0509),
        Place("Pasig", LUZON, 14.5764, 121.0851),
        Place("Antipolo", LUZON, 14.5865, 121.1760),
        // Cordillera (CAR)
        Place("Baguio", LUZON, 16.4023, 120.5960),
        Place("La Trinidad", LUZON, 16.4550, 120.5870),
        Place("Bangued", LUZON, 17.5966, 120.6175),
        Place("Bontoc", LUZON, 17.0894, 120.9767),
        Place("Lagawe", LUZON, 16.7997, 121.1200),
        Place("Tabuk", LUZON, 17.4089, 121.4443),
        // Ilocos Region
        Place("Laoag", LUZON, 18.1978, 120.5936),
        Place("Vigan", LUZON, 17.5747, 120.3869),
        Place("San Fernando (La Union)", LUZON, 16.6159, 120.3166),
        Place("Dagupan", LUZON, 16.0430, 120.3330),
        Place("Lingayen", LUZON, 16.0217, 120.2317),
        // Cagayan Valley
        Place("Tuguegarao", LUZON, 17.6132, 121.7270),
        Place("Ilagan", LUZON, 17.1489, 121.8894),
        Place("Bayombong", LUZON, 16.4820, 121.1440),
        Place("Cauayan", LUZON, 16.9350, 121.7710),
        Place("Basco (Batanes)", LUZON, 20.4487, 121.9702),
        // Central Luzon
        Place("San Fernando (Pampanga)", LUZON, 15.0286, 120.6898),
        Place("Angeles", LUZON, 15.1450, 120.5887),
        Place("Olongapo", LUZON, 14.8296, 120.2828),
        Place("Balanga", LUZON, 14.6768, 120.5363),
        Place("Malolos", LUZON, 14.8433, 120.8114),
        Place("Cabanatuan", LUZON, 15.4869, 120.9675),
        Place("Tarlac City", LUZON, 15.4755, 120.5960),
        Place("Iba", LUZON, 15.3276, 119.9787),
        // CALABARZON
        Place("Calamba", LUZON, 14.2117, 121.1653),
        Place("Batangas City", LUZON, 13.7565, 121.0583),
        Place("Lipa", LUZON, 13.9411, 121.1631),
        Place("Lucena", LUZON, 13.9373, 121.6170),
        Place("Tanauan", LUZON, 14.0863, 121.1497),
        Place("Trece Martires", LUZON, 14.2820, 120.8650),
        Place("Santa Cruz (Laguna)", LUZON, 14.2792, 121.4160),
        // MIMAROPA
        Place("Calapan", LUZON, 13.4117, 121.1803),
        Place("Puerto Princesa", LUZON, 9.7392, 118.7353),
        Place("Boac (Marinduque)", LUZON, 13.4470, 121.8400),
        Place("Romblon", LUZON, 12.5760, 122.2710),
        Place("Mamburao", LUZON, 13.2230, 120.5960),
        // Bicol Region
        Place("Legazpi", LUZON, 13.1391, 123.7438),
        Place("Naga", LUZON, 13.6218, 123.1948),
        Place("Sorsogon City", LUZON, 12.9743, 124.0064),
        Place("Daet", LUZON, 14.1121, 122.9550),
        Place("Masbate City", LUZON, 12.3660, 123.6200),
        Place("Virac (Catanduanes)", LUZON, 13.5827, 124.2310),

        // ── VISAYAS ──────────────────────────────────────────────────────────
        // Western Visayas
        Place("Iloilo City", VISAYAS, 10.7202, 122.5621),
        Place("Bacolod", VISAYAS, 10.6770, 122.9500),
        Place("Roxas City", VISAYAS, 11.5853, 122.7511),
        Place("Kalibo", VISAYAS, 11.7086, 122.3653),
        Place("San Jose de Buenavista", VISAYAS, 10.7400, 121.9390),
        Place("Jordan (Guimaras)", VISAYAS, 10.5940, 122.5960),
        Place("Bago", VISAYAS, 10.5333, 122.8350),
        // Central Visayas
        Place("Cebu City", VISAYAS, 10.3157, 123.8854),
        Place("Mandaue", VISAYAS, 10.3237, 123.9227),
        Place("Lapu-Lapu", VISAYAS, 10.3103, 123.9494),
        Place("Tagbilaran", VISAYAS, 9.6475, 123.8556),
        Place("Dumaguete", VISAYAS, 9.3103, 123.3081),
        Place("Toledo", VISAYAS, 10.3770, 123.6390),
        Place("Siquijor", VISAYAS, 9.2140, 123.5150),
        // Eastern Visayas
        Place("Tacloban", VISAYAS, 11.2444, 125.0048),
        Place("Ormoc", VISAYAS, 11.0064, 124.6075),
        Place("Catbalogan", VISAYAS, 11.7753, 124.8861),
        Place("Calbayog", VISAYAS, 12.0668, 124.5960),
        Place("Maasin", VISAYAS, 10.1330, 124.8440),
        Place("Borongan", VISAYAS, 11.6080, 125.4320),
        Place("Naval (Biliran)", VISAYAS, 11.5600, 124.4010),

        // ── MINDANAO ─────────────────────────────────────────────────────────
        // Davao Region
        Place("Davao City", MINDANAO, 7.1907, 125.4553),
        Place("Tagum", MINDANAO, 7.4478, 125.8078),
        Place("Digos", MINDANAO, 6.7497, 125.3572),
        Place("Mati", MINDANAO, 6.9550, 126.2160),
        Place("Panabo", MINDANAO, 7.3080, 125.6840),
        Place("Nabunturan", MINDANAO, 7.6050, 125.9660),
        // Northern Mindanao
        Place("Cagayan de Oro", MINDANAO, 8.4542, 124.6319),
        Place("Iligan", MINDANAO, 8.2280, 124.2452),
        Place("Malaybalay", MINDANAO, 8.1575, 125.1278),
        Place("Valencia", MINDANAO, 7.9060, 125.0940),
        Place("Oroquieta", MINDANAO, 8.4850, 123.8050),
        Place("Gingoog", MINDANAO, 8.8290, 125.1000),
        // Zamboanga Peninsula
        Place("Zamboanga City", MINDANAO, 6.9214, 122.0790),
        Place("Pagadian", MINDANAO, 7.8257, 123.4370),
        Place("Dipolog", MINDANAO, 8.5883, 123.3417),
        Place("Dapitan", MINDANAO, 8.6580, 123.4230),
        Place("Ipil", MINDANAO, 7.7840, 122.5860),
        // SOCCSKSARGEN
        Place("General Santos", MINDANAO, 6.1164, 125.1716),
        Place("Koronadal", MINDANAO, 6.5031, 124.8469),
        Place("Kidapawan", MINDANAO, 7.0083, 125.0894),
        Place("Cotabato City", MINDANAO, 7.2047, 124.2310),
        Place("Tacurong", MINDANAO, 6.6920, 124.6760),
        Place("Alabel", MINDANAO, 6.1030, 125.2900),
        // Caraga
        Place("Butuan", MINDANAO, 8.9475, 125.5406),
        Place("Surigao City", MINDANAO, 9.7839, 125.4889),
        Place("Tandag", MINDANAO, 9.0783, 126.1985),
        Place("Bislig", MINDANAO, 8.2100, 126.3170),
        Place("Bayugan", MINDANAO, 8.7140, 125.7490),
        Place("Prosperidad", MINDANAO, 8.6010, 125.9160),
        // BARMM
        Place("Marawi", MINDANAO, 8.0000, 124.2928),
        Place("Lamitan", MINDANAO, 6.6500, 122.1330),
        Place("Jolo (Sulu)", MINDANAO, 6.0530, 121.0020),
        Place("Bongao (Tawi-Tawi)", MINDANAO, 5.0290, 119.7730),
    )

    val ISLANDS = listOf(LUZON, VISAYAS, MINDANAO)
}
