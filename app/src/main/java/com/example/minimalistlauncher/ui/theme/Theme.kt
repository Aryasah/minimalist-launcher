package com.example.minimalistlauncher.ui.theme
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.minimalistlauncher.FontManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

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
        typography = buildTypography(FontManager.composeFontFamily),
        shapes = Shapes(),
        content = content
    )
}

fun buildTypography(dynamicFamily: FontFamily?): Typography {
    val family = dynamicFamily ?: InterFontFamily   // InterFontFamily is the fallback from Type.kt
    return Typography(
        titleLarge = TextStyle(fontFamily = family, fontSize = 20.sp),
        titleMedium = TextStyle(fontFamily = family, fontSize = 16.sp),
        bodyLarge = TextStyle(fontFamily = family, fontSize = 16.sp),
        bodyMedium = TextStyle(fontFamily = family, fontSize = 14.sp)
    )
}