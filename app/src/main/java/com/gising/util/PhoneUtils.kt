package com.gising.util

/**
 * Phone-number validation/normalisation tuned for Philippine numbers, with a
 * sensible fallback for international formats.
 *
 * Accepted inputs (spaces, dashes and parentheses are ignored):
 *   09XXXXXXXXX        → +639XXXXXXXXX   (local mobile)
 *   +639XXXXXXXXX      → unchanged       (E.164 PH mobile)
 *   639XXXXXXXXX       → +639XXXXXXXXX
 *   02XXXXXXXX / 0XX…  → +63XX…          (local landline with leading 0)
 *   +<8–15 digits>     → unchanged       (other international numbers)
 */
object PhoneUtils {

    /** Returns a normalised dial string, or null if the input isn't a usable number. */
    fun normalize(raw: String): String? {
        val cleaned = raw.trim().replace(Regex("[\\s\\-()]"), "")
        if (cleaned.isEmpty()) return null

        return when {
            // +63… international PH
            cleaned.startsWith("+63") && cleaned.length in 12..13 && isDigits(cleaned.drop(1)) ->
                cleaned
            // 639XXXXXXXXX
            cleaned.startsWith("63") && cleaned.length == 12 && isDigits(cleaned) ->
                "+$cleaned"
            // local mobile 09XXXXXXXXX (11 digits)
            cleaned.startsWith("09") && cleaned.length == 11 && isDigits(cleaned) ->
                "+63" + cleaned.substring(1)
            // local landline with leading 0 (e.g. 02XXXXXXXX)
            cleaned.startsWith("0") && cleaned.length in 8..11 && isDigits(cleaned) ->
                "+63" + cleaned.substring(1)
            // generic international
            cleaned.startsWith("+") && cleaned.length in 9..16 && isDigits(cleaned.drop(1)) ->
                cleaned
            else -> null
        }
    }

    fun isValid(raw: String): Boolean = normalize(raw) != null

    private fun isDigits(s: String): Boolean = s.isNotEmpty() && s.all { it.isDigit() }
}
