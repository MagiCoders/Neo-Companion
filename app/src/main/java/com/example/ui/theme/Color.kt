package com.example.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp

/**
 * The two palettes from the agreed design (Claude Design canvas, "MochiLife App Screens").
 * Every screen reads colours through [MochiTokens], which always points at the active palette,
 * so light mode can never end up with dark-mode text again.
 */
data class MochiPalette(
    val isLight: Boolean,
    val bg: Color,
    /** Cards on the page. */
    val card: Color,
    /** Inputs, chips, secondary buttons, nested rows. */
    val card2: Color,
    /** Bottom sheets, dialogs, floating bubbles (solid). */
    val sheet: Color,
    val border: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val gold: Color,
    val goldBg: Color,
    val danger: Color,
    val dangerSolid: Color,
    val scrim: Color,
    val toggleOff: Color,
    val onAccent: Color,
    val navBar: Color
) {
    companion object {
        val Dark = MochiPalette(
            isLight = false,
            bg = Color(0xFF141519),
            card = Color(0x0AFAF8F5),          // rgba(250,248,245,0.04)
            card2 = Color(0x14FAF8F5),         // rgba(250,248,245,0.08)
            sheet = Color(0xFF1C1E25),
            border = Color(0x17FAF8F5),        // rgba(250,248,245,0.09)
            divider = Color(0x0FFAF8F5),       // rgba(250,248,245,0.06)
            textPrimary = Color(0xFFFAF8F5),
            textSecondary = Color(0xFFC8C6C2),
            textTertiary = Color(0xFFA8A6A1),
            gold = Color(0xFFE5B869),
            goldBg = Color(0x29E5B869),
            danger = Color(0xFFFF8A8E),
            dangerSolid = Color(0xFFD93F45),
            scrim = Color(0x8C000000),         // rgba(0,0,0,0.55)
            toggleOff = Color(0x2EFAF8F5),     // rgba(250,248,245,0.18)
            onAccent = Color(0xFF0E1A15),
            navBar = Color(0xF2181A1F)
        )

        val Light = MochiPalette(
            isLight = true,
            bg = Color(0xFFF6F3EE),
            card = Color(0xFFFFFFFF),
            card2 = Color(0xFFF1EDE6),
            sheet = Color(0xFFFFFFFF),
            border = Color(0x141A1B20),        // rgba(26,27,32,0.08)
            divider = Color(0x0F1A1B20),       // rgba(26,27,32,0.06)
            textPrimary = Color(0xFF1A1B20),
            textSecondary = Color(0xFF5E5B55),
            textTertiary = Color(0xFF6E6A63),
            gold = Color(0xFF8A5D14),
            goldBg = Color(0x38E5B869),        // rgba(229,184,105,0.22)
            danger = Color(0xFFC2353B),
            dangerSolid = Color(0xFFC2353B),
            scrim = Color(0x611A1B20),         // rgba(26,27,32,0.38)
            toggleOff = Color(0x291A1B20),
            onAccent = Color(0xFF0E1A15),
            navBar = Color(0xF2FFFFFF)
        )
    }
}

/**
 * Visual theme modes. Only Dark and Light are part of the agreed design; the other entries are
 * kept so older saved settings still load, and they all use the dark palette.
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
        label = "Dark",
        subtitle = "Deep graphite with soft ivory text",
        bg = MochiPalette.Dark.bg,
        bgRaised = MochiPalette.Dark.sheet,
        secondaryGlow = Color(0xFFE5B869),
        cardBorderAlpha = 0.09f,
        isLight = false
    ),
    WARM_STUDIO_LUXE(
        id = "warm_luxe",
        label = "Light",
        subtitle = "Warm paper with crisp white cards",
        bg = MochiPalette.Light.bg,
        bgRaised = MochiPalette.Light.sheet,
        secondaryGlow = Color(0xFFE5B869),
        cardBorderAlpha = 0.08f,
        isLight = true
    ),
    ANIME_STUDIO_POP(
        id = "anime_pop",
        label = "Dark",
        subtitle = "Legacy style (uses Dark)",
        bg = MochiPalette.Dark.bg,
        bgRaised = MochiPalette.Dark.sheet,
        secondaryGlow = Color(0xFF5AB8FF),
        cardBorderAlpha = 0.09f,
        isLight = false,
        isCelShaded = true
    ),
    CYBER_LUXE_NEON(
        id = "cyber_luxe",
        label = "Dark",
        subtitle = "Legacy style (uses Dark)",
        bg = MochiPalette.Dark.bg,
        bgRaised = MochiPalette.Dark.sheet,
        secondaryGlow = Color(0xFFF5B04C),
        cardBorderAlpha = 0.09f,
        isLight = false
    ),
    HOLOGRAPHIC_HUD(
        id = "holo_hud",
        label = "Dark",
        subtitle = "Legacy style (uses Dark)",
        bg = MochiPalette.Dark.bg,
        bgRaised = MochiPalette.Dark.sheet,
        secondaryGlow = Color(0xFFE5B869),
        cardBorderAlpha = 0.09f,
        isLight = false
    )
}

/**
 * Colour tokens. The palette-backed values switch with the theme (see [MochiTheme]); reading them
 * from composition or from a draw block both update automatically when the theme changes.
 */
object MochiTokens {
    var palette: MochiPalette by mutableStateOf(MochiPalette.Dark)
        internal set

    val isLight: Boolean get() = palette.isLight

    val bg: Color get() = palette.bg
    val card: Color get() = palette.card
    val card2: Color get() = palette.card2
    val sheet: Color get() = palette.sheet
    val border: Color get() = palette.border
    val divider: Color get() = palette.divider
    val gold: Color get() = palette.gold
    val goldBg: Color get() = palette.goldBg
    val scrim: Color get() = palette.scrim
    val toggleOff: Color get() = palette.toggleOff
    val onAccent: Color get() = palette.onAccent
    val navBar: Color get() = palette.navBar
    val dangerSolid: Color get() = palette.dangerSolid

    // Legacy names used across the existing screens — now theme-aware.
    val bgRaised: Color get() = palette.sheet
    val bgRaisedFallback: Color get() = palette.sheet
    val textPrimary: Color get() = palette.textPrimary
    val textSecondary: Color get() = palette.textSecondary
    val textTertiary: Color get() = palette.textTertiary
    val hairline: Color get() = palette.border
    val glassTint: Color get() = palette.card
    val glassTopHighlight: Color get() = palette.border
    val danger: Color get() = palette.danger
    val primaryGold: Color get() = palette.gold

    val success = Color(0xFF5CC8A1)

    // Lighting colours for the character stage (theme independent).
    val studioPearl = Color(0xFFF3F5F9)
    val keyLightIvory = Color(0xFFFFF4E6)

    // Character accents.
    val accentElena = Color(0xFF5CC8A1)
    val accentKaito = Color(0xFF5AB8FF)
    val accentMarco = Color(0xFFF5B04C)

    /** Accent used for text/icons on the page: the accent itself in dark, a deeper ink in light. */
    fun accentInk(accent: Color): Color =
        if (palette.isLight) lerp(accent, Color(0xFF0B2A20), 0.45f) else accent

    /** A tinted fill behind accent content (chips, selected rows). */
    fun accentSoft(accent: Color): Color =
        accent.copy(alpha = if (palette.isLight) 0.16f else 0.14f).compositeOver(Color.Transparent)

    // Explicit light values (kept for screens that branch on isLight themselves).
    val lightBg = MochiPalette.Light.bg
    val lightBgRaised = MochiPalette.Light.sheet
    val lightTextPrimary = MochiPalette.Light.textPrimary
    val lightTextSecondary = MochiPalette.Light.textSecondary
    val lightTextTertiary = MochiPalette.Light.textTertiary
    val lightHairline = MochiPalette.Light.border
    val warmGoldAccent = MochiPalette.Light.gold

    fun adaptiveTextPrimary(isDark: Boolean): Color = if (isDark) MochiPalette.Dark.textPrimary else lightTextPrimary
    fun adaptiveTextSecondary(isDark: Boolean): Color = if (isDark) MochiPalette.Dark.textSecondary else lightTextSecondary
    fun adaptiveBgRaised(isDark: Boolean): Color = if (isDark) MochiPalette.Dark.sheet else lightBgRaised
    fun adaptiveHairline(isDark: Boolean): Color = if (isDark) MochiPalette.Dark.border else lightHairline
}
