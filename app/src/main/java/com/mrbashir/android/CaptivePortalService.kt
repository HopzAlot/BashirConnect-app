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
 * We do NOT rely on Android's NET_CAPABILITY_CAPTIVE_PORTAL flag as the
 * trigger — that flag isn't reliably raised on a secondary network (Wi-Fi)
 * when a validated network (mobile data) already exists as the OS's
 * preferred default. From the OS's point of view you already have working
 * internet, so it may never bother flagging Wi-Fi as captive at all, and
 * our old capability-triggered check simply never fired.
 *
 * Instead: the moment ANY Wi-Fi network connects (TRANSPORT_WIFI, checked
 * directly, independent of the captive flag), we actively check it
 * ourselves on a short retry loop until it's confirmed online — every
 * check still fully bound to that specific network (socket + DNS, see
 * PortalLoginClient) so mobile data can never interfere with the check
 * itself, only with whether we bother checking.
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

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)

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