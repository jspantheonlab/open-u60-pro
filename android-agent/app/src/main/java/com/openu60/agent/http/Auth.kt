package com.openu60.agent.http

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Bearer-token auth matching zte-agent: random 64-hex tokens, 1 h idle / 24 h max,
 * and a login rate limit. Never log the password or a token.
 *
 * [clock] is monotonic milliseconds (SystemClock.elapsedRealtime on the device), so
 * expiry survives wall-clock changes and counts time asleep.
 */
class Auth(
    private val store: PasswordStore,
    private val clock: () -> Long,
    private val random: SecureRandom = SecureRandom(),
) {
    interface PasswordStore {
        /** "salt-hex:sha256-hex", or null when no password is set. */
        fun load(): String?
        fun save(value: String)
    }

    sealed class Login {
        data class Ok(val token: String) : Login()
        object NoPassword : Login()
        object BadPassword : Login()
        data class RateLimited(val retryAfterSecs: Long) : Login()
    }

    private class Session(val created: Long, var lastUsed: Long)

    private val sessions = HashMap<String, Session>()
    private var failures = 0
    private var blockedUntil = 0L

    val hasPassword: Boolean get() = store.load() != null

    @Synchronized
    fun setPassword(password: String) {
        require(password.length >= MIN_PASSWORD) { "password must be at least $MIN_PASSWORD characters" }
        val salt = ByteArray(16).also(random::nextBytes)
        store.save(hex(salt) + ":" + hex(hash(salt, password)))
        // A new password ends every session.
        sessions.clear()
    }

    @Synchronized
    fun login(password: String): Login {
        val now = clock()
        if (now < blockedUntil) return Login.RateLimited(((blockedUntil - now) + 999) / 1000)
        val stored = store.load() ?: return Login.NoPassword
        val (saltHex, hashHex) = stored.split(":", limit = 2).takeIf { it.size == 2 }
            ?: return Login.NoPassword
        val ok = MessageDigest.isEqual(hash(unhex(saltHex), password), unhex(hashHex))
        if (!ok) {
            failures++
            if (failures >= FREE_ATTEMPTS) {
                // 1 s, 2 s, 4 s ... capped at 10 s, like zte-agent's loopback cap.
                val delay = minOf(MAX_BLOCK_MS, 1000L shl minOf(failures - FREE_ATTEMPTS, 4))
                blockedUntil = now + delay
            }
            return Login.BadPassword
        }
        failures = 0
        prune(now)
        val token = ByteArray(32).also(random::nextBytes).let(::hex)
        sessions[token] = Session(now, now)
        return Login.Ok(token)
    }

    /** True when [token] is live; refreshes its idle timer. */
    @Synchronized
    fun check(token: String?): Boolean {
        if (token == null) return false
        val now = clock()
        val s = sessions[token] ?: return false
        if (now - s.lastUsed > IDLE_MS || now - s.created > MAX_MS) {
            sessions.remove(token)
            return false
        }
        s.lastUsed = now
        return true
    }

    @Synchronized
    fun logout(token: String) {
        sessions.remove(token)
    }

    private fun prune(now: Long) {
        sessions.entries.removeAll { (_, s) -> now - s.lastUsed > IDLE_MS || now - s.created > MAX_MS }
        while (sessions.size >= MAX_SESSIONS) {
            sessions.remove(sessions.minByOrNull { it.value.lastUsed }!!.key)
        }
    }

    companion object {
        const val MIN_PASSWORD = 8
        const val IDLE_MS = 60 * 60 * 1000L
        const val MAX_MS = 24 * 60 * 60 * 1000L
        private const val MAX_SESSIONS = 32
        private const val FREE_ATTEMPTS = 5
        private const val MAX_BLOCK_MS = 10_000L

        private fun hash(salt: ByteArray, password: String): ByteArray {
            val md = MessageDigest.getInstance("SHA-256")
            md.update(salt)
            return md.digest(password.toByteArray(Charsets.UTF_8))
        }

        fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }

        private fun unhex(s: String): ByteArray =
            ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}
