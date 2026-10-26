package com.openu60.agent.sms

import android.content.Context
import android.provider.Telephony
import android.telephony.SmsManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.TimeZone

/**
 * SMS from the system provider (`content://sms`), shaped like zte-agent's wms output.
 *
 * Only the default SMS app may write the provider, and taking that role from the stock
 * ZTE software could break its own SMS handling. So "read" and "delete" are kept in an
 * overlay of ids here, and the provider is never modified.
 */
class SmsStore(private val context: Context) {
    private val overlay = context.getSharedPreferences("sms_overlay", Context.MODE_PRIVATE)

    fun list(page: Int, perPage: Int): JSONObject {
        val read = overlay.getStringSet(KEY_READ, emptySet())!!
        val hidden = overlay.getStringSet(KEY_HIDDEN, emptySet())!!
        val zone = TimeZone.getDefault()
        val messages = JSONArray()
        val limit = perPage.coerceIn(1, MAX_PAGE)
        var skip = page.coerceAtLeast(0) * limit
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
                Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.READ,
                Telephony.Sms.THREAD_ID,
            ),
            null, null, "${Telephony.Sms._ID} DESC",
        )?.use { c ->
            while (c.moveToNext() && messages.length() < limit) {
                val id = c.getLong(0)
                if (id.toString() in hidden) continue
                if (skip > 0) { skip--; continue }
                val isRead = c.getInt(5) != 0 || id.toString() in read
                messages.put(
                    JSONObject()
                        .put("id", id)
                        .put("number", c.getString(1) ?: "")
                        .put("content", ZteSmsFormat.encodeUcs2Hex(c.getString(2) ?: ""))
                        .put("date", ZteSmsFormat.formatDate(c.getLong(3), zone))
                        .put("tag", ZteSmsFormat.tagFor(c.getInt(4), isRead).toString())
                        .put("draft_group_id", c.getLong(6).toString())
                        .put("mem_store", "nv"),
                )
            }
        }
        return JSONObject().put("messages", messages)
    }

    fun capacity(): JSONObject {
        val read = overlay.getStringSet(KEY_READ, emptySet())!!
        val hidden = overlay.getStringSet(KEY_HIDDEN, emptySet())!!
        var total = 0
        var unread = 0
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.TYPE, Telephony.Sms.READ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0).toString()
                if (id in hidden) continue
                total++
                if (c.getInt(1) == Telephony.Sms.MESSAGE_TYPE_INBOX && c.getInt(2) == 0 && id !in read) unread++
            }
        }
        // The phone's storage, not a SIM: report it as NV with no fixed ceiling.
        return JSONObject()
            .put("sms_nv_total", maxOf(total, NV_NOMINAL))
            .put("sms_nvused_total", total)
            .put("sms_sim_total", 0)
            .put("sms_simused_total", 0)
            .put("sms_dev_unread_num", unread)
    }

    fun markRead(ids: List<Long>) = addTo(KEY_READ, ids)

    fun hide(ids: List<Long>) = addTo(KEY_HIDDEN, ids)

    private fun addTo(key: String, ids: List<Long>) {
        synchronized(this) {
            val set = HashSet(overlay.getStringSet(key, emptySet())!!)
            ids.forEach { set.add(it.toString()) }
            overlay.edit().putStringSet(key, set).apply()
        }
    }

    /** Sends [text]; the system records it in the provider's sent box for non-default apps. */
    fun send(number: String, text: String) {
        val sms = context.getSystemService(SmsManager::class.java)
        val parts = sms.divideMessage(text)
        if (parts.size == 1) {
            sms.sendTextMessage(number, null, text, null, null)
        } else {
            sms.sendMultipartTextMessage(number, null, parts, null, null)
        }
    }

    companion object {
        private const val KEY_READ = "read_ids"
        private const val KEY_HIDDEN = "hidden_ids"
        private const val MAX_PAGE = 500
        private const val NV_NOMINAL = 5000
    }
}
