package com.openu60.agent

import com.openu60.agent.http.Auth
import com.openu60.agent.http.Request
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiTest {
    private class FakeDevice : Api.Device {
        val sent = ArrayList<Pair<String, String>>()
        val sims = ArrayList<Int?>()
        val hidden = ArrayList<Long>()
        var dialed: String? = null
        override fun info() = JSONObject().put("model", "fake")
        override fun smsList(page: Int, perPage: Int) = JSONObject().put("page", page).put("per", perPage)
        override fun smsCapacity() = JSONObject()
        override fun smsSend(number: String, text: String, sim: Int?): String? {
            sent += number to text; sims += sim
            return if (sim == 1) "no active SIM in slot 1" else null
        }
        override fun smsMarkRead(ids: List<Long>) {}
        override fun smsHide(ids: List<Long>) { hidden += ids }
        override fun callStatus() = JSONObject().put("calls", org.json.JSONArray())
        override fun callDial(number: String, sim: Int?): String? { dialed = number; sims += sim; return null }
        override fun callAnswer() = false
        override fun callHangup() = true
        override fun callDtmf(digits: String) = true
        override fun callMute(muted: Boolean) = true
    }

    private class MemStore : Auth.PasswordStore {
        var v: String? = null
        override fun load() = v
        override fun save(value: String) { v = value }
    }

    private val device = FakeDevice()
    private val api = Api(Auth(MemStore(), { 0L }), device)

    private fun call(method: String, path: String, body: String = "", token: String? = null, remote: String = "192.168.0.100"): Pair<Int, JSONObject> {
        val headers = HashMap<String, String>()
        token?.let { headers["authorization"] = "Bearer $it" }
        val r = api.handle(Request(method, path, "", headers, body.toByteArray(), remote))
        return r.status to JSONObject(String(r.body))
    }

    private fun setup(): String {
        assertEquals(200, call("POST", "/api/auth/password", """{"new":"correct horse"}""", remote = "127.0.0.1").first)
        val (s, j) = call("POST", "/api/auth/login", """{"password":"correct horse"}""")
        assertEquals(200, s)
        return j.getJSONObject("data").getString("token")
    }

    @Test fun firstPasswordOnlyFromLoopback() {
        assertEquals(403, call("POST", "/api/auth/password", """{"new":"correct horse"}""").first)
        assertEquals(403, call("GET", "/api/device").first)
        setup()
        // Once set, changing it needs a token even from loopback.
        assertEquals(401, call("POST", "/api/auth/password", """{"new":"other password"}""", remote = "127.0.0.1").first)
    }

    @Test fun requiresToken() {
        setup()
        assertEquals(401, call("GET", "/api/device").first)
        assertEquals(401, call("GET", "/api/device", token = "0".repeat(64)).first)
    }

    @Test fun envelopeAndRouting() {
        val t = setup()
        val (s, j) = call("GET", "/api/device", token = t)
        assertEquals(200, s)
        assertTrue(j.getBoolean("ok"))
        assertEquals("fake", j.getJSONObject("data").getString("model"))
        assertEquals(404, call("GET", "/api/nope", token = t).first)
        assertEquals(405, call("GET", "/api/sms/send", token = t).first)
    }

    @Test fun smsSendDecodesUcs2() {
        val t = setup()
        val (s, _) = call("POST", "/api/sms/send", """{"number":"+8613800138000","message_body":"4F60597D","id":"-1","encode_type":"UNICODE"}""", t)
        assertEquals(200, s)
        assertEquals("+8613800138000" to "你好", device.sent.single())
        assertEquals(400, call("POST", "/api/sms/send", """{"number":"x","message_body":"4F60"}""", t).first)
        assertEquals(400, call("POST", "/api/sms/send", """{"number":"10086","message_body":"zz"}""", t).first)
    }

    @Test fun optionalSimSlot() {
        val t = setup()
        val body = """"number":"10086","message_body":"4F60""""
        assertEquals(200, call("POST", "/api/sms/send", "{$body}", t).first)
        assertEquals(200, call("POST", "/api/sms/send", "{$body,\"sim\":0}", t).first)
        assertEquals(200, call("POST", "/api/sms/send", "{$body,\"sim\":\"0\"}", t).first)
        assertEquals(503, call("POST", "/api/sms/send", "{$body,\"sim\":1}", t).first)
        assertEquals(400, call("POST", "/api/sms/send", "{$body,\"sim\":9}", t).first)
        assertEquals(400, call("POST", "/api/sms/send", "{$body,\"sim\":\"a\"}", t).first)
        assertEquals(200, call("POST", "/api/call/dial", """{"number":"10086","sim":1}""", t).first)
        assertEquals(listOf(null, 0, 0, 1, 1), device.sims)
    }

    @Test fun smsDeleteTakesZteIdList() {
        val t = setup()
        assertEquals(200, call("POST", "/api/sms/delete", """{"id":"5;6;"}""", t).first)
        assertEquals(listOf(5L, 6L), device.hidden)
        assertEquals(400, call("POST", "/api/sms/delete", """{"id":""}""", t).first)
    }

    @Test fun callRoutes() {
        val t = setup()
        assertEquals(200, call("POST", "/api/call/dial", """{"number":"10086"}""", t).first)
        assertEquals("10086", device.dialed)
        assertEquals(400, call("POST", "/api/call/dial", """{"number":"1;rm"}""", t).first)
        assertEquals(409, call("POST", "/api/call/answer", "", t).first)
        assertEquals(400, call("POST", "/api/call/dtmf", """{"digits":"1x"}""", t).first)
        val (s, j) = call("POST", "/api/call/mute", """{"enabled":true}""", t)
        assertEquals(200, s)
        assertTrue(j.getJSONObject("data").getBoolean("muted"))
    }

    @Test fun badJsonIs400() {
        val t = setup()
        assertEquals(400, call("POST", "/api/sms/list", "not json", t).first)
    }
}
