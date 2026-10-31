package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalCharacterAccent = staticCompositionLocalOf { MochiTokens.accentElena }
val LocalVisualStyle = staticCompositionLocalOf { MochiVisualStyle.STUDIO_4K_OPTICAL }
val LocalMochiDarkTheme = staticCompositionLocalOf { true }

@Composable
fun MochiTheme(
    characterAccent: Color = MochiTokens.accentElena,
    visualStyle: MochiVisualStyle = MochiVisualStyle.STUDIO_4K_OPTICAL,
    content: @Composable () -> Unit
) {
    val animatedAccent by animateColorAsState(
        targetValue = characterAccent,
        animationSpec = tween(durationMillis = 600),
        label = "theme_accent_crossfade"
    )
    val animatedBg by animateColorAsState(
        targetValue = visualStyle.bg,
        animationSpec = tween(durationMillis = 600),
        label = "theme_bg_crossfade"
    )
    val animatedSurface by animateColorAsState(
        targetValue = visualStyle.bgRaised,
        animationSpec = tween(durationMillis = 600),
        label = "theme_surface_crossfade"
    )

    val colorScheme = if (visualStyle.isLight) {
        lightColorScheme(
            primary = animatedAccent,
            onPrimary = MochiTokens.lightTextPrimary,
            secondary = visualStyle.secondaryGlow,
            onSecondary = MochiTokens.lightTextPrimary,
            background = animatedBg,
            onBackground = MochiTokens.lightTextPrimary,
            surface = animatedSurface,
            onSurface = MochiTokens.lightTextPrimary,
            surfaceVariant = animatedSurface,
            onSurfaceVariant = MochiTokens.lightTextSecondary,
            outline = MochiTokens.lightHairline,
            error = MochiTokens.danger,
            onError = Color.White
        )
    } else {
        darkColorScheme(
            primary = animatedAccent,
            onPrimary = MochiTokens.bg,
            secondary = visualStyle.secondaryGlow,
            onSecondary = MochiTokens.textPrimary,
            background = animatedBg,
            onBackground = MochiTokens.textPrimary,
            surface = animatedSurface,
            onSurface = MochiTokens.textPrimary,
            surfaceVariant = animatedSurface,
            onSurfaceVariant = MochiTokens.textSecondary,
            outline = MochiTokens.hairline,
            error = MochiTokens.danger,
            onError = MochiTokens.textPrimary
        )
    }

    CompositionLocalProvider(
        LocalCharacterAccent provides animatedAccent,
        LocalVisualStyle provides visualStyle,
        LocalMochiDarkTheme provides !visualStyle.isLight
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MochiTypography,
            shapes = MochiMaterialShapes,
            content = content
        )
    }
}

@Composable
fun CadenceTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MochiTheme(content = content)
}
