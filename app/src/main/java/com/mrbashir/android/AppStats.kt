package com.mrbashir.android

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Stats(val streakMs: Long, val loginsToday: Int, val avgSpeedMs: Long)

/**
 * Live-observable stats, same pub/sub pattern as AppStatus. StatsStore is
 * the only writer (after persisting to SharedPreferences); the UI just
 * observes.
 */
object AppStats {
    private val _stats = MutableStateFlow(Stats(streakMs = 0, loginsToday = 0, avgSpeedMs = 0))
    val stats = _stats.asStateFlow()

    fun update(stats: Stats) {
        _stats.value = stats
    }
}
