package com.example.ai

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.core.content.FileProvider
import com.example.actions.ActionExecutor
import com.example.actions.ActionOutcome
import com.example.actions.PreparedAction
import com.example.actions.Preparation
import com.example.data.CompanionActionType
import com.example.data.CompanionChatTurn
import com.example.data.MochiHashRoutes
import com.example.ui.character.CharacterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.coroutines.resume

enum class AttachmentMediaType(val label: String) {
    TEXT_ONLY("Text"),
    VOICE_NOTE("Voice (.wav)"),
    IMAGE_CARD("Image (.png)"),
    PDF_DOCUMENT("PDF (.pdf)"),
    USER_GALLERY_MEDIA("Gallery file")
}

/**
 * Speech output and sharing. Phone actions (calls, messages, searches) live in
 * [com.example.actions.ActionExecutor]; this object only forwards to it.
 */
object OmnichannelMediaBridge {

    /**
     * Selects the highest-definition, most natural human-like neural voice available on the Android device
     * and configures character-authentic pitch, cadence, and audio attributes so speech never sounds robotic.
     */
    fun configureHumanLikeNeuralVoice(
        tts: TextToSpeech?,
        character: CharacterSpec,
        locale: Locale = Locale.getDefault()
    ) {
        if (tts == null) return
        try {
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )

            val targetLang = locale.language.ifBlank { "en" }
            val availableVoices: Set<Voice>? = try {
                tts.voices
            } catch (_: Throwable) {
                null
            }

            if (!availableVoices.isNullOrEmpty()) {
                // Same gender as her natural voice, so the voice never switches from female to male.
                val wantsFemaleVoice = VoiceGender.isFemaleCharacter(character.id)
                val bestVoice = availableVoices
                    // Only voices of the reply's language: an English voice reading Persian is gibberish.
                    .filter { v -> v.locale.language.equals(targetLang, ignoreCase = true) }
                    .filterNot { v -> v.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true }
                    .maxByOrNull { v ->
                        var score = 0
                        val nameLower = v.name.lowercase()
                        if (v.locale.country.equals(locale.country, ignoreCase = true)) score += 15
                        if (v.quality >= Voice.QUALITY_VERY_HIGH) score += 50
                        else if (v.quality >= Voice.QUALITY_HIGH) score += 35
                        if (nameLower.contains("network") || nameLower.contains("wavenet") ||
                            nameLower.contains("neural") || nameLower.contains("natural") ||
                            nameLower.contains("studio") || nameLower.contains("journey")
                        ) {
                            score += 45
                        }
                        // Gender outweighs everything else: a female companion never gets a male voice.
                        when (VoiceGender.guess(v.name)) {
                            null -> {}
                            wantsFemaleVoice -> score += 200
                            else -> score -= 200
                        }
                        if (v.latency <= Voice.LATENCY_LOW) score += 10
                        score
                    }

                if (bestVoice != null) {
                    tts.voice = bestVoice
                } else {
                    tts.setLanguage(locale)
                }
            } else {
                tts.setLanguage(locale)
            }

            // Fine-tuned human prosody per companion (warm, conversational, non-robotic)
            val naturalPitch = when (character.id) {
                "elena" -> 1.03f
                "kaito" -> 0.98f
                "marco" -> 0.94f
                else -> character.pitch
            }
            val naturalRate = when (character.id) {
                "elena" -> 0.97f
                "kaito" -> 1.02f
                "marco" -> 0.95f
                else -> character.speechRate
            }
            tts.setPitch(naturalPitch)
            tts.setSpeechRate(naturalRate)
        } catch (_: Throwable) {
        }
    }

    /**
     * Shapes text for warm, natural human speech by removing markdown symbols, emojis, and raw URLs,
     * and normalizing punctuation pauses.
     */
    fun formatNaturalSpeechProsody(rawText: String): String {
        return rawText
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("[*_`~#•|>]+"), " ")
            .replace(Regex("[\\p{So}\\p{Cn}]"), "") // Strip emoji symbols so TTS never reads emoji names aloud
            .replace(" · ", ", ")
            .replace("—", ", ")
            .replace("...", ", ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** Speaks a line through the given engine. Never changes the user's volume. */
    fun speak(context: Context, tts: TextToSpeech?, text: String, character: CharacterSpec = CharacterSpec.ELENA) {
        val clean = formatNaturalSpeechProsody(text)
        if (clean.isEmpty() || tts == null) return
        configureHumanLikeNeuralVoice(tts, character)
        val params = Bundle().apply { putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC) }
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, params, "mochi_${System.currentTimeMillis()}")
        VoiceCommandService.shared(context).watchTtsUntilSilent(tts)
    }

    /**
     * Prepares and runs the turn's action, returning what really happened.
     * Navigation is handled by the caller through [onNavigateHashRoute].
     */
    fun autoExecuteCompanionAction(
        context: Context,
        tts: TextToSpeech?,
        turn: CompanionChatTurn,
        character: CharacterSpec = CharacterSpec.ELENA,
        onNavigateHashRoute: ((String) -> Unit)? = null
    ): ActionOutcome {
        return when (turn.actionType) {
            CompanionActionType.NAVIGATE_ROUTE -> {
                val route = turn.actionPayload.takeIf { it in MochiHashRoutes.ALL }
                    ?: return ActionOutcome.Failed("I don't know that screen.")
                onNavigateHashRoute?.invoke(route)
                ActionOutcome.Done("")
            }
            CompanionActionType.SPEAK_LOUD -> {
                speak(context, tts, turn.spokenConfirmation.ifBlank { turn.message }, character)
                ActionOutcome.Done("")
            }
            else -> {
                val executor = ActionExecutor(context)
                when (val prep = executor.prepare(turn)) {
                    is Preparation.Blocked -> prep.outcome
                    is Preparation.Ready -> executor.execute(prep.action)
                }
            }
        }
    }

    fun executePrepared(context: Context, action: PreparedAction): ActionOutcome = ActionExecutor(context).execute(action)

    // --- Sharing files into WhatsApp / Telegram -------------------------------------------------

    /**
     * Creates the attachment and opens the chosen app's share screen. The user taps Send there.
     * @throws IllegalStateException if the attachment could not be produced (e.g. no TTS voice).
     */
    suspend fun sendAttachmentToPlatform(
        context: Context,
        tts: TextToSpeech?,
        targetPlatform: String,
        recipient: String,
        messageText: String,
        mediaType: AttachmentMediaType
    ): ActionOutcome {
        val body = messageText.trim()
        if (body.isEmpty() && mediaType != AttachmentMediaType.USER_GALLERY_MEDIA) {
            return ActionOutcome.Failed("There's nothing to send yet.")
        }
        return try {
            when (mediaType) {
                AttachmentMediaType.TEXT_ONLY, AttachmentMediaType.USER_GALLERY_MEDIA ->
                    launchOmnichannelSend(context, targetPlatform, body)
                AttachmentMediaType.VOICE_NOTE ->
                    launchOmnichannelSend(context, targetPlatform, "", generateVoiceNoteFile(context, tts, body), "audio/wav")
                AttachmentMediaType.IMAGE_CARD ->
                    launchOmnichannelSend(context, targetPlatform, body, generateVisualCardFile(context, recipient, body), "image/png")
                AttachmentMediaType.PDF_DOCUMENT ->
                    launchOmnichannelSend(context, targetPlatform, "", generatePdfDocumentFile(context, recipient, body), "application/pdf")
            }
        } catch (e: IllegalStateException) {
            ActionOutcome.Failed(e.message ?: "I couldn't create that file.")
        }
    }

    /** Synthesises speech to a .wav file. Throws if the phone's voice engine can't do it. */
    suspend fun generateVoiceNoteFile(context: Context, tts: TextToSpeech?, spokenText: String): File =
        withContext(Dispatchers.IO) {
            checkNotNull(tts) { "The voice engine isn't ready yet. Try again in a moment." }
            val outFile = File(sharedDir(context), "voice_${System.currentTimeMillis()}.wav")
            val ok = suspendCancellableCoroutine { cont ->
                val id = "synth_${System.currentTimeMillis()}"
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { if (utteranceId == id && cont.isActive) cont.resume(true) }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { if (utteranceId == id && cont.isActive) cont.resume(false) }
                    override fun onError(utteranceId: String?, errorCode: Int) { if (utteranceId == id && cont.isActive) cont.resume(false) }
                })
                if (tts.synthesizeToFile(formatNaturalSpeechProsody(spokenText), Bundle(), outFile, id) != TextToSpeech.SUCCESS && cont.isActive) {
                    cont.resume(false)
                }
            }
            check(ok && outFile.length() > 44) { "Your phone's voice engine couldn't record that voice note." }
            outFile
        }

    suspend fun generatePdfDocumentFile(
        context: Context,
        recipient: String,
        bodyText: String
    ): File = withContext(Dispatchers.IO) {
        val outFile = File(sharedDir(context), "document_${System.currentTimeMillis()}.pdf")
        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdf.startPage(pageInfo)
        val canvas = page.canvas

        val bgPaint = Paint().apply { color = Color.parseColor("#FAF8F5") }
        canvas.drawRect(0f, 0f, 595f, 842f, bgPaint)

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 595f, 120f,
                Color.parseColor("#141519"),
                Color.parseColor("#22252E"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(RectF(32f, 32f, 563f, 132f), 20f, 20f, headerPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E5B869")
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(if (recipient.isBlank()) "MochiLife note" else "For $recipient", 54f, 88f, titlePaint)

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#141519")
            textSize = 15f
        }

        var y = 185f
        val words = bodyText.split(" ")
        var line = StringBuilder()
        for (word in words) {
            if (line.length + word.length > 62) {
                canvas.drawText(line.toString(), 48f, y, bodyPaint)
                y += 26f
                line = StringBuilder("$word ")
            } else {
                line.append("$word ")
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line.toString(), 48f, y, bodyPaint)
        }

        pdf.finishPage(page)
        FileOutputStream(outFile).use { pdf.writeTo(it) }
        pdf.close()
        outFile
    }

    suspend fun generateVisualCardFile(
        context: Context,
        recipient: String,
        bodyText: String
    ): File = withContext(Dispatchers.IO) {
        val outFile = File(sharedDir(context), "card_${System.currentTimeMillis()}.png")
        val bmp = Bitmap.createBitmap(900, 600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#141519")
        }
        canvas.drawRect(0f, 0f, 900f, 600f, bgPaint)

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#22252E")
        }
        canvas.drawRoundRect(RectF(48f, 48f, 852f, 552f), 36f, 36f, cardPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E5B869")
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(if (recipient.isBlank()) "MochiLife note" else "For $recipient", 88f, 120f, titlePaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FAF8F5")
            textSize = 26f
        }
        var y = 195f
        val words = bodyText.split(" ")
        var line = StringBuilder()
        for (word in words) {
            if (line.length + word.length > 38) {
                canvas.drawText(line.toString(), 88f, y, textPaint)
                y += 40f
                line = StringBuilder("$word ")
            } else {
                line.append("$word ")
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line.toString(), 88f, y, textPaint)
        }

        FileOutputStream(outFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
        outFile
    }

    /** Opens the platform's share screen with the text/file. Returns an honest outcome. */
    fun launchOmnichannelSend(
        context: Context,
        targetPlatform: String,
        messageText: String,
        attachedFile: File? = null,
        mimeType: String = "text/plain"
    ): ActionOutcome {
        val streamUri = attachedFile?.let { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) }
        val targetPackage = when (targetPlatform.uppercase(Locale.ROOT)) {
            "WHATSAPP" -> ActionExecutor.WHATSAPP
            "TELEGRAM" -> ActionExecutor.TELEGRAM
            else -> null
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = if (streamUri != null) mimeType else "text/plain"
            if (messageText.isNotBlank()) putExtra(Intent.EXTRA_TEXT, messageText)
            if (streamUri != null) {
                putExtra(Intent.EXTRA_STREAM, streamUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            targetPackage?.let { setPackage(it) }
        }
        val name = targetPlatform.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase() }
        return try {
            context.startActivity(send.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ActionOutcome.Done("$name is open — pick the chat and tap Send.")
        } catch (_: ActivityNotFoundException) {
            try {
                send.setPackage(null)
                context.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                ActionOutcome.Done("$name isn't installed, so I opened the share menu.")
            } catch (_: ActivityNotFoundException) {
                ActionOutcome.Failed("No app on this phone can share that.")
            }
        }
    }

    /** Only this folder is exposed through the FileProvider (see res/xml/file_paths.xml). */
    private fun sharedDir(context: Context): File =
        File(context.cacheDir, "shared").apply { mkdirs() }
}
