package com.openu60.agent.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallStatesTest {
    @Test fun mapsTelecomStatesToClccNames() {
        assertEquals("dialing", CallStates.stat(CallStates.CONNECTING, false))
        assertEquals("alerting", CallStates.stat(CallStates.DIALING, false))
        assertEquals("incoming", CallStates.stat(CallStates.RINGING, false))
        assertEquals("waiting", CallStates.stat(CallStates.RINGING, true))
        assertEquals("active", CallStates.stat(CallStates.ACTIVE, false))
        assertEquals("held", CallStates.stat(CallStates.HOLDING, true))
        assertEquals("releasing", CallStates.stat(CallStates.DISCONNECTING, false))
        assertNull(CallStates.stat(CallStates.DISCONNECTED, false))
    }

    @Test fun direction() {
        assertEquals("mt", CallStates.dir(CallStates.DIRECTION_INCOMING))
        assertEquals("mo", CallStates.dir(CallStates.DIRECTION_OUTGOING))
    }

    @Test fun dtmf() {
        assertTrue("0123456789*#ABCD".all(CallStates::isDtmf))
        assertFalse(CallStates.isDtmf('e'))
        assertFalse(CallStates.isDtmf(','))
    }
}
