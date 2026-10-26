package com.openu60.agent.http

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class HttpServerTest {
    private fun parse(raw: String) = HttpServer.read(ByteArrayInputStream(raw.toByteArray()), "1.2.3.4")

    @Test fun parsesRequestWithBody() {
        val r = parse("POST /api/sms/list?x=1 HTTP/1.1\r\nHost: a\r\nAuthorization: Bearer abc\r\nContent-Length: 2\r\n\r\n{}")!!
        assertEquals("POST", r.method)
        assertEquals("/api/sms/list", r.path)
        assertEquals("x=1", r.query)
        assertEquals("abc", r.bearer())
        assertEquals("{}", String(r.body))
    }

    @Test fun emptyConnectionIsNull() {
        assertNull(parse(""))
    }

    @Test(expected = HttpServer.BadRequest::class)
    fun rejectsOversizedBody() {
        parse("POST / HTTP/1.1\r\nContent-Length: ${HttpServer.MAX_BODY_BYTES + 1}\r\n\r\n")
    }

    @Test(expected = HttpServer.BadRequest::class)
    fun rejectsChunked() {
        parse("POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n")
    }

    @Test fun writesStatusAndLength() {
        val out = ByteArrayOutputStream()
        HttpServer.write(out, Response(200, "{}".toByteArray()))
        val s = out.toString()
        assertEquals("HTTP/1.1 200 OK", s.lineSequence().first())
        assert(s.contains("Content-Length: 2\r\n"))
        assert(s.endsWith("\r\n\r\n{}"))
    }
}
