package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.data.MochiPersistentSettings
import com.example.data.QuestCategory
import com.example.data.QuestItem
import com.example.ui.character.CharacterController
import com.example.ui.character.CharacterSpec
import com.example.ui.character.CharacterStageView
import com.example.ui.theme.LocalCharacterAccent
import com.example.ui.theme.LocalVisualStyle
import com.example.ui.theme.MochiShapes
import com.example.ui.theme.MochiSpacing
import com.example.ui.theme.MochiTokens
import com.example.ui.theme.MochiType
import kotlinx.coroutines.delay

enum class MochiMainTab {
    HOME,
    TOOLS,
    LIBRARY
}

@Composable
private fun rememberSuiteColors(): SuitePalette {
    val style = LocalVisualStyle.current
    val isLight = style.isLight
    return remember(isLight) {
        if (isLight) {
            SuitePalette(
                bg = MochiTokens.lightBg,
                card = MochiTokens.lightBgRaised,
                textPrimary = MochiTokens.lightTextPrimary,
                textSecondary = MochiTokens.lightTextSecondary,
                textTertiary = MochiTokens.lightTextTertiary,
                hairline = MochiTokens.lightHairline,
                gold = Color(0xFF9C722B),
                goldPillBg = Color(0xFFF1E6CE)
            )
        } else {
            SuitePalette(
                bg = MochiTokens.bg,
                card = MochiTokens.bgRaisedFallback,
                textPrimary = MochiTokens.textPrimary,
                textSecondary = MochiTokens.textSecondary,
                textTertiary = MochiTokens.textTertiary,
                hairline = MochiTokens.hairline,
                gold = Color(0xFFE5B869),
                goldPillBg = Color(0xFF332919)
            )
        }
    }
}

private data class SuitePalette(
    val bg: Color,
    val card: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val hairline: Color,
    val gold: Color,
    val goldPillBg: Color
)

/**
 * Bottom Navigation Bar matching Home.png, Tools.png, and Library.png.
 */
@Composable
fun MochiBottomNavBar(
    currentTab: MochiMainTab,
    onSelectTab: (MochiMainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val p = rememberSuiteColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(p.card)
            .border(width = 0.5.dp, color = p.hairline)
            .navigationBarsPadding()
            .padding(vertical = 8.dp, horizontal = 24.dp)
            .testTag("mochi_bottom_nav_bar"),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MochiBottomNavItem(
            label = "Home",
            icon = Icons.Outlined.Home,
            selected = currentTab == MochiMainTab.HOME,
            palette = p,
            testTag = "nav_tab_home",
            onClick = { onSelectTab(MochiMainTab.HOME) }
        )
        MochiBottomNavItem(
            label = "Tools",
            icon = Icons.Outlined.GridView,
            selected = currentTab == MochiMainTab.TOOLS,
            palette = p,
            testTag = "nav_tab_tools",
            onClick = { onSelectTab(MochiMainTab.TOOLS) }
        )
        MochiBottomNavItem(
            label = "Library",
            icon = Icons.Outlined.BookmarkBorder,
            selected = currentTab == MochiMainTab.LIBRARY,
            palette = p,
            testTag = "nav_tab_library",
            onClick = { onSelectTab(MochiMainTab.LIBRARY) }
        )
    }
}

@Composable
private fun MochiBottomNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    palette: SuitePalette,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(30.dp)
                .clip(MochiShapes.pill)
                .background(if (selected) palette.goldPillBg else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) palette.gold else palette.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = label,
            style = MochiType.Label.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (selected) palette.textPrimary else palette.textSecondary
        )
    }
}

/**
 * Library Screen matching Library.png & Library - light.png.
 */
@Composable
fun LibraryScreen(
    onOpenSearch: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenFridgeChef: () -> Unit,
    onOpenTools: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = rememberSuiteColors()
    val filters = listOf("All", "Chats", "Recipes", "Bills", "Files")
    var selectedFilter by remember { mutableStateOf("All") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = MochiSpacing.screenPadding, vertical = MochiSpacing.sm)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MochiSpacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Library",
                style = MochiType.Display,
                color = p.textPrimary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(MochiSpacing.xs)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(MochiShapes.pill)
                        .background(p.card)
                        .border(1.dp, p.hairline, MochiShapes.pill)
                        .clickable { onOpenSearch() }
                        .testTag("library_search_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search library",
                        tint = p.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(MochiShapes.pill)
                        .background(p.card)
                        .border(1.dp, p.hairline, MochiShapes.pill)
                        .clickable { onOpenProfile() }
                        .testTag("library_profile_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = "Profile",
                        tint = p.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Text(
            text = "Everything you and your companions made.",
            style = MochiType.Body,
            color = p.textSecondary
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(MochiSpacing.xs)
        ) {
            filters.forEach { filter ->
                val isSelected = selectedFilter == filter
                Box(
                    modifier = Modifier
                        .height(38.dp)
                        .clip(MochiShapes.pill)
                        .background(if (isSelected) p.goldPillBg else p.card)
                        .border(
                            1.dp,
                            if (isSelected) p.gold else p.hairline,
                            MochiShapes.pill
                        )
                        .clickable { selectedFilter = filter }
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = filter,
                        style = MochiType.Label.copy(fontWeight = FontWeight.Medium),
                        color = if (isSelected) p.gold else p.textPrimary
                    )
                }
            }
        }

        Text(
            text = "Today",
            style = MochiType.Label,
            color = p.textSecondary
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MochiShapes.card)
                .background(p.card)
                .border(1.dp, p.hairline, MochiShapes.card)
        ) {
            LibraryItemRow(
                icon = Icons.Outlined.ChatBubbleOutline,
                iconTint = MochiTokens.accentElena,
                title = "Planned tomorrow’s errands",
                subtitle = "Chat with Elena",
                time = "9:40 PM",
                palette = p,
                onClick = {}
            )
            HairlineDivider(p.hairline)
            LibraryItemRow(
                icon = Icons.Outlined.RestaurantMenu,
                iconTint = MochiTokens.accentMarco,
                title = "Lemon spinach pasta",
                subtitle = "Recipe · from your fridge photo",
                time = "7:15 PM",
                palette = p,
                onClick = onOpenFridgeChef
            )
            HairlineDivider(p.hairline)
            LibraryItemRow(
                icon = Icons.Outlined.Description,
                iconTint = MochiTokens.accentKaito,
                title = "Lease agreement.pdf",
                subtitle = "File · made from 4 photos",
                time = "2:02 PM",
                palette = p,
                onClick = onOpenTools
            )
        }

        Text(
            text = "This week",
            style = MochiType.Label,
            color = p.textSecondary
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MochiShapes.card)
                .background(p.card)
                .border(1.dp, p.hairline, MochiShapes.card)
        ) {
            LibraryItemRow(
                icon = Icons.Outlined.ReceiptLong,
                iconTint = p.gold,
                title = "Dinner at Rosa's",
                subtitle = "Bill split · 4 people · 1 unpaid",
                time = "Thu",
                palette = p,
                onClick = {}
            )
            HairlineDivider(p.hairline)
            LibraryItemRow(
                icon = Icons.Outlined.Mic,
                iconTint = Color(0xFFB388FF),
                title = "Voice note for Neo.wav",
                subtitle = "Shared to WhatsApp",
                time = "Wed",
                palette = p,
                onClick = {}
            )
            HairlineDivider(p.hairline)
            LibraryItemRow(
                icon = Icons.Outlined.ChatBubbleOutline,
                iconTint = MochiTokens.accentMarco,
                title = "Meal plan for the week",
                subtitle = "Chat with Chef Marco",
                time = "Mon",
                palette = p,
                onClick = {}
            )
            HairlineDivider(p.hairline)
            LibraryItemRow(
                icon = Icons.Outlined.MusicNote,
                iconTint = MochiTokens.accentKaito,
                title = "Team meeting.m4a",
                subtitle = "File · audio taken from a video",
                time = "Mon",
                palette = p,
                onClick = {}
            )
        }

        Spacer(modifier = Modifier.height(MochiSpacing.sectionGap))
    }
}

@Composable
private fun LibraryItemRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    time: String,
    palette: SuitePalette,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(MochiSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MochiSpacing.sm),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
                    color = palette.textPrimary
                )
                Text(
                    text = subtitle,
                    style = MochiType.Label,
                    color = palette.textSecondary
                )
            }
        }
        Text(
            text = time,
            style = MochiType.Label,
            color = palette.textSecondary
        )
    }
}

/**
 * Search Screen matching Search.png & Search - light.png.
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenFridgeChef: () -> Unit,
    onOpenTools: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = rememberSuiteColors()
    val accent = LocalCharacterAccent.current
    var query by remember { mutableStateOf("pasta") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = MochiSpacing.screenPadding, vertical = MochiSpacing.sm)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MochiSpacing.md)
    ) {
        // Search Input Bar with gold border + Cancel button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MochiSpacing.sm)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(MochiShapes.pill)
                    .background(p.card)
                    .border(1.2.dp, p.gold, MochiShapes.pill)
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = p.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        textStyle = MochiType.Body.copy(color = p.textPrimary),
                        cursorBrush = SolidColor(p.gold),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(p.hairline)
                            .clickable { query = "" },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Clear search",
                            tint = p.textPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Text(
                text = "Cancel",
                style = MochiType.Body.copy(fontWeight = FontWeight.Medium),
                color = p.gold,
                modifier = Modifier.clickable { onBack() }
            )
        }

        // Category pills with counts ("All · 4", "Recipes · 2", "Chats · 1", "Files · 1")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(MochiSpacing.xs)
        ) {
            listOf("All · 4", "Recipes · 2", "Chats · 1", "Files · 1").forEachIndexed { idx, label ->
                Box(
                    modifier = Modifier
                        .height(38.dp)
                        .clip(MochiShapes.pill)
                        .background(if (idx == 0) p.goldPillBg else p.card)
                        .border(1.dp, if (idx == 0) p.gold else p.hairline, MochiShapes.pill)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MochiType.Label.copy(fontWeight = FontWeight.Medium),
                        color = if (idx == 0) p.gold else p.textPrimary
                    )
                }
            }
        }

        // Ask Elena about "pasta" card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MochiShapes.card)
                .background(accent.copy(alpha = 0.12f))
                .border(1.dp, accent.copy(alpha = 0.35f), MochiShapes.card)
                .clickable { onBack() }
                .padding(MochiSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MochiSpacing.sm)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "E",
                        style = MochiType.Body.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF07090F)
                    )
                }
                Column {
                    Text(
                        text = "Ask Elena about \"${query.ifBlank { "pasta" }}\"",
                        style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
                        color = p.textPrimary
                    )
                    Text(
                        text = "Get an answer instead of a list",
                        style = MochiType.Label,
                        color = p.textSecondary
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = p.textSecondary
            )
        }

        // Highlighted search results matching Search.png
        SearchResultHighlightedRow(
            icon = Icons.Outlined.RestaurantMenu,
            iconTint = MochiTokens.accentMarco,
            prefix = "Lemon spinach ",
            highlight = "pasta",
            suffix = "",
            subtitle = "Recipe · from your fridge photo · today",
            palette = p,
            onClick = onOpenFridgeChef
        )

        SearchResultHighlightedRow(
            icon = Icons.Outlined.RestaurantMenu,
            iconTint = MochiTokens.accentMarco,
            prefix = "Garlic ",
            highlight = "pasta",
            suffix = " bake",
            subtitle = "Recipe · from Chef Marco · Sep 28",
            palette = p,
            onClick = onOpenFridgeChef
        )

        SearchResultHighlightedRow(
            icon = Icons.Outlined.ChatBubbleOutline,
            iconTint = MochiTokens.accentElena,
            prefix = "Meal plan for the week",
            highlight = "",
            suffix = "",
            quotePrefix = "\"...a quick ",
            quoteHighlight = "pasta",
            quoteSuffix = " on Tuesday and soup on Thursday...\"",
            subtitle = "Chat with Chef Marco · Mon",
            palette = p,
            onClick = {}
        )

        SearchResultHighlightedRow(
            icon = Icons.Outlined.Description,
            iconTint = MochiTokens.accentKaito,
            prefix = "Shopping list.pdf",
            highlight = "",
            suffix = "",
            quotePrefix = "\"...",
            quoteHighlight = "pasta",
            quoteSuffix = ", lemons, feta, spinach...\"",
            subtitle = "File · Oct 20",
            palette = p,
            onClick = onOpenTools
        )
    }
}

@Composable
private fun SearchResultHighlightedRow(
    icon: ImageVector,
    iconTint: Color,
    prefix: String,
    highlight: String,
    suffix: String,
    subtitle: String,
    palette: SuitePalette,
    quotePrefix: String? = null,
    quoteHighlight: String? = null,
    quoteSuffix: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MochiSpacing.sm)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = buildAnnotatedString {
                    append(prefix)
                    if (highlight.isNotEmpty()) {
                        withStyle(SpanStyle(color = palette.gold, fontWeight = FontWeight.SemiBold)) {
                            append(highlight)
                        }
                    }
                    append(suffix)
                },
                style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
                color = palette.textPrimary
            )
            if (quotePrefix != null && quoteHighlight != null && quoteSuffix != null) {
                Text(
                    text = buildAnnotatedString {
                        append(quotePrefix)
                        withStyle(SpanStyle(color = palette.gold, fontWeight = FontWeight.SemiBold)) {
                            append(quoteHighlight)
                        }
                        append(quoteSuffix)
                    },
                    style = MochiType.Body,
                    color = palette.textSecondary
                )
            }
            Text(
                text = subtitle,
                style = MochiType.Label,
                color = palette.textSecondary
            )
        }
    }
}

/**
 * Profile and Settings Screen matching Profile and settings.png & Profile and settings - light.png.
 */
@Composable
fun ProfileAndSettingsScreen(
    activeCharacter: CharacterSpec,
    isDarkTheme: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit,
    onOpenEditProfile: () -> Unit,
    onOpenWallet: () -> Unit,
    onOpenCharacterPicker: () -> Unit,
    onOpenWhenBusy: () -> Unit,
    onOpenDrivingMode: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenPrivacyData: () -> Unit,
    onOpenHelpFeedback: () -> Unit,
    onOpenComponentGallery: () -> Unit,
    onBack: () -> Unit,
    settings: MochiPersistentSettings = MochiPersistentSettings(),
    onUpdateSettings: ((MochiPersistentSettings) -> MochiPersistentSettings) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val p = rememberSuiteColors()
    val accent = LocalCharacterAccent.current
    val busyModeLabel = when (settings.busyBehaviorIndex) {
        0 -> "Voice only"
        1 -> "Save for later"
        else -> "Normal"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = MochiSpacing.screenPadding, vertical = MochiSpacing.sm)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MochiSpacing.md)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MochiSpacing.md)
        ) {
            BackPillButton(palette = p, onClick = onBack)
            Text(
                text = "Profile and settings",
                style = MochiType.Title,
                color = p.textPrimary
            )
        }

        // Top Unified Profile + Wallet + Plan Card (exact match to Profile and settings.png)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MochiShapes.card)
                .background(p.card)
                .border(1.dp, p.hairline, MochiShapes.card)
                .padding(MochiSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MochiSpacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MochiSpacing.sm)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE0B462)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = settings.userName.take(1).uppercase().ifEmpty { "Y" },
                            style = MochiType.Title,
                            color = Color(0xFF17181A)
                        )
                    }
                    Column {
                        Text(
                            text = settings.userName.ifBlank { "Your name" },
                            style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
                            color = p.textPrimary
                        )
                        Text(
                            text = if (settings.userNickname.isNotBlank()) {
                                "Called \"${settings.userNickname}\" · Signed in with Google"
                            } else {
                                "Signed in with Google"
                            },
                            style = MochiType.Label,
                            color = p.textSecondary
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(MochiShapes.pill)
                        .background(p.bg)
                        .border(1.dp, p.hairline, MochiShapes.pill)
                        .clickable { onOpenEditProfile() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Edit",
                        style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold),
                        color = p.textPrimary
                    )
                }
            }

            // Two side-by-side cards: Wallet and Plan (Pro)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MochiSpacing.sm)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(p.goldPillBg.copy(alpha = 0.65f))
                        .border(1.dp, p.gold.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                        .clickable { onOpenWallet() }
                        .padding(MochiSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Wallet",
                        style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold),
                        color = p.gold
                    )
                    Text(
                        text = "%,d".format(settings.aiEnergyBalance),
                        style = MochiType.Title,
                        color = p.textPrimary
                    )
                    Text(
                        text = "AI energy left",
                        style = MochiType.Label,
                        color = p.textSecondary
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(p.bg)
                        .border(1.dp, p.hairline, RoundedCornerShape(16.dp))
                        .clickable { onOpenWallet() }
                        .padding(MochiSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Plan",
                        style = MochiType.Label,
                        color = p.textSecondary
                    )
                    Text(
                        text = "Pro",
                        style = MochiType.Title,
                        color = p.textPrimary
                    )
                    Text(
                        text = "Renews Nov 30",
                        style = MochiType.Label,
                        color = p.textSecondary
                    )
                }
            }
        }

        Text(
            text = "Companion",
            style = MochiType.Label,
            color = p.textSecondary
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MochiShapes.card)
                .background(p.card)
                .border(1.dp, p.hairline, MochiShapes.card)
        ) {
            SettingsIconNavRow(
                icon = Icons.Outlined.PersonOutline,
                title = "Current companion",
                trailingText = activeCharacter.displayName.substringAfter(" "),
                palette = p,
                onClick = onOpenCharacterPicker
            )
            HairlineDivider(p.hairline)
            SettingsIconNavRow(
                icon = Icons.Outlined.GraphicEq,
                title = "Voice",
                trailingText = activeCharacter.voiceStyle.substringBefore(" •"),
                palette = p,
                onClick = onOpenCharacterPicker
            )
            HairlineDivider(p.hairline)
            SettingsIconNavRow(
                icon = Icons.Outlined.Language,
                title = "Language",
                trailingText = settings.preferredLanguage,
                palette = p,
                onClick = onOpenEditProfile
            )
        }

        Text(
            text = "On your home screen",
            style = MochiType.Label,
            color = p.textSecondary
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MochiShapes.card)
                .background(p.card)
                .border(1.dp, p.hairline, MochiShapes.card)
        ) {
            SettingsIconToggleRow(
                icon = Icons.Outlined.PictureInPictureAlt,
                title = "Floating companion",
                subtitle = "Stays on screen when you leave the app",
                checked = settings.floatingCompanionEnabled,
                accent = accent,
                palette = p,
                onCheckedChange = { enabled ->
                    onUpdateSettings { it.copy(floatingCompanionEnabled = enabled) }
                }
            )
            HairlineDivider(p.hairline)
            SettingsIconNavRow(
                icon = Icons.Outlined.Nightlight,
                title = "When I’m busy",
                trailingText = busyModeLabel,
                palette = p,
                onClick = onOpenWhenBusy
            )
            HairlineDivider(p.hairline)
            SettingsIconToggleRow(
                icon = Icons.Outlined.Mic,
                title = "\"Hey Mochi\" wake word",
                subtitle = "Keeps the mic on, uses more battery",
                checked = settings.wakeWordEnabled,
                accent = accent,
                palette = p,
                onCheckedChange = { enabled ->
                    onUpdateSettings { it.copy(wakeWordEnabled = enabled) }
                }
            )
        }

        Text(
            text = "In the car",
            style = MochiType.Label,
            color = p.textSecondary
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MochiShapes.card)
                .background(p.card)
                .border(1.dp, p.hairline, MochiShapes.card)
        ) {
            SettingsIconNavRow(
                icon = Icons.Outlined.DirectionsCar,
                title = "Driving mode",
                subtitle = "Voice only, big buttons",
                trailingText = if (settings.drivingAutoStart) "Automatic" else "Manual",
                palette = p,
                onClick = onOpenDrivingMode
            )
        }

        Text(
            text = "App & privacy",
            style = MochiType.Label,
            color = p.textSecondary
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MochiShapes.card)
                .background(p.card)
                .border(1.dp, p.hairline, MochiShapes.card)
        ) {
            SettingsIconNavRow(
                icon = Icons.Outlined.Shield,
                title = "Permissions",
                trailingText = "${settings.allowedPermissions.size} of 8 allowed",
                palette = p,
                onClick = onOpenPermissions
            )
            HairlineDivider(p.hairline)
            SettingsIconNavRow(
                icon = Icons.Outlined.PersonOutline,
                title = "Privacy and data",
                trailingText = "",
                palette = p,
                onClick = onOpenPrivacyData
            )
            HairlineDivider(p.hairline)
            SettingsIconNavRow(
                icon = Icons.Outlined.ChatBubbleOutline,
                title = "Help and feedback",
                trailingText = "",
                palette = p,
                onClick = onOpenHelpFeedback
            )
            HairlineDivider(p.hairline)
            SettingsIconToggleRow(
                icon = Icons.Outlined.Nightlight,
                title = "Dark stage theme",
                subtitle = "Switch between Dark Aurora and Warm Paper light mode",
                checked = isDarkTheme,
                accent = accent,
                palette = p,
                onCheckedChange = onToggleDarkTheme
            )
            HairlineDivider(p.hairline)
            SettingsIconNavRow(
                icon = Icons.Outlined.GridView,
                title = "Component gallery",
                trailingText = "Tokens",
                palette = p,
                onClick = onOpenComponentGallery
            )
        }

        Spacer(modifier = Modifier.height(MochiSpacing.md))
    }
}

/**
 * Edit Profile Screen matching Edit profile.png & Edit profile - light.png.
 */
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    settings: MochiPersistentSettings = MochiPersistentSettings(),
    onUpdateSettings: ((MochiPersistentSettings) -> MochiPersistentSettings) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val p = rememberSuiteColors()
    val accent = LocalCharacterAccent.current
    var fullName by remember(settings.userName) { mutableStateOf(settings.userName) }
    var nickname by remember(settings.userNickname) { mutableStateOf(settings.userNickname) }
    var aboutYou by remember(settings.userAboutBio) { mutableStateOf(settings.userAboutBio) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = MochiSpacing.screenPadding, vertical = MochiSpacing.sm),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MochiSpacing.md)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MochiSpacing.md)
            ) {
                BackPillButton(
                    palette = p,
                    onClick = {
                        onUpdateSettings {
                            it.copy(
                                userName = fullName.ifBlank { "Your name" },
                                userNickname = nickname,
                                userAboutBio = aboutYou
                            )
                        }
                        onBack()
                    }
                )
                Text(
                    text = "Edit profile",
                    style = MochiType.Title,
                    color = p.textPrimary
                )
            }

            // Gold Avatar with green camera badge at bottom-right + "Change photo"
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(modifier = Modifier.size(96.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color(0xFFE0B462)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = fullName.take(1).uppercase().ifEmpty { "Y" },
                            style = MochiType.Display,
                            color = Color(0xFF17181A)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(accent)
                            .border(2.dp, p.bg, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CameraAlt,
                            contentDescription = "Change photo",
                            tint = Color(0xFF07090F),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = "Change photo",
                    style = MochiType.Body.copy(fontWeight = FontWeight.Medium),
                    color = accent
                )
            }

            // Name Field
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Name", style = MochiType.Label, color = p.textSecondary)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(p.card)
                        .border(1.dp, p.hairline, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = fullName,
                        onValueChange = {
                            fullName = it
                            onUpdateSettings { s -> s.copy(userName = it.ifBlank { "Your name" }) }
                        },
                        textStyle = MochiType.Body.copy(color = p.textPrimary),
                        cursorBrush = SolidColor(accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // What should Elena call you?
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("What should Elena call you?", style = MochiType.Label, color = p.textSecondary)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(p.card)
                        .border(1.dp, p.hairline, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (nickname.isEmpty()) {
                        Text(
                            text = "A nickname, or leave blank",
                            style = MochiType.Body,
                            color = p.textTertiary
                        )
                    }
                    BasicTextField(
                        value = nickname,
                        onValueChange = {
                            nickname = it
                            onUpdateSettings { s -> s.copy(userNickname = it) }
                        },
                        textStyle = MochiType.Body.copy(color = p.textPrimary),
                        cursorBrush = SolidColor(accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // A few things about you
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("A few things about you", style = MochiType.Label, color = p.textSecondary)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(p.card)
                        .border(1.dp, p.hairline, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    if (aboutYou.isEmpty()) {
                        Text(
                            text = "For example: I’m vegetarian, I work night shifts, I’m learning Spanish.",
                            style = MochiType.Body,
                            color = p.textTertiary
                        )
                    }
                    BasicTextField(
                        value = aboutYou,
                        onValueChange = {
                            aboutYou = it
                            onUpdateSettings { s -> s.copy(userAboutBio = it) }
                        },
                        textStyle = MochiType.Body.copy(color = p.textPrimary),
                        cursorBrush = SolidColor(accent),
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Text(
                    text = "Your companions use this to give more personal answers. Only you can see it.",
                    style = MochiType.Label,
                    color = p.textSecondary
                )
            }

            // Account section
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Account", style = MochiType.Label, color = p.textSecondary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MochiShapes.card)
                        .background(p.card)
                        .border(1.dp, p.hairline, MochiShapes.card)
                        .padding(MochiSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MochiSpacing.sm)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(p.bg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MailOutline,
                            contentDescription = null,
                            tint = p.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Google account",
                            style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
                            color = p.textPrimary
                        )
                        Text(
                            text = "Your email comes from Google",
                            style = MochiType.Label,
                            color = p.textSecondary
                        )
                    }
                }
            }
        }

        // Bottom full-width Save changes pill button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MochiSpacing.sm)
                .height(54.dp)
                .clip(MochiShapes.pill)
                .background(accent)
                .clickable {
                    onUpdateSettings {
                        it.copy(
                            userName = fullName.ifBlank { "Your name" },
                            userNickname = nickname,
                            userAboutBio = aboutYou
                        )
                    }
                    onBack()
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Save changes",
                style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFF07090F)
            )
        }
    }
}

/**
 * Wallet Screen matching Wallet.png & Wallet - light.png.
 */
@Composable
fun WalletScreen(
    onBack: () -> Unit,
    settings: MochiPersistentSettings = MochiPersistentSettings(),
    onUpdateSettings: ((MochiPersistentSettings) -> MochiPersistentSettings) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val p = rememberSuiteColors()
    val accent = LocalCharacterAccent.current
    val balance = settings.aiEnergyBalance
    val checkedInToday = settings.checkedInToday

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = MochiSpacing.screenPadding, vertical = MochiSpacing.sm)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MochiSpacing.md)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MochiSpacing.md)
        ) {
            BackPillButton(palette = p, onClick = onBack)
            Text(
                text = "Wallet",
                style = MochiType.Title,
                color = p.textPrimary
            )
        }

        // Balance Card matching Wallet.png
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MochiShapes.card)
                .background(
                    Brush.linearGradient(
                        colors = listOf(p.card, p.goldPillBg)
                    )
                )
                .border(1.dp, p.gold.copy(alpha = 0.4f), MochiShapes.card)
                .padding(MochiSpacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "⚡ AI energy",
                style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold),
                color = p.gold
            )
            Text(
                text = "%,d".format(balance),
                style = MochiType.Display,
                color = p.textPrimary
            )
            Text(
                text = "Used for voice chats, photos and file conversions. Refills to 2,000 on Nov 30.",
                style = MochiType.Label,
                color = p.textSecondary
            )
        }

        // MochiLife Pro Card with 3 feature pills & Manage button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MochiShapes.card)
                .background(p.card)
                .border(1.dp, p.hairline, MochiShapes.card)
                .padding(MochiSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MochiSpacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "MochiLife Pro",
                        style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
                        color = p.textPrimary
                    )
                    Text(
                        text = "[PRICE] a month · renews Nov 30",
                        style = MochiType.Label,
                        color = p.textSecondary
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(MochiShapes.pill)
                        .background(p.bg)
                        .border(1.dp, p.hairline, MochiShapes.pill)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Manage",
                        style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold),
                        color = p.textPrimary
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProFeaturePill("Every companion", p)
                ProFeaturePill("Longer voice chats", p)
            }
            ProFeaturePill("2,000 energy a month", p)
        }

        Text(
            text = "Top up energy",
            style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
            color = p.textPrimary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MochiSpacing.xs)
        ) {
            listOf(
                Triple("500", "[PRICE]", false),
                Triple("1,500", "[PRICE]", true),
                Triple("5,000", "[PRICE]", false)
            ).forEach { (amount, price, popular) ->
                Box(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MochiShapes.card)
                            .background(if (popular) p.goldPillBg.copy(alpha = 0.65f) else p.card)
                            .border(
                                1.dp,
                                if (popular) p.gold else p.hairline,
                                MochiShapes.card
                            )
                            .clickable {
                                val delta = amount.replace(",", "").toInt()
                                onUpdateSettings { it.copy(aiEnergyBalance = it.aiEnergyBalance + delta) }
                            }
                            .padding(vertical = 18.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = amount,
                            style = MochiType.Title,
                            color = p.textPrimary
                        )
                        Text(
                            text = "energy",
                            style = MochiType.Label,
                            color = p.textSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = price,
                            style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold),
                            color = p.gold
                        )
                    }
                    if (popular) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-10).dp)
                                .clip(MochiShapes.pill)
                                .background(p.gold)
                                .padding(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "POPULAR",
                                style = MochiType.Label.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF17181A)
                            )
                        }
                    }
                }
            }
        }

        // Daily check-in card with 7 streak bars & Check in button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MochiShapes.card)
                .background(p.card)
                .border(1.dp, p.hairline, MochiShapes.card)
                .padding(MochiSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Daily check-in",
                    style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
                    color = p.textPrimary
                )
                Text(
                    text = "${settings.streakDays}-day streak · +10 energy a day",
                    style = MochiType.Label,
                    color = p.textSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(7) { idx ->
                        val active = idx < settings.streakDays
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height(6.dp)
                                .clip(MochiShapes.pill)
                                .background(if (active) p.gold else p.hairline)
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .clip(MochiShapes.pill)
                    .background(accent)
                    .clickable(enabled = !checkedInToday) {
                        onUpdateSettings {
                            it.copy(
                                checkedInToday = true,
                                streakDays = (it.streakDays + 1).coerceAtMost(7),
                                aiEnergyBalance = it.aiEnergyBalance + 10
                            )
                        }
                    }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    text = if (checkedInToday) "Checked in" else "Check in",
                    style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF07090F)
                )
            }
        }

        Text(
            text = "Recent activity",
            style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
            color = p.textPrimary
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MochiShapes.card)
                .background(p.card)
                .border(1.dp, p.hairline, MochiShapes.card)
                .padding(MochiSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MochiSpacing.sm)
        ) {
            WalletActivityRow("Fridge Chef photo", "Today, 7:12 PM", "–20", p)
            HairlineDivider(p.hairline)
            WalletActivityRow("Voice chat with Elena, 6 min", "Today, 6:40 PM", "–30", p)
        }
    }
}

@Composable
private fun ProFeaturePill(text: String, palette: SuitePalette) {
    Box(
        modifier = Modifier
            .clip(MochiShapes.pill)
            .background(palette.bg)
            .border(1.dp, palette.hairline, MochiShapes.pill)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = text, style = MochiType.Label, color = palette.textPrimary)
    }
}

@Composable
private fun WalletActivityRow(title: String, subtitle: String, delta: String, palette: SuitePalette) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, style = MochiType.Body.copy(fontWeight = FontWeight.Medium), color = palette.textPrimary)
            Text(subtitle, style = MochiType.Label, color = palette.textSecondary)
        }
        Text(
            text = delta,
            style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
            color = palette.textPrimary
        )
    }
}

private data class PermissionEntry(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val allowed: Boolean
)

/**
 * Permissions Screen matching Permissions.png & Permissions - light.png.
 */
@Composable
fun PermissionsScreen(
    onBack: () -> Unit,
    settings: MochiPersistentSettings = MochiPersistentSettings(),
    onTogglePermission: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val p = rememberSuiteColors()
    val accent = LocalCharacterAccent.current
    val basePermissions = remember {
        listOf(
            PermissionEntry(
                icon = Icons.Outlined.Equalizer,
                title = "Usage access",
                subtitle = "Needed for busy mode. Tells Elena when a video or game is open.",
                allowed = false
            ),
            PermissionEntry(
                icon = Icons.Outlined.DirectionsCar,
                title = "Physical activity",
                subtitle = "Lets driving mode start by itself.",
                allowed = false
            ),
            PermissionEntry(
                icon = Icons.Outlined.Mic,
                title = "Microphone",
                subtitle = "So you can talk to your companion.",
                allowed = true
            ),
            PermissionEntry(
                icon = Icons.Outlined.PictureInPictureAlt,
                title = "Display over other apps",
                subtitle = "Lets Elena float on your home screen.",
                allowed = true
            ),
            PermissionEntry(
                icon = Icons.Outlined.PersonOutline,
                title = "Contacts",
                subtitle = "To call or message people by name.",
                allowed = true
            ),
            PermissionEntry(
                icon = Icons.Outlined.Call,
                title = "Phone calls",
                subtitle = "To place calls when you ask.",
                allowed = true
            ),
            PermissionEntry(
                icon = Icons.Outlined.CameraAlt,
                title = "Camera",
                subtitle = "For Fridge Chef and scanning documents.",
                allowed = true
            ),
            PermissionEntry(
                icon = Icons.Outlined.NotificationsNone,
                title = "Notifications",
                subtitle = "Reminders and answers saved for later.",
                allowed = true
            )
        )
    }
    val permissions = basePermissions.map { entry ->
        entry.copy(allowed = settings.allowedPermissions.contains(entry.title))
    }
    val allowedCount = permissions.count { it.allowed }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = MochiSpacing.screenPadding, vertical = MochiSpacing.sm)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MochiSpacing.md)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MochiSpacing.md)
        ) {
            BackPillButton(palette = p, onClick = onBack)
            Text(
                text = "Permissions",
                style = MochiType.Title,
                color = p.textPrimary
            )
        }

        // Allowed summary card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MochiShapes.card)
                .background(p.card)
                .border(1.dp, p.hairline, MochiShapes.card)
                .padding(MochiSpacing.md),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "$allowedCount of ${permissions.size} allowed",
                style = MochiType.Title,
                color = p.textPrimary
            )
            Text(
                text = "MochiLife only asks for what each feature needs. You can change any of these later.",
                style = MochiType.Label,
                color = p.textSecondary
            )
            LinearProgressIndicator(
                progress = { allowedCount.toFloat() / permissions.size.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(MochiShapes.pill),
                color = accent,
                trackColor = p.hairline
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MochiShapes.card)
                .background(p.card)
                .border(1.dp, p.hairline, MochiShapes.card)
        ) {
            permissions.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onTogglePermission(entry.title)
                        }
                        .padding(MochiSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MochiSpacing.sm),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(p.bg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = entry.icon,
                                contentDescription = null,
                                tint = p.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = entry.title,
                                style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
                                color = p.textPrimary
                            )
                            Text(
                                text = entry.subtitle,
                                style = MochiType.Label,
                                color = p.textSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (entry.allowed) {
                        Box(
                            modifier = Modifier
                                .clip(MochiShapes.pill)
                                .background(accent.copy(alpha = 0.16f))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "✓ Allowed",
                                style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold),
                                color = accent
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(MochiShapes.pill)
                                .background(accent)
                                .padding(horizontal = 18.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Allow",
                                style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFF07090F)
                            )
                        }
                    }
                }
                if (index < permissions.lastIndex) {
                    HairlineDivider(p.hairline)
                }
            }
        }
    }
}

/**
 * Privacy and Data Screen matching Privacy and data.png & Privacy and data - light.png.
 */
@Composable
fun PrivacyAndDataScreen(
    onBack: () -> Unit,
    settings: MochiPersistentSettings = MochiPersistentSettings(),
    onUpdateSettings: ((MochiPersistentSettings) -> MochiPersistentSettings) -> Unit = {},
    onClearChatHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val p = rememberSuiteColors()
    val accent = LocalCharacterAccent.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = MochiSpacing.screenPadding, vertical = MochiSpacing.sm)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MochiSpacing.md)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MochiSpacing.md)
        ) {
            BackPillButton(palette = p, onClick = onBack)
            Text(
                text = "Privacy and data",
                style = MochiType.Title,
                color = p.textPrimary
            )
        }

        // Top Shield Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MochiShapes.card)
                .background(accent.copy(alpha = 0.12f))
                .border(1.dp, accent.copy(alpha = 0.3f), MochiShapes.card)
                .padding(MochiSpacing.md),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(MochiSpacing.sm)
        ) {
            Icon(
                imageVector = Icons.Outlined.Shield,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = "Your chats are private to your account. Elena never posts, sends or shares anything without asking you first.",
                style = MochiType.Body,
                color = p.textPrimary
            )
        }

        Text(
            text = "What MochiLife keeps",
            style = MochiType.Label,
            color = p.textSecondary
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MochiShapes.card)
                .background(p.card)
                .border(1.dp, p.hairline, MochiShapes.card)
        ) {
            SettingsToggleRow(
                title = "Save chat history",
                subtitle = "So you can find past chats in Library",
                checked = settings.saveChatHistory,
                accent = accent,
                palette = p,
                onCheckedChange = { enabled ->
                    onUpdateSettings { it.copy(saveChatHistory = enabled) }
                }
            )
            HairlineDivider(p.hairline)
            SettingsToggleRow(
                title = "Use my voice tone for check-ins",
                subtitle = "Elena notices stress in your voice. Off by default.",
                checked = settings.useVoiceToneForCheckIns,
                accent = accent,
                palette = p,
                onCheckedChange = { enabled ->
                    onUpdateSettings { it.copy(useVoiceToneForCheckIns = enabled) }
                }
            )
            HairlineDivider(p.hairline)
            SettingsToggleRow(
                title = "Share anonymous usage data",
                subtitle = "Helps improve MochiLife. Never includes your chats.",
                checked = settings.shareAnonymousUsage,
                accent = accent,
                palette = p,
                onCheckedChange = { enabled ->
                    onUpdateSettings { it.copy(shareAnonymousUsage = enabled) }
                }
            )
        }

        Text(
            text = "Your data",
            style = MochiType.Label,
            color = p.textSecondary
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MochiShapes.card)
                .background(p.card)
                .border(1.dp, p.hairline, MochiShapes.card)
        ) {
            PrivacyActionRow(
                icon = Icons.Outlined.Download,
                label = "Download my data",
                tint = p.textPrimary
            )
            HairlineDivider(p.hairline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClearChatHistory() }
                    .padding(MochiSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MochiSpacing.sm)
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = MochiTokens.danger,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Clear chat history",
                    style = MochiType.Body.copy(fontWeight = FontWeight.Medium),
                    color = MochiTokens.danger
                )
            }
            HairlineDivider(p.hairline)
            PrivacyActionRow(
                icon = Icons.Outlined.PersonRemove,
                label = "Delete my account",
                tint = MochiTokens.danger
            )
        }

        Text(
            text = "Read the privacy policy",
            style = MochiType.Body.copy(fontWeight = FontWeight.Medium),
            color = accent,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun PrivacyActionRow(icon: ImageVector, label: String, tint: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {}
            .padding(MochiSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MochiSpacing.sm)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MochiType.Body.copy(fontWeight = FontWeight.Medium),
            color = tint
        )
    }
}

/**
 * Help and Feedback Screen matching Help and feedback.png & Help and feedback - light.png.
 */
@Composable
fun HelpAndFeedbackScreen(
    onAskCompanion: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = rememberSuiteColors()
    val accent = LocalCharacterAccent.current
    var expandedQuestionIndex by remember { mutableIntStateOf(0) }

    val faqs = listOf(
        "Why can’t Elena hear me after I leave the app?" to
            "Turn on the floating companion inside the app first. Android only gives the microphone to a companion that was started while the app was open.",
        "How do I stop her interrupting my video or game?" to
            "Open Settings → When you're busy and enable Quiet mode while playing games or watching full-screen videos.",
        "Does Elena send WhatsApp messages for me?" to
            "Elena drafts your message and shows a 3-second countdown before opening WhatsApp so you are always in control.",
        "What is AI energy?" to
            "AI energy powers cloud tools like Fridge Chef and deep voice chats. On-device file conversions are always free.",
        "Can I use MochiLife while driving?" to
            "Yes. Driving mode uses extra-large voice buttons and reads incoming replies out loud so you never have to look at the screen."
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = MochiSpacing.screenPadding, vertical = MochiSpacing.sm)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MochiSpacing.md)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MochiSpacing.md)
        ) {
            BackPillButton(palette = p, onClick = onBack)
            Text(
                text = "Help and feedback",
                style = MochiType.Title,
                color = p.textPrimary
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MochiShapes.card)
                .background(accent.copy(alpha = 0.12f))
                .border(1.dp, accent.copy(alpha = 0.35f), MochiShapes.card)
                .clickable { onAskCompanion() }
                .padding(MochiSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MochiSpacing.sm)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "E",
                        style = MochiType.Body.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF07090F)
                    )
                }
                Column {
                    Text(
                        text = "Ask Elena how to do something",
                        style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
                        color = p.textPrimary
                    )
                    Text(
                        text = "She knows every setting in the app",
                        style = MochiType.Label,
                        color = p.textSecondary
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = p.textSecondary
            )
        }

        Text(
            text = "Common questions",
            style = MochiType.Label,
            color = p.textSecondary
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MochiShapes.card)
                .background(p.card)
                .border(1.dp, p.hairline, MochiShapes.card)
        ) {
            faqs.forEachIndexed { index, (question, answer) ->
                val expanded = expandedQuestionIndex == index
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            expandedQuestionIndex = if (expanded) -1 else index
                        }
                        .padding(MochiSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = question,
                            style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
                            color = p.textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                            contentDescription = null,
                            tint = p.textSecondary
                        )
                    }
                    if (expanded) {
                        Text(
                            text = answer,
                            style = MochiType.Body,
                            color = p.textSecondary
                        )
                    }
                }
                if (index < faqs.lastIndex) {
                    HairlineDivider(p.hairline)
                }
            }
        }

        Text(
            text = "Get in touch",
            style = MochiType.Label,
            color = p.textSecondary
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MochiShapes.card)
                .background(p.card)
                .border(1.dp, p.hairline, MochiShapes.card)
        ) {
            PrivacyActionRow(Icons.Outlined.ChatBubbleOutline, "Contact support", p.textPrimary)
            HairlineDivider(p.hairline)
            PrivacyActionRow(Icons.Outlined.WarningAmber, "Report a problem", p.textPrimary)
            HairlineDivider(p.hairline)
            PrivacyActionRow(Icons.Outlined.StarBorder, "Rate MochiLife", p.textPrimary)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "MochiLife 1.0",
                style = MochiType.Label,
                color = p.textTertiary
            )
        }
    }
}

/**
 * When You're Busy Screen matching When you're busy.png & When you're busy - light.png.
 */
@Composable
fun WhenYouAreBusyScreen(
    onBack: () -> Unit,
    settings: MochiPersistentSettings = MochiPersistentSettings(),
    onUpdateSettings: ((MochiPersistentSettings) -> MochiPersistentSettings) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val p = rememberSuiteColors()
    val accent = LocalCharacterAccent.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = MochiSpacing.screenPadding, vertical = MochiSpacing.sm)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MochiSpacing.md)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MochiSpacing.md)
        ) {
            BackPillButton(palette = p, onClick = onBack)
            Text(
                text = "When you're busy",
                style = MochiType.Title,
                color = p.textPrimary
            )
        }

        Text(
            text = "Elena never covers what you’re doing. You can still talk to her; she answers quietly.",
            style = MochiType.Body,
            color = p.textSecondary
        )

        // Mountain video preview card with edge-docked "E ||| Answering" pill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .clip(MochiShapes.card)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF333B54), Color(0xFFB57C5E), Color(0xFF2E1E2B))
                    )
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path().apply {
                    moveTo(0f, size.height * 0.72f)
                    lineTo(size.width * 0.22f, size.height * 0.36f)
                    lineTo(size.width * 0.38f, size.height * 0.58f)
                    lineTo(size.width * 0.62f, size.height * 0.28f)
                    lineTo(size.width * 0.82f, size.height * 0.52f)
                    lineTo(size.width, size.height * 0.38f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(path, Color(0xFF2D1D2B))
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 18.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                    .background(Color(0xFF1A1C23))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "E",
                        style = MochiType.Label.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF07090F)
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.GraphicEq,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Answering",
                    style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White
                )
            }

            Text(
                text = "What it looks like over a video",
                style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
            )
        }

        Text(
            text = "She treats you as busy when you’re...",
            style = MochiType.Label,
            color = p.textSecondary
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MochiShapes.card)
                .background(p.card)
                .border(1.dp, p.hairline, MochiShapes.card)
        ) {
            SettingsToggleRow(
                title = "Watching a video",
                subtitle = null,
                checked = settings.busyWatchingVideos,
                accent = accent,
                palette = p,
                onCheckedChange = { enabled ->
                    onUpdateSettings { it.copy(busyWatchingVideos = enabled) }
                }
            )
            HairlineDivider(p.hairline)
            SettingsToggleRow(
                title = "Playing a game",
                subtitle = null,
                checked = settings.busyPlayingGames,
                accent = accent,
                palette = p,
                onCheckedChange = { enabled ->
                    onUpdateSettings { it.copy(busyPlayingGames = enabled) }
                }
            )
            HairlineDivider(p.hairline)
            SettingsToggleRow(
                title = "In any full-screen app",
                subtitle = "Reading, documents, social feeds",
                checked = settings.busyFullScreenApps,
                accent = accent,
                palette = p,
                onCheckedChange = { enabled ->
                    onUpdateSettings { it.copy(busyFullScreenApps = enabled) }
                }
            )
            HairlineDivider(p.hairline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MochiSpacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "On a call or in Do Not Disturb",
                    style = MochiType.Body.copy(fontWeight = FontWeight.Medium),
                    color = p.textPrimary
                )
                Text(
                    text = "Always",
                    style = MochiType.Body,
                    color = p.textSecondary
                )
            }
        }

        Text(
            text = "While you’re busy, she...",
            style = MochiType.Label,
            color = p.textSecondary
        )

        val behaviors = listOf(
            "Answers by voice, shows a small pill" to "Her reply is spoken. Only a slim pill appears at the screen edge.",
            "Stays silent and saves it for later" to "A small chip appears. Read the answer when you are ready.",
            "Acts normally" to "Shows her speech bubble as usual."
        )

        behaviors.forEachIndexed { index, (title, subtitle) ->
            val selected = settings.busyBehaviorIndex == index
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MochiShapes.card)
                    .background(if (selected) accent.copy(alpha = 0.12f) else p.card)
                    .border(
                        1.dp,
                        if (selected) accent.copy(alpha = 0.6f) else p.hairline,
                        MochiShapes.card
                    )
                    .clickable {
                        onUpdateSettings { it.copy(busyBehaviorIndex = index) }
                    }
                    .padding(MochiSpacing.md),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(MochiSpacing.sm)
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (selected) accent else Color.Transparent)
                        .border(1.5.dp, if (selected) accent else p.textTertiary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF07090F))
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
                        color = p.textPrimary
                    )
                    Text(
                        text = subtitle,
                        style = MochiType.Label,
                        color = p.textSecondary
                    )
                }
            }
        }
    }
}

/**
 * Driving Mode Screen matching Driving mode.png & Driving mode - light.png.
 */
@Composable
fun DrivingModeScreen(
    activeCharacter: CharacterSpec,
    controller: CharacterController,
    onStartVoicePrompt: (String) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = rememberSuiteColors()
    val isLight = LocalVisualStyle.current.isLight
    val accent = LocalCharacterAccent.current
    var countdown by remember { mutableIntStateOf(3) }

    LaunchedEffect(Unit) {
        while (countdown > 1) {
            delay(1000L)
            countdown--
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(p.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = MochiSpacing.screenPadding, vertical = MochiSpacing.sm),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar: Car icon + "Driving mode" + "Voice only" pill on left, "Exit" button on right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.DirectionsCar,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Driving mode",
                    style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
                    color = p.textPrimary
                )
                Box(
                    modifier = Modifier
                        .clip(MochiShapes.pill)
                        .background(accent.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Voice only",
                        style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold),
                        color = accent
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(MochiShapes.pill)
                    .background(p.card)
                    .border(1.dp, p.hairline, MochiShapes.pill)
                    .clickable { onExit() }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Exit",
                    style = MochiType.Body.copy(fontWeight = FontWeight.Medium),
                    color = p.textPrimary
                )
            }
        }

        // Center Concentric Rings + 3D Avatar + "Calling Mom in 3" + 'Say "stop" to cancel'
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MochiSpacing.md)
        ) {
            Box(
                modifier = Modifier.size(260.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = accent.copy(alpha = 0.12f),
                        radius = size.minDimension * 0.48f,
                        style = Stroke(width = 3f)
                    )
                    drawCircle(
                        color = accent.copy(alpha = 0.24f),
                        radius = size.minDimension * 0.41f,
                        style = Stroke(width = 3f)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(182.dp)
                        .clip(CircleShape)
                        .background(p.card)
                        .border(2.5.dp, accent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    CharacterStageView(
                        character = activeCharacter,
                        controller = controller,
                        halfResolutionMode = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Text(
                text = "Calling Mom in $countdown",
                style = MochiType.Display,
                color = p.textPrimary
            )
            Text(
                text = "Say \"stop\" to cancel",
                style = MochiType.Body,
                color = p.textSecondary
            )
        }

        // Giant high-contrast Cancel button at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = MochiSpacing.md)
                .height(84.dp)
                .clip(MochiShapes.pill)
                .background(if (isLight) Color(0xFF17181A) else Color(0xFFF3F5F9))
                .clickable { onExit() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Cancel",
                style = MochiType.Title.copy(fontWeight = FontWeight.SemiBold),
                color = if (isLight) Color(0xFFF6F3EC) else Color(0xFF07090F)
            )
        }
    }
}

@Composable
private fun BackPillButton(palette: SuitePalette, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(MochiSpacing.minTouchTarget)
            .clip(MochiShapes.pill)
            .background(palette.card)
            .border(1.dp, palette.hairline, MochiShapes.pill)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = "Back",
            tint = palette.textPrimary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun SettingsIconNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailingText: String = "",
    palette: SuitePalette,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(MochiSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MochiSpacing.sm),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.bg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = palette.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
                    color = palette.textPrimary
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MochiType.Label,
                        color = palette.textSecondary
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (trailingText.isNotEmpty()) {
                Text(
                    text = trailingText,
                    style = MochiType.Body,
                    color = palette.textSecondary
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = palette.textSecondary
            )
        }
    }
}

@Composable
private fun SettingsIconToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    accent: Color,
    palette: SuitePalette,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MochiSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MochiSpacing.sm),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.bg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = palette.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
                    color = palette.textPrimary
                )
                Text(
                    text = subtitle,
                    style = MochiType.Label,
                    color = palette.textSecondary
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = palette.hairline
            )
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    accent: Color,
    palette: SuitePalette,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MochiSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
                color = palette.textPrimary
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MochiType.Label,
                    color = palette.textSecondary
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = palette.hairline
            )
        )
    }
}

@Composable
private fun HairlineDivider(color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}

/**
 * Quests View (`#/quests` HashRoute) — Interactive daily micro-wins & companion quests.
 */
@Composable
fun QuestsScreen(
    quests: List<QuestItem>,
    aiEnergyBalance: Int,
    onToggleQuest: (String) -> Unit,
    onAddQuest: (String, QuestCategory) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = rememberSuiteColors()
    val accent = LocalCharacterAccent.current
    var newQuestTitle by remember { mutableStateOf("") }
    val completedCount = quests.count { it.isCompleted }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = MochiSpacing.screenPadding, vertical = MochiSpacing.sm)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MochiSpacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MochiSpacing.md)
            ) {
                BackPillButton(palette = p, onClick = onBack)
                Text(
                    text = "Daily quests",
                    style = MochiType.Title,
                    color = p.textPrimary
                )
            }
            Box(
                modifier = Modifier
                    .clip(MochiShapes.pill)
                    .background(p.goldPillBg)
                    .border(1.dp, p.gold.copy(alpha = 0.45f), MochiShapes.pill)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "⚡ %,d energy".format(aiEnergyBalance),
                    style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold),
                    color = p.gold
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MochiShapes.card)
                .background(p.card)
                .border(1.dp, p.hairline, MochiShapes.card)
                .padding(MochiSpacing.md),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "$completedCount of ${quests.size.coerceAtLeast(1)} completed today",
                style = MochiType.Title,
                color = p.textPrimary
            )
            Text(
                text = "Each completed quest rewards AI energy for voice chats and Fridge Chef.",
                style = MochiType.Label,
                color = p.textSecondary
            )
            LinearProgressIndicator(
                progress = { completedCount.toFloat() / quests.size.coerceAtLeast(1).toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(MochiShapes.pill),
                color = accent,
                trackColor = p.hairline
            )
        }

        // Add new quest bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MochiSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .clip(MochiShapes.pill)
                    .background(p.card)
                    .border(1.dp, p.hairline, MochiShapes.pill)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (newQuestTitle.isEmpty()) {
                    Text(
                        text = "Add a quick 5-minute win...",
                        style = MochiType.Body,
                        color = p.textTertiary
                    )
                }
                BasicTextField(
                    value = newQuestTitle,
                    onValueChange = { newQuestTitle = it },
                    textStyle = MochiType.Body.copy(color = p.textPrimary),
                    cursorBrush = SolidColor(accent),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Box(
                modifier = Modifier
                    .height(50.dp)
                    .clip(MochiShapes.pill)
                    .background(accent)
                    .clickable {
                        if (newQuestTitle.isNotBlank()) {
                            onAddQuest(newQuestTitle, QuestCategory.DAY)
                            newQuestTitle = ""
                        }
                    }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Add",
                    style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF07090F)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MochiShapes.card)
                .background(p.card)
                .border(1.dp, p.hairline, MochiShapes.card)
        ) {
            quests.forEachIndexed { index, quest ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleQuest(quest.id) }
                        .padding(MochiSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MochiSpacing.sm),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (quest.isCompleted) accent else Color.Transparent)
                                .border(1.5.dp, if (quest.isCompleted) accent else p.textSecondary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (quest.isCompleted) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = "Completed",
                                    tint = Color(0xFF07090F),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = quest.title,
                                style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
                                color = if (quest.isCompleted) p.textSecondary else p.textPrimary
                            )
                            Text(
                                text = "${quest.categoryEnum.label} · +${quest.coinsReward} energy",
                                style = MochiType.Label,
                                color = p.textSecondary
                            )
                        }
                    }
                }
                if (index < quests.lastIndex) {
                    HairlineDivider(p.hairline)
                }
            }
        }
    }
}

