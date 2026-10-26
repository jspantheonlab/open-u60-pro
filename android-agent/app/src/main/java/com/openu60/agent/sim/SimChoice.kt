package com.openu60.agent.sim

/** An active SIM subscription. No number, IMSI or ICCID: the API never exposes them. */
data class Sim(val slot: Int, val subId: Int, val name: String, val embedded: Boolean)

/**
 * Which SIM an SMS or call goes out on. Dual-SIM hotspots (F50 Pro) may have no default
 * ("ask every time"), and a screenless device cannot answer that prompt: an SMS would be
 * dropped and a call would sit in SELECT_PHONE_ACCOUNT. So there is always an answer.
 */
object SimChoice {
    sealed interface Pick {
        data class Use(val sim: Sim) : Pick
        data class Error(val message: String) : Pick
    }

    /**
     * [requestedSlot] (from the API's optional `sim`) wins; otherwise the system default
     * [defaultSubId] if it is active; otherwise the lowest slot.
     */
    fun pick(requestedSlot: Int?, defaultSubId: Int, active: List<Sim>): Pick {
        if (active.isEmpty()) return Pick.Error("no active SIM")
        if (requestedSlot != null) {
            return active.firstOrNull { it.slot == requestedSlot }?.let { Pick.Use(it) }
                ?: Pick.Error("no active SIM in slot $requestedSlot")
        }
        val sim = active.firstOrNull { it.subId == defaultSubId } ?: active.minBy { it.slot }
        return Pick.Use(sim)
    }
}
