package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val ManropeFontFamily = FontFamily(
    Font(R.font.manrope, FontWeight.Normal),
    Font(R.font.manrope, FontWeight.Medium),
    Font(R.font.manrope, FontWeight.SemiBold)
)

/**
 * Exact 4 Typography Styles from DESIGN.md using Manrope:
 * - Display: 34 / 40 sp, weight 600, tracking -0.5sp
 * - Title:   22 / 28 sp, weight 600, tracking -0.2sp
 * - Body:    16 / 24 sp, weight 400, tracking  0.sp
 * - Label:   13 / 18 sp, weight 500, tracking +0.2sp
 */
object MochiType {
    val Display = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.W600,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    )

    val Title = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.W600,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp
    )

    val Body = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.W400,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    )

    val Label = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.W500,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    )
}

val MochiTypography = Typography(
    displayLarge = MochiType.Display,
    displayMedium = MochiType.Display,
    displaySmall = MochiType.Display,
    headlineLarge = MochiType.Display,
    headlineMedium = MochiType.Title,
    headlineSmall = MochiType.Title,
    titleLarge = MochiType.Title,
    titleMedium = MochiType.Title,
    titleSmall = MochiType.Label,
    bodyLarge = MochiType.Body,
    bodyMedium = MochiType.Body,
    bodySmall = MochiType.Label,
    labelLarge = MochiType.Label,
    labelMedium = MochiType.Label,
    labelSmall = MochiType.Label
)
