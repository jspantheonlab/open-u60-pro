package com.openu60.agent.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.TimeZone

class ZteSmsFormatTest {
    @Test fun ucs2RoundTripsChineseAndEmoji() {
        val text = "验证码 123456 😀"
        val hex = ZteSmsFormat.encodeUcs2Hex(text)
        assertEquals("9A8C", hex.substring(0, 4))
        // The emoji is a surrogate pair: two 4-digit units.
        assertEquals("D83DDE00", hex.takeLast(8))
        assertEquals(text, ZteSmsFormat.decodeUcs2Hex(hex))
    }

    @Test fun decodeRejectsNonHex() {
        assertNull(ZteSmsFormat.decodeUcs2Hex("004"))
        assertNull(ZteSmsFormat.decodeUcs2Hex("00ZZ"))
        assertEquals("", ZteSmsFormat.decodeUcs2Hex(""))
    }

    @Test fun dateUsesQuarterHourZone() {
        // 2026-10-26 09:05:07 UTC
        val t = 1793005507000L
        assertEquals("26,10,26,17,05,07,+32", ZteSmsFormat.formatDate(t, TimeZone.getTimeZone("Asia/Shanghai")))
        assertEquals("26,10,26,09,05,07,+0", ZteSmsFormat.formatDate(t, TimeZone.getTimeZone("UTC")))
        assertEquals("26,10,26,04,05,07,-20", ZteSmsFormat.formatDate(t, TimeZone.getTimeZone("GMT-05:00")))
    }

    @Test fun tags() {
        assertEquals(ZteSmsFormat.TAG_UNREAD, ZteSmsFormat.tagFor(1, read = false))
        assertEquals(ZteSmsFormat.TAG_READ, ZteSmsFormat.tagFor(1, read = true))
        assertEquals(ZteSmsFormat.TAG_SENT, ZteSmsFormat.tagFor(2, read = true))
        assertEquals(ZteSmsFormat.TAG_SENT, ZteSmsFormat.tagFor(4, read = true))
        assertEquals(ZteSmsFormat.TAG_FAILED, ZteSmsFormat.tagFor(5, read = true))
        assertEquals(ZteSmsFormat.TAG_DRAFT, ZteSmsFormat.tagFor(3, read = true))
    }

    @Test fun idList() {
        assertEquals(listOf(3681L, 3682L), ZteSmsFormat.parseIdList("3681;3682;"))
        assertEquals(listOf(1L, 2L), ZteSmsFormat.parseIdList("1, x;2"))
    }
}
