package com.openu60.agent.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.openu60.agent.AgentService

/**
 * The provider already stores the message; this only makes sure the agent is running
 * (it may have been killed) so the next poll or push sees it. Never logs number or text.
 */
class SmsReceivedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) AgentService.start(context)
    }
}
