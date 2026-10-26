package com.openu60.agent.call

import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.util.Log
import com.openu60.agent.sim.SimChoice
import com.openu60.agent.sim.Sims
import org.json.JSONArray
import org.json.JSONObject

/**
 * Telecom binds this while the agent holds the default dialer role and a call exists.
 * The device has no in-call screen; calls are driven over the HTTP API instead.
 */
class AgentInCallService : InCallService() {
    override fun onCreate() {
        super.onCreate()
        CallControl.service = this
        CallControl.sims = Sims(this)
    }

    override fun onDestroy() {
        if (CallControl.service === this) CallControl.service = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        CallControl.add(call)
        call.registerCallback(selectSim)
        CallControl.selectSimIfAsked(call)
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(selectSim)
        CallControl.remove(call)
    }

    private val selectSim = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = CallControl.selectSimIfAsked(call)
    }
}

/** The live calls Telecom gave the in-call service, and the actions on them. */
object CallControl {
    @Volatile var service: AgentInCallService? = null
    @Volatile var sims: Sims? = null
    private val calls = ArrayList<Call>()
    private val ids = HashMap<Call, Int>()
    private var nextId = 1

    @Synchronized
    fun add(call: Call) {
        if (call in ids) return
        calls.add(call)
        ids[call] = nextId++
    }

    @Synchronized
    fun remove(call: Call) {
        calls.remove(call)
        ids.remove(call)
        if (calls.isEmpty()) nextId = 1
    }

    @Synchronized
    private fun snapshot(): List<Pair<Int, Call>> = calls.map { ids[it]!! to it }

    fun status(): JSONObject {
        val live = snapshot().filter { it.second.details.state != CallStates.DISCONNECTED }
        val out = JSONArray()
        for ((id, call) in live) {
            val state = call.details.state
            val others = live.any { it.second !== call && it.second.details.state != CallStates.RINGING }
            val stat = CallStates.stat(state, others) ?: continue
            out.put(
                JSONObject()
                    .put("id", id)
                    .put("dir", CallStates.dir(call.details.callDirection))
                    .put("stat", stat)
                    .put("mode", 0)
                    .put("number", call.details.handle?.schemeSpecificPart ?: "")
                    .putOpt("sim", sims?.slotOfAccount(call.details.accountHandle)),
            )
        }
        return JSONObject().put("calls", out).put("muted", isMuted())
    }

    /**
     * A call that reached Telecom without an account (dual SIM, "ask every time", dialled by
     * something other than the API) waits for an on-screen choice this device cannot show.
     * Answer it with the default voice SIM, or the lowest slot.
     */
    fun selectSimIfAsked(call: Call) {
        if (call.details.state != CallStates.SELECT_PHONE_ACCOUNT) return
        val s = sims ?: return
        val handle = (s.forCall(null) as? SimChoice.Pick.Use)?.let { s.accountFor(it.sim) }
        if (handle == null) {
            Log.w("OpenU60Agent", "call needs a SIM choice but no SIM account is available; ending it")
            call.disconnect()
            return
        }
        call.phoneAccountSelected(handle, false)
    }

    private fun ringing(): Call? = snapshot().map { it.second }.firstOrNull {
        it.details.state == CallStates.RINGING || it.details.state == CallStates.SIMULATED_RINGING
    }

    private fun current(): Call? {
        val all = snapshot().map { it.second }
        return all.firstOrNull { it.details.state == CallStates.ACTIVE }
            ?: all.firstOrNull { it.details.state == CallStates.DIALING || it.details.state == CallStates.CONNECTING }
            ?: all.firstOrNull { it.details.state != CallStates.DISCONNECTED }
    }

    /** False when nothing is ringing. */
    fun answer(): Boolean {
        val call = ringing() ?: return false
        call.answer(VideoProfile.STATE_AUDIO_ONLY)
        return true
    }

    /** Rejects a ringing call, otherwise ends the current one; false when there is none. */
    fun hangup(): Boolean {
        val all = snapshot().map { it.second }
        val ringing = ringing()
        if (ringing != null && all.size == 1) {
            ringing.reject(false, null)
            return true
        }
        val call = current() ?: return false
        call.disconnect()
        return true
    }

    /** Plays each digit for [toneMs]; blocks the calling worker thread. */
    fun dtmf(digits: String, toneMs: Long = 150): Boolean {
        val call = snapshot().map { it.second }.firstOrNull { it.details.state == CallStates.ACTIVE }
            ?: return false
        for (d in digits) {
            call.playDtmfTone(d)
            Thread.sleep(toneMs)
            call.stopDtmfTone()
            Thread.sleep(50)
        }
        return true
    }

    fun setMuted(muted: Boolean): Boolean {
        val s = service ?: return false
        s.setMuted(muted)
        return true
    }

    fun isMuted(): Boolean = service?.callAudioState?.isMuted ?: false
}
