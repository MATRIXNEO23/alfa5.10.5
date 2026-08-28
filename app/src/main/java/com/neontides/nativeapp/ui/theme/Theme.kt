package com.neontides.nativeapp.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NeonDark = darkColorScheme(
    primary = Color(0xFFFF3E81),
    secondary = Color(0xFF8B7CFF),
    tertiary = Color(0xFF4FD7D2),
    background = Color(0xFF0B0E18),
    surface = Color(0xFF151827),
    onPrimary = Color.White,
    onBackground = Color(0xFFF4F5FA),
    onSurface = Color(0xFFF4F5FA)
)

@Composable
fun NeonTidesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NeonDark,
        typography = Typography(),
        content = content
    )
}
