package com.mrbashir.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Operational controls and secondary destructive action buttons.
 * Groups Start / Stop as primary operational controls with distinct visual weight
 * (filled vs outlined), and places the destructive "Forget me" button in a clearly
 * separated position with a red-tinted outline.
 */
@Composable
fun ActionButtons(
    isRunning: Boolean,
    hasCredentials: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!hasCredentials) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onStart,
                enabled = !isRunning,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Start", style = MaterialTheme.typography.labelLarge)
            }

            OutlinedButton(
                onClick = onStop,
                enabled = isRunning,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Stop", style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onForget,
            enabled = hasCredentials,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Forget me", style = MaterialTheme.typography.labelLarge)
        }
    }
}
