package com.openu60.agent.call

/**
 * Telecom call states to the `+CLCC` stat names zte-agent reports (and the apps switch on):
 * active, held, dialing, alerting, incoming, waiting, releasing.
 * Values are android.telecom.Call.STATE_*; kept as literals so this is JVM-testable.
 */
object CallStates {
    const val NEW = 0
    const val DIALING = 1
    const val RINGING = 2
    const val HOLDING = 3
    const val ACTIVE = 4
    const val DISCONNECTED = 7
    const val SELECT_PHONE_ACCOUNT = 8
    const val CONNECTING = 9
    const val DISCONNECTING = 10
    const val PULLING_CALL = 11
    const val AUDIO_PROCESSING = 12
    const val SIMULATED_RINGING = 13

    // android.telecom.Call.Details.DIRECTION_*
    const val DIRECTION_INCOMING = 0
    const val DIRECTION_OUTGOING = 1

    /**
     * Null for calls that are gone ([DISCONNECTED]) and should not be listed.
     * A ringing call while another call exists is "waiting" (call waiting), as in CLCC.
     */
    fun stat(state: Int, anotherCallLive: Boolean): String? = when (state) {
        NEW, SELECT_PHONE_ACCOUNT, CONNECTING, PULLING_CALL -> "dialing"
        DIALING -> "alerting"
        RINGING, SIMULATED_RINGING -> if (anotherCallLive) "waiting" else "incoming"
        HOLDING -> "held"
        ACTIVE, AUDIO_PROCESSING -> "active"
        DISCONNECTING -> "releasing"
        DISCONNECTED -> null
        else -> null
    }

    fun dir(direction: Int): String = if (direction == DIRECTION_INCOMING) "mt" else "mo"

    /** DTMF digits Telecom accepts. */
    fun isDtmf(c: Char): Boolean = c in '0'..'9' || c == '*' || c == '#' || c in 'A'..'D'
}
