package com.example.ai.gemma

import android.content.Context
import com.example.ai.VoiceLog
import com.example.data.CompanionActionType
import com.example.data.CompanionChatTurn
import org.json.JSONObject
import java.util.UUID

/**
 * Gemma as Mochi's on-phone brain (Settings → AI engine → On this phone, or Automatic with no
 * internet). Turn by turn: Gemma hears the recording itself (or reads typed text), answers in a
 * small JSON format, the app checks any action exactly as for the cloud, and the phone's voice
 * speaks. No speech recogniser is involved, so nothing else ever takes Mochi's microphone.
 * The model is loaded on first use (about 10–30 s) from a download made in Gemma Lab.
 */
object GemmaEngine {

    sealed class Result {
        data class Answer(val turn: CompanionChatTurn, val seconds: Double) : Result()
        data class Unavailable(val reason: String) : Result()
    }

    private val ACTIONS = mapOf(
        "CALL" to CompanionActionType.SIM_CALL,
        "WHATSAPP_CALL" to CompanionActionType.WHATSAPP_CALL,
        "OPEN_APP" to CompanionActionType.OPEN_APP,
        "YOUTUBE" to CompanionActionType.YOUTUBE,
        "READ_MESSAGES" to CompanionActionType.READ_MESSAGES
    )

    private fun systemPrompt(companionName: String, languageName: String) = """
        You are $companionName, a warm companion on the user's phone. Reply with JSON only:
        {"reply": "what you say, one or two short sentences, in the language the user wrote in", "action": "NONE or one of ${ACTIONS.keys.joinToString(", ")}", "target": "person, app or video search, or empty"}
        CALL = phone call, WHATSAPP_CALL = call on WhatsApp, OPEN_APP = open an app, YOUTUBE = play or find a video, READ_MESSAGES = read new messages.
        The user most likely writes ${languageName.ifBlank { "English" }}; Arabic in their everyday dialect. Never invent contacts or results.
    """.trimIndent()

    private fun hearingPrompt(companionName: String, languageName: String) = systemPrompt(companionName, languageName) + "\n" +
        "You receive a short voice recording. Add the field \"heard\": the user's exact words, written in the language they spoke. " +
        "If the recording has no clear speech meant for you (silence, noise, a TV, music), reply with {\"heard\": \"\"} only."

    /** Reads what Gemma heard in a recording ("" when nothing was said). Pure, unit-tested. */
    fun parseHeard(answer: String): String? {
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching {
            val o = JSONObject(answer.substring(start, end + 1))
            if (o.has("heard")) o.optString("heard").trim() else null
        }.getOrNull()
    }

    /** Reads Gemma's answer: (reply, action, target). Falls back to plain text as the reply. Pure, unit-tested. */
    fun parse(answer: String): Triple<String, CompanionActionType, String> {
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        if (start >= 0 && end > start) {
            runCatching {
                val o = JSONObject(answer.substring(start, end + 1))
                val type = ACTIONS[o.optString("action").trim().uppercase()] ?: CompanionActionType.NONE
                return Triple(o.optString("reply").trim(), type, o.optString("target").trim().take(80))
            }
        }
        return Triple(answer.replace(Regex("```[a-z]*"), "").trim().take(400), CompanionActionType.NONE, "")
    }

    /** The model file to use: E2B if downloaded (faster), else E4B, else none. */
    internal fun downloadedModel(context: Context): GemmaModel? {
        val d = GemmaLab.downloader(context)
        return GemmaCatalog.MODELS.firstOrNull { d.file(it).exists() }
    }

    fun isDownloaded(context: Context): Boolean = downloadedModel(context) != null

    /**
     * Gemma can take a spoken turn on its own: a model is downloaded and, if already loaded, it
     * loaded with hearing. (If not loaded yet, hearing is tried when it loads.)
     */
    fun canHear(context: Context): Boolean {
        if (!isDownloaded(context)) return false
        val r = GemmaLab.runner(context)
        return r.state.value !is GemmaRunner.State.Loaded || r.hearsAudio
    }

    private suspend fun ensureLoaded(context: Context): Result.Unavailable? {
        val runner = GemmaLab.runner(context)
        if (runner.state.value is GemmaRunner.State.Loaded) return null
        val model = downloadedModel(context)
            ?: return Result.Unavailable("No on-phone AI is downloaded yet. Download Gemma 4 E2B in Settings → Gemma Lab.")
        VoiceLog.add(VoiceLog.Kind.AI, "Loading ${model.label} on the phone (first use takes a moment)…")
        runner.load(model, GemmaLab.downloader(context).file(model), GemmaRunner.Processor.CPU)
        (runner.state.value as? GemmaRunner.State.Failed)?.let { return Result.Unavailable(it.reason) }
        return null
    }

    sealed class Heard {
        /** [heard] is "" when the recording had nothing for her. */
        data class Turn(val heard: String, val turn: CompanionChatTurn?, val seconds: Double) : Heard()
        data class Unavailable(val reason: String) : Heard()
    }

    /** Gemma listens to the user's recording and answers it, all on the phone. */
    suspend fun hear(
        context: Context,
        wav: ByteArray,
        companionName: String,
        languageName: String,
        history: List<CompanionChatTurn>
    ): Heard {
        ensureLoaded(context)?.let { return Heard.Unavailable(it.reason) }
        val runner = GemmaLab.runner(context)
        if (!runner.hearsAudio) return Heard.Unavailable("This on-phone model can't hear recordings on this phone.")
        val earlier = history.filterNot { it.isActionReport }.takeLast(4)
            .joinToString("\n") { (if (it.senderIsUser) "User: " else "You: ") + it.message.take(200) }
        val instruction = (if (earlier.isNotBlank()) "Earlier:\n$earlier\n\n" else "") + "Listen to the recording and answer with JSON only."
        val t0 = System.currentTimeMillis()
        val raw = try {
            runner.answerAudio(hearingPrompt(companionName, languageName), wav, instruction)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            return Heard.Unavailable("The on-phone AI could not listen (${e.javaClass.simpleName}).")
        }
        val seconds = (System.currentTimeMillis() - t0) / 1000.0
        VoiceLog.add(VoiceLog.Kind.AI, "Gemma replied: ${raw.replace('\n', ' ').take(120)}")
        val parsed = parseHeard(raw)
        val heard = when {
            parsed != null -> parsed
            parse(raw).first.isNotBlank() -> {
                VoiceLog.add(VoiceLog.Kind.AI, "Gemma answered without writing down the words")
                "(voice)"
            }
            else -> ""
        }
        if (heard.isEmpty()) {
            VoiceLog.add(VoiceLog.Kind.IGNORED, "Gemma heard nothing for her (%.1f s)".format(seconds))
            return Heard.Turn("", null, seconds)
        }
        VoiceLog.add(VoiceLog.Kind.HEARD, "Gemma heard \"${heard.take(80)}\" and answered in %.1f s".format(seconds))
        return Heard.Turn(heard, toTurn(raw), seconds)
    }

    private fun toTurn(raw: String): CompanionChatTurn {
        val (reply, type, target) = parse(raw)
        return CompanionChatTurn(
            id = UUID.randomUUID().toString(),
            senderIsUser = false,
            message = reply.ifBlank { "Okay." },
            actionType = type,
            actionRecipient = if (type == CompanionActionType.SIM_CALL || type == CompanionActionType.WHATSAPP_CALL) target else "",
            actionPayload = if (type == CompanionActionType.OPEN_APP || type == CompanionActionType.YOUTUBE) target else ""
        )
    }

    suspend fun answer(
        context: Context,
        text: String,
        companionName: String,
        languageName: String,
        history: List<CompanionChatTurn>
    ): Result {
        ensureLoaded(context)?.let { return it }
        val runner = GemmaLab.runner(context)
        val context4 = history.filterNot { it.isActionReport }.takeLast(4)
            .joinToString("\n") { (if (it.senderIsUser) "User: " else "You: ") + it.message.take(200) }
        val prompt = (if (context4.isNotBlank()) "Earlier:\n$context4\n\nNow the user says: " else "") + text
        val t0 = System.currentTimeMillis()
        val raw = try {
            runner.answerOnce(systemPrompt(companionName, languageName), prompt)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            return Result.Unavailable("The on-phone AI could not answer (${e.javaClass.simpleName}).")
        }
        val seconds = (System.currentTimeMillis() - t0) / 1000.0
        VoiceLog.add(VoiceLog.Kind.AI, "Answered on the phone by Gemma in %.1f s".format(seconds))
        return Result.Answer(toTurn(raw), seconds)
    }
}
