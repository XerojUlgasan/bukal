package com.example.bukal.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val BukalLightColorScheme = lightColorScheme(
    primary = BukalPrimary,
    onPrimary = BukalSurface,
    primaryContainer = BukalPrimaryContainer,
    onPrimaryContainer = BukalText,
    secondary = BukalAccent,
    onSecondary = BukalText,
    background = BukalBackground,
    onBackground = BukalText,
    surface = BukalSurface,
    onSurface = BukalText,
    surfaceVariant = BukalPrimaryContainer,
    onSurfaceVariant = BukalMutedText,
    outline = BukalOutline,
    error = BukalError,
)

@Composable
fun BukalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BukalLightColorScheme,
        typography = BukalTypography,
        content = content,
    )
}
