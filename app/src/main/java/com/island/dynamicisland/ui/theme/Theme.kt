package com.island.dynamicisland.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** The island's signature green. Light mode uses a deeper shade so text keeps 4.5:1 contrast. */
private val AccentDark = Color(0xFF34C759)
private val AccentLight = Color(0xFF1E7B34)

private val DarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF12351E),
    onPrimaryContainer = Color(0xFF57D982),
    secondaryContainer = Color(0xFF2E2E34),
    onSecondaryContainer = Color.White,
    background = Color(0xFF000000),
    onBackground = Color(0xFFEDEDF2),
    surface = Color(0xFF0B0B0F),
    onSurface = Color(0xFFEDEDF2),
    surfaceVariant = Color(0xFF2A2A2E),
    onSurfaceVariant = Color(0xFF9A9AA0),
    surfaceContainer = Color(0xFF16161A),
    surfaceContainerHighest = Color(0xFF2E2E34),
    outline = Color(0xFF6A6A70),
    outlineVariant = Color(0xFF2E2E34),
)

private val LightColors = lightColorScheme(
    primary = AccentLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7F5DF),
    onPrimaryContainer = Color(0xFF0E4D1F),
    secondaryContainer = Color(0xFFE6E6EB),
    onSecondaryContainer = Color(0xFF111114),
    background = Color(0xFFF5F5F7),
    onBackground = Color(0xFF111114),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111114),
    surfaceVariant = Color(0xFFE6E6EB),
    onSurfaceVariant = Color(0xFF5C5C63),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFE6E6EB),
    outline = Color(0xFF8A8A90),
    outlineVariant = Color(0xFFD9D9DE),
)

@Composable
fun DynamicIslandTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = IslandTypography,
        content = content,
    )
}
