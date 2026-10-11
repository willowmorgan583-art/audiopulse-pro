package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val StudioColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = ObsidianBg,
    primaryContainer = StudioCardSurface,
    onPrimaryContainer = NeonCyan,
    secondary = AmberAnalog,
    onSecondary = ObsidianBg,
    secondaryContainer = StudioDeckSurface,
    onSecondaryContainer = AmberGlow,
    tertiary = ElectricViolet,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = StudioDeckSurface,
    onSurface = TextPrimary,
    surfaceVariant = StudioCardSurface,
    onSurfaceVariant = TextSecondary,
    outline = StudioBorder,
    error = PeakCoral,
    onError = TextPrimary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StudioColorScheme,
        typography = Typography,
        content = content
    )
}
