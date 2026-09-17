package com.mrbashir.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Where the Python script had a `while True: poll neverssl every 10-60s`
 * loop, this is event-driven: Android tells us the instant a network
 * gains or loses the captive-portal capability, via NetworkCallback.
 * No polling, near-zero battery cost while idle.
 */
class CaptivePortalService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var credentialStore: CredentialStore
    private lateinit var statsStore: StatsStore

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {

        override fun onCapabilitiesChanged(
            network: Network,
            capabilities: NetworkCapabilities
        ) {
            val isCaptivePortal = capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL
            )

            if (isCaptivePortal) {
                handlePortalDetected(network)
            }
        }

        override fun onLost(network: Network) {
            AppStatus.update(ConnectionState.WATCHING)
            AppStatus.appendLog("Network lost, back to watching")
        }
    }

    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        credentialStore = CredentialStore(this)
        statsStore = StatsStore(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Watching for captive portals..."))
        AppStatus.update(ConnectionState.WATCHING)
        AppStatus.appendLog("Mr. Bashir woke up, watching networks")
        statsStore.markStarted()

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, networkCallback)

        return START_STICKY
    }

    private fun handlePortalDetected(network: Network) {
        val username = credentialStore.getUsername()
        val password = credentialStore.getPassword()

        if (username == null || password == null) {
            AppStatus.appendLog("Portal detected but no saved credentials yet")
            return
        }

        AppStatus.update(ConnectionState.PORTAL_DETECTED)
        AppStatus.appendLog("Captive portal detected, logging in...")
        updateNotification("Portal detected, logging in...")

        serviceScope.launch {
            AppStatus.update(ConnectionState.LOGGING_IN)
            val startTime = System.currentTimeMillis()

            val client = PortalLoginClient(network)
            when (val result = client.attemptLogin(username, password)) {
                is PortalLoginClient.Result.LoggedIn -> {
                    val durationMs = System.currentTimeMillis() - startTime
                    statsStore.recordLogin(durationMs)
                    AppStatus.update(ConnectionState.LOGGED_IN)
                    AppStatus.appendLog(result.message)
                    updateNotification("Logged in ✅")
                }
                is PortalLoginClient.Result.AlreadyOnline -> {
                    AppStatus.update(ConnectionState.WATCHING)
                    AppStatus.appendLog(result.message)
                }
                is PortalLoginClient.Result.NoPortalYet -> {
                    AppStatus.update(ConnectionState.WATCHING)
                    AppStatus.appendLog(result.message)
                }
                is PortalLoginClient.Result.Failure -> {
                    AppStatus.update(ConnectionState.ERROR)
                    AppStatus.appendLog("Error: ${result.error}")
                    updateNotification("Login failed, will retry on next network change")
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BashirConnect Wi-Fi login",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BashirConnect")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        connectivityManager.unregisterNetworkCallback(networkCallback)
        AppStatus.update(ConnectionState.IDLE)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "mr_bashir_channel"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, CaptivePortalService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            // markStopped() lives here, not in onDestroy(), so it only
            // fires on a genuine user-requested stop — not when the OS
            // kills and START_STICKY silently restarts the service.
            StatsStore(context).markStopped()
            context.stopService(Intent(context, CaptivePortalService::class.java))
        }
    }
}
