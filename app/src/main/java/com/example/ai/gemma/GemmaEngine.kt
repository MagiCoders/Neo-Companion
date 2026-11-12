package com.example.ai.gemma

import android.content.Context
import com.example.ai.VoiceLog
import com.example.data.CompanionActionType
import com.example.data.CompanionChatTurn
import org.json.JSONObject
import java.util.UUID

/**
 * Gemma as Mochi's on-phone brain (Settings → AI engine → On this phone, or Automatic with no
 * internet). Turn by turn: the phone turns speech into text, Gemma answers in a small JSON format,
 * the app checks any action exactly as for the cloud, and the phone's voice speaks.
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
    private fun downloadedModel(context: Context): GemmaModel? {
        val d = GemmaLab.downloader(context)
        return GemmaCatalog.MODELS.firstOrNull { d.file(it).exists() }
    }

    fun isDownloaded(context: Context): Boolean = downloadedModel(context) != null

    suspend fun answer(
        context: Context,
        text: String,
        companionName: String,
        languageName: String,
        history: List<CompanionChatTurn>
    ): Result {
        val runner = GemmaLab.runner(context)
        if (runner.state.value !is GemmaRunner.State.Loaded) {
            val model = downloadedModel(context)
                ?: return Result.Unavailable("No on-phone AI is downloaded yet. Download Gemma 4 E2B in Settings → Gemma Lab.")
            VoiceLog.add(VoiceLog.Kind.AI, "Loading ${model.label} on the phone (first use takes a moment)…")
            runner.load(model, GemmaLab.downloader(context).file(model), GemmaRunner.Processor.CPU)
            (runner.state.value as? GemmaRunner.State.Failed)?.let { return Result.Unavailable(it.reason) }
        }
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
        val (reply, type, target) = parse(raw)
        VoiceLog.add(VoiceLog.Kind.AI, "Answered on the phone by Gemma in %.1f s".format(seconds))
        val turn = CompanionChatTurn(
            id = UUID.randomUUID().toString(),
            senderIsUser = false,
            message = reply.ifBlank { "Okay." },
            actionType = type,
            actionRecipient = if (type == CompanionActionType.SIM_CALL || type == CompanionActionType.WHATSAPP_CALL) target else "",
            actionPayload = if (type == CompanionActionType.OPEN_APP || type == CompanionActionType.YOUTUBE) target else ""
        )
        return Result.Answer(turn, seconds)
    }
}
