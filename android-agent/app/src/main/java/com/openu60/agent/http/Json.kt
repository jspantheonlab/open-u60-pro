package com.openu60.agent.http

import org.json.JSONObject

/** zte-agent's envelope: `{"ok":true,"data":...}` or `{"ok":false,"error":"..."}`. */
object Json {
    fun ok(data: Any? = JSONObject()): ByteArray =
        JSONObject().put("ok", true).put("data", data ?: JSONObject.NULL).toString().toByteArray()

    fun error(message: String): ByteArray =
        JSONObject().put("ok", false).put("error", message).toString().toByteArray()

    /** The request body as an object; an empty body is `{}`. Null when it is not a JSON object. */
    fun body(req: Request): JSONObject? {
        if (req.body.isEmpty()) return JSONObject()
        return runCatching { JSONObject(String(req.body, Charsets.UTF_8)) }.getOrNull()
    }
}
