package com.taskmanager.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7A8BFF),
    onPrimary = Color(0xFF091028),
    secondary = Color(0xFFFF9E1A),
    onSecondary = Color(0xFF2B1800),
    tertiary = Color(0xFF47C1A8),
    background = Color(0xFF050505),
    onBackground = Color(0xFFF5F5F2),
    surface = Color(0xFF171717),
    onSurface = Color(0xFFF5F5F2),
    surfaceVariant = Color(0xFF242424),
    onSurfaceVariant = Color(0xFFB3B3AE),
    outline = Color(0xFF363636),
    error = Color(0xFFFF766B),
)

@Composable
fun TaskManagerTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography,
        content = content,
    )
}
