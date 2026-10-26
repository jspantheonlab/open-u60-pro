package com.openu60.agent.sim

import android.annotation.SuppressLint
import android.content.Context
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import org.json.JSONArray
import org.json.JSONObject

/** The device's active SIMs and the Telecom phone accounts behind them. READ_PHONE_STATE is granted at setup. */
@SuppressLint("MissingPermission")
class Sims(context: Context) {
    private val subs = context.getSystemService(SubscriptionManager::class.java)
    private val telephony = context.getSystemService(TelephonyManager::class.java)
    private val telecom = context.getSystemService(TelecomManager::class.java)

    fun active(): List<Sim> = try {
        subs.activeSubscriptionInfoList.orEmpty().map {
            Sim(it.simSlotIndex, it.subscriptionId, it.displayName?.toString() ?: "", it.isEmbedded)
        }.sortedBy { it.slot }
    } catch (e: SecurityException) {
        emptyList()
    }

    fun forSms(requestedSlot: Int?) =
        SimChoice.pick(requestedSlot, SubscriptionManager.getDefaultSmsSubscriptionId(), active())

    fun forCall(requestedSlot: Int?) =
        SimChoice.pick(requestedSlot, SubscriptionManager.getDefaultVoiceSubscriptionId(), active())

    /** The Telecom account that places calls on [sim], or null if Telecom has none. */
    fun accountFor(sim: Sim): PhoneAccountHandle? = try {
        telecom.callCapablePhoneAccounts.firstOrNull { telephony.getSubscriptionId(it) == sim.subId }
    } catch (e: SecurityException) {
        null
    }

    fun slotOfAccount(handle: PhoneAccountHandle?): Int? {
        handle ?: return null
        val subId = try { telephony.getSubscriptionId(handle) } catch (e: SecurityException) { return null }
        return active().firstOrNull { it.subId == subId }?.slot
    }

    fun slotOfSub(subId: Int, active: List<Sim>): Int? = active.firstOrNull { it.subId == subId }?.slot

    fun toJson(): JSONArray {
        val smsDefault = SubscriptionManager.getDefaultSmsSubscriptionId()
        val voiceDefault = SubscriptionManager.getDefaultVoiceSubscriptionId()
        val out = JSONArray()
        for (s in active()) {
            out.put(
                JSONObject()
                    .put("slot", s.slot)
                    .put("name", s.name)
                    .put("embedded", s.embedded)
                    .put("default_sms", s.subId == smsDefault)
                    .put("default_voice", s.subId == voiceDefault),
            )
        }
        return out
    }
}
