package com.mrbashir.android

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Centered hero section card containing the mascot with animated speech bubble
 * and real-time status title and subtitle.
 */
@Composable
fun HeroCard(
    dialogue: String,
    statusTitle: String,
    statusSubtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            AnimatedMascot(dialogue = dialogue)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = statusTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = statusSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Convenient overload deriving dialogue and status text from app state.
 */
@Composable
fun HeroCard(
    hasCredentials: Boolean,
    isRunning: Boolean,
    state: ConnectionState,
    modifier: Modifier = Modifier
) {
    val dialogue = getMascotDialogue(hasCredentials, isRunning, state)
    val (statusTitle, statusSubtitle) = when {
        !hasCredentials -> "Not set up yet" to "Enter your details once and Mr. Bashir takes it from here"
        state == ConnectionState.LOGGED_IN -> "Logged in!" to "Mr. Bashir handled it while you weren't looking"
        !isRunning -> "Paused" to "Mr. Bashir won't auto-login until you start again"
        state == ConnectionState.LOGGING_IN -> "Connecting..." to "Talking to university portal..."
        else -> "Watching" to "Mr. Bashir is keeping an eye on your networks"
    }
    HeroCard(
        dialogue = dialogue,
        statusTitle = statusTitle,
        statusSubtitle = statusSubtitle,
        modifier = modifier
    )
}
