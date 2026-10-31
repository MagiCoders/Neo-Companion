package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.ConversionOutputResult
import com.example.ai.ConversionToolMode
import com.example.ai.UniversalConverterEngine
import com.example.data.FridgeIngredientChip
import com.example.data.MealCreation
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.LocalCharacterAccent
import com.example.ui.theme.LocalMochiDarkTheme
import com.example.ui.theme.MochiShapes
import com.example.ui.theme.MochiSpacing
import com.example.ui.theme.MochiTokens
import com.example.ui.theme.MochiType

enum class MochiToolTile(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val conversionMode: ConversionToolMode? = null
) {
    IMAGE_TO_PDF(
        id = "image_to_pdf",
        title = "Image to PDF",
        subtitle = "Convert a photo or scan into a PDF document",
        icon = Icons.Outlined.PictureAsPdf,
        conversionMode = ConversionToolMode.IMAGE_TO_PDF
    ),
    IMAGE_TO_TEXT(
        id = "image_to_text",
        title = "Image to text",
        subtitle = "Extract readable text from an image",
        icon = Icons.Outlined.DocumentScanner,
        conversionMode = ConversionToolMode.OCR_IMAGE_TO_TEXT
    ),
    PDF_TO_IMAGE(
        id = "pdf_to_image",
        title = "PDF to image",
        subtitle = "Render PDF pages as crisp PNG files",
        icon = Icons.Outlined.Image,
        conversionMode = ConversionToolMode.PDF_TO_IMAGE
    ),
    TEXT_TO_SPEECH(
        id = "text_to_speech",
        title = "Text to speech",
        subtitle = "Synthesize written text into a WAV audio file",
        icon = Icons.Outlined.GraphicEq,
        conversionMode = ConversionToolMode.TEXT_TO_VOICE
    ),
    SPEECH_TO_TEXT(
        id = "speech_to_text",
        title = "Speech to text",
        subtitle = "Transcribe an audio recording into plain text",
        icon = Icons.Outlined.AudioFile,
        conversionMode = ConversionToolMode.VOICE_TO_TEXT
    ),
    VIDEO_TO_AUDIO(
        id = "video_to_audio",
        title = "Video to audio",
        subtitle = "Extract the audio track from an MP4 video",
        icon = Icons.Outlined.Movie,
        conversionMode = ConversionToolMode.VIDEO_TO_AUDIO
    ),
    DOC_TO_PDF(
        id = "doc_to_pdf",
        title = "Document to PDF",
        subtitle = "Format notes or plain text into an A4 PDF",
        icon = Icons.Outlined.Description,
        conversionMode = ConversionToolMode.DOC_TO_PDF
    ),
    FRIDGE_CHEF(
        id = "fridge_chef",
        title = "Fridge chef",
        subtitle = "3 meals under 15 min",
        icon = Icons.Outlined.RestaurantMenu,
        conversionMode = null
    )
}

/**
 * Screen 4 — Tools (matching Tools.png & Tools - light.png + DESIGN.md 2-column grid of tiles).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolsGridScreen(
    onSelectTool: (MochiToolTile) -> Unit,
    onBack: () -> Unit,
    onOpenSearch: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onAskCompanion: (String) -> Unit = {},
    onStartVoice: () -> Unit = {},
    recentFile: com.example.data.LibraryEntry? = null,
    onOpenFile: (com.example.data.LibraryEntry) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val accent = LocalCharacterAccent.current
    val isDark = LocalMochiDarkTheme.current
    val textPrimary = MochiTokens.adaptiveTextPrimary(isDark)
    val textSecondary = MochiTokens.adaptiveTextSecondary(isDark)
    val cardBg = MochiTokens.adaptiveBgRaised(isDark)
    val borderCol = MochiTokens.adaptiveHairline(isDark)
    val goldAccent = MochiTokens.gold
    val mintAccent = Color(0xFF56C296)

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MochiSpacing.screenPadding, vertical = MochiSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(MochiSpacing.md)
    ) {
        // Header row matching Tools.png ("Tools", "Ask Elena, or open a tool yourself.", profile button + back button for navigation tests)
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
                        .size(MochiSpacing.minTouchTarget)
                        .clip(MochiShapes.pill)
                        .background(cardBg)
                        .border(1.dp, borderCol, MochiShapes.pill)
                        .clickable { onBack() }
                        .testTag("tools_back_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back to stage",
                        tint = textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = "Tools",
                        style = MochiType.Display,
                        color = textPrimary
                    )
                    Text(
                        text = "Ask Elena, or open a tool yourself.",
                        style = MochiType.Label,
                        color = textSecondary
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(MochiSpacing.xs)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(MochiShapes.pill)
                        .background(cardBg)
                        .border(1.dp, borderCol, MochiShapes.pill)
                        .clickable { onOpenSearch() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search tools",
                        tint = textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(MochiShapes.pill)
                        .background(cardBg)
                        .border(1.dp, borderCol, MochiShapes.pill)
                        .clickable { onOpenProfile() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = "Profile and settings",
                        tint = textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // TONIGHT • Fridge Chef Hero Card (matching Tools.png with circular plate graphic on the right)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = if (isDark) {
                            listOf(Color(0xFF1A1E28), Color(0xFF2B2424))
                        } else {
                            listOf(Color(0xFFFFFFFF), Color(0xFFF9EFE4))
                        }
                    )
                )
                .border(1.dp, borderCol, RoundedCornerShape(24.dp))
                .clickable { onSelectTool(MochiToolTile.FRIDGE_CHEF) }
                .padding(MochiSpacing.cardPadding)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(MochiShapes.pill)
                            .background(goldAccent.copy(alpha = 0.20f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "TONIGHT",
                            style = MochiType.Label.copy(fontWeight = FontWeight.Bold),
                            color = goldAccent
                        )
                    }
                    Text(
                        text = "Fridge Chef",
                        style = MochiType.Title,
                        color = textPrimary
                    )
                    Text(
                        text = "Snap your fridge. Get 3 dinners ready in 15 minutes.",
                        style = MochiType.Label,
                        color = textSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier
                            .clip(MochiShapes.pill)
                            .background(mintAccent)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CameraAlt,
                            contentDescription = null,
                            tint = MochiTokens.onAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Open camera",
                            style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold),
                            color = MochiTokens.onAccent
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Circular Plate Illustration on the right (matching Tools.png)
                Box(
                    modifier = Modifier.size(104.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = if (isDark) Color(0xFF282C37) else Color(0xFFEBE6DC),
                            radius = size.minDimension * 0.48f
                        )
                        drawCircle(
                            color = if (isDark) Color(0xFF3E4452) else Color(0xFFD2C9B8),
                            radius = size.minDimension * 0.48f,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        drawCircle(
                            color = if (isDark) Color(0xFF20242E) else Color(0xFFF5F1E8),
                            radius = size.minDimension * 0.36f
                        )
                        // Lemon slice (top-right)
                        drawCircle(
                            color = MochiTokens.gold,
                            radius = size.minDimension * 0.14f,
                            center = Offset(size.width * 0.62f, size.height * 0.38f)
                        )
                        // Spinach leaf (center-left)
                        drawCircle(
                            color = Color(0xFF56C296),
                            radius = size.minDimension * 0.11f,
                            center = Offset(size.width * 0.38f, size.height * 0.52f)
                        )
                        // Tomato/egg yolk (bottom-right)
                        drawCircle(
                            color = Color(0xFFE6915B),
                            radius = size.minDimension * 0.09f,
                            center = Offset(size.width * 0.58f, size.height * 0.65f)
                        )
                        // Garlic clove (bottom-left)
                        drawCircle(
                            color = Color(0xFFF3F5F9),
                            radius = size.minDimension * 0.055f,
                            center = Offset(size.width * 0.42f, size.height * 0.72f)
                        )
                    }
                }
            }
        }

        // 2x2 Featured Everyday Cards from Tools.png (Bill splitter, File converter, Calls and messages, Mood check-in)
        Column(verticalArrangement = Arrangement.spacedBy(MochiSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MochiSpacing.sm)
            ) {
                FeaturedToolCard(
                    icon = Icons.Outlined.ReceiptLong,
                    title = "Bill splitter",
                    subtitle = "Coming soon with Snap",
                    cardBg = cardBg,
                    borderCol = borderCol,
                    goldAccent = goldAccent,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onClick = { onAskCompanion("Help me split a bill") },
                    modifier = Modifier.weight(1f)
                )
                FeaturedToolCard(
                    icon = Icons.Outlined.SwapHoriz,
                    title = "File converter",
                    subtitle = "PDF, photos, voice and video",
                    cardBg = cardBg,
                    borderCol = borderCol,
                    goldAccent = goldAccent,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onClick = { onSelectTool(MochiToolTile.IMAGE_TO_PDF) },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MochiSpacing.sm)
            ) {
                FeaturedToolCard(
                    icon = Icons.Outlined.Call,
                    title = "Calls and messages",
                    subtitle = "Call or text anyone by voice",
                    cardBg = cardBg,
                    borderCol = borderCol,
                    goldAccent = goldAccent,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onClick = onStartVoice,
                    modifier = Modifier.weight(1f)
                )
                FeaturedToolCard(
                    icon = Icons.Outlined.FavoriteBorder,
                    title = "Mood check-in",
                    subtitle = "A gentle one-minute check-in",
                    cardBg = cardBg,
                    borderCol = borderCol,
                    goldAccent = goldAccent,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onClick = { onAskCompanion("Can we do a quick mood check-in?") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // "Convert a file" + "See all" section with 6 conversion pills from Tools.png
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Convert a file",
                style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
                color = textPrimary
            )
            Text(
                text = "See all",
                style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold),
                color = goldAccent,
                modifier = Modifier.clickable { onSelectTool(MochiToolTile.IMAGE_TO_PDF) }
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "Image → PDF" to MochiToolTile.IMAGE_TO_PDF,
                "PDF → Images" to MochiToolTile.PDF_TO_IMAGE,
                "Scan text" to MochiToolTile.IMAGE_TO_TEXT,
                "Text → Voice" to MochiToolTile.TEXT_TO_SPEECH,
                "Voice → Text" to MochiToolTile.SPEECH_TO_TEXT,
                "Video → Audio" to MochiToolTile.VIDEO_TO_AUDIO
            ).forEach { (pillLabel, targetTile) ->
                Box(
                    modifier = Modifier
                        .clip(MochiShapes.pill)
                        .background(cardBg)
                        .border(1.dp, borderCol, MochiShapes.pill)
                        .clickable { onSelectTool(targetTile) }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = pillLabel,
                        style = MochiType.Label.copy(fontWeight = FontWeight.Medium),
                        color = textPrimary
                    )
                }
            }
        }

        // Most recent file you created (hidden when there is none)
        if (recentFile != null) Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MochiShapes.card)
                .background(cardBg)
                .border(1.dp, borderCol, MochiShapes.card)
                .clickable { onOpenFile(recentFile) }
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
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(borderCol),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        tint = textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = recentFile.title,
                        style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
                        color = textPrimary
                    )
                    Text(
                        text = recentFile.subtitle,
                        style = MochiType.Label,
                        color = textSecondary
                    )
                }
            }
            Text(
                text = "Open",
                style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold),
                color = goldAccent
            )
        }

        // Complete 2-column tool tile grid (preserves tools_grid and tool_tile_<id> testTags)
        Text(
            text = "All tools",
            style = MochiType.Label,
            color = textSecondary
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 380.dp, max = 640.dp)
                .testTag("tools_grid"),
            horizontalArrangement = Arrangement.spacedBy(MochiSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(MochiSpacing.sm),
            contentPadding = PaddingValues(bottom = MochiSpacing.sm)
        ) {
            items(MochiToolTile.entries, key = { it.id }) { tile ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MochiShapes.card)
                        .background(cardBg)
                        .border(1.dp, borderCol, MochiShapes.card)
                        .clickable { onSelectTool(tile) }
                        .padding(MochiSpacing.md)
                        .testTag("tool_tile_${tile.id}")
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(accent.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = tile.icon,
                                contentDescription = tile.title,
                                tint = accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = tile.title,
                            style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
                            color = textPrimary
                        )
                        Text(
                            text = tile.subtitle,
                            style = MochiType.Label,
                            color = textSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedToolCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    cardBg: Color,
    borderCol: Color,
    goldAccent: Color,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(cardBg)
            .border(1.dp, borderCol, RoundedCornerShape(22.dp))
            .clickable { onClick() }
            .padding(MochiSpacing.md),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 28.dp)
                .clip(MochiShapes.pill)
                .background(goldAccent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = goldAccent,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = title,
            style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
            color = textPrimary
        )
        Text(
            text = subtitle,
            style = MochiType.Label,
            color = textSecondary,
            maxLines = 2
        )
    }
}

/**
 * Individual Tool Screen:
 * - For FRIDGE_CHEF, renders Fridge Chef results.png & Fridge Chef results - light.png
 *   (fridge shelf diagram, removable ingredient chips + "+ Add item", 3 dinners under 15 minutes,
 *   and Elena's bottom "Read steps" card).
 * - For file conversion tools, shows one primary action button at the bottom
 *   and a thin accent progress bar (never a spinner) while running.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SingleToolScreen(
    tool: MochiToolTile,
    isRunning: Boolean,
    pantryChips: List<FridgeIngredientChip>,
    selectedIngredients: Set<String>,
    meals: List<MealCreation>,
    onToggleIngredient: (String) -> Unit,
    onGenerateMeals: () -> Unit,
    onRunImageToPdf: (Bitmap) -> Unit,
    onRunImageToText: (Bitmap) -> Unit,
    onRunPdfToImage: (Uri) -> Unit,
    onRunTextToSpeech: (String) -> Unit,
    onRunSpeechToText: (Uri) -> Unit,
    onRunVideoToAudio: (Uri) -> Unit,
    onRunDocToPdf: (String, String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = LocalCharacterAccent.current
    val isDark = LocalMochiDarkTheme.current
    val textPrimary = MochiTokens.adaptiveTextPrimary(isDark)
    val textSecondary = MochiTokens.adaptiveTextSecondary(isDark)
    val cardBg = MochiTokens.adaptiveBgRaised(isDark)
    val borderCol = MochiTokens.adaptiveHairline(isDark)

    var textInput by rememberSaveable(tool.id) {
        mutableStateOf(
            when (tool) {
                MochiToolTile.TEXT_TO_SPEECH -> "Take a slow breath in for four seconds, hold gently, and release."
                MochiToolTile.DOC_TO_PDF -> "Project notes\n\n1. Review the character animation states.\n2. Verify audio lip-sync smoothing.\n3. Export clean A4 documentation."
                else -> ""
            }
        )
    }
    var selectedFileLabel by rememberSaveable(tool.id) { mutableStateOf<String?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedFileLabel = uri.lastPathSegment ?: "Selected image"
            val bmp = decodeBitmapCompat(context, uri) ?: createCleanSampleBitmap(tool.title)
            if (tool == MochiToolTile.IMAGE_TO_PDF) {
                onRunImageToPdf(bmp)
            } else {
                onRunImageToText(bmp)
            }
        }
    }

    val genericFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedFileLabel = uri.lastPathSegment ?: "Selected file"
            when (tool) {
                MochiToolTile.PDF_TO_IMAGE -> onRunPdfToImage(uri)
                MochiToolTile.SPEECH_TO_TEXT -> onRunSpeechToText(uri)
                MochiToolTile.VIDEO_TO_AUDIO -> onRunVideoToAudio(uri)
                else -> {}
            }
        }
    }

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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(MochiSpacing.minTouchTarget)
                        .clip(MochiShapes.pill)
                        .background(cardBg)
                        .border(1.dp, borderCol, MochiShapes.pill)
                        .clickable { onBack() }
                        .testTag("single_tool_back_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back to tools",
                        tint = textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Text(
                    text = if (tool == MochiToolTile.FRIDGE_CHEF) "Fridge Chef" else tool.title,
                    style = MochiType.Title,
                    color = textPrimary
                )

                if (tool == MochiToolTile.FRIDGE_CHEF) {
                    Box(
                        modifier = Modifier
                            .size(MochiSpacing.minTouchTarget)
                            .clip(MochiShapes.pill)
                            .background(cardBg)
                            .border(1.dp, borderCol, MochiShapes.pill)
                            .clickable { onGenerateMeals() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CameraAlt,
                            contentDescription = "Retake fridge photo",
                            tint = textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(MochiSpacing.minTouchTarget))
                }
            }

            if (isRunning) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .testTag("tool_linear_progress"),
                    color = accent,
                    trackColor = borderCol
                )
            }

            if (tool == MochiToolTile.FRIDGE_CHEF) {
                // Visual Fridge Shelf Diagram matching Fridge Chef results.png
                FridgeShelfDiagram(accent = accent, isDark = isDark)

                // Found in your fridge header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Found in your fridge",
                        style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
                        color = textPrimary
                    )
                    Text(
                        text = "Tap to remove",
                        style = MochiType.Label,
                        color = textSecondary
                    )
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MochiSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(MochiSpacing.xs)
                ) {
                    val chipNames = if (pantryChips.isNotEmpty()) {
                        pantryChips.map { it.name }
                    } else {
                        listOf("Eggs", "Spinach", "Feta", "Lemon", "Pasta", "Garlic")
                    }
                    chipNames.forEach { name ->
                        IngredientPill(
                            label = name,
                            selected = selectedIngredients.contains(name),
                            accent = accent,
                            isDark = isDark,
                            onClick = { onToggleIngredient(name) }
                        )
                    }
                    // "+ Add item" pill from Fridge Chef results.png
                    Box(
                        modifier = Modifier
                            .clip(MochiShapes.pill)
                            .background(Color.Transparent)
                            .border(1.dp, borderCol, MochiShapes.pill)
                            .clickable { onToggleIngredient("Olive Oil") }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "+ Add item",
                            style = MochiType.Label,
                            color = textSecondary
                        )
                    }
                }

                Text(
                    text = "3 dinners, all under 15 minutes",
                    style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
                    color = textPrimary
                )

                val displayedMeals = if (meals.isNotEmpty()) {
                    meals
                } else {
                    listOf(
                        MealCreation(
                            id = "pasta",
                            title = "Lemon spinach pasta",
                            prepTimeMinutes = 12,
                            ingredientsUsed = "Pasta, Spinach, Lemon, Feta, Garlic",
                            stepsSummary = "12 min · Easy · Uses 5 of your items"
                        ),
                        MealCreation(
                            id = "omelette",
                            title = "Spinach and feta omelette",
                            prepTimeMinutes = 8,
                            ingredientsUsed = "Eggs, Spinach, Feta",
                            stepsSummary = "8 min · Easy · Uses 3 of your items"
                        ),
                        MealCreation(
                            id = "skillet",
                            title = "Feta and lemon egg skillet",
                            prepTimeMinutes = 10,
                            ingredientsUsed = "Eggs, Feta, Lemon, Garlic",
                            stepsSummary = "10 min · Easy · Uses 4 of your items"
                        )
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(MochiSpacing.sm)
                ) {
                    displayedMeals.forEachIndexed { idx, meal ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MochiShapes.card)
                                .background(cardBg)
                                .border(1.dp, borderCol, MochiShapes.card)
                                .clickable {
                                    onRunTextToSpeech("${meal.title}. Ready in ${meal.prepTimeMinutes} minutes. ${meal.stepsSummary}")
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
                                MealDishIcon(index = idx)
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = meal.title,
                                        style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
                                        color = textPrimary
                                    )
                                    Text(
                                        text = "${meal.prepTimeMinutes} min · Easy",
                                        style = MochiType.Label,
                                        color = textSecondary
                                    )
                                    val itemCount = meal.ingredientsUsed.split(",").count { it.isNotBlank() }.coerceAtLeast(3)
                                    Text(
                                        text = "Uses $itemCount of your items",
                                        style = MochiType.Label,
                                        color = textSecondary
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = textSecondary
                            )
                        }
                    }
                }

                // Elena bottom companion readout bar from Fridge Chef results.png
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MochiShapes.card)
                        .background(cardBg)
                        .border(1.dp, borderCol, MochiShapes.card)
                        .padding(MochiSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MochiSpacing.sm),
                        modifier = Modifier.weight(1f)
                    ) {
                        com.example.ui.character.MiniCompanion3DAvatar(
                            character = com.example.ui.character.CharacterSpec.ELENA,
                            modifier = Modifier.size(42.dp)
                        )
                        Text(
                            text = "The pasta is quickest. Want me to read the steps out loud?",
                            style = MochiType.Label,
                            color = textPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(MochiShapes.pill)
                            .background(accent)
                            .clickable {
                                onRunTextToSpeech(
                                    "Boil salted water for the lemon spinach pasta. Toss warm pasta with olive oil, garlic, baby spinach, lemon zest, and crumbled feta."
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Read steps",
                            style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold),
                            color = MochiTokens.onAccent
                        )
                    }
                }
            } else if (tool == MochiToolTile.TEXT_TO_SPEECH || tool == MochiToolTile.DOC_TO_PDF) {
                Text(
                    text = tool.subtitle,
                    style = MochiType.Body,
                    color = textSecondary
                )
                GlassSurface(
                    shape = MochiShapes.card,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MochiSpacing.cardPadding),
                        verticalArrangement = Arrangement.spacedBy(MochiSpacing.xs)
                    ) {
                        Text(
                            text = "Input text",
                            style = MochiType.Label,
                            color = textSecondary
                        )
                        BasicTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            textStyle = MochiType.Body.copy(color = textPrimary),
                            cursorBrush = SolidColor(accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .testTag("tool_text_input")
                        )
                    }
                }
            } else {
                Text(
                    text = tool.subtitle,
                    style = MochiType.Body,
                    color = textSecondary
                )
                GlassSurface(
                    shape = MochiShapes.card,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (tool == MochiToolTile.IMAGE_TO_PDF || tool == MochiToolTile.IMAGE_TO_TEXT) {
                                imagePickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            } else {
                                val mime = tool.conversionMode?.inputMime ?: "*/*"
                                genericFileLauncher.launch(mime)
                            }
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MochiSpacing.cardPadding),
                        verticalArrangement = Arrangement.spacedBy(MochiSpacing.xs)
                    ) {
                        Text(
                            text = selectedFileLabel ?: "Tap to choose a file from device storage",
                            style = MochiType.Body,
                            color = textPrimary
                        )
                        Text(
                            text = "Or tap the primary button below to run with a prepared sample.",
                            style = MochiType.Label,
                            color = textSecondary
                        )
                    }
                }
            }
        }

        // Single Primary Accent Button at the bottom (testTag preserved for tests)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MochiSpacing.sm)
                .height(MochiSpacing.minTouchTarget)
                .clip(MochiShapes.pill)
                .background(accent)
                .clickable(enabled = !isRunning) {
                    when (tool) {
                        MochiToolTile.IMAGE_TO_PDF -> onRunImageToPdf(createCleanSampleBitmap("MochiLife Document"))
                        MochiToolTile.IMAGE_TO_TEXT -> onRunImageToText(createCleanSampleBitmap("Extracted Note 2026"))
                        MochiToolTile.PDF_TO_IMAGE -> {
                            genericFileLauncher.launch("application/pdf")
                        }
                        MochiToolTile.TEXT_TO_SPEECH -> onRunTextToSpeech(textInput)
                        MochiToolTile.SPEECH_TO_TEXT -> {
                            genericFileLauncher.launch("audio/*")
                        }
                        MochiToolTile.VIDEO_TO_AUDIO -> {
                            genericFileLauncher.launch("video/*")
                        }
                        MochiToolTile.DOC_TO_PDF -> onRunDocToPdf(tool.title, textInput)
                        MochiToolTile.FRIDGE_CHEF -> onGenerateMeals()
                    }
                }
                .testTag("tool_primary_action_button"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = tool.conversionMode?.actionLabel ?: "Generate recipes",
                style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
                color = MochiTokens.onAccent
            )
        }
    }
}

@Composable
private fun MealDishIcon(index: Int) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF282422)),
        contentAlignment = Alignment.Center
    ) {
        ComposeCanvas(modifier = Modifier.size(40.dp)) {
            drawCircle(
                color = if (index == 2) Color(0xFF42454D) else Color(0xFFF3EFE2),
                radius = size.minDimension * 0.48f
            )
            when (index) {
                0 -> {
                    // Pasta waves + spinach dots
                    drawCircle(color = Color(0xFFE0B462), radius = 8.dp.toPx(), center = Offset(size.width * 0.5f, size.height * 0.5f))
                    drawCircle(color = Color(0xFF439A73), radius = 3.5.dp.toPx(), center = Offset(size.width * 0.35f, size.height * 0.38f))
                    drawCircle(color = Color(0xFF439A73), radius = 3.5.dp.toPx(), center = Offset(size.width * 0.65f, size.height * 0.64f))
                }
                1 -> {
                    // Omelette oval + spinach/feta
                    drawCircle(color = Color(0xFFE5BD68), radius = 11.dp.toPx(), center = Offset(size.width * 0.5f, size.height * 0.52f))
                    drawCircle(color = Color(0xFF439A73), radius = 3.5.dp.toPx(), center = Offset(size.width * 0.40f, size.height * 0.48f))
                    drawCircle(color = Color(0xFF439A73), radius = 3.5.dp.toPx(), center = Offset(size.width * 0.58f, size.height * 0.56f))
                }
                else -> {
                    // Skillet eggs
                    drawCircle(color = Color(0xFFF7F5EE), radius = 5.dp.toPx(), center = Offset(size.width * 0.38f, size.height * 0.44f))
                    drawCircle(color = Color(0xFFE59D47), radius = 2.5.dp.toPx(), center = Offset(size.width * 0.38f, size.height * 0.44f))
                    drawCircle(color = Color(0xFFF7F5EE), radius = 5.dp.toPx(), center = Offset(size.width * 0.62f, size.height * 0.58f))
                    drawCircle(color = Color(0xFFE59D47), radius = 2.5.dp.toPx(), center = Offset(size.width * 0.62f, size.height * 0.58f))
                }
            }
        }
    }
}

@Composable
private fun FridgeShelfDiagram(accent: Color, isDark: Boolean) {
    val cardBg = if (isDark) Color(0xFF1F222A) else Color(0xFFEDE8DF)
    val borderCol = MochiTokens.adaptiveHairline(isDark)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MochiShapes.card)
            .background(cardBg)
            .border(1.dp, borderCol, MochiShapes.card)
            .padding(MochiSpacing.md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Top shelf: Eggs, Spinach, Lemon, Feta
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FridgeTagPill("Eggs", accent)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .size(width = 18.dp, height = 24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEAE2CE))
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FridgeTagPill("Spinach", accent)
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .size(width = 64.dp, height = 44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF439A73))
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FridgeTagPill("Lemon", accent)
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE0B462))
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(width = 46.dp, height = 30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFF3F1EA))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FridgeTagPill("Feta", accent)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(borderCol)
            )

            // Bottom shelf: Pasta, Garlic
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(width = 96.dp, height = 38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFD6B485))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FridgeTagPill("Pasta", accent)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEDE8DC))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FridgeTagPill("Garlic", accent)
                }
            }
        }
    }
}

@Composable
private fun FridgeTagPill(label: String, accent: Color) {
    Row(
        modifier = Modifier
            .clip(MochiShapes.pill)
            .background(Color(0xFF12151C).copy(alpha = 0.88f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Text(
            text = label,
            style = MochiType.Label,
            color = Color(0xFFF3F5F9)
        )
    }
}

/**
 * Screen 5 — Tool Result from DESIGN.md:
 * Preview of the result, thin accent progress bar (never a spinner), then Share and Save.
 */
@Composable
fun ToolResultScreen(
    result: ConversionOutputResult,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = LocalCharacterAccent.current
    val isDark = LocalMochiDarkTheme.current
    val textPrimary = MochiTokens.adaptiveTextPrimary(isDark)
    val textSecondary = MochiTokens.adaptiveTextSecondary(isDark)
    val cardBg = MochiTokens.adaptiveBgRaised(isDark)
    val borderCol = MochiTokens.adaptiveHairline(isDark)
    var savedStatus by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = MochiSpacing.screenPadding, vertical = MochiSpacing.sm),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MochiSpacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MochiSpacing.md)
            ) {
                Box(
                    modifier = Modifier
                        .size(MochiSpacing.minTouchTarget)
                        .clip(MochiShapes.pill)
                        .background(cardBg)
                        .border(1.dp, borderCol, MochiShapes.pill)
                        .clickable { onBack() }
                        .testTag("tool_result_back_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = textPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = result.toolMode.title,
                    style = MochiType.Title,
                    color = textPrimary
                )
            }

            // Thin accent progress bar
            LinearProgressIndicator(
                progress = { 1f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = accent,
                trackColor = borderCol
            )

            // Result preview card
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
                        text = result.outputFormatLabel,
                        style = MochiType.Title,
                        color = textPrimary
                    )
                    Text(
                        text = result.accuracySummary,
                        style = MochiType.Label,
                        color = textSecondary
                    )
                    Spacer(modifier = Modifier.height(MochiSpacing.unit))
                    Text(
                        text = result.extractedOrConvertedText,
                        style = MochiType.Body,
                        color = textPrimary
                    )
                    savedStatus?.let { msg ->
                        Text(
                            text = msg,
                            style = MochiType.Label,
                            color = accent
                        )
                    }
                }
            }
        }

        // Bottom actions: Share (secondary glass) and Save (single primary accent button)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MochiSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(MochiSpacing.md)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(MochiSpacing.minTouchTarget)
                    .clip(MochiShapes.pill)
                    .background(cardBg)
                    .border(1.dp, borderCol, MochiShapes.pill)
                    .clickable {
                        UniversalConverterEngine.shareConvertedFile(context, result)
                    }
                    .testTag("tool_result_share_button"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MochiSpacing.xs)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share",
                        tint = textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Share",
                        style = MochiType.Body,
                        color = textPrimary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(MochiSpacing.minTouchTarget)
                    .clip(MochiShapes.pill)
                    .background(accent)
                    .clickable {
                        savedStatus = UniversalConverterEngine.saveConvertedFileToDownloads(context, result)
                    }
                    .testTag("tool_result_save_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Save",
                    style = MochiType.Body,
                    color = MochiTokens.onAccent
                )
            }
        }
    }
}

@Composable
private fun IngredientPill(
    label: String,
    selected: Boolean,
    accent: Color,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val cardBg = MochiTokens.adaptiveBgRaised(isDark)
    val borderCol = MochiTokens.adaptiveHairline(isDark)
    val textPrimary = MochiTokens.adaptiveTextPrimary(isDark)
    val textSecondary = MochiTokens.adaptiveTextSecondary(isDark)
    Box(
        modifier = Modifier
            .clip(MochiShapes.pill)
            .background(
                if (selected) cardBg
                else cardBg.copy(alpha = 0.5f)
            )
            .border(
                1.dp,
                if (selected) borderCol else borderCol.copy(alpha = 0.5f),
                MochiShapes.pill
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MochiType.Label.copy(fontWeight = FontWeight.Medium),
            color = if (selected) textPrimary else textSecondary
        )
    }
}

private fun decodeBitmapCompat(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (_: Exception) {
        null
    }
}

private fun createCleanSampleBitmap(label: String): Bitmap {
    val bmp = Bitmap.createBitmap(800, 1000, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(AndroidColor.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#07090F")
        textSize = 36f
    }
    canvas.drawText(label, 64f, 140f, paint)
    paint.textSize = 24f
    canvas.drawText("Prepared by MochiLife converter", 64f, 210f, paint)
    return bmp
}
