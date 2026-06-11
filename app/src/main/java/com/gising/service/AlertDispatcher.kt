package com.gising.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.gising.data.model.EmergencyContact
import com.gising.util.PhoneUtils

/**
 * Sends earthquake alerts to the user's saved family/friends across multiple channels.
 *
 * Reality check on "auto-send to Messenger": Facebook/Meta does NOT allow apps to
 * silently send Messenger messages on a user's behalf. So:
 *   - SMS and a phone call are TRULY automatic (handled here, no user tap).
 *   - Messenger / WhatsApp / others are "one-tap": we open the app with a pre-filled
 *     message that the user confirms. That is the closest thing Facebook permits.
 */
object AlertDispatcher {

    private const val TAG = "AlertDispatcher"

    /** Build the human-readable alert text shared across every channel. */
    fun buildMessage(
        magnitude: Double,
        place: String,
        userLat: Double,
        userLon: Double
    ): String {
        val mapLink = "https://maps.google.com/?q=$userLat,$userLon"
        val mag = String.format("%.1f", magnitude)
        return buildString {
            append("🚨 GISING! Earthquake Alert\n")
            append("Magnitude $mag near $place.\n")
            append("I may be affected — this is my location: $mapLink\n")
            append("Sent automatically by the SeismicWatch app.")
        }
    }

    /**
     * A deliberately compact SOS text for the offline failback path. Built to survive a
     * single 160-char GSM segment when possible: a category tag, raw coordinates (machine- and
     * human-readable), an ISO-8601 UTC timestamp, and a tappable maps link any phone understands.
     *
     * Example: `GISING SOS [MEDICAL] 14.5995,120.9842 2026-06-11T08:00Z maps:https://maps.google.com/?q=14.5995,120.9842`
     */
    fun buildCompactSms(
        category: com.gising.emergency.SosCategory,
        lat: Double?,
        lon: Double?,
        timeMs: Long,
    ): String {
        val coords = if (lat != null && lon != null)
            "${String.format("%.4f", lat)},${String.format("%.4f", lon)}"
        else "location unknown"
        val ts = java.time.Instant.ofEpochMilli(if (timeMs > 0) timeMs else System.currentTimeMillis())
            .truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString()
        return buildString {
            append("GISING ${category.smsTag}")
            append(" $coords")
            append(" $ts")
            if (lat != null && lon != null) append(" https://maps.google.com/?q=$lat,$lon")
            append(" -sent via SeismicWatch")
        }
    }


    /**
     * Automatically text every contact flagged [EmergencyContact.notifyBySms].
     * Returns the number of contacts messaged (0 if permission missing or list empty).
     */
    fun sendAutoSms(context: Context, contacts: List<EmergencyContact>, message: String): Int {
        if (!hasPermission(context, Manifest.permission.SEND_SMS)) {
            Log.w(TAG, "SEND_SMS not granted; skipping auto-SMS")
            return 0
        }
        val recipients = contacts
            .filter { it.notifyBySms }
            .mapNotNull { PhoneUtils.normalize(it.phone) }
            .distinct()
        if (recipients.isEmpty()) return 0

        val sms = smsManager(context) ?: return 0
        var sent = 0
        recipients.forEach { number ->
            try {
                val parts = sms.divideMessage(message)
                sms.sendMultipartTextMessage(number, null, parts, null, null)
                sent++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to SMS $number", e)
            }
        }
        return sent
    }

    /**
     * Auto-dial the primary contact (used on major quakes). Must be started with a
     * NEW_TASK flag because it may originate from a background service.
     */
    fun autoCallPrimary(context: Context, primary: EmergencyContact?): Boolean {
        val number = primary?.let { PhoneUtils.normalize(it.phone) } ?: return false
        if (!hasPermission(context, Manifest.permission.CALL_PHONE)) {
            Log.w(TAG, "CALL_PHONE not granted; skipping auto-call")
            return false
        }
        return try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Auto-call failed", e)
            false
        }
    }

    /** One-tap Messenger share. Falls back to the system chooser if Messenger is absent. */
    fun shareToMessenger(context: Context, message: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            setPackage("com.facebook.orca")
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            Toast.makeText(context, "Messenger not installed — opening share sheet", Toast.LENGTH_SHORT).show()
            shareGeneric(context, message)
        }
    }

    /** Opens the Android share sheet (WhatsApp, Viber, Telegram, email, etc.). */
    fun shareGeneric(context: Context, message: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        val chooser = Intent.createChooser(send, "Alert my family via…")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /** Open the SMS composer pre-filled (used for a manual one-tap from the alert screen). */
    fun composeSms(context: Context, contacts: List<EmergencyContact>, message: String) {
        val numbers = contacts.filter { it.phone.isNotBlank() }.joinToString(";") { it.phone }
        val uri = Uri.parse("smsto:$numbers")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            shareGeneric(context, message)
        }
    }

    private fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun smsManager(context: Context): SmsManager? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }
    } catch (e: Exception) {
        Log.e(TAG, "No SmsManager available", e)
        null
    }
}
