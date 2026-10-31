package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.character.CharacterSpec
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.LocalCharacterAccent
import com.example.ui.theme.LocalVisualStyle
import com.example.ui.theme.MochiShapes
import com.example.ui.theme.MochiSpacing
import com.example.ui.theme.MochiTokens
import com.example.ui.theme.MochiType
import com.example.ui.theme.MochiVisualStyle

/**
 * Component & Visual Style Studio Gallery:
 * - Live switching between all 4 Visual Styles (Cyber Neon, Anime Studio, Warm Studio, Holo HUD)
 * - Shows all 4 Manrope text styles (Display, Title, Body, Label)
 * - Shows a GlassSurface over the AuroraBackground
 * - Shows primary and secondary buttons
 * - Shows the bottom input bar and character accents
 */
@Composable
fun ComponentGalleryScreen(
    onSelectAccentCharacter: (CharacterSpec) -> Unit,
    onBack: () -> Unit,
    onSelectVisualStyle: (MochiVisualStyle) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val accent = LocalCharacterAccent.current
    val visualStyle = LocalVisualStyle.current
    var sampleInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = MochiSpacing.screenPadding, vertical = MochiSpacing.sm)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MochiSpacing.sectionGap)
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MochiSpacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(MochiSpacing.minTouchTarget)
                    .clip(MochiShapes.pill)
                    .background(visualStyle.bgRaised.copy(alpha = 0.90f))
                    .border(1.2.dp, accent.copy(alpha = 0.65f), MochiShapes.pill)
                    .clickable { onBack() }
                    .testTag("gallery_back_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = MochiTokens.textPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "Style & Component Studio",
                style = MochiType.Title,
                color = MochiTokens.textPrimary
            )
        }

        // 0. Live Visual Style Presets
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MochiSpacing.sm)
        ) {
            Text(
                text = "Visual style presets",
                style = MochiType.Title,
                color = MochiTokens.textPrimary
            )
            MochiVisualStyle.entries.forEach { preset ->
                val isSelected = preset == visualStyle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MochiShapes.card)
                        .background(preset.bgRaised.copy(alpha = 0.92f))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            brush = Brush.horizontalGradient(
                                listOf(
                                    if (isSelected) accent else MochiTokens.hairline,
                                    preset.secondaryGlow
                                )
                            ),
                            shape = MochiShapes.card
                        )
                        .clickable { onSelectVisualStyle(preset) }
                        .padding(MochiSpacing.md)
                        .testTag("gallery_style_${preset.id}")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = preset.label + if (isSelected) " • Active" else "",
                            style = MochiType.Body,
                            color = if (isSelected) accent else MochiTokens.textPrimary
                        )
                        Text(
                            text = preset.subtitle,
                            style = MochiType.Label,
                            color = MochiTokens.textSecondary
                        )
                    }
                }
            }
        }

        // 1. Typography showcase inside GlassSurface #1
        GlassSurface(
            shape = MochiShapes.card,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MochiSpacing.cardPadding),
                verticalArrangement = Arrangement.spacedBy(MochiSpacing.sm)
            ) {
                Text(
                    text = "Display 34/40sp",
                    style = MochiType.Display,
                    color = MochiTokens.textPrimary
                )
                Text(
                    text = "Title 22/28sp Manrope SemiBold",
                    style = MochiType.Title,
                    color = MochiTokens.textPrimary
                )
                Text(
                    text = "Body 16/24sp Manrope Regular with high-contrast smoked glass and neon rim lighting.",
                    style = MochiType.Body,
                    color = MochiTokens.textSecondary
                )
                Text(
                    text = "Label 13/18sp Manrope Medium for chips and metadata",
                    style = MochiType.Label,
                    color = MochiTokens.textTertiary
                )
            }
        }

        // 2. Character Accent Tokens
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MochiSpacing.sm)
        ) {
            Text(
                text = "Character accents",
                style = MochiType.Title,
                color = MochiTokens.textPrimary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MochiSpacing.sm)
            ) {
                CharacterSpec.entries.forEach { spec ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(MochiSpacing.minTouchTarget)
                            .clip(MochiShapes.pill)
                            .background(spec.accent.copy(alpha = 0.24f))
                            .border(1.5.dp, spec.accent, MochiShapes.pill)
                            .clickable { onSelectAccentCharacter(spec) }
                            .testTag("gallery_accent_${spec.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = spec.displayName,
                            style = MochiType.Label,
                            color = MochiTokens.textPrimary
                        )
                    }
                }
            }
        }

        // 3. Primary & Secondary Buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MochiSpacing.sm)
        ) {
            Text(
                text = "Buttons",
                style = MochiType.Title,
                color = MochiTokens.textPrimary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MochiSpacing.md)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(MochiSpacing.minTouchTarget)
                        .clip(MochiShapes.pill)
                        .background(visualStyle.bgRaised.copy(alpha = 0.90f))
                        .border(1.dp, accent.copy(alpha = 0.55f), MochiShapes.pill),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Secondary",
                        style = MochiType.Body,
                        color = MochiTokens.textPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(MochiSpacing.minTouchTarget)
                        .clip(MochiShapes.pill)
                        .background(Brush.horizontalGradient(listOf(accent, visualStyle.secondaryGlow))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Primary action",
                        style = MochiType.Body,
                        color = MochiTokens.textPrimary
                    )
                }
            }
        }

        // 4. Bottom Input Bar (GlassSurface #2)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MochiSpacing.sm)
        ) {
            Text(
                text = "Stage input bar",
                style = MochiType.Title,
                color = MochiTokens.textPrimary
            )
            StageInputBar(
                value = sampleInput,
                onValueChange = { sampleInput = it },
                isListening = false,
                onMicClick = {},
                onSendClick = {}
            )
        }

        Spacer(modifier = Modifier.height(MochiSpacing.sectionGap))
    }
}
