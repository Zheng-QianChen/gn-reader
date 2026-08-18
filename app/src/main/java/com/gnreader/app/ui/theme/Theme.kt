package com.gnreader.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF2F5EA8),
    onPrimary = Color.White,
    background = Color(0xFFF7F4EE),
    onBackground = Color(0xFF1C1916),
    surface = Color(0xFFFFFBF5),
    onSurface = Color(0xFF1C1916),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8BB4F0),
    onPrimary = Color(0xFF0B1B33),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE8E6E3),
    surface = Color(0xFF1A1A1A),
    onSurface = Color(0xFFE8E6E3),
)

@Composable
fun GnReaderTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        content = content,
    )
}
