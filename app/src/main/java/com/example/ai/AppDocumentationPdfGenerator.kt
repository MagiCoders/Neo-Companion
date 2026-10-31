package com.example.ai

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.example.ui.character.CharacterFramePose
import com.example.ui.character.GlbLoadedModel
import com.example.ui.character.GlbLoader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object AppDocumentationPdfGenerator {

    data class GeneratedDocsBundle(
        val pdfFile: File,
        val screenshotFiles: List<File>
    )

    fun generateCompleteDocsAndScreenshots(
        outputDir: File,
        context: Context? = null
    ): GeneratedDocsBundle {
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        val screenshotsDir = File(outputDir, "screenshots").apply { mkdirs() }

        val elenaModel = context?.let { GlbLoader.loadGlbAsset(it, "characters/elena.glb") }
        val kaitoModel = context?.let { GlbLoader.loadGlbAsset(it, "characters/kaito.glb") }
        val marcoModel = context?.let { GlbLoader.loadGlbAsset(it, "characters/marco.glb") }

        val shot1 = renderScreenshot1Stage(elenaModel)
        val shot2 = renderScreenshot2CharacterPicker(kaitoModel)
        val shot3 = renderScreenshot3ConversationSheet(marcoModel)
        val shot4 = renderScreenshot4ToolsAndGallery()

        val shot1File = File(screenshotsDir, "01_stage_home_elena.png")
        val shot2File = File(screenshotsDir, "02_character_picker_pager.png")
        val shot3File = File(screenshotsDir, "03_conversation_glass_sheet.png")
        val shot4File = File(screenshotsDir, "04_tools_grid_and_result.png")

        saveBitmapPng(shot1, shot1File)
        saveBitmapPng(shot2, shot2File)
        saveBitmapPng(shot3, shot3File)
        saveBitmapPng(shot4, shot4File)

        // Also overwrite legacy filenames so existing links stay up to date
        saveBitmapPng(shot1, File(screenshotsDir, "01_spatial_3d_stage_and_mood_analyzer.png"))
        saveBitmapPng(shot2, File(screenshotsDir, "02_character_roster_10_models.png"))
        saveBitmapPng(shot3, File(screenshotsDir, "03_universal_converter_and_ocr_studio.png"))
        saveBitmapPng(shot4, File(screenshotsDir, "04_fridge_chef_receipt_and_pip.png"))

        val pdfFile = File(outputDir, "MOCHILIFE_COMPLETE_DOCUMENTATION.pdf")
        buildFivePageIllustratedPdf(
            outFile = pdfFile,
            shot1 = shot1,
            shot2 = shot2,
            shot3 = shot3,
            shot4 = shot4
        )

        return GeneratedDocsBundle(
            pdfFile = pdfFile,
            screenshotFiles = listOf(shot1File, shot2File, shot3File, shot4File)
        )
    }

    fun exportAndShareCompleteDocumentationPdf(context: Context) {
        val outDir = File(context.cacheDir, "app_docs")
        val bundle = generateCompleteDocsAndScreenshots(outDir, context)
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, bundle.pdfFile)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "MochiLife Design Specification & Screenshots (PDF)")
            putExtra(Intent.EXTRA_TEXT, "Attached is the MochiLife redesign documentation and screenshots.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(intent, "Share MochiLife Documentation (.PDF)").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    private fun saveBitmapPng(bmp: Bitmap, file: File) {
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    /**
     * Screenshot 1: Screen 1 — Stage (Home) with Nurse Elena (.glb mesh),
     * 20s Aurora background (#07090F + #4FD1A5 desaturated blobs), top-left avatar button,
     * top-right tools button, 3 suggestion chips, and 56dp GlassSurface input bar.
     */
    fun renderScreenshot1Stage(model: GlbLoadedModel? = null): Bitmap {
        val w = 1080
        val h = 1920
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        drawDesignAurora(c, w, h, "#4FD1A5")
        drawStageTopBar(c, w, "Nurse Elena", "#4FD1A5")

        // Render 3D .glb mesh in top 62% of screen
        drawGlbCharacterOnCanvas(
            c = c,
            model = model,
            cx = w * 0.5f,
            cy = h * 0.34f,
            scale = 430f,
            accentHex = "#4FD1A5",
            pose = CharacterFramePose(primaryClip = "idle")
        )

        // Up to 3 suggestion chips
        val chips = listOf(
            "Guide me through a calm breathing reset",
            "Help me wind down",
            "Organize tonight"
        )
        var chipX = 60f
        chips.forEach { label ->
            val cw = (label.length * 18f + 64f).coerceAtMost(540f)
            if (chipX + cw < w - 40f) {
                drawDesignPill(c, chipX, h - 290f, chipX + cw, h - 216f, label, "#1AFFFFFF", "#F3F5F9", false)
                chipX += cw + 20f
            }
        }

        // 56dp GlassSurface input bar at bottom
        drawDesignGlassInputBar(c, 60f, h - 184f, w - 60f, h - 64f, "Message or ask anything...", "#4FD1A5")
        return bmp
    }

    /**
     * Screenshot 2: Screen 2 — Character Picker (HorizontalPager with Kaito Storm #5AB8FF,
     * Category filter chips All / Medical / Anime / Culinary, 3D .glb preview, and single accent Choose button).
     */
    fun renderScreenshot2CharacterPicker(model: GlbLoadedModel? = null): Bitmap {
        val w = 1080
        val h = 1920
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        drawDesignAurora(c, w, h, "#5AB8FF")

        // Top back button + Category filter chips (All, Medical, Anime, Culinary)
        drawCircleIconButton(c, 96f, 118f, 36f)
        val cats = listOf("All" to false, "Medical" to false, "Anime" to true, "Culinary" to false)
        var cx = 168f
        cats.forEach { (label, active) ->
            val pw = label.length * 22f + 68f
            drawDesignPill(
                c, cx, 82f, cx + pw, 154f,
                label,
                if (active) "#5AB8FF" else "#1AFFFFFF",
                "#F3F5F9",
                active
            )
            cx += pw + 18f
        }

        // Large 3D .glb preview
        drawGlbCharacterOnCanvas(
            c = c,
            model = model,
            cx = w * 0.5f,
            cy = h * 0.38f,
            scale = 450f,
            accentHex = "#5AB8FF",
            pose = CharacterFramePose(primaryClip = "idle_look", touchYawDegrees = 6f)
        )

        // Bottom 28dp GlassSurface sheet with Name (Title), Role (Body secondary), and one Choose accent button
        drawDesignGlassSurface(c, 60f, h - 540f, w - 60f, h - 80f, 56f)
        drawTextCentered(c, "Kaito Storm", w * 0.5f, h - 430f, 46f, "#F3F5F9", true)
        drawTextCentered(c, "Anime • Energetic, playful, quick replies", w * 0.5f, h - 360f, 32f, "#A3F3F5F9", false)

        // Page indicator dots
        val dotY = h - 285f
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        dotPaint.color = Color.parseColor("#26FFFFFF")
        c.drawCircle(w * 0.5f - 36f, dotY, 8f, dotPaint)
        c.drawCircle(w * 0.5f + 36f, dotY, 8f, dotPaint)
        dotPaint.color = Color.parseColor("#5AB8FF")
        c.drawCircle(w * 0.5f, dotY, 12f, dotPaint)

        // Single accent Choose button
        drawPrimaryAccentButton(c, 110f, h - 220f, w - 110f, h - 116f, "Choose", "#5AB8FF")
        return bmp
    }

    /**
     * Screenshot 3: Screen 3 — Conversation Glass Bottom Sheet over Stage (Chef Marco #F5B04C).
     */
    fun renderScreenshot3ConversationSheet(model: GlbLoadedModel? = null): Bitmap {
        val w = 1080
        val h = 1920
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        drawDesignAurora(c, w, h, "#F5B04C")
        drawStageTopBar(c, w, "Chef Marco", "#F5B04C")

        drawGlbCharacterOnCanvas(
            c = c,
            model = model,
            cx = w * 0.5f,
            cy = h * 0.29f,
            scale = 390f,
            accentHex = "#F5B04C",
            pose = CharacterFramePose(
                primaryClip = "talk_a",
                morphWeights = mapOf(
                    "jawOpen" to 0.55f,
                    "mouthO" to 0.35f,
                    "mouthE" to 0.20f,
                    "mouthSmile" to 0.45f,
                    "eyeBlinkLeft" to 0f,
                    "eyeBlinkRight" to 0f
                )
            )
        )

        // Glass bottom sheet over Stage (GlassSurface #1)
        drawDesignGlassSurface(c, 60f, h - 820f, w - 60f, h - 220f, 56f)
        drawTextLeft(c, "Chef Marco", 110f, h - 735f, 42f, "#F3F5F9", true)

        // Thin accent progress line
        val progPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F5B04C")
            strokeWidth = 4f
        }
        c.drawLine(110f, h - 700f, w - 110f, h - 700f, progPaint)

        // User message bubble
        drawChatBubble(
            c, 260f, h - 660f, w - 110f, h - 545f,
            "What can I cook with eggs, rice, and garlic?",
            "#29F5B04C", "#59F5B04C"
        )
        // Character reply bubble
        drawChatBubble(
            c, 110f, h - 515f, w - 180f, h - 270f,
            "Golden garlic egg fried rice takes 12 minutes. Sear minced garlic in butter, toss cold rice on high heat, and fold in two whisked eggs.",
            "#0E121B", "#1AFFFFFF"
        )

        // Input bar (GlassSurface #2)
        drawDesignGlassInputBar(c, 60f, h - 184f, w - 60f, h - 64f, "Message or ask anything...", "#F5B04C")
        return bmp
    }

    /**
     * Screenshot 4: Screen 4 — Tools (Clean 2-column grid of tiles: icon + name, sentence case, zero emojis).
     */
    fun renderScreenshot4ToolsAndGallery(): Bitmap {
        val w = 1080
        val h = 1920
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        drawDesignAurora(c, w, h, "#4FD1A5")
        drawCircleIconButton(c, 96f, 118f, 36f)
        drawTextLeft(c, "Tools", 168f, 132f, 44f, "#F3F5F9", true)

        val tools = listOf(
            "Image to PDF",
            "Image to text",
            "PDF to image",
            "Text to speech",
            "Speech to text",
            "Video to audio",
            "Document to PDF",
            "Fridge chef"
        )

        val colW = (w - 156f) / 2f
        val rowH = 220f
        tools.forEachIndexed { idx, name ->
            val col = idx % 2
            val row = idx / 2
            val left = 60f + col * (colW + 36f)
            val top = 220f + row * (rowH + 32f)
            drawDesignGlassSurface(c, left, top, left + colW, top + rowH, 40f)

            // Minimalist outlined icon indicator
            val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#4FD1A5")
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
            c.drawRoundRect(RectF(left + 40f, top + 40f, left + 88f, top + 88f), 12f, 12f, iconPaint)
            drawTextLeft(c, name, left + 40f, top + 162f, 32f, "#F3F5F9", false)
        }

        // Tool Result preview card at bottom
        drawDesignGlassSurface(c, 60f, 1260f, w - 60f, h - 220f, 40f)
        drawTextLeft(c, "PDF document (.pdf)", 108f, 1340f, 40f, "#F3F5F9", true)
        drawTextLeft(c, "A4 PDF document • Ready to share or save", 108f, 1396f, 28f, "#A3F3F5F9", false)
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4FD1A5")
            strokeWidth = 5f
        }
        c.drawLine(108f, 1435f, w - 108f, 1435f, barPaint)
        drawTextLeft(c, "Saved to Downloads/document_export.pdf", 108f, 1510f, 30f, "#4FD1A5", false)

        // Share (secondary) and Save (single primary accent button)
        drawDesignPill(c, 60f, h - 180f, w * 0.5f - 18f, h - 76f, "Share", "#1AFFFFFF", "#F3F5F9", false)
        drawPrimaryAccentButton(c, w * 0.5f + 18f, h - 180f, w - 60f, h - 76f, "Save", "#4FD1A5")
        return bmp
    }

    private fun drawDesignAurora(c: Canvas, w: Int, h: Int, accentHex: String) {
        c.drawColor(Color.parseColor("#07090F"))
        val accent = Color.parseColor(accentHex)
        val r = Color.red(accent)
        val g = Color.green(accent)
        val b = Color.blue(accent)
        val luma = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        val dr = (r * 0.68 + luma * 0.32).toInt().coerceIn(0, 255)
        val dg = (g * 0.68 + luma * 0.32).toInt().coerceIn(0, 255)
        val db = (b * 0.68 + luma * 0.32).toInt().coerceIn(0, 255)

        val blob1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                w * 0.52f, h * 0.32f, w * 0.62f,
                Color.argb(58, dr, dg, db),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        c.drawCircle(w * 0.52f, h * 0.32f, w * 0.62f, blob1)

        val blob2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                w * 0.46f, h * 0.54f, w * 0.54f,
                Color.argb(40, dr, dg, db),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        c.drawCircle(w * 0.46f, h * 0.54f, w * 0.54f, blob2)
    }

    private fun drawGlbCharacterOnCanvas(
        c: Canvas,
        model: GlbLoadedModel?,
        cx: Float,
        cy: Float,
        scale: Float,
        accentHex: String,
        pose: CharacterFramePose
    ) {
        val accent = Color.parseColor(accentHex)
        val accR = Color.red(accent) / 255f
        val accG = Color.green(accent) / 255f
        val accB = Color.blue(accent) / 255f

        if (model == null) return

        val vCount = model.vertexCount
        val transformed = FloatArray(model.basePositions.size)
        System.arraycopy(model.basePositions, 0, transformed, 0, model.basePositions.size)
        for ((target, weight) in pose.morphWeights) {
            if (weight > 0.002f) {
                val deltas = model.morphDeltas[target] ?: continue
                for (i in transformed.indices) {
                    transformed[i] += deltas[i] * weight
                }
            }
        }

        val projX = FloatArray(vCount)
        val projY = FloatArray(vCount)
        val projZ = FloatArray(vCount)
        val yaw = Math.toRadians(pose.touchYawDegrees.toDouble()).toFloat()
        val cosY = cos(yaw)
        val sinY = sin(yaw)

        for (v in 0 until vCount) {
            val px = transformed[v * 3]
            val py = transformed[v * 3 + 1]
            val pz = transformed[v * 3 + 2]
            val rx = px * cosY + pz * sinY
            val rz = -px * sinY + pz * cosY
            val zCam = (2.05f - rz).coerceAtLeast(0.4f)
            val persp = (scale * 1.85f) / zCam
            projX[v] = cx + rx * persp
            projY[v] = cy - py * persp
            projZ[v] = rz
        }

        val triCount = model.triangleCount
        val order = IntArray(triCount) { it }
        val depths = FloatArray(triCount) { t ->
            val i0 = model.indices[t * 3]
            val i1 = model.indices[t * 3 + 1]
            val i2 = model.indices[t * 3 + 2]
            (projZ[i0] + projZ[i1] + projZ[i2]) / 3f
        }
        var gap = triCount / 2
        while (gap > 0) {
            for (i in gap until triCount) {
                val tmp = order[i]
                val d = depths[tmp]
                var j = i
                while (j >= gap && depths[order[j - gap]] > d) {
                    order[j] = order[j - gap]
                    j -= gap
                }
                order[j] = tmp
            }
            gap /= 2
        }

        val triPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val path = Path()
        for (idx in 0 until triCount) {
            val t = order[idx]
            val i0 = model.indices[t * 3]
            val i1 = model.indices[t * 3 + 1]
            val i2 = model.indices[t * 3 + 2]
            val x0 = projX[i0]
            val y0 = projY[i0]
            val x1 = projX[i1]
            val y1 = projY[i1]
            val x2 = projX[i2]
            val y2 = projY[i2]
            if ((x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0) >= 0f) continue

            val nx = model.baseNormals[i0 * 3]
            val ny = model.baseNormals[i0 * 3 + 1]
            val nz = model.baseNormals[i0 * 3 + 2]
            val ndotl = max(0f, nx * 0.42f + ny * 0.58f + nz * 0.70f)
            val ambient = 0.42f + 0.14f * (ny * 0.5f + 0.5f)
            val rim = (1f - max(0f, nz)).pow(2.4f) * 0.42f
            val lit = ambient + ndotl * 0.56f

            val br = model.vertexColors[i0 * 4]
            val bg = model.vertexColors[i0 * 4 + 1]
            val bb = model.vertexColors[i0 * 4 + 2]
            val r = ((br * lit + accR * rim).coerceIn(0f, 1f) * 255f).toInt()
            val g = ((bg * lit + accG * rim).coerceIn(0f, 1f) * 255f).toInt()
            val b = ((bb * lit + accB * rim).coerceIn(0f, 1f) * 255f).toInt()

            triPaint.color = Color.rgb(r, g, b)
            path.reset()
            path.moveTo(x0, y0)
            path.lineTo(x1, y1)
            path.lineTo(x2, y2)
            path.close()
            c.drawPath(path, triPaint)
        }
    }

    private fun drawStageTopBar(c: Canvas, w: Int, charName: String, accentHex: String) {
        drawCircleIconButton(c, 96f, 118f, 36f)
        drawDesignPill(c, w * 0.5f - 150f, 84f, w * 0.5f + 150f, 152f, charName, "#1AFFFFFF", "#F3F5F9", false)
        drawCircleIconButton(c, w - 96f, 118f, 36f)
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(accentHex) }
        c.drawCircle(w * 0.5f - 110f, 118f, 8f, dotPaint)
    }

    private fun drawCircleIconButton(c: Canvas, cx: Float, cy: Float, r: Float) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EB0E121B") }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1AFFFFFF")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        c.drawCircle(cx, cy, r, fill)
        c.drawCircle(cx, cy, r, stroke)
    }

    private fun drawDesignGlassSurface(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float) {
        val rect = RectF(l, t, r, b)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EB0E121B") }
        val tint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0FFFFFFF") }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1AFFFFFF")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val topHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#29FFFFFF")
            strokeWidth = 2f
        }
        c.drawRoundRect(rect, rad, rad, fill)
        c.drawRoundRect(rect, rad, rad, tint)
        c.drawRoundRect(rect, rad, rad, border)
        c.drawLine(l + rad, t + 1f, r - rad, t + 1f, topHighlight)
    }

    private fun drawDesignPill(
        c: Canvas,
        l: Float,
        t: Float,
        r: Float,
        b: Float,
        label: String,
        borderHex: String,
        textHex: String,
        active: Boolean
    ) {
        val rect = RectF(l, t, r, b)
        val rad = (b - t) * 0.5f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(if (active) "#24FFFFFF" else "#EB0E121B")
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(borderHex)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        c.drawRoundRect(rect, rad, rad, fill)
        c.drawRoundRect(rect, rad, rad, stroke)
        drawTextCentered(c, label, (l + r) * 0.5f, (t + b) * 0.5f + 10f, 26f, textHex, active)
    }

    private fun drawPrimaryAccentButton(
        c: Canvas,
        l: Float,
        t: Float,
        r: Float,
        b: Float,
        label: String,
        accentHex: String
    ) {
        val rect = RectF(l, t, r, b)
        val rad = (b - t) * 0.5f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(accentHex) }
        c.drawRoundRect(rect, rad, rad, fill)
        drawTextCentered(c, label, (l + r) * 0.5f, (t + b) * 0.5f + 11f, 32f, "#07090F", true)
    }

    private fun drawDesignGlassInputBar(
        c: Canvas,
        l: Float,
        t: Float,
        r: Float,
        b: Float,
        hint: String,
        accentHex: String
    ) {
        val rad = (b - t) * 0.5f
        drawDesignGlassSurface(c, l, t, r, b, rad)
        drawTextLeft(c, hint, l + 110f, (t + b) * 0.5f + 10f, 30f, "#66F3F5F9", false)
        val sendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(accentHex) }
        c.drawCircle(r - 60f, (t + b) * 0.5f, 38f, sendPaint)
    }

    private fun drawChatBubble(
        c: Canvas,
        l: Float,
        t: Float,
        r: Float,
        b: Float,
        text: String,
        fillHex: String,
        borderHex: String
    ) {
        val rect = RectF(l, t, r, b)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(fillHex) }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(borderHex)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        c.drawRoundRect(rect, 36f, 36f, fill)
        c.drawRoundRect(rect, 36f, 36f, stroke)

        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F3F5F9")
            textSize = 28f
        }
        val words = text.split(" ")
        var line = StringBuilder()
        var y = t + 52f
        for (w in words) {
            if (line.length + w.length > 38) {
                c.drawText(line.toString(), l + 32f, y, tp)
                y += 38f
                line = StringBuilder("$w ")
            } else {
                line.append("$w ")
            }
        }
        if (line.isNotEmpty()) {
            c.drawText(line.toString(), l + 32f, y, tp)
        }
    }

    private fun drawTextCentered(
        c: Canvas,
        text: String,
        cx: Float,
        y: Float,
        size: Float,
        colorHex: String,
        bold: Boolean
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(colorHex)
            textSize = size
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        c.drawText(text, cx, y, p)
    }

    private fun drawTextLeft(
        c: Canvas,
        text: String,
        x: Float,
        y: Float,
        size: Float,
        colorHex: String,
        bold: Boolean
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(colorHex)
            textSize = size
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        c.drawText(text, x, y, p)
    }

    private fun buildFivePageIllustratedPdf(
        outFile: File,
        shot1: Bitmap,
        shot2: Bitmap,
        shot3: Bitmap,
        shot4: Bitmap
    ) {
        val pages = listOf(
            renderOverviewPdfPageBitmap(),
            renderAnnotatedScreenshotPageBitmap(
                pageNum = 2,
                title = "Screen 1 — Stage (Home)",
                subtitle = "Nurse Elena (.glb) over 20s AGSL aurora, 3 suggestion chips, and 56dp GlassSurface input bar.",
                shot = shot1
            ),
            renderAnnotatedScreenshotPageBitmap(
                pageNum = 3,
                title = "Screen 2 — Character Picker",
                subtitle = "Full-screen HorizontalPager (Elena, Kaito, Marco), category chips, and single accent Choose button.",
                shot = shot2
            ),
            renderAnnotatedScreenshotPageBitmap(
                pageNum = 4,
                title = "Screen 3 — Conversation Sheet",
                subtitle = "GlassSurface bottom sheet over the live 3D Stage while the character remains visible and lip-synced.",
                shot = shot3
            ),
            renderAnnotatedScreenshotPageBitmap(
                pageNum = 5,
                title = "Screens 4 & 5 — Tools Grid & Tool Result",
                subtitle = "Clean 2-column grid of tiles, thin accent progress indicator, and Share / Save result view.",
                shot = shot4
            )
        )
        writeBitmapsAsPdf14(outFile, pages)
    }

    private fun renderOverviewPdfPageBitmap(): Bitmap {
        val pw = 1190
        val ph = 1684
        val bmp = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#07090F"))

        drawDesignGlassSurface(c, 56f, 56f, pw - 56f, 240f, 36f)
        drawTextLeft(c, "MochiLife — Redesign Specification & Audit", 96f, 132f, 38f, "#F3F5F9", true)
        drawTextLeft(c, "Strictly aligned with DESIGN.md and CHARACTERS.md", 96f, 188f, 24f, "#4FD1A5", false)

        val lines = listOf(
            "1. Design Tokens (DESIGN.md):" to "#4FD1A5",
            "   • bg #07090F, bgRaised #0E121B, textPrimary #F3F5F9, hairline #FFFFFF 10%, danger #FF5A5F" to "#F3F5F9",
            "   • Manrope Typography: Display 34/40sp, Title 22/28sp, Body 16/24sp, Label 13/18sp" to "#F3F5F9",
            "   • GlassSurface: 24dp blur on Android 12+, 6% white tint, 16% top edge, max 2 per screen" to "#F3F5F9",
            "   • AuroraBackground: Android 13+ AGSL RuntimeShader (20s drift + 3% film grain)" to "#F3F5F9",
            "2. 3D Character Pipeline (CHARACTERS.md):" to "#5AB8FF",
            "   • Binary glTF 2.0 (.glb) assets in app/src/main/assets/characters/: elena.glb, kaito.glb, marco.glb" to "#F3F5F9",
            "   • 8 Animation Clips: idle, idle_look, listen, think, talk_a, talk_b, wave, happy" to "#F3F5F9",
            "   • 6 Morph Targets: jawOpen, mouthO, mouthE, mouthSmile, eyeBlinkLeft, eyeBlinkRight" to "#F3F5F9",
            "   • 5 Runtime States: Idle, Listening, Thinking, Talking (WAV RMS 80ms/150ms lip-sync), Reacting" to "#F3F5F9",
            "3. Parked Modules (com.example.parked):" to "#F5B04C",
            "   • Legacy Canvas character renderer removed; Bio-Mood, Receipt Splitter & Calling Bridge parked" to "#F3F5F9"
        )
        var y = 320f
        lines.forEach { (txt, col) ->
            drawTextLeft(c, txt, 76f, y, 24f, col, col != "#F3F5F9")
            y += 64f
        }
        return bmp
    }

    private fun renderAnnotatedScreenshotPageBitmap(
        pageNum: Int,
        title: String,
        subtitle: String,
        shot: Bitmap
    ): Bitmap {
        val pw = 1190
        val ph = 1684
        val bmp = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#07090F"))

        drawTextLeft(c, title, 72f, 96f, 34f, "#F3F5F9", true)
        drawTextLeft(c, subtitle, 72f, 142f, 22f, "#A3F3F5F9", false)

        val dstRect = RectF(195f, 185f, pw - 195f, ph - 95f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        c.drawBitmap(shot, null, dstRect, p)
        drawTextCentered(c, "Page $pageNum of 5", pw * 0.5f, ph - 36f, 20f, "#66F3F5F9", false)
        return bmp
    }

    private fun writeBitmapsAsPdf14(outFile: File, pageBitmaps: List<Bitmap>) {
        val pageWidthPt = 595
        val pageHeightPt = 842
        val jpegPages = pageBitmaps.map { bmp ->
            ByteArrayOutputStream().use { baos ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, baos)
                baos.toByteArray()
            }
        }
        val numPages = jpegPages.size
        val totalObjects = 2 + numPages * 3
        val offsets = IntArray(totalObjects + 1)
        val out = ByteArrayOutputStream()

        fun writeAscii(s: String) {
            out.write(s.toByteArray(Charsets.US_ASCII))
        }

        writeAscii("%PDF-1.4\n%\u00E2\u00E3\u00CF\u00D3\n")
        offsets[1] = out.size()
        writeAscii("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        val kids = (0 until numPages).joinToString(" ") { i -> "${3 + i * 3} 0 R" }
        offsets[2] = out.size()
        writeAscii("2 0 obj\n<< /Type /Pages /Kids [ $kids ] /Count $numPages >>\nendobj\n")

        for (i in 0 until numPages) {
            val pageObjId = 3 + i * 3
            val imgObjId = pageObjId + 1
            val contentObjId = pageObjId + 2
            val jpegBytes = jpegPages[i]
            val bmp = pageBitmaps[i]

            offsets[pageObjId] = out.size()
            writeAscii(
                "$pageObjId 0 obj\n<< /Type /Page /Parent 2 0 R " +
                    "/MediaBox [0 0 $pageWidthPt $pageHeightPt] " +
                    "/Resources << /XObject << /Im$i $imgObjId 0 R >> >> " +
                    "/Contents $contentObjId 0 R >>\nendobj\n"
            )

            offsets[imgObjId] = out.size()
            writeAscii(
                "$imgObjId 0 obj\n<< /Type /XObject /Subtype /Image " +
                    "/Width ${bmp.width} /Height ${bmp.height} " +
                    "/ColorSpace /DeviceRGB /BitsPerComponent 8 " +
                    "/Filter /DCTDecode /Length ${jpegBytes.size} >>\nstream\n"
            )
            out.write(jpegBytes)
            writeAscii("\nendstream\nendobj\n")

            val contentStream = "q\n$pageWidthPt 0 0 $pageHeightPt 0 0 cm\n/Im$i Do\nQ\n"
            val contentBytes = contentStream.toByteArray(Charsets.US_ASCII)
            offsets[contentObjId] = out.size()
            writeAscii("$contentObjId 0 obj\n<< /Length ${contentBytes.size} >>\nstream\n")
            out.write(contentBytes)
            writeAscii("endstream\nendobj\n")
        }

        val xrefOffset = out.size()
        writeAscii("xref\n0 ${totalObjects + 1}\n0000000000 65535 f \n")
        for (objId in 1..totalObjects) {
            writeAscii(String.format("%010d 00000 n \n", offsets[objId]))
        }
        writeAscii(
            "trailer\n<< /Size ${totalObjects + 1} /Root 1 0 R >>\n" +
                "startxref\n$xrefOffset\n%%EOF\n"
        )
        FileOutputStream(outFile).use { fos -> out.writeTo(fos) }
    }
}
