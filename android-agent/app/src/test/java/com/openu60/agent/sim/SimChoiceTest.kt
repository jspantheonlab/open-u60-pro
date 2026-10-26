package com.openu60.agent.sim

import com.openu60.agent.sim.SimChoice.Pick
import org.junit.Assert.assertEquals
import org.junit.Test

class SimChoiceTest {
    private val physical = Sim(slot = 0, subId = 3, name = "CMCC", embedded = false)
    private val second = Sim(slot = 1, subId = 7, name = "CT", embedded = false)
    private val both = listOf(second, physical)

    @Test fun requestedSlotWins() {
        assertEquals(Pick.Use(second), SimChoice.pick(1, defaultSubId = 3, active = both))
    }

    @Test fun requestedSlotMustBeActive() {
        assertEquals(Pick.Error("no active SIM in slot 1"), SimChoice.pick(1, 3, listOf(physical)))
    }

    @Test fun systemDefaultOtherwise() {
        assertEquals(Pick.Use(second), SimChoice.pick(null, defaultSubId = 7, active = both))
    }

    @Test fun askEveryTimeFallsBackToLowestSlot() {
        // SubscriptionManager.INVALID_SUBSCRIPTION_ID is -1.
        assertEquals(Pick.Use(physical), SimChoice.pick(null, defaultSubId = -1, active = both))
    }

    @Test fun noSim() {
        assertEquals(Pick.Error("no active SIM"), SimChoice.pick(null, -1, emptyList()))
    }
}
