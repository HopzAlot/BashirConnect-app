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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Two things had to be fixed vs. a naive NetworkCallback approach, both
 * around the same root problem: registerNetworkCallback only gives an app
 * VISIBILITY into a network's existence, not permission to actually route
 * traffic through it when it's unvalidated and mobile data is the OS's
 * chosen default.
 *
 *  1. requestNetwork() (not registerNetworkCallback) is what actually
 *     reserves usage rights to Wi-Fi for this app, even while it's
 *     unvalidated.
 *  2. bindProcessToNetwork() during the actual check forces ALL of this
 *     app's traffic over that specific network for the duration of the
 *     check, then releases the bind immediately after — belt-and-braces
 *     on top of PortalLoginClient's own per-request socket + DNS binding.
 *
 * We also don't rely on Android's NET_CAPABILITY_CAPTIVE_PORTAL flag as
 * the trigger — it isn't reliably raised on a secondary network when a
 * validated network (mobile data) already exists as the default. Instead,
 * the moment ANY Wi-Fi network connects (TRANSPORT_WIFI, checked
 * directly), we actively check it ourselves on a short retry loop until
 * it's confirmed online.
 */
class CaptivePortalService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var credentialStore: CredentialStore
    private lateinit var statsStore: StatsStore

    private var wifiNetwork: Network? = null
    private var confirmedOnlineForNetwork: Network? = null
    private var pollingJob: Job? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            if (!isWifi) return // deliberately ignore cellular here — Wi-Fi only

            if (wifiNetwork != network) {
                wifiNetwork = network
                confirmedOnlineForNetwork = null
                AppStatus.appendLog("Wi-Fi network detected, checking it directly")
                startPolling(network)
            }
        }

        override fun onLost(network: Network) {
            if (network == wifiNetwork) {
                AppStatus.appendLog("Wi-Fi lost, back to watching")
                wifiNetwork = null
                confirmedOnlineForNetwork = null
                pollingJob?.cancel()
                AppStatus.update(ConnectionState.WATCHING)
                updateNotification("Watching for captive portals...")
            }
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

        // requestNetwork, not registerNetworkCallback — see class doc.
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.requestNetwork(request, networkCallback)

        return START_STICKY
    }

    /**
     * Retries every 10s until this Wi-Fi network is confirmed online (either
     * a successful login or a plain "already online" result), then stops.
     * Cancelled early if Wi-Fi is lost or swapped for a different network.
     */
    private fun startPolling(network: Network) {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            while (wifiNetwork == network && confirmedOnlineForNetwork != network) {
                checkNetwork(network)
                delay(10_000)
            }
        }
    }

    private suspend fun checkNetwork(network: Network) {
        val username = credentialStore.getUsername()
        val password = credentialStore.getPassword()
        if (username == null || password == null) {
            AppStatus.appendLog("Wi-Fi connected but no saved credentials yet")
            return
        }

        AppStatus.update(ConnectionState.LOGGING_IN)
        val startTime = System.currentTimeMillis()
        val client = PortalLoginClient(network)

        // Forces ALL of this app's traffic through Wi-Fi for the duration
        // of the check, released in the finally block right after — we
        // don't want to permanently pin the whole app off mobile data.
        connectivityManager.bindProcessToNetwork(network)

        try {
            when (val result = client.attemptLogin(username, password)) {
                is PortalLoginClient.Result.LoggedIn -> {
                    confirmedOnlineForNetwork = network
                    statsStore.recordLogin(System.currentTimeMillis() - startTime)
                    AppStatus.update(ConnectionState.LOGGED_IN)
                    AppStatus.appendLog(result.message)
                    updateNotification("Logged in ✅")
                }
                is PortalLoginClient.Result.AlreadyOnline -> {
                    confirmedOnlineForNetwork = network
                    AppStatus.update(ConnectionState.WATCHING)
                    AppStatus.appendLog(result.message)
                    updateNotification("Watching for captive portals...")
                }
                is PortalLoginClient.Result.NoPortalYet -> {
                    AppStatus.update(ConnectionState.WATCHING)
                    AppStatus.appendLog(result.message)
                }
                is PortalLoginClient.Result.Failure -> {
                    AppStatus.update(ConnectionState.ERROR)
                    AppStatus.appendLog("Check failed, retrying: ${result.error}")
                    updateNotification("Retrying login...")
                }
            }
        } finally {
            connectivityManager.bindProcessToNetwork(null)
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
        connectivityManager.bindProcessToNetwork(null)
        pollingJob?.cancel()
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
            StatsStore(context).markStopped()
            context.stopService(Intent(context, CaptivePortalService::class.java))
        }
    }
}