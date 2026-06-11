package com.gising.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A saved family member / friend that SeismicWatch can reach when an earthquake fires.
 *
 * - [isPrimary] marks the single "top contact" that gets auto-called on a major quake.
 * - [notifyBySms] contacts receive an automatic SMS the moment an alert triggers.
 * - [messengerLink] is an optional m.me / profile link used by the one-tap Messenger button.
 */
@Entity(tableName = "emergency_contacts")
data class EmergencyContact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val relationship: String = "",      // e.g. "Nanay", "Kapatid", "Kaibigan"
    val isPrimary: Boolean = false,      // top contact — auto-called on major quakes
    val notifyBySms: Boolean = true,     // include in the automatic SMS blast
    val messengerLink: String = ""       // optional m.me/<username> for one-tap Messenger
)
