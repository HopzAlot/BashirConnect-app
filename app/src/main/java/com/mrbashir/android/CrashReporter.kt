package com.mrbashir.android

import android.content.Context
import android.content.SharedPreferences

/**
 * Zero-dependency crash reporter for development builds.
 *
 * Install via [install] once in Application or MainActivity.onCreate().
 * When an uncaught exception kills the process, the stack trace is written
 * to plain SharedPreferences. On next launch, call [consumePendingCrash]
 * — it returns the saved trace (and clears it) so the UI can show it.
 *
 * This lets you debug crashes without USB/Logcat/Termux.
 */
object CrashReporter {

    private const val PREFS = "mr_bashir_crash"
    private const val KEY_TRACE = "last_crash"
    private const val MAX_LEN = 8_000 // chars — keeps SharedPreferences happy

    fun install(context: Context) {
        val appContext = context.applicationContext
        val default = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val trace = buildString {
                    append("Thread: ${thread.name}\n\n")
                    append(throwable.stackTraceToString())
                }.take(MAX_LEN)

                appContext
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_TRACE, trace)
                    .commit() // commit (not apply) — process is dying, we need it flushed NOW
            } catch (_: Exception) { /* never crash the crash handler */ }

            // Let the system default handler finish (shows the "app stopped" dialog)
            default?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Returns the stack trace from the previous crash, or null if no crash
     * was recorded. Clears the saved trace so it's only shown once.
     */
    fun consumePendingCrash(context: Context): String? {
        val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val trace = prefs.getString(KEY_TRACE, null) ?: return null
        prefs.edit().remove(KEY_TRACE).apply()
        return trace
    }
}
