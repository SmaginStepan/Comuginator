package com.an0obis.comuginator.util

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Converts server UTC ISO timestamps to text in the device's timezone,
 * using the device locale's date/time formats.
 */
object TimeFormat {

    private val utcPatterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'"
    )

    fun parseUtc(iso: String?): Date? {
        if (iso.isNullOrBlank()) return null
        for (pattern in utcPatterns) {
            try {
                val parser = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                return parser.parse(iso) ?: continue
            } catch (_: Exception) {
                // try next pattern
            }
        }
        return null
    }

    /** ISO UTC timestamp → localized "date, time with seconds" in device timezone. */
    fun dateTime(iso: String?): String {
        val date = parseUtc(iso) ?: return iso.orEmpty()
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(date)
    }

    /**
     * Calendar date (e.g. schedule item date) → localized date. The value is a
     * plain date, so it is taken as-is rather than shifted through timezones.
     */
    fun date(isoDate: String?): String {
        if (isoDate.isNullOrBlank()) return ""
        val datePart = isoDate.take(10)
        return try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(datePart)
                ?: return datePart
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(parsed)
        } catch (_: Exception) {
            datePart
        }
    }
}
