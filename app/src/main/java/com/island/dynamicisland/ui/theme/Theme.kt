package com.island.dynamicisland.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFEDEDF2),
    background = Color(0xFF000000),
    surface = Color(0xFF0B0B0F),
    onBackground = Color(0xFFEDEDF2),
    onSurface = Color(0xFFEDEDF2),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF111114),
    background = Color(0xFFF5F5F7),
    surface = Color(0xFFFFFFFF),
)

@Composable
fun DynamicIslandTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = IslandTypography,
        content = content,
    )
}
