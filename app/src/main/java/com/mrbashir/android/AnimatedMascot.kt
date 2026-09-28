package com.mrbashir.android

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Custom Shape producing a comic-book speech cloud with rounded corners
 * and a downward-pointing triangular tail directed at the mascot.
 *
 * Uses a single continuous closed perimeter Path without internal seams,
 * ensuring clean rendering with borders, elevation shadows, and background fills.
 */
class SpeechBubbleShape(
    val cornerRadius: Dp = 16.dp,
    val tailWidth: Dp = 14.dp,
    val tailHeight: Dp = 10.dp,
    val tailOffsetPercent: Float = 0.5f
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        if (size.width <= 0f || size.height <= 0f) {
            return Outline.Generic(Path())
        }

        val tailHPx = with(density) { tailHeight.toPx() }
        val tailWPx = with(density) { tailWidth.toPx() }
        val rawRPx = with(density) { cornerRadius.toPx() }

        // The bubble body height excludes the tail
        val bubbleH = (size.height - tailHPx).coerceAtLeast(0f)
        val maxR = minOf(size.width / 2f, bubbleH / 2f)
        val r = rawRPx.coerceIn(0f, maxR.coerceAtLeast(0f))

        // Tail coordinates along the bottom edge
        val tailCenterX = size.width * tailOffsetPercent.coerceIn(0f, 1f)
        val tailLeft = (tailCenterX - tailWPx / 2f).coerceIn(r, (size.width - r - tailWPx).coerceAtLeast(r))
        val tailRight = (tailLeft + tailWPx).coerceAtMost((size.width - r).coerceAtLeast(tailLeft))
        val tailTipX = (tailLeft + tailRight) / 2f
        val tailTipY = size.height

        val path = Path().apply {
            // Start at top-left corner (after the curve)
            moveTo(r, 0f)

            // Top edge
            lineTo(size.width - r, 0f)
            // Top-right corner arc
            if (r > 0f) {
                quadraticBezierTo(size.width, 0f, size.width, r)
            }

            // Right edge
            lineTo(size.width, bubbleH - r)
            // Bottom-right corner arc
            if (r > 0f) {
                quadraticBezierTo(size.width, bubbleH, size.width - r, bubbleH)
            }

            // Bottom edge to the right side of the tail
            lineTo(tailRight, bubbleH)
            // Tail pointing down to the tip
            lineTo(tailTipX, tailTipY)
            // Tail going back up to bubble bottom edge
            lineTo(tailLeft, bubbleH)

            // Bottom edge to the bottom-left corner
            lineTo(r, bubbleH)
            // Bottom-left corner arc
            if (r > 0f) {
                quadraticBezierTo(0f, bubbleH, 0f, bubbleH - r)
            }

            // Left edge
            lineTo(0f, r)
            // Top-left corner arc
            if (r > 0f) {
                quadraticBezierTo(0f, 0f, r, 0f)
            }

            close()
        }

        return Outline.Generic(path)
    }
}

/**
 * Maps the live application state to the corresponding desi personality dialogue for Mr. Bashir.
 *
 * Precedence / mapping:
 * 1. No credentials saved -> "Pehle details daalo janaab, ek baar poochh loon phir nahi 😤"
 * 2. Stopped / Paused -> "Bashir Saab chai break pe hai ☕"
 * 3. Actively logging in -> "Connection jod raha hoon... ⚡"
 * 4. Logged in successfully -> "Done ho gaya janaab! ✅"
 * 5. Service starting / Watching -> "Bashir Saab on duty! 🫡"
 */
fun getMascotDialogue(
    hasCredentials: Boolean,
    isRunning: Boolean,
    state: ConnectionState
): String {
    return when {
        !hasCredentials -> "Pehle details daalo janaab, ek baar poochh loon phir nahi 😤"
        !isRunning -> "Bashir Saab chai break pe hai ☕"
        state == ConnectionState.LOGGING_IN -> "Connection jod raha hoon... ⚡"
        state == ConnectionState.LOGGED_IN -> "Done ho gaya janaab! ✅"
        else -> "Bashir Saab on duty! 🫡"
    }
}

/**
 * Compatibility overload allowing callers to pass `connectionState` as named parameter.
 */
@JvmName("getMascotDialogueConnectionState")
fun getMascotDialogue(
    hasCredentials: Boolean,
    isRunning: Boolean,
    connectionState: ConnectionState,
    compat: Unit = Unit
): String = getMascotDialogue(hasCredentials, isRunning, connectionState)

/**
 * Composable that displays dialogue text in a comic-book speech bubble.
 *
 * Animates in and out (fade + gentle scale) anchored at TransformOrigin(0.5f, 1.0f)
 * (the tail tip) when dialogue changes, styled with Fredoka typography and
 * theme colors (surface, onSurface, outline).
 */
@Composable
fun MascotSpeechBubble(
    dialogue: String = "",
    modifier: Modifier = Modifier,
    text: String = dialogue
) {
    val displayText = if (text.isNotEmpty()) text else dialogue

    AnimatedContent(
        targetState = displayText,
        transitionSpec = {
            (fadeIn(animationSpec = tween(durationMillis = 220, delayMillis = 60)) +
             scaleIn(
                 initialScale = 0.85f,
                 transformOrigin = TransformOrigin(0.5f, 1.0f),
                 animationSpec = tween(durationMillis = 220, delayMillis = 60, easing = FastOutSlowInEasing)
             ))
                .togetherWith(
                    fadeOut(animationSpec = tween(durationMillis = 140)) +
                    scaleOut(
                        targetScale = 0.85f,
                        transformOrigin = TransformOrigin(0.5f, 1.0f),
                        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing)
                    )
                )
        },
        label = "MascotSpeechBubbleAnimation",
        modifier = modifier
    ) { currentText ->
        if (currentText.isNotBlank()) {
            val bubbleShape = remember {
                SpeechBubbleShape(
                    cornerRadius = 16.dp,
                    tailWidth = 14.dp,
                    tailHeight = 10.dp
                )
            }

            Surface(
                shape = bubbleShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .widthIn(min = 140.dp, max = 290.dp)
                    .wrapContentSize()
            ) {
                Box(
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 10.dp,
                        bottom = 20.dp // 10dp content padding + 10dp tail height
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currentText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = Fredoka,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            lineHeight = 19.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

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

/**
 * Combined mascot composable displaying the speech bubble above the mascot.
 * The mascot's idle bob and breathe animations remain completely independent
 * and outside the speech bubble's animated transitions.
 */
@Composable
fun AnimatedMascot(
    dialogue: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        if (dialogue.isNotBlank()) {
            MascotSpeechBubble(
                dialogue = dialogue,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        AnimatedMascot()
    }
}