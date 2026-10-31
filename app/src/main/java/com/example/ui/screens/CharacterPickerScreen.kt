package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.character.CharacterCategoryFilter
import com.example.ui.character.CharacterController
import com.example.ui.character.CharacterSpec
import com.example.ui.character.CharacterStageView
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.LocalMochiDarkTheme
import com.example.ui.theme.MochiShapes
import com.example.ui.theme.MochiSpacing
import com.example.ui.theme.MochiTokens
import com.example.ui.theme.MochiType
import kotlinx.coroutines.launch

/**
 * Screen 2 — Character Picker from DESIGN.md & CHARACTERS.md + Choose_a_companion_e1311e4f.png:
 * - Bottom-sheet style header with drag handle, "Choose your companion", subtitle, and X close button.
 * - Category filter chips at the top (All, Care/Medical, Kitchen/Culinary, Anime) with testTags preserved.
 * - Supports both DESIGN.md's full-screen HorizontalPager and the 2-column companion card grid from Choose a companion.png,
 *   including the "Current" badge pill and voice preview pill ("▶ Warm, calm", "▶ Calm, precise", "▶ Cheerful, Italian").
 */
@Composable
fun CharacterPickerScreen(
    activeCharacter: CharacterSpec,
    controller: CharacterController,
    onPreviewAccentChanged: (CharacterSpec) -> Unit,
    onChooseCharacter: (CharacterSpec) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val isDark = LocalMochiDarkTheme.current
    val textPrimary = MochiTokens.adaptiveTextPrimary(isDark)
    val textSecondary = MochiTokens.adaptiveTextSecondary(isDark)
    val cardBg = MochiTokens.adaptiveBgRaised(isDark)
    val borderCol = MochiTokens.adaptiveHairline(isDark)
    val goldAccent = MochiTokens.gold

    var selectedCategory by remember { mutableStateOf(CharacterCategoryFilter.ALL) }
    var showGridView by remember { mutableStateOf(true) }
    var selectedGridCharacter by remember(activeCharacter) { mutableStateOf(activeCharacter) }

    val filteredCharacters = remember(selectedCategory) {
        if (selectedCategory == CharacterCategoryFilter.ALL) {
            CharacterSpec.entries
        } else {
            CharacterSpec.entries.filter { it.category == selectedCategory }
        }
    }

    val initialPage = remember(filteredCharacters, activeCharacter) {
        filteredCharacters.indexOf(activeCharacter).coerceAtLeast(0)
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { filteredCharacters.size }
    )

    val currentCharacter = if (showGridView) {
        selectedGridCharacter
    } else {
        filteredCharacters.getOrElse(pagerState.currentPage) { CharacterSpec.ELENA }
    }

    LaunchedEffect(currentCharacter) {
        onPreviewAccentChanged(currentCharacter)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = MochiSpacing.screenPadding, vertical = MochiSpacing.sm)
    ) {
        // Sheet drag handle from Choose a companion.png
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(MochiShapes.pill)
                    .background(borderCol)
            )
        }

        Spacer(modifier = Modifier.height(MochiSpacing.sm))

        // Top header row: Title + Subtitle on left, View mode toggle + Close X button on right
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Choose your companion",
                    style = MochiType.Display,
                    color = textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Each one has their own look, voice and personality.",
                    style = MochiType.Label,
                    color = textSecondary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(MochiShapes.pill)
                        .background(cardBg)
                        .border(1.dp, borderCol, MochiShapes.pill)
                        .clickable { showGridView = !showGridView }
                        .testTag("picker_view_toggle"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (showGridView) Icons.Outlined.ViewCarousel else Icons.Outlined.GridView,
                        contentDescription = if (showGridView) "Switch to pager view" else "Switch to grid view",
                        tint = textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(MochiShapes.pill)
                        .background(cardBg)
                        .border(1.dp, borderCol, MochiShapes.pill)
                        .clickable { onBack() }
                        .testTag("picker_back_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close companion picker",
                        tint = textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(MochiSpacing.md))

        // Category filter chips matching Choose a companion.png (while keeping testTags intact)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(MochiSpacing.xs)
        ) {
            CharacterCategoryFilter.entries.forEach { category ->
                val isSelected = selectedCategory == category
                val chipDisplayLabel = when (category) {
                    CharacterCategoryFilter.ALL -> "All"
                    CharacterCategoryFilter.EVERYDAY -> "Everyday"
                    CharacterCategoryFilter.MEDICAL -> "Care"
                    CharacterCategoryFilter.CULINARY -> "Kitchen"
                    CharacterCategoryFilter.ANIME -> "Anime"
                }
                Box(
                    modifier = Modifier
                        .height(38.dp)
                        .clip(MochiShapes.pill)
                        .background(
                            if (isSelected) goldAccent.copy(alpha = 0.20f)
                            else cardBg
                        )
                        .border(
                            1.dp,
                            if (isSelected) goldAccent else borderCol,
                            MochiShapes.pill
                        )
                        .clickable {
                            selectedCategory = category
                            scope.launch { pagerState.scrollToPage(0) }
                        }
                        .padding(horizontal = 18.dp)
                        .testTag("picker_category_${category.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = chipDisplayLabel,
                        style = MochiType.Label.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium),
                        color = if (isSelected) goldAccent else textPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(MochiSpacing.md))

        if (!showGridView) {
            // Full-screen HorizontalPager, one character per page (DESIGN.md default)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("character_pager")
            ) { pageIndex ->
                val pageCharacter = filteredCharacters[pageIndex]
                val isCurrent = pageCharacter == activeCharacter
                val roleSubtext = companionRoleSubtitle(pageCharacter)
                val voiceSummary = companionVoiceSummary(pageCharacter)

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Large 3D preview with "Current" pill badge & voice preview pill
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.62f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(cardBg)
                            .border(
                                width = if (isCurrent) 2.dp else 1.dp,
                                color = if (isCurrent) pageCharacter.accent else borderCol,
                                shape = RoundedCornerShape(24.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        CharacterStageView(
                            character = pageCharacter,
                            controller = controller,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (isCurrent) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(MochiSpacing.md)
                                    .clip(MochiShapes.pill)
                                    .background(pageCharacter.accent)
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Current",
                                    style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold),
                                    color = MochiTokens.onAccent
                                )
                            }
                        }

                        // Voice preview pill at bottom-left of 3D card
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(MochiSpacing.md)
                                .clip(MochiShapes.pill)
                                .background(cardBg.copy(alpha = 0.90f))
                                .border(1.dp, borderCol, MochiShapes.pill)
                                .clickable {
                                    controller.react("wave")
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PlayArrow,
                                contentDescription = "Preview voice",
                                tint = pageCharacter.accent,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = voiceSummary,
                                style = MochiType.Label.copy(fontWeight = FontWeight.Medium),
                                color = textPrimary
                            )
                        }
                    }

                    // Glass card with Name (Title), one-line role (Body, secondary), and one accent "Choose" button
                    GlassSurface(
                        shape = MochiShapes.sheet,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(MochiSpacing.cardPadding),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(MochiSpacing.xs)
                        ) {
                            Text(
                                text = pageCharacter.displayName,
                                style = MochiType.Title,
                                color = textPrimary
                            )
                            Text(
                                text = roleSubtext,
                                style = MochiType.Body,
                                color = textSecondary
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Page indicator dots
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(MochiSpacing.xs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                filteredCharacters.forEachIndexed { idx, item ->
                                    Box(
                                        modifier = Modifier
                                            .size(if (idx == pageIndex) 10.dp else 6.dp)
                                            .clip(MochiShapes.pill)
                                            .background(
                                                if (idx == pageIndex) item.accent
                                                else borderCol
                                            )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Single accent button per screen ("Choose" / "Current companion")
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(MochiSpacing.minTouchTarget)
                                    .clip(MochiShapes.pill)
                                    .background(pageCharacter.accent)
                                    .clickable { onChooseCharacter(pageCharacter) }
                                    .testTag("choose_character_button_${pageCharacter.id}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isCurrent) "Current companion" else "Choose ${pageCharacter.displayName}",
                                    style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
                                    color = MochiTokens.onAccent
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // 2-column companion card grid matching Choose_a_companion_e1311e4f.png
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("character_pager"),
                horizontalArrangement = Arrangement.spacedBy(MochiSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(MochiSpacing.sm)
            ) {
                items(filteredCharacters, key = { it.id }) { spec ->
                    val isSelected = spec == selectedGridCharacter
                    val isCurrent = spec == activeCharacter
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(cardBg)
                            .border(
                                width = if (isSelected || isCurrent) 2.dp else 1.dp,
                                color = if (isSelected || isCurrent) spec.accent else borderCol,
                                shape = RoundedCornerShape(22.dp)
                            )
                            .clickable {
                                selectedGridCharacter = spec
                                onPreviewAccentChanged(spec)
                            }
                            .padding(MochiSpacing.sm)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(152.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(spec.accent.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CharacterStageView(
                                character = spec,
                                controller = controller,
                                halfResolutionMode = true,
                                modifier = Modifier.fillMaxSize()
                            )

                            if (isCurrent) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(8.dp)
                                        .clip(MochiShapes.pill)
                                        .background(spec.accent)
                                        .padding(horizontal = 10.dp, vertical = 3.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Current",
                                        style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold),
                                        color = MochiTokens.onAccent
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = spec.displayName,
                            style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
                            color = textPrimary
                        )
                        Text(
                            text = companionRoleSubtitle(spec),
                            style = MochiType.Label,
                            color = textSecondary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .clip(MochiShapes.pill)
                                .background(MochiTokens.card2)
                                .clickable { controller.react("wave") }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PlayArrow,
                                contentDescription = null,
                                tint = spec.accent,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = companionVoiceSummary(spec),
                                style = MochiType.Label.copy(fontWeight = FontWeight.Medium),
                                color = textPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(MochiSpacing.sm))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MochiSpacing.minTouchTarget)
                    .clip(MochiShapes.pill)
                    .background(selectedGridCharacter.accent)
                    .clickable { onChooseCharacter(selectedGridCharacter) }
                    .testTag("choose_character_button_${selectedGridCharacter.id}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (selectedGridCharacter == activeCharacter) "Current companion" else "Choose ${selectedGridCharacter.displayName}",
                    style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
                    color = MochiTokens.onAccent
                )
            }
        }
    }
}

private fun companionRoleSubtitle(spec: CharacterSpec): String = when (spec) {
    CharacterSpec.MOCHI -> "Your everyday friend"
    CharacterSpec.ELENA -> "Health and calm"
    CharacterSpec.KAITO -> "Focus and anime"
    CharacterSpec.MARCO -> "Cooking and meals"
}

private fun companionVoiceSummary(spec: CharacterSpec): String = when (spec) {
    CharacterSpec.MOCHI -> "Warm, playful"
    CharacterSpec.ELENA -> "Warm, calm"
    CharacterSpec.KAITO -> "Calm, precise"
    CharacterSpec.MARCO -> "Cheerful, Italian"
}
