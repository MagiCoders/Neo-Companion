package com.example.ui.character

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.MochiTokens

enum class CharacterCategoryFilter(val label: String) {
    ALL("All"),
    EVERYDAY("Everyday"),
    MEDICAL("Medical"),
    ANIME("Anime"),
    CULINARY("Culinary")
}

/**
 * Exact 3 Launch Characters from CHARACTERS.md.
 */
enum class CharacterSpec(
    val id: String,
    val displayName: String,
    val category: CharacterCategoryFilter,
    val assetFile: String,
    val accent: Color,
    val voiceStyle: String,
    val pitch: Float,
    val speechRate: Float,
    val defaultSuggestions: List<String>,
    /** True when [assetFile] is a real, textured 3D model rendered with Filament. */
    val isRealtime3D: Boolean = false,
    /** Rotation that makes the model face the camera. */
    val modelYawDeg: Float = 0f
) {
    MOCHI(
        id = "mochi",
        displayName = "Mochi",
        category = CharacterCategoryFilter.EVERYDAY,
        assetFile = "characters/mochi.glb",
        accent = MochiTokens.accentElena,
        voiceStyle = "Warm, playful, gentle",
        pitch = 1.08f,
        speechRate = 1.0f,
        defaultSuggestions = listOf(
            "Help me plan my day",
            "What's for dinner tonight?",
            "I need to calm down"
        ),
        isRealtime3D = true,
        modelYawDeg = -90f // the Tripo export faces +X; turn it to face the viewer
    ),
    ELENA(
        id = "elena",
        displayName = "Nurse Elena",
        category = CharacterCategoryFilter.MEDICAL,
        assetFile = "characters/elena.glb",
        accent = MochiTokens.accentElena,
        voiceStyle = "Warm, calm, speaks slowly",
        pitch = 1.04f,
        speechRate = 0.90f,
        defaultSuggestions = listOf(
            "Guide me through a calm breathing reset",
            "Help me wind down after a long shift",
            "Organize my priorities for tonight"
        )
    ),
    KAITO(
        id = "kaito",
        displayName = "Kaito Storm",
        category = CharacterCategoryFilter.ANIME,
        assetFile = "characters/kaito.glb",
        accent = MochiTokens.accentKaito,
        voiceStyle = "Energetic, playful, quick replies",
        pitch = 0.96f,
        speechRate = 1.12f,
        defaultSuggestions = listOf(
            "Break my toughest task into three fast steps",
            "Draft a confident reply to a difficult message",
            "Give me a quick focus challenge"
        )
    ),
    MARCO(
        id = "marco",
        displayName = "Chef Marco",
        category = CharacterCategoryFilter.CULINARY,
        assetFile = "characters/marco.glb",
        accent = MochiTokens.accentMarco,
        voiceStyle = "Cheerful, practical, loves food",
        pitch = 0.92f,
        speechRate = 1.02f,
        defaultSuggestions = listOf(
            "What can I cook with eggs, rice, and garlic?",
            "Suggest a 15-minute dinner from pantry staples",
            "Plan three simple meals for this week"
        )
    );

    companion object {
        fun fromId(id: String): CharacterSpec =
            entries.firstOrNull { it.id == id } ?: MOCHI
    }
}
