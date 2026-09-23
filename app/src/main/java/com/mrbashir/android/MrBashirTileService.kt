package com.mrbashir.android

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Quick Settings tile — lets you toggle Mr. Bashir from the pulldown
 * shade without opening the app, same way you'd toggle flashlight or
 * airplane mode. The user adds it themselves after install via the
 * shade's "Edit tiles" (pencil) button; there's no way for an app to
 * add itself automatically.
 *
 * Three states:
 *  - UNAVAILABLE: no credentials saved yet — tapping opens the app
 *    instead of silently doing nothing.
 *  - ACTIVE: service running, watching for captive portals.
 *  - INACTIVE: credentials saved but stopped.
 *
 * isRunning is determined by StatsStore.isServiceEnabled() rather than
 * AppStatus.state — AppStatus is an in-process singleton that resets to
 * IDLE on every process start (including START_STICKY restarts), making
 * it unreliable for the tile. StatsStore persists to disk and accurately
 * reflects user intent across process restarts.
 */
class MrBashirTileService : TileService() {

    private var listeningScope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()

        // Keep the tile live-updated (e.g. flips to "Watching" the moment
        // a login succeeds) for as long as the shade has it visible.
        val scope = CoroutineScope(Dispatchers.Main + Job())
        listeningScope = scope
        scope.launch {
            AppStatus.state.collect { refreshTile() }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        listeningScope?.cancel()
        listeningScope = null
    }

    override fun onClick() {
        super.onClick()

        val credentialStore = CredentialStore(this)
        if (!credentialStore.hasCredentials()) {
            openApp()
            return
        }

        // Use StatsStore (disk-backed) rather than AppStatus.state (in-memory).
        // If the process was restarted by START_STICKY, AppStatus is IDLE even
        // though the service is running — StatsStore tells the truth.
        val isRunning = StatsStore(this).isServiceEnabled()
        if (isRunning) {
            CaptivePortalService.stop(this)
        } else {
            CaptivePortalService.start(this)
        }
        refreshTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val credentialStore = CredentialStore(this)
        // Same reasoning as onClick: StatsStore survives process restarts.
        val isRunning = StatsStore(this).isServiceEnabled()

        tile.state = when {
            !credentialStore.hasCredentials() -> Tile.STATE_UNAVAILABLE
            isRunning -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = "BashirConnect"
        tile.subtitle = when {
            !credentialStore.hasCredentials() -> "Set up in app"
            isRunning -> "Watching"
            else -> "Stopped"
        }
        tile.updateTile()
    }
}
