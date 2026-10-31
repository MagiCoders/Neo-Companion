package com.example.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

/**
 * Turns AI failures into an honest, specific explanation (instead of always blaming the internet)
 * and keeps the last one for Settings → Diagnostics. Only the error type and reason are kept,
 * never the user's words.
 */
object AiDiagnostics {
    enum class Reason { APP_CHECK, API_DISABLED, QUOTA, MODEL, NETWORK, TIMEOUT, UNKNOWN }

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
            "401" in text || "403" in text || "permission_denied" in text || "unauthenticated" in text -> Reason.APP_CHECK
            "service_disabled" in text || "has not been used" in text || "is disabled" in text || "api not enabled" in text -> Reason.API_DISABLED
            "429" in text || "quota" in text || "resource_exhausted" in text -> Reason.QUOTA
            "404" in text || "not found" in text || "not supported" in text -> Reason.MODEL
            chain.any { it is IOException } -> Reason.NETWORK
            else -> Reason.UNKNOWN
        }
    }

    fun userMessage(reason: Reason): String = when (reason) {
        Reason.APP_CHECK -> "Firebase didn't accept this copy of the app yet (App Check). Settings → Diagnostics shows how to fix it."
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
        val detail = "${root.javaClass.simpleName}: ${root.message.orEmpty().take(160)}"
        _last.value = Failure(reason, detail, System.currentTimeMillis())
        return userMessage(reason)
    }
}
