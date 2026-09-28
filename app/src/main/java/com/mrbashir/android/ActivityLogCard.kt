package com.mrbashir.android

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

typealias ActivityLogEntry = LogLine

/**
 * Collapsible activity log card displaying live connection logs in monospace font
 * with dimmed timestamps.
 */
@Composable
fun ActivityLogCard(
    logs: List<ActivityLogEntry>,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent activity",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (expanded) "▲" else "▼",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                if (logs.isEmpty()) {
                    Text(
                        text = "No activity recorded yet",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .heightIn(max = 160.dp)
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        items(logs.reversed()) { line ->
                            Row(modifier = Modifier.padding(vertical = 3.dp)) {
                                Text(
                                    text = line.timestamp,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                    modifier = Modifier.width(68.dp)
                                )
                                Text(
                                    text = line.message,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interface contract overload managing expand/collapse state internally.
 * Matches PROJECT.md interface contract.
 */
@Composable
fun ActivityLogCard(
    logs: List<ActivityLogEntry>,
    modifier: Modifier = Modifier,
    initialExpanded: Boolean = false
) {
    var expanded by remember { mutableStateOf(initialExpanded) }
    ActivityLogCard(
        logs = logs,
        expanded = expanded,
        onToggleExpand = { expanded = !expanded },
        modifier = modifier
    )
}
