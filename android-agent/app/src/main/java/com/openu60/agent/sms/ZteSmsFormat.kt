package com.openu60.agent.sms

import java.util.Calendar
import java.util.TimeZone

/**
 * The ZTE wms wire format the companion apps already parse (see the iOS `SMSParser`):
 * bodies are UCS-2 hex, dates are "YY,MM,DD,HH,MM,SS,+Q" with the zone in quarter-hours,
 * and the tag is 0 read, 1 unread, 2 sent, 3 failed, 4 draft.
 */
object ZteSmsFormat {
    const val TAG_READ = 0
    const val TAG_UNREAD = 1
    const val TAG_SENT = 2
    const val TAG_FAILED = 3
    const val TAG_DRAFT = 4

    // android.provider.Telephony.TextBasedSmsColumns.MESSAGE_TYPE_*
    private const val TYPE_INBOX = 1
    private const val TYPE_SENT = 2
    private const val TYPE_DRAFT = 3
    private const val TYPE_OUTBOX = 4
    private const val TYPE_FAILED = 5
    private const val TYPE_QUEUED = 6

    private val HEX = "0123456789ABCDEF".toCharArray()

    /** UTF-16BE code units as upper-case hex, 4 digits each (surrogate pairs stay two units). */
    fun encodeUcs2Hex(text: String): String {
        val out = StringBuilder(text.length * 4)
        for (c in text) {
            val u = c.code
            out.append(HEX[(u shr 12) and 0xF]).append(HEX[(u shr 8) and 0xF])
                .append(HEX[(u shr 4) and 0xF]).append(HEX[u and 0xF])
        }
        return out.toString()
    }

    /**
     * Decodes what the apps send as `message_body`. Returns null when the input is not
     * UCS-2 hex (odd length or a non-hex digit), so callers can reject it.
     */
    fun decodeUcs2Hex(hex: String): String? {
        if (hex.length % 4 != 0) return null
        val out = StringBuilder(hex.length / 4)
        var i = 0
        while (i < hex.length) {
            var unit = 0
            for (j in 0 until 4) {
                val d = Character.digit(hex[i + j], 16)
                if (d < 0) return null
                unit = (unit shl 4) or d
            }
            out.append(unit.toChar())
            i += 4
        }
        return out.toString()
    }

    /** Provider message type plus read flag to the ZTE tag. */
    fun tagFor(type: Int, read: Boolean): Int = when (type) {
        TYPE_INBOX -> if (read) TAG_READ else TAG_UNREAD
        TYPE_SENT, TYPE_OUTBOX, TYPE_QUEUED -> TAG_SENT
        TYPE_FAILED -> TAG_FAILED
        TYPE_DRAFT -> TAG_DRAFT
        else -> TAG_READ
    }

    /** "YY,MM,DD,HH,MM,SS,+Q" in [zone]; Q is the offset in quarter-hours, signed. */
    fun formatDate(epochMillis: Long, zone: TimeZone): String {
        val cal = Calendar.getInstance(zone)
        cal.timeInMillis = epochMillis
        val quarters = zone.getOffset(epochMillis) / (15 * 60 * 1000)
        val sign = if (quarters >= 0) "+" else ""
        return String.format(
            java.util.Locale.ROOT,
            "%02d,%02d,%02d,%02d,%02d,%02d,%s%d",
            cal.get(Calendar.YEAR) % 100,
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            cal.get(Calendar.SECOND),
            sign,
            quarters,
        )
    }

    /** "3681;3682;" (the legacy ZTE id list) to ids; blanks and junk are skipped. */
    fun parseIdList(raw: String): List<Long> =
        raw.split(';', ',').mapNotNull { it.trim().toLongOrNull() }
}
