package com.example.actions

import android.content.Context
import com.google.firebase.FirebaseApp
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Finds the actual video to play, using the YouTube Data API (free quota, no Gemini quota) with
 * the Firebase project's own API key; restricted keys are honoured by sending this app's package
 * and signing certificate. The API must be enabled once for the project in Google Cloud.
 */
class YouTubeSearch(private val context: Context) {

    data class Video(val id: String, val title: String, val channel: String)

    sealed class Result {
        data class Found(val videos: List<Video>) : Result()
        /** The API isn't enabled / the key doesn't allow it: fall back to opening a search. */
        data class Unavailable(val reason: String) : Result()
    }

    /** Network call: run off the main thread. */
    fun search(query: String, max: Int = 5): Result {
        val key = try { FirebaseApp.getInstance().options.apiKey } catch (_: Exception) { null }
            ?: return Result.Unavailable("No API key")
        val url = URL(
            "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=$max" +
                "&q=${URLEncoder.encode(query, "UTF-8")}&key=${URLEncoder.encode(key, "UTF-8")}"
        )
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("X-Android-Package", context.packageName)
            conn.setRequestProperty("X-Android-Cert", com.example.ai.AiDiagnostics.signingSha1(context).substringBefore(",").replace(":", ""))
            val code = conn.responseCode
            if (code != 200) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val reason = try { JSONObject(err).getJSONObject("error").optString("message") } catch (_: Exception) { "" }
                return Result.Unavailable("HTTP $code ${reason.take(160)}")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            Result.Found(parse(body))
        } catch (e: Exception) {
            Result.Unavailable(e.javaClass.simpleName)
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        fun parse(json: String): List<Video> {
            val items = JSONObject(json).optJSONArray("items") ?: return emptyList()
            return (0 until items.length()).mapNotNull { i ->
                val item = items.getJSONObject(i)
                val id = item.optJSONObject("id")?.optString("videoId").orEmpty()
                val snippet = item.optJSONObject("snippet") ?: return@mapNotNull null
                if (id.isEmpty()) null else Video(id, unescape(snippet.optString("title")), unescape(snippet.optString("channelTitle")))
            }
        }

        /**
         * Which video to play: the top result when it clearly is what was asked for (most of the
         * asked words are in its title), otherwise null = ask the user to pick. Pure, tested.
         */
        fun pick(videos: List<Video>, query: String): Video? {
            val top = videos.firstOrNull() ?: return null
            if (videos.size == 1) return top
            return if (match(top.title + " " + top.channel, query) >= CLEAR_MATCH) top else null
        }

        /** Share of the asked words (2+ letters) found in the text. */
        fun match(text: String, query: String): Double {
            val q = words(query)
            if (q.isEmpty()) return 0.0
            val t = words(text).toSet()
            return q.count { it in t }.toDouble() / q.size
        }

        private fun words(s: String) = s.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 }

        private fun unescape(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")

        private const val CLEAR_MATCH = 0.75
    }
}
