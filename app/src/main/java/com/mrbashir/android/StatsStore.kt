package com.mrbashir.android

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Not sensitive data, so plain SharedPreferences rather than
 * CredentialStore's encrypted store.
 *
 * "Streak" here means continuous runtime since the last explicit Start —
 * NOT consecutive calendar days. It resets to 0 the moment the user (or
 * the QS tile) stops the service, and survives a phone reboot untouched
 * (BootReceiver resuming the service after a restart is not a new
 * "start" from the user's point of view — Mr. Bashir was never
 * deliberately stopped, so the clock keeps running through the gap).
 */
class StatsStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("mr_bashir_stats", Context.MODE_PRIVATE)
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun today(): String = dayFormat.format(Date())

    /**
     * Call when the service transitions to running — safe to call every
     * time onStartCommand fires (including a reboot-triggered restart),
     * because it's idempotent: it only stamps a NEW start time if one
     * isn't already set. That's what keeps the streak intact across a
     * reboot instead of resetting it just because Android restarted the
     * process.
     */
    fun markStarted() {
        val editor = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, true)
        if (prefs.getLong(KEY_RUNNING_SINCE_MS, 0L) == 0L) {
            editor.putLong(KEY_RUNNING_SINCE_MS, System.currentTimeMillis())
        }
        editor.apply()
        AppStats.update(currentStats())
    }

    /** Call when the service is explicitly stopped (button or tile). */
    fun markStopped() {
        prefs.edit()
            .putLong(KEY_RUNNING_SINCE_MS, 0L)
            .putBoolean(KEY_SERVICE_ENABLED, false)
            .apply()
        AppStats.update(currentStats())
    }

    /** Whether the service should be running — survives reboot, used by BootReceiver. */
    fun isServiceEnabled(): Boolean = prefs.getBoolean(KEY_SERVICE_ENABLED, false)

    /** Call this once per successful portal login, with how long it took. */
    fun recordLogin(durationMs: Long) {
        val todayStr = today()
        val storedDay = prefs.getString(KEY_LOGINS_DAY, null)
        val loginsToday = (if (storedDay == todayStr) prefs.getInt(KEY_LOGINS_TODAY, 0) else 0) + 1

        val totalCount = prefs.getLong(KEY_SPEED_COUNT, 0) + 1
        val totalTimeMs = prefs.getLong(KEY_SPEED_TOTAL_MS, 0) + durationMs

        prefs.edit()
            .putString(KEY_LOGINS_DAY, todayStr)
            .putInt(KEY_LOGINS_TODAY, loginsToday)
            .putLong(KEY_SPEED_COUNT, totalCount)
            .putLong(KEY_SPEED_TOTAL_MS, totalTimeMs)
            .apply()

        AppStats.update(currentStats())
    }

    /** Read current stats fresh (recomputes elapsed streak time) — no writes. */
    fun currentStats(): Stats {
        val todayStr = today()
        val storedDay = prefs.getString(KEY_LOGINS_DAY, null)
        val loginsToday = if (storedDay == todayStr) prefs.getInt(KEY_LOGINS_TODAY, 0) else 0

        val runningSinceMs = prefs.getLong(KEY_RUNNING_SINCE_MS, 0L)
        val streakMs = if (runningSinceMs > 0) System.currentTimeMillis() - runningSinceMs else 0L

        val count = prefs.getLong(KEY_SPEED_COUNT, 0)
        val avgSpeedMs = if (count > 0) prefs.getLong(KEY_SPEED_TOTAL_MS, 0) / count else 0L

        return Stats(streakMs = streakMs, loginsToday = loginsToday, avgSpeedMs = avgSpeedMs)
    }

    companion object {
        private const val KEY_LOGINS_DAY = "logins_day"
        private const val KEY_LOGINS_TODAY = "logins_today"
        private const val KEY_RUNNING_SINCE_MS = "running_since_ms"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_SPEED_COUNT = "speed_count"
        private const val KEY_SPEED_TOTAL_MS = "speed_total_ms"
    }
}
