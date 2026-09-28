package com.mrbashir.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Formats elapsed streak milliseconds into readable time (e.g., "2d 4h", "3h 15m", "45m").
 */
fun formatStreak(streakMs: Long): String {
    if (streakMs <= 0) return "—"
    val totalMinutes = streakMs / 60_000
    val days = totalMinutes / (60 * 24)
    val hours = (totalMinutes / 60) % 24
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

/**
 * Formats average login speed in milliseconds to seconds (e.g. "1.4s").
 */
fun formatSpeed(avgSpeedMs: Long): String {
    if (avgSpeedMs <= 0) return "—"
    return "%.1fs".format(avgSpeedMs / 1000.0)
}

/**
 * Single stat card in the mini-dashboard with clear typography hierarchy:
 * Fredoka numbers/values and Nunito labels.
 */
@Composable
fun StatCard(
    emoji: String,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(10.dp)
        ) {
            Text(emoji, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
        }
    }
}

/**
 * 3-stat card row mini-dashboard (Streak, Logins today, Avg speed).
 */
@Composable
fun StatsDashboard(
    streakMs: Long,
    loginsToday: Int,
    avgSpeedMs: Long,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        StatCard(
            emoji = "🔥",
            value = formatStreak(streakMs),
            label = "running",
            modifier = Modifier.weight(1f)
        )
        StatCard(
            emoji = "⚡",
            value = "$loginsToday",
            label = "logins today",
            modifier = Modifier.weight(1f)
        )
        StatCard(
            emoji = "⏱",
            value = formatSpeed(avgSpeedMs),
            label = "avg speed",
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Overload accepting the AppStats [Stats] data class.
 */
@Composable
fun StatsDashboard(
    stats: Stats,
    modifier: Modifier = Modifier
) {
    StatsDashboard(
        streakMs = stats.streakMs,
        loginsToday = stats.loginsToday,
        avgSpeedMs = stats.avgSpeedMs,
        modifier = modifier
    )
}

/**
 * Interface contract compatibility overload matching PROJECT.md.
 */
@Composable
fun StatsDashboard(
    streakDays: Int,
    loginsToday: Int,
    avgSpeedBps: Long,
    modifier: Modifier = Modifier
) {
    val streakMs = streakDays.toLong() * 24 * 60 * 60 * 1000
    StatsDashboard(
        streakMs = streakMs,
        loginsToday = loginsToday,
        avgSpeedMs = avgSpeedBps,
        modifier = modifier
    )
}
