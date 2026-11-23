package com.example.calendar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DaymarkColors = darkColorScheme(
    primary = Color(0xFFA8E9CC),
    onPrimary = Color(0xFF11362A),
    primaryContainer = Color(0xFF203C33),
    onPrimaryContainer = Color(0xFFC9F5E1),
    secondary = Color(0xFFCDC0FA),
    onSecondary = Color(0xFF31254D),
    secondaryContainer = Color(0xFF342D49),
    onSecondaryContainer = Color(0xFFE9DEFF),
    tertiary = Color(0xFFFFC8A8),
    tertiaryContainer = Color(0xFF48332B),
    onTertiaryContainer = Color(0xFFFFDBC5),
    background = Color(0xFF131719),
    onBackground = Color(0xFFEDF1EE),
    surface = Color(0xFF1A1F22),
    onSurface = Color(0xFFEDF1EE),
    surfaceVariant = Color(0xFF2A3134),
    onSurfaceVariant = Color(0xFFB4BFBB),
    surfaceContainer = Color(0xFF202628),
    surfaceContainerHigh = Color(0xFF262D30),
    outline = Color(0xFF85928E),
    outlineVariant = Color(0xFF394443)
)

@Composable
fun CalendarTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DaymarkColors, typography = Typography, content = content)
}

