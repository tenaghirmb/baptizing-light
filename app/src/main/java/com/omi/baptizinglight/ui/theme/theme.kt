package com.omi.baptizinglight.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark theme
private val DarkColorScheme = darkColorScheme(
    primary = HolyGoldDark,
    secondary = Teal200,
    background = Color(0xFF121212),
    onBackground = Color.White
)

// light theme
private val LightColorScheme = lightColorScheme(
    primary = HolyGoldLight,
    secondary = Teal500,
    background = Color(0xFFFDFDFD),
    onBackground = Color.Black
)

@Composable
fun BaptizingLightTheme(
    // color scheme determination
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // Inject Material Theme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}