package com.example.minimalistlauncher.ui.theme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFBBBBBB),
    onPrimary = Color.Black,
    background = Color(0xFF000000),
    onBackground = Color.White,
    surface = Color(0xFF111111),
    onSurface = Color.White
)

@Composable
fun MinimalLauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}
