package com.example.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

/**
 * Turns AI failures into an honest, specific explanation (instead of always blaming the internet)
 * and keeps the last one for Settings → Diagnostics. Only the error type and reason are kept,
 * never the user's words.
 */
object AiDiagnostics {
    enum class Reason { APP_CHECK, API_KEY_BLOCKED, ACCESS_DENIED, API_DISABLED, QUOTA, MODEL, NETWORK, TIMEOUT, UNKNOWN }

    data class Failure(val reason: Reason, val detail: String, val atMillis: Long)

    private val _last = MutableStateFlow<Failure?>(null)
    val last: StateFlow<Failure?> = _last.asStateFlow()

    fun classify(e: Throwable): Reason {
        val chain = generateSequence(e) { it.cause }.take(6).toList()
        val text = chain.joinToString(" | ") { "${it.javaClass.simpleName} ${it.message.orEmpty()}" }.lowercase()
        return when {
            chain.any { it is UnknownHostException } -> Reason.NETWORK
            chain.any { it is TimeoutException || it is kotlinx.coroutines.TimeoutCancellationException } || "timeout" in text -> Reason.TIMEOUT
            "app check" in text || "appcheck" in text || "app_check" in text || "attestation" in text -> Reason.APP_CHECK
            "service_disabled" in text || "has not been used" in text || "is disabled" in text || "api not enabled" in text -> Reason.API_DISABLED
            "api key" in text || "api_key" in text || "client application" in text || "are blocked" in text ||
                "android client" in text || "referer" in text -> Reason.API_KEY_BLOCKED
            "401" in text || "403" in text || "permission_denied" in text || "unauthenticated" in text -> Reason.ACCESS_DENIED
            "429" in text || "quota" in text || "resource_exhausted" in text -> Reason.QUOTA
            "404" in text || "not found" in text || "not supported" in text -> Reason.MODEL
            chain.any { it is IOException } -> Reason.NETWORK
            else -> Reason.UNKNOWN
        }
    }

    fun userMessage(reason: Reason): String = when (reason) {
        Reason.APP_CHECK -> "Firebase App Check rejected this copy of the app. Settings → Diagnostics → Run check shows the details."
        Reason.API_KEY_BLOCKED -> "The project's API key doesn't allow this copy of the app. Settings → Diagnostics → Run check shows the details."
        Reason.ACCESS_DENIED -> "Firebase refused the AI request. Settings → Diagnostics → Run check shows the exact reason."
        Reason.API_DISABLED -> "The Firebase AI service isn't switched on for this project yet."
        Reason.QUOTA -> "The AI is busy or today's AI quota is used up. Please try again in a little while."
        Reason.MODEL -> "The AI model isn't available right now. Please try again later."
        Reason.NETWORK -> "I can't reach the internet right now. Check your connection and try again."
        Reason.TIMEOUT -> "The AI took too long to answer. Please try again."
        Reason.UNKNOWN -> "I can't reach my brain right now. Please try again."
    }

    /** Records the failure and returns the message to show or say. */
    fun report(e: Throwable): String {
        val reason = classify(e)
        val root = generateSequence(e) { it.cause }.take(6).last()
        val detail = describe(e)
        _last.value = Failure(reason, detail, System.currentTimeMillis())
        return userMessage(reason)
    }

    /** Every message in the cause chain (no user content is ever part of these). */
    fun describe(e: Throwable): String =
        generateSequence(e) { it.cause }.take(6)
            .map { "${it.javaClass.simpleName}: ${it.message.orEmpty().take(300)}" }
            .distinct()
            .joinToString("\n← ")

    data class Step(val name: String, val ok: Boolean, val detail: String)

    /**
     * Settings → Diagnostics → Run check: tests each link of the AI chain separately so the
     * failing setting is obvious (signing key, sign-in, App Check token, the AI call itself).
     */
    suspend fun runSelfTest(context: android.content.Context): List<Step> {
        val steps = mutableListOf<Step>()
        steps += Step("This app", true, "${context.packageName}\nversion ${com.example.BuildConfig.VERSION_NAME} (${com.example.BuildConfig.VERSION_CODE}), " +
            (if (com.example.BuildConfig.DEBUG) "test build" else "release build") + "\nSHA-1 ${signingSha1(context)}")
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        steps += Step("Signed in", user != null, if (user != null) "yes" else "not signed in")
        steps += try {
            val token = kotlinx.coroutines.withTimeout(20_000L) {
                com.google.firebase.appcheck.FirebaseAppCheck.getInstance().getAppCheckToken(true).await()
            }
            val minutes = (token.expireTimeMillis - System.currentTimeMillis()) / 60_000
            Step("App Check token", true, "received, valid for about $minutes min")
        } catch (e: Throwable) {
            Step("App Check token", false, describe(e))
        }
        steps += try {
            val reply = kotlinx.coroutines.withTimeout(30_000L) {
                Firebase.ai(backend = GenerativeBackend.googleAI())
                    .generativeModel(AiModels.CHAT)
                    .generateContent("Reply with the single word OK.").text
            }
            Step("AI request (${AiModels.CHAT})", true, "answered: ${reply.orEmpty().trim().take(40)}")
        } catch (e: Throwable) {
            report(e)
            Step("AI request (${AiModels.CHAT})", false, describe(e))
        }
        return steps
    }

    @Suppress("DEPRECATION")
    private fun signingSha1(context: android.content.Context): String = try {
        val pm = context.packageManager
        val certs: List<ByteArray> = if (android.os.Build.VERSION.SDK_INT >= 28) {
            val info = pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
            info.signingInfo?.apkContentsSigners?.map { it.toByteArray() }.orEmpty()
        } else {
            pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNATURES).signatures?.map { it.toByteArray() }.orEmpty()
        }
        certs.joinToString(", ") { cert ->
            java.security.MessageDigest.getInstance("SHA-1").digest(cert).joinToString(":") { "%02X".format(it) }
        }.ifEmpty { "unknown" }
    } catch (_: Exception) {
        "unknown"
    }
}
