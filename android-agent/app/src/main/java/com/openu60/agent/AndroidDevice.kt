package com.openu60.agent

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import com.openu60.agent.call.CallControl
import com.openu60.agent.sms.SmsStore
import org.json.JSONObject

class AndroidDevice(private val context: Context) : Api.Device {
    private val sms = SmsStore(context)
    private val telecom = context.getSystemService(TelecomManager::class.java)

    override fun info(): JSONObject = JSONObject()
        .put("platform", "android")
        .put("manufacturer", Build.MANUFACTURER)
        .put("model", Build.MODEL)
        .put("device", Build.DEVICE)
        .put("android", Build.VERSION.RELEASE)
        .put("sdk", Build.VERSION.SDK_INT)
        .put("build", Build.DISPLAY)
        .put("agent_version", BuildConfigCompat.versionName(context))
        .put("default_dialer", telecom.defaultDialerPackage == context.packageName)

    override fun smsList(page: Int, perPage: Int) = sms.list(page, perPage)
    override fun smsCapacity() = sms.capacity()
    override fun smsSend(number: String, text: String) = sms.send(number, text)
    override fun smsMarkRead(ids: List<Long>) = sms.markRead(ids)
    override fun smsHide(ids: List<Long>) = sms.hide(ids)

    override fun callStatus() = CallControl.status()

    @SuppressLint("MissingPermission") // CALL_PHONE is granted at setup (pm grant); a SecurityException is reported.
    override fun callDial(number: String): String? {
        if (telecom.defaultDialerPackage != context.packageName) {
            // Without the role Telecom never binds AgentInCallService, so the call could
            // not be watched or hung up from the API.
            return "the agent is not the default dialer (cmd role add-role-holder android.app.role.DIALER ${context.packageName})"
        }
        return try {
            telecom.placeCall(Uri.fromParts("tel", number, null), Bundle())
            null
        } catch (e: SecurityException) {
            "CALL_PHONE not granted"
        }
    }

    override fun callAnswer() = CallControl.answer()
    override fun callHangup() = CallControl.hangup()
    override fun callDtmf(digits: String) = CallControl.dtmf(digits)
    override fun callMute(muted: Boolean) = CallControl.setMuted(muted)
}

private object BuildConfigCompat {
    fun versionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
}
