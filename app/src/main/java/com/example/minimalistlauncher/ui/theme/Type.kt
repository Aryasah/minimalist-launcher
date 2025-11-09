package com.example.minimalistlauncher.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.example.minimalistlauncher.R

// Add two font files to res/font: inter_regular.ttf and inter_medium.ttf (or any fonts you prefer)
val InterFontFamily = FontFamily(
        Font(R.font.inter),
        Font(R.font.inter_medium)
)

val AppTypography = Typography(
        titleLarge = TextStyle(
                fontFamily = InterFontFamily,
                fontSize = 20.sp
        ),
        titleMedium = TextStyle(
                fontFamily = InterFontFamily,
                fontSize = 16.sp
        ),
        bodyLarge = TextStyle(
                fontFamily = InterFontFamily,
                fontSize = 16.sp
        ),
        bodyMedium = TextStyle(
                fontFamily = InterFontFamily,
                fontSize = 14.sp
        )
)
