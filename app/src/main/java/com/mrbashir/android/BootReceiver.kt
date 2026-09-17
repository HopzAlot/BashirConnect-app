package com.mrbashir.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Android equivalent of the Windows Startup-folder copy / Linux .desktop
 * autostart file: relaunches the service after a reboot, but only if
 * credentials are already saved (so we don't spin up a service with
 * nothing to do).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val credentialStore = CredentialStore(context)
            val statsStore = StatsStore(context)
            // Only auto-resume if the user hadn't deliberately stopped it
            // before the reboot — otherwise a Stop gets silently undone
            // every time the phone restarts.
            if (credentialStore.hasCredentials() && statsStore.isServiceEnabled()) {
                CaptivePortalService.start(context)
            }
        }
    }
}
