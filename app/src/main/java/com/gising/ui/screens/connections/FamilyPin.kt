package com.gising.ui.screens.connections

/**
 * A lightweight, UI-facing location pin for a family member, used to render markers on the
 * map without leaking the full Firestore model into the view layer.
 */
data class FamilyPin(
    val name: String,
    val lat: Double,
    val lng: Double,
    val updatedAt: Long,
    val batteryPct: Int,
)
