package com.mrbashir.android

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * Animates the mascot artwork with transforms only (translation + scale) —
 * the source image (res/drawable-xxhdpi/mascot_wizard.png) is never modified,
 * redrawn, or regenerated. Two infinite loops, out of phase, give a gentle
 * "idle" feel: a slow vertical bob and a slower breathing scale.
 */
@Composable
fun AnimatedMascot(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "mascot")

    val bobOffset by transition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob"
    )

    val breathScale by transition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    Image(
        painter = painterResource(id = R.drawable.mascot_wizard),
        contentDescription = "Mr. Bashir",
        modifier = modifier
            .size(160.dp)
            .graphicsLayer {
                translationY = bobOffset
                scaleX = breathScale
                scaleY = breathScale
            }
    )
}
