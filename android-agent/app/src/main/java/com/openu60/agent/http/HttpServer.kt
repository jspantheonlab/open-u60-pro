package com.openu60.agent.http

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class Request(
    val method: String,
    val path: String,
    val query: String,
    val headers: Map<String, String>,
    val body: ByteArray,
    val remote: String,
) {
    fun header(name: String): String? = headers[name.lowercase()]

    fun bearer(): String? = header("authorization")
        ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
        ?.substring(7)?.trim()
}

class Response(val status: Int, val body: ByteArray, val contentType: String = "application/json") {
    val extraHeaders = LinkedHashMap<String, String>()
}

/**
 * A small blocking HTTP/1.1 server: one request per connection (`Connection: close`),
 * bounded body and header sizes, a bounded worker pool. The API is a handful of small
 * JSON calls from one or two phones, so this is all it needs and adds no dependency.
 */
class HttpServer(
    private val bind: InetSocketAddress,
    private val handler: (Request) -> Response,
    workers: Int = 4,
    /** Connections whose local address fails this are closed without a reply. */
    private val acceptLocal: (java.net.InetAddress) -> Boolean = { true },
) {
    private val pool: ExecutorService = ThreadPoolExecutor(
        1, workers, 30, TimeUnit.SECONDS, SynchronousQueue(),
    ) { r -> Thread(r, "http-worker").apply { isDaemon = true } }
    @Volatile private var socket: ServerSocket? = null
    private var acceptThread: Thread? = null

    val port: Int get() = socket?.localPort ?: -1

    fun start() {
        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(bind)
        socket = ss
        acceptThread = Thread({ acceptLoop(ss) }, "http-accept").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        socket?.close()
        socket = null
        pool.shutdownNow()
    }

    private fun acceptLoop(ss: ServerSocket) {
        while (!ss.isClosed) {
            val client = try {
                ss.accept()
            } catch (_: Exception) {
                break
            }
            if (!acceptLocal(client.localAddress)) {
                runCatching { client.close() }
                continue
            }
            try {
                pool.execute { serve(client) }
            } catch (_: RejectedExecutionException) {
                // Every worker busy: answer like zte-agent's gate instead of queueing forever.
                runCatching {
                    client.soTimeout = 1000
                    write(client.getOutputStream(), Response(503, Json.error("agent busy, retry")).apply {
                        extraHeaders["Retry-After"] = "1"
                    })
                }
                runCatching { client.close() }
            }
        }
    }

    private fun serve(client: Socket) {
        client.use { s ->
            s.soTimeout = READ_TIMEOUT_MS
            val response = try {
                val req = read(BufferedInputStream(s.getInputStream()), s.inetAddress.hostAddress ?: "")
                    ?: return
                try {
                    handler(req)
                } catch (e: Exception) {
                    Response(500, Json.error("internal error: ${e.javaClass.simpleName}"))
                }
            } catch (e: BadRequest) {
                Response(e.status, Json.error(e.message ?: "bad request"))
            } catch (_: SocketTimeoutException) {
                return
            }
            runCatching { write(s.getOutputStream(), response) }
        }
    }

    class BadRequest(val status: Int, message: String) : Exception(message)

    companion object {
        private const val READ_TIMEOUT_MS = 10_000
        private const val MAX_HEADER_BYTES = 16 * 1024
        const val MAX_BODY_BYTES = 256 * 1024

        /** Parses one request; null on a connection closed before any byte. */
        fun read(input: InputStream, remote: String): Request? {
            val head = readHead(input) ?: return null
            val lines = head.split("\r\n")
            val parts = lines[0].split(" ")
            if (parts.size != 3 || !parts[2].startsWith("HTTP/1.")) throw BadRequest(400, "bad request line")
            val headers = HashMap<String, String>()
            for (line in lines.drop(1)) {
                if (line.isEmpty()) continue
                val colon = line.indexOf(':')
                if (colon <= 0) throw BadRequest(400, "bad header")
                headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            }
            if (headers["transfer-encoding"] != null) throw BadRequest(501, "chunked bodies not supported")
            val length = headers["content-length"]?.let {
                it.toIntOrNull()?.takeIf { n -> n >= 0 } ?: throw BadRequest(400, "bad content-length")
            } ?: 0
            if (length > MAX_BODY_BYTES) throw BadRequest(413, "body too large")
            val body = ByteArray(length)
            var off = 0
            while (off < length) {
                val n = input.read(body, off, length - off)
                if (n < 0) throw BadRequest(400, "truncated body")
                off += n
            }
            val target = parts[1]
            val q = target.indexOf('?')
            return Request(
                method = parts[0],
                path = if (q >= 0) target.substring(0, q) else target,
                query = if (q >= 0) target.substring(q + 1) else "",
                headers = headers,
                body = body,
                remote = remote,
            )
        }

        private fun readHead(input: InputStream): String? {
            val buf = ByteArrayOutputStream()
            var matched = 0
            val end = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
            while (true) {
                val b = input.read()
                if (b < 0) {
                    if (buf.size() == 0) return null
                    throw BadRequest(400, "truncated headers")
                }
                buf.write(b)
                if (buf.size() > MAX_HEADER_BYTES) throw BadRequest(431, "headers too large")
                matched = if (b.toByte() == end[matched]) matched + 1 else if (b == '\r'.code) 1 else 0
                if (matched == 4) break
            }
            val bytes = buf.toByteArray()
            return String(bytes, 0, bytes.size - 4, Charsets.ISO_8859_1)
        }

        fun write(out: OutputStream, r: Response) {
            val sb = StringBuilder()
            sb.append("HTTP/1.1 ").append(r.status).append(' ').append(reason(r.status)).append("\r\n")
            sb.append("Content-Type: ").append(r.contentType).append("\r\n")
            sb.append("Content-Length: ").append(r.body.size).append("\r\n")
            sb.append("Cache-Control: no-store\r\n")
            sb.append("Connection: close\r\n")
            for ((k, v) in r.extraHeaders) sb.append(k).append(": ").append(v).append("\r\n")
            sb.append("\r\n")
            out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
            out.write(r.body)
            out.flush()
        }

        private fun reason(status: Int) = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            409 -> "Conflict"
            413 -> "Payload Too Large"
            429 -> "Too Many Requests"
            431 -> "Request Header Fields Too Large"
            501 -> "Not Implemented"
            503 -> "Service Unavailable"
            else -> "Status"
        }
    }
}
