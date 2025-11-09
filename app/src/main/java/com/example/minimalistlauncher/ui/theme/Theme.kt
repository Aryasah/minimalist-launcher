package com.example.minimalistlauncher.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.example.minimalistlauncher.FontManager
import com.example.minimalistlauncher.DataStoreManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
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
    val context = LocalContext.current
    val store = remember { DataStoreManager(context) }
    val fontSize by store.launcherFontSizeFlow.collectAsState(initial = 16)

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = buildTypography(FontManager.composeFontFamily, fontSize),
        shapes = Shapes(),
        content = content
    )
}

fun buildTypography(dynamicFamily: FontFamily?, baseSp: Int): Typography {
    val family = dynamicFamily ?: InterFontFamily
    // derive sizes from baseSp
    val bodyLarge = baseSp.sp
    val bodyMedium = (baseSp - 2).coerceAtLeast(10).sp
    val titleMedium = (baseSp + 2).sp
    val titleLarge = (baseSp + 6).sp

    return Typography(
        titleLarge = androidx.compose.ui.text.TextStyle(fontFamily = family, fontSize = titleLarge),
        titleMedium = androidx.compose.ui.text.TextStyle(fontFamily = family, fontSize = titleMedium),
        bodyLarge = androidx.compose.ui.text.TextStyle(fontFamily = family, fontSize = bodyLarge),
        bodyMedium = androidx.compose.ui.text.TextStyle(fontFamily = family, fontSize = bodyMedium)
    )
}
