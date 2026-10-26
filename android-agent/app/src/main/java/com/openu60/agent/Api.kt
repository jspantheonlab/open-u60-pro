package com.openu60.agent

import com.openu60.agent.http.Auth
import com.openu60.agent.http.Json
import com.openu60.agent.http.Request
import com.openu60.agent.http.Response
import com.openu60.agent.sms.ZteSmsFormat
import org.json.JSONArray
import org.json.JSONObject

/**
 * The zte-agent-compatible subset of `/api/` that this device can serve.
 * Device access is behind [Device] so routing, auth and validation are JVM-testable.
 */
class Api(private val auth: Auth, private val device: Device) {

    interface Device {
        fun info(): JSONObject
        fun smsList(page: Int, perPage: Int): JSONObject
        fun smsCapacity(): JSONObject
        /** [sim] is the requested slot (null = the default SIM). Null on success, otherwise the reason. */
        fun smsSend(number: String, text: String, sim: Int?): String?
        fun smsMarkRead(ids: List<Long>)
        fun smsHide(ids: List<Long>)
        fun callStatus(): JSONObject
        /** [sim] is the requested slot (null = the default SIM). Null on success, otherwise the reason. */
        fun callDial(number: String, sim: Int?): String?
        fun callAnswer(): Boolean
        fun callHangup(): Boolean
        fun callDtmf(digits: String): Boolean
        fun callMute(muted: Boolean): Boolean
    }

    fun handle(req: Request): Response {
        if (!req.path.startsWith("/api/")) return Response(404, Json.error("not found"))
        val route = req.method to req.path

        // Unauthenticated routes.
        when (route) {
            "POST" to "/api/auth/login" -> return login(req)
            "POST" to "/api/auth/password" -> return setPassword(req)
            "GET" to "/api/health" -> return ok(JSONObject().put("password_set", auth.hasPassword))
        }

        if (!auth.hasPassword) return Response(403, Json.error("set a password first: POST /api/auth/password from the device (adb forward)"))
        if (!auth.check(req.bearer())) return Response(401, Json.error("unauthorized"))

        return when (route) {
            "POST" to "/api/auth/logout" -> { req.bearer()?.let(auth::logout); ok() }
            "GET" to "/api/device" -> ok(device.info())
            "GET" to "/api/capabilities" -> ok(capabilities())

            "POST" to "/api/sms/list" -> smsList(req)
            "GET" to "/api/sms/capacity" -> ok(device.smsCapacity())
            "POST" to "/api/sms/send" -> smsSend(req)
            "POST" to "/api/sms/delete" -> withIds(req) { device.smsHide(it) }
            "POST" to "/api/sms/read" -> withIds(req) { device.smsMarkRead(it) }

            "GET" to "/api/call/status" -> ok(device.callStatus())
            "POST" to "/api/call/dial" -> callDial(req)
            "POST" to "/api/call/answer" -> if (device.callAnswer()) ok() else conflict("no ringing call")
            "POST" to "/api/call/hangup" -> if (device.callHangup()) ok() else conflict("no call")
            "POST" to "/api/call/dtmf" -> callDtmf(req)
            "POST" to "/api/call/mute" -> callMute(req)

            else -> if (KNOWN_PATHS.contains(req.path)) Response(405, Json.error("method not allowed"))
            else Response(404, Json.error("not found"))
        }
    }

    private fun login(req: Request): Response {
        val body = Json.body(req) ?: return bad("invalid JSON")
        val password = body.optString("password", "").ifEmpty { return bad("missing 'password' field") }
        return when (val r = auth.login(password)) {
            is Auth.Login.Ok -> ok(JSONObject().put("token", r.token))
            Auth.Login.NoPassword, Auth.Login.BadPassword -> Response(401, Json.error("invalid password"))
            is Auth.Login.RateLimited -> Response(
                429,
                JSONObject().put("ok", false).put("error", "too many login attempts")
                    .put("retry_after", r.retryAfterSecs).toString().toByteArray(),
            ).apply { extraHeaders["Retry-After"] = r.retryAfterSecs.toString() }
        }
    }

    /**
     * The first password may only be set from the device itself (loopback, i.e. through
     * `adb forward`), so whoever joins the hotspot first cannot claim it. Changing it later
     * needs a valid token.
     */
    private fun setPassword(req: Request): Response {
        val body = Json.body(req) ?: return bad("invalid JSON")
        val new = body.optString("new", "")
        if (auth.hasPassword) {
            if (!auth.check(req.bearer())) return Response(401, Json.error("unauthorized"))
        } else if (!isLoopback(req.remote)) {
            return Response(403, Json.error("the first password can only be set over adb forward"))
        }
        return try {
            auth.setPassword(new)
            ok()
        } catch (e: IllegalArgumentException) {
            bad(e.message ?: "invalid password")
        }
    }

    private fun smsList(req: Request): Response {
        val body = Json.body(req) ?: return bad("invalid JSON")
        return ok(device.smsList(body.optInt("page", 0), body.optInt("data_per_page", 100)))
    }

    private fun smsSend(req: Request): Response {
        val body = Json.body(req) ?: return bad("invalid JSON")
        val number = body.optString("number", "").trim()
        if (!isPhoneNumber(number)) return bad("invalid number")
        val hex = body.optString("message_body", "")
        val text = ZteSmsFormat.decodeUcs2Hex(hex) ?: return bad("message_body must be UCS-2 hex")
        if (text.isEmpty()) return bad("empty message")
        if (text.length > MAX_SMS_CHARS) return bad("message too long")
        val sim = simSlot(body) ?: return bad("'sim' must be a slot number 0-7")
        val err = device.smsSend(number, text, sim.slot)
        return if (err == null) ok() else Response(503, Json.error(err))
    }

    private inline fun withIds(req: Request, action: (List<Long>) -> Unit): Response {
        val body = Json.body(req) ?: return bad("invalid JSON")
        val ids = when (val raw = body.opt("id")) {
            is String -> ZteSmsFormat.parseIdList(raw)
            is Number -> listOf(raw.toLong())
            is JSONArray -> (0 until raw.length()).mapNotNull { raw.optString(it).toLongOrNull() }
            else -> emptyList()
        }
        if (ids.isEmpty()) return bad("missing 'id'")
        action(ids)
        return ok()
    }

    private fun callDial(req: Request): Response {
        val body = Json.body(req) ?: return bad("invalid JSON")
        val number = body.optString("number", "").trim()
        if (!isPhoneNumber(number)) return bad("invalid number")
        val sim = simSlot(body) ?: return bad("'sim' must be a slot number 0-7")
        val err = device.callDial(number, sim.slot)
        return if (err == null) ok() else Response(503, Json.error(err))
    }

    private class SimSlot(val slot: Int?)

    /** The optional `sim` field: absent means the default SIM; null return means invalid. */
    private fun simSlot(body: JSONObject): SimSlot? {
        if (!body.has("sim")) return SimSlot(null)
        val v = body.opt("sim")
        val slot = when (v) {
            is Int -> v
            is String -> v.toIntOrNull()
            else -> null
        }
        return if (slot != null && slot in 0..7) SimSlot(slot) else null
    }

    private fun callDtmf(req: Request): Response {
        val body = Json.body(req) ?: return bad("invalid JSON")
        val digits = body.optString("digits", "")
        if (digits.isEmpty() || digits.length > 32 || !digits.all(com.openu60.agent.call.CallStates::isDtmf)) {
            return bad("invalid digits")
        }
        return if (device.callDtmf(digits)) ok() else conflict("no active call")
    }

    private fun callMute(req: Request): Response {
        val body = Json.body(req) ?: return bad("invalid JSON")
        if (!body.has("enabled")) return bad("missing 'enabled'")
        val muted = body.optBoolean("enabled")
        return if (device.callMute(muted)) ok(JSONObject().put("muted", muted)) else conflict("no call")
    }

    private fun capabilities() = JSONObject()
        .put("platform", "android")
        .put("sms", true)
        .put("call_control", true)
        // Bridging call audio over Wi-Fi needs the shell PCM helper, not built yet.
        .put("call_audio", false)

    companion object {
        private const val MAX_SMS_CHARS = 1600

        private val KNOWN_PATHS = setOf(
            "/api/auth/logout", "/api/device", "/api/capabilities",
            "/api/sms/list", "/api/sms/capacity", "/api/sms/send", "/api/sms/delete", "/api/sms/read",
            "/api/call/status", "/api/call/dial", "/api/call/answer", "/api/call/hangup",
            "/api/call/dtmf", "/api/call/mute",
        )

        /** Digits with an optional leading +, and * # for service codes; 3–32 long. */
        fun isPhoneNumber(s: String): Boolean =
            s.length in 3..32 && s.withIndex().all { (i, c) -> c.isDigit() || c == '*' || c == '#' || (c == '+' && i == 0) }

        fun isLoopback(addr: String): Boolean =
            addr == "127.0.0.1" || addr == "::1" || addr == "0:0:0:0:0:0:0:1" || addr.startsWith("127.")

        private fun ok(data: Any? = JSONObject()) = Response(200, Json.ok(data))
        private fun bad(msg: String) = Response(400, Json.error(msg))
        private fun conflict(msg: String) = Response(409, Json.error(msg))
    }
}
