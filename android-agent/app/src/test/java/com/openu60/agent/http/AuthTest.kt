package com.openu60.agent.http

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthTest {
    private class MemStore : Auth.PasswordStore {
        var v: String? = null
        override fun load() = v
        override fun save(value: String) { v = value }
    }

    private var now = 0L
    private val store = MemStore()
    private val auth = Auth(store, { now })

    @Test fun noPasswordRefusesLogin() {
        assertEquals(Auth.Login.NoPassword, auth.login("anything1"))
    }

    @Test fun storesSaltedHashNotPassword() {
        auth.setPassword("correct horse")
        assertFalse(store.v!!.contains("correct"))
        assertEquals(2, store.v!!.split(":").size)
    }

    @Test fun loginIssues64HexTokenThatExpiresWhenIdle() {
        auth.setPassword("correct horse")
        val r = auth.login("correct horse") as Auth.Login.Ok
        assertTrue(r.token.matches(Regex("[0-9a-f]{64}")))
        assertTrue(auth.check(r.token))
        now += Auth.IDLE_MS - 1
        assertTrue(auth.check(r.token)) // refreshed
        now += Auth.IDLE_MS + 1
        assertFalse(auth.check(r.token))
    }

    @Test fun maxLifetimeEvenWhenUsed() {
        auth.setPassword("correct horse")
        val t = (auth.login("correct horse") as Auth.Login.Ok).token
        while (now < Auth.MAX_MS) { now += 30 * 60 * 1000L; auth.check(t) }
        now += 1
        assertFalse(auth.check(t))
    }

    @Test fun rateLimitsAfterFiveFailures() {
        auth.setPassword("correct horse")
        repeat(4) { assertEquals(Auth.Login.BadPassword, auth.login("wrong")) }
        assertEquals(Auth.Login.BadPassword, auth.login("wrong")) // 5th arms a 1 s block
        assertTrue(auth.login("correct horse") is Auth.Login.RateLimited)
        now += 1000
        assertTrue(auth.login("correct horse") is Auth.Login.Ok)
    }

    @Test fun newPasswordEndsSessions() {
        auth.setPassword("correct horse")
        val t = (auth.login("correct horse") as Auth.Login.Ok).token
        auth.setPassword("battery staple")
        assertFalse(auth.check(t))
    }

    @Test(expected = IllegalArgumentException::class)
    fun shortPasswordRejected() {
        auth.setPassword("short")
    }
}
