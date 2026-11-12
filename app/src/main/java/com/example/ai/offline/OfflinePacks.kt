package com.example.ai.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import android.util.Log
import com.example.ai.gemma.GemmaCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * The open speech models the offline voice test can download: a Whisper listener (MIT licence)
 * and Piper voices for English, Arabic and Persian, from the sherpa-onnx pages on Hugging Face.
 * Each file is checked against the SHA-256 the server publishes before it is used.
 */
data class OfflinePack(
    val id: String,
    val label: String,
    val repo: String,
    /** File name → approximate size in bytes (shown before downloading). */
    val files: List<Pair<String, Long>>,
    val kind: Kind
) {
    enum class Kind { LISTENER, VOICE }
    val approxBytes: Long get() = files.sumOf { it.second }
    fun url(file: String) = "https://huggingface.co/$repo/resolve/main/$file"
}

object OfflineCatalog {
    val LISTENER_BASE = OfflinePack(
        "whisper-base", "Whisper base (faster)", "csukuangfj/sherpa-onnx-whisper-base",
        listOf("base-encoder.int8.onnx" to 29_120_534L, "base-decoder.int8.onnx" to 130_672_026L, "base-tokens.txt" to 816_730L),
        OfflinePack.Kind.LISTENER
    )
    val LISTENER_SMALL = OfflinePack(
        "whisper-small", "Whisper small (more accurate)", "csukuangfj/sherpa-onnx-whisper-small",
        listOf("small-encoder.int8.onnx" to 112_442_483L, "small-decoder.int8.onnx" to 262_226_114L, "small-tokens.txt" to 816_730L),
        OfflinePack.Kind.LISTENER
    )
    val VOICE_EN = OfflinePack(
        "voice-en", "English voice (Amy)", "csukuangfj/vits-piper-en_US-amy-medium",
        listOf("en_US-amy-medium.onnx" to 63_201_425L, "tokens.txt" to 921L), OfflinePack.Kind.VOICE
    )
    val VOICE_AR = OfflinePack(
        "voice-ar", "Arabic voice (Kareem)", "csukuangfj/vits-piper-ar_JO-kareem-medium",
        listOf("ar_JO-kareem-medium.onnx" to 63_201_421L, "tokens.txt" to 954L), OfflinePack.Kind.VOICE
    )
    val VOICE_FA = OfflinePack(
        "voice-fa", "Persian voice (Gyro)", "csukuangfj/vits-piper-fa_IR-gyro-medium",
        listOf("fa_IR-gyro-medium.onnx" to 63_122_437L, "tokens.txt" to 940L), OfflinePack.Kind.VOICE
    )
    val LISTENERS = listOf(LISTENER_BASE, LISTENER_SMALL)
    val VOICES = listOf(VOICE_EN, VOICE_AR, VOICE_FA)
    val ALL = LISTENERS + VOICES

    /** The voice pack for a language code ("en", "ar", "fa"). */
    fun voiceFor(language: String): OfflinePack = when (language) { "ar" -> VOICE_AR; "fa" -> VOICE_FA; else -> VOICE_EN }
}

/** Downloads packs into the app's private storage, with resume, a space check and hash checks. */
class OfflinePackDownloader(context: Context) {

    sealed class State {
        object NotDownloaded : State()
        data class Downloading(val bytes: Long, val total: Long, val bytesPerSecond: Long) : State()
        object Verifying : State()
        object Ready : State()
        data class Failed(val reason: String) : State()
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val root = File(appContext.filesDir, "offline-speech").apply { mkdirs() }
    private val states = OfflineCatalog.ALL.associate { it.id to MutableStateFlow(initial(it)) }
    private val jobs = HashMap<String, Job>()

    fun dir(pack: OfflinePack) = File(root, pack.id)
    fun state(pack: OfflinePack): StateFlow<State> = states.getValue(pack.id).asStateFlow()
    fun isReady(pack: OfflinePack) = pack.files.all { File(dir(pack), it.first).exists() }

    private fun initial(pack: OfflinePack): State = if (isReady(pack)) State.Ready else State.NotDownloaded

    private fun unmetered(): Boolean {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    fun start(pack: OfflinePack, allowMobileData: Boolean) {
        if (jobs[pack.id]?.isActive == true) return
        val flow = states.getValue(pack.id)
        if (!allowMobileData && !unmetered()) {
            flow.value = State.Failed("Connect to Wi-Fi first, or allow mobile data.")
            return
        }
        val free = StatFs(root.absolutePath).availableBytes
        if (free < pack.approxBytes + 200_000_000L) {
            flow.value = State.Failed("Not enough free space: %.2f GB needed.".format((pack.approxBytes + 200_000_000L) / 1e9))
            return
        }
        jobs[pack.id] = scope.launch {
            try {
                download(pack, flow)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "Download failed: ${e.javaClass.simpleName}")
                flow.value = State.Failed("Download stopped (${e.javaClass.simpleName}). Tap Download to resume.")
            }
        }
    }

    fun delete(pack: OfflinePack) {
        jobs.remove(pack.id)?.cancel()
        dir(pack).deleteRecursively()
        states.getValue(pack.id).value = State.NotDownloaded
    }

    private suspend fun download(pack: OfflinePack, flow: MutableStateFlow<State>) {
        val d = dir(pack).apply { mkdirs() }
        val total = pack.approxBytes
        var doneBefore = 0L
        for ((name, approx) in pack.files) {
            val target = File(d, name)
            if (target.exists()) { doneBefore += target.length(); continue }
            // Exact size and published SHA-256 (from the redirect response).
            var size = approx
            var sha: String? = null
            (URL(pack.url(name)).openConnection() as HttpURLConnection).run {
                requestMethod = "HEAD"; instanceFollowRedirects = false; connectTimeout = 15_000; readTimeout = 15_000
                try {
                    connect()
                    if (responseCode == 404) { flow.value = State.Failed("$name was not found on the server."); return }
                    size = getHeaderField("X-Linked-Size")?.toLongOrNull() ?: getHeaderField("Content-Length")?.toLongOrNull() ?: approx
                    sha = GemmaCatalog.sha256FromEtag(getHeaderField("X-Linked-Etag"))
                } finally { disconnect() }
            }
            val part = File(d, "$name.part")
            val have = if (part.exists()) part.length() else 0L
            if (have < size) {
                val conn = (URL(pack.url(name)).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true; connectTimeout = 20_000; readTimeout = 30_000
                    if (have > 0) setRequestProperty("Range", "bytes=$have-")
                }
                try {
                    val code = conn.responseCode
                    val resumed = code == HttpURLConnection.HTTP_PARTIAL
                    if (code != HttpURLConnection.HTTP_OK && !resumed) { flow.value = State.Failed("The server answered $code for $name."); return }
                    var written = if (resumed) have else 0L
                    FileOutputStream(part, resumed).use { out ->
                        conn.inputStream.use { input ->
                            val buf = ByteArray(256 * 1024)
                            var last = 0L; var winStart = System.currentTimeMillis(); var winBytes = 0L; var rate = 0L
                            while (currentCoroutineContext().isActive) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                written += n; winBytes += n
                                val now = System.currentTimeMillis()
                                if (now - winStart >= 1_000) { rate = winBytes * 1000 / (now - winStart); winStart = now; winBytes = 0 }
                                if (now - last >= 250) { last = now; flow.value = State.Downloading(doneBefore + written, total, rate) }
                            }
                        }
                    }
                    if (written < size) { flow.value = State.Failed("Download paused. Tap Download to continue."); return }
                } finally { conn.disconnect() }
            }
            flow.value = State.Verifying
            if (part.length() != size) { part.delete(); flow.value = State.Failed("$name was incomplete. Please download again."); return }
            val expected = sha
            if (expected != null && sha256(part) != expected) {
                part.delete(); flow.value = State.Failed("$name did not match its published fingerprint and was deleted."); return
            }
            if (!part.renameTo(target)) { flow.value = State.Failed("Could not save $name."); return }
            doneBefore += size
        }
        flow.value = State.Ready
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        RandomAccessFile(f, "r").use { raf ->
            val buf = ByteArray(1024 * 1024)
            while (true) { val n = raf.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object { const val TAG = "OfflinePacks" }
}
