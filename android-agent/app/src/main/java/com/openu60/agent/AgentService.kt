package com.openu60.agent

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.openu60.agent.http.Auth
import com.openu60.agent.http.HttpServer
import com.openu60.agent.http.InterfacePolicy
import java.net.InetSocketAddress

/** Foreground service that owns the HTTP API on :9090. */
class AgentService : Service() {
    private var server: HttpServer? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            // Android 12/13 (U30 Air) predate the specialUse type.
            startForeground(NOTIFICATION_ID, notification())
        }
        val auth = Auth(PrefsPasswordStore(this), SystemClock::elapsedRealtime)
        val api = Api(auth, AndroidDevice(applicationContext))
        val s = HttpServer(InetSocketAddress(PORT), api::handle, acceptLocal = InterfacePolicy::allows)
        try {
            s.start()
            server = s
            Log.i(TAG, "listening on :$PORT")
        } catch (e: Exception) {
            Log.e(TAG, "cannot listen on :$PORT: ${e.message}")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        server?.stop()
        server = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.app_name), NotificationManager.IMPORTANCE_MIN),
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("API on :$PORT")
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setOngoing(true)
            .build()
    }

    private class PrefsPasswordStore(context: Context) : Auth.PasswordStore {
        private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
        override fun load(): String? = prefs.getString("password", null)
        // commit, not apply: the password must be on disk before the API reports success.
        @SuppressLint("ApplySharedPref")
        override fun save(value: String) {
            prefs.edit().putString("password", value).commit()
        }
    }

    companion object {
        private const val TAG = "OpenU60Agent"
        const val PORT = 9090
        private const val CHANNEL = "agent"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, AgentService::class.java))
        }
    }
}
