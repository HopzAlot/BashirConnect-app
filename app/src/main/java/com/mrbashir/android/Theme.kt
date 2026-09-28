package com.mrbashir.android

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

val LightColorScheme = lightColorScheme(
    primary = ChaiAmberLight,
    onPrimary = OnChaiAmberLight,
    primaryContainer = ChaiAmberContainerLight,
    onPrimaryContainer = OnChaiAmberContainerLight,
    secondary = MascotTealLight,
    onSecondary = OnMascotTealLight,
    secondaryContainer = MascotTealContainerLight,
    onSecondaryContainer = OnMascotTealContainerLight,
    background = WarmBackgroundLight,
    onBackground = WarmOnSurfaceLight,
    surface = WarmSurfaceLight,
    onSurface = WarmOnSurfaceLight,
    surfaceVariant = WarmSurfaceVariantLight,
    onSurfaceVariant = WarmOnSurfaceVariantLight,
    outline = WarmOutlineLight,
    outlineVariant = WarmOutlineVariantLight,
    error = ErrorRedLight,
    onError = OnErrorRedLight
)

val DarkColorScheme = darkColorScheme(
    primary = ChaiAmberDark,
    onPrimary = OnChaiAmberDark,
    primaryContainer = ChaiAmberContainerDark,
    onPrimaryContainer = OnChaiAmberContainerDark,
    secondary = MascotTealDark,
    onSecondary = OnMascotTealDark,
    secondaryContainer = MascotTealContainerDark,
    onSecondaryContainer = OnMascotTealContainerDark,
    background = WarmBackgroundDark,
    onBackground = WarmOnSurfaceDark,
    surface = WarmSurfaceDark,
    onSurface = WarmOnSurfaceDark,
    surfaceVariant = WarmSurfaceVariantDark,
    onSurfaceVariant = WarmOnSurfaceVariantDark,
    outline = WarmOutlineDark,
    outlineVariant = WarmOutlineVariantDark,
    error = ErrorRedDark,
    onError = OnErrorRedDark
)

@Composable
fun MrBashirTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MrBashirTypography,
        content = content
    )
}
