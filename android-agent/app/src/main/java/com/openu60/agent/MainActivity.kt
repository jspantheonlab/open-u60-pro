package com.openu60.agent

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/**
 * Status screen. It exists mainly because the default dialer role requires an activity
 * that handles ACTION_DIAL; opening it also (re)starts the agent.
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AgentService.start(this)
        setContentView(TextView(this).apply {
            text = getString(R.string.status_text, AgentService.PORT)
            setPadding(32, 32, 32, 32)
        })
    }
}
