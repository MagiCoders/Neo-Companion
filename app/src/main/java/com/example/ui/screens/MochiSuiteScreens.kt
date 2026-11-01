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
import androidx.compose.foundation.layout.heightIn
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
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
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
import com.example.data.LibraryEntry
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.CheckCircleOutline
import com.example.data.MochiPersistentSettings
import com.example.data.UserProfile
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
 * Library: everything you really made — recipes, quests and files. Nothing here is sample content.
 */
@Composable
fun LibraryScreen(
    entries: List<LibraryEntry>,
    onOpenSearch: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenEntry: (LibraryEntry) -> Unit,
    onOpenTools: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = rememberSuiteColors()
    var selected by remember { mutableStateOf<LibraryEntry.Kind?>(null) }
    val shown = entries.filter { selected == null || it.kind == selected }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = MochiSpacing.screenPadding, vertical = MochiSpacing.sm)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MochiSpacing.md)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Library", style = MochiType.Display, color = p.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(MochiSpacing.xs)) {
                RoundIconButton(Icons.Outlined.Search, "Search library", p, onOpenSearch)
                RoundIconButton(Icons.Outlined.Person, "Profile", p, onOpenProfile)
            }
        }
        Text("Everything you and your companions made.", style = MochiType.Body, color = p.textSecondary)

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MochiSpacing.xs)) {
            KindChip("All", entries.size, selected == null, p) { selected = null }
            LibraryEntry.Kind.entries.forEach { kind ->
                KindChip(kind.label, entries.count { it.kind == kind }, selected == kind, p) { selected = kind }
            }
        }

        if (shown.isEmpty()) {
            EmptyLibraryCard(p, onOpenTools)
        } else {
            LibraryEntryList(shown, p, onOpenEntry)
        }
        Spacer(Modifier.height(MochiSpacing.md))
    }
}

/** Search across the Library. Results come only from your own items. */
@Composable
fun SearchScreen(
    entries: List<LibraryEntry>,
    onBack: () -> Unit,
    onOpenEntry: (LibraryEntry) -> Unit,
    onAskCompanion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val p = rememberSuiteColors()
    val accent = LocalCharacterAccent.current
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val results = if (q.isEmpty()) emptyList() else entries.filter {
        it.title.contains(q, ignoreCase = true) || it.subtitle.contains(q, ignoreCase = true)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = MochiSpacing.screenPadding, vertical = MochiSpacing.sm)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MochiSpacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MochiSpacing.sm)) {
            Row(
                Modifier.weight(1f).height(48.dp).clip(MochiShapes.pill).background(p.card)
                    .border(1.dp, p.hairline, MochiShapes.pill).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Search, null, tint = p.textSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search recipes, quests, files", style = MochiType.Body, color = p.textTertiary)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it.take(100) },
                        singleLine = true,
                        textStyle = MochiType.Body.copy(color = p.textPrimary),
                        cursorBrush = SolidColor(accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(Icons.Outlined.Close, "Clear", tint = p.textSecondary, modifier = Modifier.size(18.dp).clickable { query = "" })
                }
            }
            Text("Cancel", style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold), color = p.gold, modifier = Modifier.clickable(onClick = onBack))
        }

        if (q.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().clip(MochiShapes.card).background(accent.copy(alpha = 0.12f))
                    .border(1.dp, accent.copy(alpha = 0.35f), MochiShapes.card).clickable { onAskCompanion(q) }.padding(MochiSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Ask your companion about \"$q\"", style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold), color = p.textPrimary)
                    Text("Get an answer instead of a list", style = MochiType.Label, color = p.textSecondary)
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = p.textSecondary)
            }
            if (results.isEmpty()) {
                Text("Nothing in your Library matches \"$q\".", style = MochiType.Body, color = p.textSecondary)
            } else {
                LibraryEntryList(results, p, onOpenEntry)
            }
        } else if (entries.isEmpty()) {
            Text("Your Library is empty. Recipes, quests and converted files will show up here.", style = MochiType.Body, color = p.textSecondary)
        }
    }
}

@Composable
private fun LibraryEntryList(items: List<LibraryEntry>, p: SuitePalette, onOpen: (LibraryEntry) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(MochiShapes.card).background(p.card).border(1.dp, p.hairline, MochiShapes.card)) {
        items.forEachIndexed { i, e ->
            LibraryItemRow(
                icon = when (e.kind) {
                    LibraryEntry.Kind.RECIPE -> Icons.Outlined.Restaurant
                    LibraryEntry.Kind.QUEST -> Icons.Outlined.CheckCircleOutline
                    LibraryEntry.Kind.FILE -> Icons.Outlined.Description
                },
                iconTint = when (e.kind) {
                    LibraryEntry.Kind.RECIPE -> p.gold
                    LibraryEntry.Kind.QUEST -> LocalCharacterAccent.current
                    LibraryEntry.Kind.FILE -> p.textSecondary
                },
                title = e.title,
                subtitle = e.subtitle,
                time = relativeTime(e.timestampMillis),
                palette = p,
                onClick = { onOpen(e) }
            )
            if (i < items.lastIndex) HairlineDivider(p.hairline)
        }
    }
}

@Composable
private fun EmptyLibraryCard(p: SuitePalette, onOpenTools: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(MochiShapes.card).background(p.card).border(1.dp, p.hairline, MochiShapes.card).padding(MochiSpacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Nothing here yet", style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold), color = p.textPrimary)
        Text("Cook with Fridge Chef, finish a quest or convert a file — it will be saved here.", style = MochiType.Label, color = p.textSecondary)
        Text("Open Tools", style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold), color = p.gold, modifier = Modifier.clickable(onClick = onOpenTools).padding(top = 4.dp))
    }
}

@Composable
private fun KindChip(label: String, count: Int, selected: Boolean, p: SuitePalette, onClick: () -> Unit) {
    Box(
        Modifier.clip(MochiShapes.pill)
            .background(if (selected) p.goldPillBg else p.card)
            .border(1.dp, if (selected) p.gold.copy(alpha = 0.5f) else p.hairline, MochiShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(if (count > 0) "$label · $count" else label, style = MochiType.Label, color = if (selected) p.gold else p.textPrimary)
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, label: String, p: SuitePalette, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(MochiShapes.pill).background(p.card).border(1.dp, p.hairline, MochiShapes.pill).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = label, tint = p.textPrimary, modifier = Modifier.size(20.dp)) }
}

private fun relativeTime(millis: Long): String {
    val diff = System.currentTimeMillis() - millis
    val min = diff / 60_000
    return when {
        min < 1 -> "now"
        min < 60 -> "${min}m"
        min < 60 * 24 -> "${min / 60}h"
        min < 60 * 24 * 7 -> "${min / (60 * 24)}d"
        else -> java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date(millis))
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
    onBack: () -> Unit,
    settings: MochiPersistentSettings = MochiPersistentSettings(),
    profile: UserProfile = UserProfile(),
    onSignOut: () -> Unit = {},
    onUpdateSettings: ((MochiPersistentSettings) -> MochiPersistentSettings) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val p = rememberSuiteColors()
    val accent = LocalCharacterAccent.current
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showWakeConfirm by remember { mutableStateOf(false) }
    var showWakePhraseSheet by remember { mutableStateOf(false) }
    val wakeDisplay = com.example.ai.WakePhrases.display(settings.wakePhrase)
    var showSignOutConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val realAllowed = remember(settings) {
        listOf("Usage access", "Physical activity", "Microphone", "Display over other apps", "Contacts", "Phone calls", "Camera", "Notifications", AccessDisclosure.READ_MESSAGES_TITLE, AccessDisclosure.WHATSAPP_HELPER_TITLE)
            .count { SystemPermissions.isGranted(context, it) }
    }
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
                        text = if (profile.isProSubscriber) "∞" else "${profile.aiCreditsRemaining}/${profile.aiCreditsMax}",
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
                title = "Voice language",
                trailingText = settings.preferredLanguage,
                palette = p,
                onClick = { showLanguageSheet = true }
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
                title = "\"$wakeDisplay\" wake word",
                subtitle = "Listens offline for this phrase only",
                checked = settings.wakeWordEnabled,
                accent = accent,
                palette = p,
                onCheckedChange = { enabled ->
                    if (enabled) showWakeConfirm = true
                    else onUpdateSettings { it.copy(wakeWordEnabled = false) }
                }
            )
            HairlineDivider(p.hairline)
            SettingsIconNavRow(
                icon = Icons.Outlined.GraphicEq,
                title = "Wake phrase",
                trailingText = wakeDisplay,
                palette = p,
                onClick = { showWakePhraseSheet = true }
            )
            HairlineDivider(p.hairline)
            SettingsIconToggleRow(
                icon = Icons.Outlined.Language,
                title = "Live voice chat (beta)",
                subtitle = "Open conversation instead of one turn at a time. Use headphones, or she may hear herself.",
                checked = settings.liveVoiceChat,
                accent = accent,
                palette = p,
                onCheckedChange = { enabled -> onUpdateSettings { it.copy(liveVoiceChat = enabled) } }
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
                trailingText = "$realAllowed of 8 allowed",
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
        }

        Text(text = "Appearance", style = MochiType.Label, color = p.textSecondary)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MochiShapes.card)
                .background(p.card)
                .border(1.dp, p.hairline, MochiShapes.card)
        ) {
            AppearanceSegmented(mode = settings.themeMode, accent = accent) { mode ->
                onUpdateSettings { it.copy(themeMode = mode) }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MochiShapes.card)
                .background(p.card)
                .border(1.dp, p.hairline, MochiShapes.card)
                .clickable { showSignOutConfirm = true }
                .padding(MochiSpacing.md),
            horizontalArrangement = Arrangement.Center
        ) {
            Text("Sign out", style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold), color = MochiTokens.danger)
        }

        DiagnosticsCard(p)

        Text(
            text = "MochiLife ${com.example.BuildConfig.VERSION_NAME} (build ${com.example.BuildConfig.VERSION_CODE})",
            style = MochiType.Label,
            color = p.textTertiary,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(MochiSpacing.md))
    }

    if (showLanguageSheet) {
        LanguageSheet(
            current = settings.preferredLanguage,
            accent = accent,
            onPick = { lang ->
                onUpdateSettings { it.copy(preferredLanguage = lang) }
                showLanguageSheet = false
            },
            onDismiss = { showLanguageSheet = false }
        )
    }
    if (showWakeConfirm) {
        SettingsConfirmDialog(
            title = "Listen for \"$wakeDisplay\"?",
            body = "While the app is open or your companion is floating, the microphone listens on this phone only, offline, for \"$wakeDisplay\". Nothing is recorded or sent anywhere until you say it. It uses a little more battery.",
            confirm = "Turn on",
            danger = false,
            accent = accent,
            onConfirm = {
                onUpdateSettings { it.copy(wakeWordEnabled = true) }
                showWakeConfirm = false
            },
            onDismiss = { showWakeConfirm = false }
        )
    }
    if (showWakePhraseSheet) {
        WakePhraseSheet(
            current = settings.wakePhrase,
            accent = accent,
            onPick = { phrase ->
                onUpdateSettings { it.copy(wakePhrase = phrase) }
                showWakePhraseSheet = false
            },
            onDismiss = { showWakePhraseSheet = false }
        )
    }
    if (showSignOutConfirm) {
        SettingsConfirmDialog(
            title = "Sign out?",
            body = "Your chats and settings stay saved to your account. You can sign back in any time.",
            confirm = "Sign out",
            danger = true,
            accent = accent,
            onConfirm = {
                showSignOutConfirm = false
                onSignOut()
            },
            onDismiss = { showSignOutConfirm = false }
        )
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
                                userName = fullName.trim(),
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
                            tint = MochiTokens.onAccent,
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
                            onUpdateSettings { s -> s.copy(userName = it.trim()) }
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
                            userName = fullName.trim(),
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
                color = MochiTokens.onAccent
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
    profile: UserProfile = UserProfile(),
    modifier: Modifier = Modifier
) {
    val p = rememberSuiteColors()
    val accent = LocalCharacterAccent.current
    var notice by remember { mutableStateOf<String?>(null) }
    val loaded = profile.userId.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = MochiSpacing.screenPadding, vertical = MochiSpacing.sm)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MochiSpacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MochiSpacing.md)) {
            BackPillButton(palette = p, onClick = onBack)
            Text(text = "Wallet", style = MochiType.Title, color = p.textPrimary)
        }

        // AI energy — read from your account, refilled by the server.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MochiShapes.card)
                .background(Brush.linearGradient(colors = listOf(p.card, p.goldPillBg)))
                .border(1.dp, p.gold.copy(alpha = 0.4f), MochiShapes.card)
                .padding(MochiSpacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("⚡ AI energy today", style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold), color = p.gold)
            Text(
                text = when {
                    !loaded -> "—"
                    profile.isProSubscriber -> "Unlimited"
                    else -> "${profile.aiCreditsRemaining} of ${profile.aiCreditsMax}"
                },
                style = MochiType.Display,
                color = p.textPrimary
            )
            if (loaded && !profile.isProSubscriber) {
                LinearProgressIndicator(
                    progress = { profile.aiCreditsRemaining.toFloat() / profile.aiCreditsMax.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(MochiShapes.pill),
                    color = accent,
                    trackColor = p.hairline
                )
            }
            Text(
                text = if (!loaded) "Sign in to see your allowance." else "One chat or photo uses one. Your allowance refills every day.",
                style = MochiType.Label,
                color = p.textSecondary
            )
        }

        // Plan
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MochiShapes.card)
                .background(p.card)
                .border(1.dp, p.hairline, MochiShapes.card)
                .padding(MochiSpacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (profile.isProSubscriber) "MochiLife Pro" else "Free plan", style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold), color = p.textPrimary)
                Text(
                    if (profile.isProSubscriber) "Unlimited voice and chat, every companion." else "Pro: unlimited voice and chat, every companion, custom personalities.",
                    style = MochiType.Label,
                    color = p.textSecondary
                )
            }
            if (!profile.isProSubscriber) {
                Box(
                    Modifier.clip(MochiShapes.pill).background(accent)
                        .clickable { notice = "Pro opens when MochiLife is published on Google Play. Purchases go through Google Play only." }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text("Go Pro", style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold), color = MochiTokens.onAccent)
                }
            }
        }

        // Progress — real numbers from your account.
        Row(horizontalArrangement = Arrangement.spacedBy(MochiSpacing.sm), modifier = Modifier.fillMaxWidth()) {
            listOf(
                "Gold coins" to "%,d".format(profile.goldCoins),
                "Level" to profile.level.toString(),
                "Saved" to "$%,d".format(profile.moneySavedDollars)
            ).forEach { (label, value) ->
                Column(
                    Modifier.weight(1f).clip(MochiShapes.card).background(p.card).border(1.dp, p.hairline, MochiShapes.card).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(value, style = MochiType.Title, color = p.textPrimary)
                    Text(label, style = MochiType.Label, color = p.textSecondary)
                }
            }
        }

        notice?.let {
            Text(it, style = MochiType.Label, color = p.textSecondary, modifier = Modifier.fillMaxWidth().clip(MochiShapes.card).background(p.card).padding(MochiSpacing.md))
        }
        Spacer(Modifier.height(MochiSpacing.md))
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
    val context = LocalContext.current
    val companionFirst = "your companion"
    // Real system state, re-read every time the screen resumes (e.g. after visiting Settings).
    var refreshTick by remember { mutableStateOf(0) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    val runtimeLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { refreshTick++ }

    val basePermissions = remember {
        listOf(
            PermissionEntry(Icons.Outlined.Equalizer, "Usage access", "Needed for busy mode. Tells $companionFirst when a video or game is open.", false),
            PermissionEntry(Icons.Outlined.DirectionsCar, "Physical activity", "Lets driving mode start by itself.", false),
            PermissionEntry(Icons.Outlined.Mic, "Microphone", "So you can talk to your companion.", false),
            PermissionEntry(Icons.Outlined.PictureInPictureAlt, "Display over other apps", "Lets your companion float over other apps.", false),
            PermissionEntry(Icons.Outlined.PersonOutline, "Contacts", "To call or message people by name.", false),
            PermissionEntry(Icons.Outlined.Call, "Phone calls", "To place calls when you ask.", false),
            PermissionEntry(Icons.Outlined.CameraAlt, "Camera", "For Fridge Chef and scanning documents.", false),
            PermissionEntry(Icons.Outlined.NotificationsNone, "Notifications", "Reminders and answers saved for later.", false),
            PermissionEntry(Icons.Outlined.NotificationsNone, AccessDisclosure.READ_MESSAGES_TITLE, "Reads new chat messages aloud and sends your spoken replies.", false),
            PermissionEntry(Icons.Outlined.Call, AccessDisclosure.WHATSAPP_HELPER_TITLE, "Presses Send, Play or Call in WhatsApp right after you ask.", false)
        )
    }
    val permissions = remember(refreshTick) {
        basePermissions.map { it.copy(allowed = SystemPermissions.isGranted(context, it.title)) }
    }
    val allowedCount = permissions.count { it.allowed }
    var disclosureFor by remember { mutableStateOf<String?>(null) }
    disclosureFor?.let { kind ->
        AccessDisclosureDialog(
            kind = kind,
            onAgree = { disclosureFor = null; AccessDisclosure.openSettings(context, kind) },
            onDismiss = { disclosureFor = null }
        )
    }
    val onTapPermission: (PermissionEntry) -> Unit = { entry ->
        if (AccessDisclosure.isSpecialAccess(entry.title)) {
            if (entry.allowed) AccessDisclosure.openSettings(context, entry.title) else disclosureFor = entry.title
        } else if (entry.allowed) {
            SystemPermissions.openAppSettings(context)
        } else {
            val runtime = SystemPermissions.runtimePermission(entry.title)
            if (runtime != null) runtimeLauncher.launch(runtime) else SystemPermissions.openSpecialSettings(context, entry.title)
        }
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
                        .clickable { onTapPermission(entry) }
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
                                color = MochiTokens.accentInk(accent)
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
                                color = MochiTokens.onAccent
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
                        color = MochiTokens.onAccent
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
                text = "MochiLife ${com.example.BuildConfig.VERSION_NAME} (build ${com.example.BuildConfig.VERSION_CODE})",
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
                        color = MochiTokens.onAccent
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
                                .background(MochiTokens.onAccent)
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
 * Driving mode: voice-first, huge targets, screen kept on. Everything shown is real state: a
 * countdown appears only when an action is actually pending, and the call status comes from the
 * live voice session.
 */
@Composable
fun DrivingModeScreen(
    activeCharacter: CharacterSpec,
    controller: CharacterController,
    pendingAction: com.example.ai.PendingVoiceAction?,
    liveStatus: com.example.ai.LiveConversation.Status,
    onTalk: () -> Unit,
    onReadMessages: () -> Unit,
    onCancelPending: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = rememberSuiteColors()
    val isLight = LocalVisualStyle.current.isLight
    val accent = LocalCharacterAccent.current
    val companionName = activeCharacter.displayName.substringAfter(" ")
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val (headline, hint) = when {
        pendingAction != null -> "${pendingAction.title} in ${pendingAction.remainingSeconds}" to "Say \"stop\" or tap Cancel"
        liveStatus == com.example.ai.LiveConversation.Status.CONNECTING -> "Connecting…" to "One moment"
        liveStatus == com.example.ai.LiveConversation.Status.LIVE -> "$companionName is listening" to "Just talk. Tap the button to hang up."
        else -> "Hands-free" to "Say \"Hey $companionName\" or tap Talk"
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.DirectionsCar, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                Text("Driving mode", style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold), color = p.textPrimary)
                Box(
                    Modifier.clip(MochiShapes.pill).background(accent.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Voice only", style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold), color = accent)
                }
            }
            Box(
                Modifier.clip(MochiShapes.pill).background(p.card).border(1.dp, p.hairline, MochiShapes.pill)
                    .clickable { onExit() }.padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Exit", style = MochiType.Body.copy(fontWeight = FontWeight.Medium), color = p.textPrimary)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MochiSpacing.md)) {
            Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                val ringAlpha = if (liveStatus == com.example.ai.LiveConversation.Status.LIVE || pendingAction != null) 0.45f else 0.18f
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(accent.copy(alpha = ringAlpha * 0.5f), radius = size.minDimension * 0.48f, style = Stroke(width = 3f))
                    drawCircle(accent.copy(alpha = ringAlpha), radius = size.minDimension * 0.41f, style = Stroke(width = 3f))
                }
                Box(
                    Modifier.size(170.dp).clip(CircleShape).background(p.card).border(2.5.dp, accent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    CharacterStageView(character = activeCharacter, controller = controller, halfResolutionMode = true, modifier = Modifier.fillMaxSize())
                }
            }
            Text(headline, style = MochiType.Display, color = p.textPrimary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(hint, style = MochiType.Body, color = p.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }

        Column(Modifier.fillMaxWidth().padding(bottom = MochiSpacing.md), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val strong = if (isLight) Color(0xFF17181A) else Color(0xFFF3F5F9)
            val onStrong = if (isLight) Color(0xFFF6F3EC) else MochiTokens.onAccent
            if (pendingAction != null) {
                DrivingButton("Cancel", strong, onStrong, onCancelPending)
            } else {
                val talkLabel = if (liveStatus == com.example.ai.LiveConversation.Status.IDLE) "Talk" else "Hang up"
                DrivingButton(talkLabel, accent, MochiTokens.onAccent, onTalk)
                DrivingButton("Read my messages", p.card, p.textPrimary, onReadMessages, border = p.hairline)
            }
        }
    }
}

@Composable
private fun DrivingButton(label: String, bg: Color, fg: Color, onClick: () -> Unit, border: Color? = null) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(MochiShapes.pill)
            .background(bg)
            .then(if (border != null) Modifier.border(1.dp, border, MochiShapes.pill) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MochiType.Title.copy(fontWeight = FontWeight.SemiBold), color = fg)
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
                    color = MochiTokens.onAccent
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
                                    tint = MochiTokens.onAccent,
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


/** Reads and requests the real Android permissions behind the Permissions screen rows. */
object SystemPermissions {
    fun runtimePermission(title: String): String? = when (title) {
        "Physical activity" -> if (android.os.Build.VERSION.SDK_INT >= 29) android.Manifest.permission.ACTIVITY_RECOGNITION else null
        "Microphone" -> android.Manifest.permission.RECORD_AUDIO
        "Contacts" -> android.Manifest.permission.READ_CONTACTS
        "Phone calls" -> android.Manifest.permission.CALL_PHONE
        "Camera" -> android.Manifest.permission.CAMERA
        "Notifications" -> if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.POST_NOTIFICATIONS else null
        else -> null
    }

    fun isGranted(context: android.content.Context, title: String): Boolean = when (title) {
        "Display over other apps" -> android.provider.Settings.canDrawOverlays(context)
        "Usage access" -> try {
            val ops = context.getSystemService(android.content.Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) ==
                android.app.AppOpsManager.MODE_ALLOWED
        } catch (_: Throwable) { false }
        "Notifications" -> androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
        "Physical activity" -> if (android.os.Build.VERSION.SDK_INT < 29) true else granted(context, android.Manifest.permission.ACTIVITY_RECOGNITION)
        AccessDisclosure.READ_MESSAGES_TITLE, AccessDisclosure.WHATSAPP_HELPER_TITLE -> AccessDisclosure.isGranted(context, title)
        else -> runtimePermission(title)?.let { granted(context, it) } ?: false
    }

    private fun granted(context: android.content.Context, perm: String) =
        androidx.core.content.ContextCompat.checkSelfPermission(context, perm) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun openSpecialSettings(context: android.content.Context, title: String) {
        val pkg = android.net.Uri.parse("package:${context.packageName}")
        val intent = when (title) {
            "Display over other apps" -> android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg)
            "Usage access" -> android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
            else -> android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        }
        try { context.startActivity(intent) } catch (_: Exception) { openAppSettings(context) }
    }

    fun openAppSettings(context: android.content.Context) {
        try {
            context.startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:${context.packageName}")
                )
            )
        } catch (_: Exception) {}
    }
}

// ---------------------------------------------------------------------------------------------
// Settings sheets & dialogs (Screen map: Language sheet, wake-word confirm, Appearance, Sign out)
// ---------------------------------------------------------------------------------------------

private val SettingsLanguages = com.example.ai.VoiceLanguages.ALL.map { it.label }

/** Pick a preset wake phrase or type a custom one (checked against the offline vocabulary). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun WakePhraseSheet(current: String, accent: Color, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var custom by remember { mutableStateOf(if (current in com.example.ai.WakePhrases.PRESETS) "" else current) }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    /** Typed phrase → explanation of the sound-alikes, waiting for the user to confirm. */
    var pendingSoundsLike by remember { mutableStateOf<Pair<String, String>?>(null) }
    pendingSoundsLike?.let { (phrase, explanation) ->
        SettingsConfirmDialog(
            title = "Use \"${com.example.ai.WakePhrases.display(phrase)}\"?",
            body = "The offline listener doesn't have every name in its dictionary, so it will listen for $explanation, which sound the same. Your phrase is still shown as you typed it.",
            confirm = "Use it",
            danger = false,
            accent = accent,
            onConfirm = { pendingSoundsLike = null; onPick(phrase) },
            onDismiss = { pendingSoundsLike = null }
        )
    }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MochiTokens.sheet,
        scrimColor = MochiTokens.scrim,
        shape = MochiShapes.sheetTop
    ) {
        Column(Modifier.padding(horizontal = MochiSpacing.screenPadding).padding(bottom = 24.dp)) {
            Text("Wake phrase", style = MochiType.Title, color = MochiTokens.textPrimary)
            Text("Say this to wake your companion. Two to four words, written in English letters. Names work too.", style = MochiType.Label, color = MochiTokens.textSecondary)
            Spacer(Modifier.height(8.dp))
            com.example.ai.WakePhrases.PRESETS.forEach { phrase ->
                Row(
                    Modifier.fillMaxWidth().clip(MochiShapes.card).clickable { onPick(phrase) }.padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(com.example.ai.WakePhrases.display(phrase), style = MochiType.Body, color = MochiTokens.textPrimary, modifier = Modifier.weight(1f))
                    if (phrase == current) Icon(Icons.Outlined.Check, "Selected", tint = MochiTokens.accentInk(accent), modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Or your own", style = MochiType.Label, color = MochiTokens.textSecondary)
            Spacer(Modifier.height(6.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = custom,
                onValueChange = { custom = it.take(40); error = null },
                singleLine = true,
                textStyle = MochiType.Body.copy(color = MochiTokens.textPrimary),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(accent),
                decorationBox = { inner ->
                    Box(
                        Modifier.fillMaxWidth().height(48.dp).clip(MochiShapes.pill).background(MochiTokens.card2).padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (custom.isEmpty()) Text("e.g. Hi Elza", style = MochiType.Body, color = MochiTokens.textTertiary)
                        inner()
                    }
                }
            )
            error?.let { Text(it, style = MochiType.Label, color = MochiTokens.danger, modifier = Modifier.padding(top = 6.dp)) }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().height(48.dp).clip(MochiShapes.pill).background(accent)
                    .clickable(enabled = !checking) {
                        val words = com.example.ai.WakePhrases.normalise(custom)
                        if (words == null) {
                            error = "Use two to four English words, letters only."
                            return@clickable
                        }
                        checking = true
                        com.example.ai.VoiceCommandService.shared(context).resolveWakePhrase(words) { r ->
                            checking = false
                            when {
                                !r.ok -> error = "Can't listen for: ${r.unresolved.joinToString(", ")}. Try a different spelling, or how it sounds (e.g. \"Elsa\" for \"Elza\")."
                                r.soundsLike.isEmpty() -> onPick(words.joinToString(" "))
                                else -> pendingSoundsLike = words.joinToString(" ") to r.soundsLike.entries.joinToString("; ") { (w, alts) ->
                                    "\"${com.example.ai.WakePhrases.display(w)}\" as " + alts.joinToString(" or ") { "\"${com.example.ai.WakePhrases.display(it)}\"" }
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(if (checking) "Checking…" else "Use my phrase", style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold), color = MochiTokens.onAccent)
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSheet(current: String, accent: Color, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MochiTokens.sheet,
        scrimColor = MochiTokens.scrim,
        shape = MochiShapes.sheetTop
    ) {
        Column(Modifier.padding(horizontal = MochiSpacing.screenPadding).padding(bottom = 24.dp)) {
            Text("Language", style = MochiType.Title, color = MochiTokens.textPrimary)
            Text("Your companion speaks and listens in this language.", style = MochiType.Label, color = MochiTokens.textSecondary)
            Spacer(Modifier.height(12.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = MochiType.Body.copy(color = MochiTokens.textPrimary),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(accent),
                decorationBox = { inner ->
                    Row(
                        Modifier.fillMaxWidth().height(48.dp).clip(MochiShapes.pill).background(MochiTokens.card2).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Search, null, tint = MochiTokens.textTertiary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) Text("Search languages", style = MochiType.Body, color = MochiTokens.textTertiary)
                            inner()
                        }
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            val list = SettingsLanguages.filter { it.contains(query, ignoreCase = true) }
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(list.size) { i ->
                    val lang = list[i]
                    // Older versions stored the label without its "(…)" part.
                    val selected = lang == current || lang.substringBefore(" (") == current
                    Row(
                        Modifier.fillMaxWidth().clip(MochiShapes.card).clickable { onPick(lang) }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(lang, style = MochiType.Body, color = MochiTokens.textPrimary, modifier = Modifier.weight(1f))
                        if (selected) Icon(Icons.Outlined.Check, "Selected", tint = MochiTokens.accentInk(accent), modifier = Modifier.size(20.dp))
                    }
                }
                if (list.isEmpty()) item {
                    Text("No language matches \"$query\"", style = MochiType.Label, color = MochiTokens.textSecondary, modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    danger: Boolean,
    accent: Color,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MochiTokens.sheet,
        shape = MochiShapes.sheet,
        title = { Text(title, style = MochiType.Title, color = MochiTokens.textPrimary) },
        text = { Text(body, style = MochiType.Body, color = MochiTokens.textSecondary) },
        confirmButton = {
            Box(
                Modifier.clip(MochiShapes.pill)
                    .background(if (danger) MochiTokens.dangerSolid else accent)
                    .clickable(onClick = onConfirm)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(confirm, style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold), color = if (danger) Color.White else MochiTokens.onAccent)
            }
        },
        dismissButton = {
            Box(Modifier.clip(MochiShapes.pill).clickable(onClick = onDismiss).padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text("Cancel", style = MochiType.Label, color = MochiTokens.textSecondary)
            }
        }
    )
}

@Composable
private fun AppearanceSegmented(mode: Int, accent: Color, onPick: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(MochiSpacing.md).clip(MochiShapes.pill).background(MochiTokens.card2).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf("Dark", "Light", "Auto").forEachIndexed { i, label ->
            val sel = i == mode
            Box(
                Modifier.weight(1f).height(40.dp).clip(MochiShapes.pill)
                    .background(if (sel) MochiTokens.sheet else Color.Transparent)
                    .then(if (sel) Modifier.border(1.dp, MochiTokens.border, MochiShapes.pill) else Modifier)
                    .clickable { onPick(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(label, style = MochiType.Label.copy(fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (sel) MochiTokens.textPrimary else MochiTokens.textSecondary)
            }
        }
    }
}


/**
 * Settings → Diagnostics: shows why the AI last failed, and (test builds only) this install's
 * App Check debug code with the exact steps to approve it in Firebase.
 */
@Composable
private fun DiagnosticsCard(p: SuitePalette) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val failure by com.example.ai.AiDiagnostics.last.collectAsState()
    val debugSecret = remember { com.example.ai.appCheckDebugSecret(context) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var steps by remember { mutableStateOf<List<com.example.ai.AiDiagnostics.Step>>(emptyList()) }
    val voiceLog by com.example.ai.VoiceLog.entries.collectAsState()
    var copiedLog by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MochiShapes.card)
            .background(p.card)
            .border(1.dp, p.hairline, MochiShapes.card)
            .padding(MochiSpacing.md),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Diagnostics", style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold), color = p.textPrimary)
        failure?.let { f ->
            Text("Last AI problem: ${com.example.ai.AiDiagnostics.userMessage(f.reason)}", style = MochiType.Label, color = p.textSecondary)
        }
        if (debugSecret != null) {
            Text(
                "Test build App Check debug token (Firebase Console → App Check → Apps → your Android app → ⋮ → Manage debug tokens):",
                style = MochiType.Label,
                color = p.textSecondary
            )
            Text(debugSecret, style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold), color = p.textPrimary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DiagnosticsButton(if (running) "Checking…" else "Run check", p) {
                if (!running) {
                    running = true
                    scope.launch {
                        steps = com.example.ai.AiDiagnostics.runSelfTest(context)
                        running = false
                    }
                }
            }
            if (debugSecret != null) {
                DiagnosticsButton("Copy token", p) { clipboard.setText(androidx.compose.ui.text.AnnotatedString(debugSecret)) }
            }
            if (steps.isNotEmpty()) {
                DiagnosticsButton(if (copied) "Copied" else "Copy report", p) {
                    val report = steps.joinToString("\n\n") { (if (it.ok) "✓ " else "✗ ") + it.name + "\n" + it.detail }
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(report + (debugSecret?.let { "\n\nDebug token: $it" } ?: "")))
                    copied = true
                }
            }
        }
        HairlineDivider(p.hairline)
        Text("Voice", style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold), color = p.textPrimary)
        Text(remember(voiceLog) { com.example.ai.VoiceCommandService.shared(context).wakeStatus }, style = MochiType.Label, color = p.textTertiary)
        if (voiceLog.isEmpty()) {
            Text("Nothing heard yet. Talk to your companion, then come back to see what she heard and why she reacted.", style = MochiType.Label, color = p.textTertiary)
        } else {
            val timeFormat = remember { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT) }
            voiceLog.takeLast(12).forEach { e ->
                Text("${timeFormat.format(java.util.Date(e.atMillis))}  ${e.kind.name.lowercase()}  ${e.text}", style = MochiType.Label, color = p.textTertiary)
            }
            DiagnosticsButton(if (copiedLog) "Copied" else "Copy voice log", p) {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(
                    com.example.ai.VoiceCommandService.shared(context).wakeStatus + "\n" +
                        voiceLog.joinToString("\n") { "${timeFormat.format(java.util.Date(it.atMillis))} ${it.kind} ${it.text}" }
                ))
                copiedLog = true
            }
        }
        steps.forEach { step ->
            Text(
                (if (step.ok) "✓ " else "✗ ") + step.name,
                style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold),
                color = if (step.ok) p.textPrimary else MochiTokens.danger
            )
            Text(step.detail, style = MochiType.Label, color = p.textTertiary)
        }
    }
}

@Composable
private fun DiagnosticsButton(label: String, p: SuitePalette, onClick: () -> Unit) {
    Box(
        Modifier.clip(MochiShapes.pill).background(p.hairline).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, style = MochiType.Label, color = p.textPrimary)
    }
}
