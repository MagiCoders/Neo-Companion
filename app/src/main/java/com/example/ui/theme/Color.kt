package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Visual theme modes supporting the strict DESIGN.md Dark Aurora specification (#07090F / #0E121B)
 * as the default, plus the Warm Paper Light Mode shown in the "- light.png" design screens.
 */
enum class MochiVisualStyle(
    val id: String,
    val label: String,
    val subtitle: String,
    val bg: Color,
    val bgRaised: Color,
    val secondaryGlow: Color,
    val cardBorderAlpha: Float,
    val isLight: Boolean = false,
    val isCelShaded: Boolean = false,
    val isHologram: Boolean = false
) {
    STUDIO_4K_OPTICAL(
        id = "studio_4k",
        label = "Dark Aurora (DESIGN.md)",
        subtitle = "Deep #07090F stage with #0E121B raised sheets and character-accent AGSL aurora",
        bg = Color(0xFF07090F),
        bgRaised = Color(0xFF0E121B),
        secondaryGlow = Color(0xFF4FD1A5),
        cardBorderAlpha = 0.16f,
        isLight = false
    ),
    WARM_STUDIO_LUXE(
        id = "warm_luxe",
        label = "Warm Paper (Light)",
        subtitle = "Soft warm ivory #F6F3EC canvas with crisp white cards from the light mode designs",
        bg = Color(0xFFF6F3EC),
        bgRaised = Color(0xFFFFFFFF),
        secondaryGlow = Color(0xFFD4A359),
        cardBorderAlpha = 0.14f,
        isLight = true
    ),
    ANIME_STUDIO_POP(
        id = "anime_pop",
        label = "Cel Studio",
        subtitle = "Crisp cel-shaded 3D rim light on #07090F stage",
        bg = Color(0xFF07090F),
        bgRaised = Color(0xFF0E121B),
        secondaryGlow = Color(0xFF5AB8FF),
        cardBorderAlpha = 0.16f,
        isLight = false,
        isCelShaded = true
    ),
    CYBER_LUXE_NEON(
        id = "cyber_luxe",
        label = "Culinary Amber",
        subtitle = "Warm #F5B04C studio rim lighting on #07090F stage",
        bg = Color(0xFF07090F),
        bgRaised = Color(0xFF0E121B),
        secondaryGlow = Color(0xFFF5B04C),
        cardBorderAlpha = 0.16f,
        isLight = false
    ),
    HOLOGRAPHIC_HUD(
        id = "holo_hud",
        label = "Midnight Slate",
        subtitle = "Minimal high-contrast #07090F stage with subtle hairline borders",
        bg = Color(0xFF07090F),
        bgRaised = Color(0xFF0E121B),
        secondaryGlow = Color(0xFF4FD1A5),
        cardBorderAlpha = 0.16f,
        isLight = false
    )
}

/**
 * Exact Color Tokens from DESIGN.md & CHARACTERS.md.
 */
object MochiTokens {
    val bg = Color(0xFF07090F) // App background (#07090F)
    val bgRaised = Color(0xFF0E121B) // Sheets with no blur behind them (#0E121B)
    val bgRaisedFallback = Color(0xEB0E121B) // Below Android 12: bgRaised at 92% opacity

    val textPrimary = Color(0xFFF3F5F9) // Main text (#F3F5F9)
    val textSecondary = Color(0xA3F3F5F9) // Supporting text (#F3F5F9 at 64%)
    val textTertiary = Color(0x66F3F5F9) // Hints, disabled (#F3F5F9 at 40%)

    val hairline = Color(0x1AFFFFFF) // Borders, dividers (#FFFFFF at 10%)
    val glassTint = Color(0x0FFFFFFF) // Glass tint (#FFFFFF at 6%)
    val glassTopHighlight = Color(0x29FFFFFF) // Top edge highlight (#FFFFFF at 16%)

    val danger = Color(0xFFFF5A5F) // Errors only (#FF5A5F)
    val success = Color(0xFF4FD1A5)

    // Three-point lighting tokens for 3D character stage
    val primaryGold = Color(0xFFE5B869)
    val studioPearl = Color(0xFFF3F5F9)
    val keyLightIvory = Color(0xFFFFF4E6)

    // Character Accents from CHARACTERS.md
    val accentElena = Color(0xFF4FD1A5) // Nurse Elena (#4FD1A5)
    val accentKaito = Color(0xFF5AB8FF) // Kaito Storm (#5AB8FF)
    val accentMarco = Color(0xFFF5B04C) // Chef Marco (#F5B04C)

    // Light mode companion tokens (matching the uploaded "- light.png" reference screens)
    val lightBg = Color(0xFFF6F3EC)
    val lightBgRaised = Color(0xFFFFFFFF)
    val lightTextPrimary = Color(0xFF17181A)
    val lightTextSecondary = Color(0xFF64666B)
    val lightTextTertiary = Color(0xFF909298)
    val lightHairline = Color(0x1A000000)
    val warmGoldAccent = Color(0xFFD4A359)

    fun adaptiveTextPrimary(isDark: Boolean): Color = if (isDark) textPrimary else lightTextPrimary
    fun adaptiveTextSecondary(isDark: Boolean): Color = if (isDark) textSecondary else lightTextSecondary
    fun adaptiveBgRaised(isDark: Boolean): Color = if (isDark) bgRaisedFallback else lightBgRaised
    fun adaptiveHairline(isDark: Boolean): Color = if (isDark) hairline else lightHairline
}

