package com.example.ai.gemma

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
 * Downloads a Gemma model into the app's private storage, with pause and resume (a half-finished
 * download continues where it stopped, even after the app was closed), a free-space check, Wi-Fi
 * only unless the user allows mobile data, and a SHA-256 check against the hash the server
 * publishes before the file is ever loaded. HTTPS only, through the app's network rules.
 */
class GemmaDownloader(context: Context) {

    sealed class State {
        object NotDownloaded : State()
        data class Downloading(val bytes: Long, val total: Long, val bytesPerSecond: Long) : State()
        data class Paused(val bytes: Long, val total: Long) : State()
        object Verifying : State()
        object Ready : State()
        data class Failed(val reason: String) : State()
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dir = File(appContext.filesDir, "gemma").apply { mkdirs() }
    private val states = GemmaCatalog.MODELS.associate { it.id to MutableStateFlow(initialState(it)) }
    private val jobs = HashMap<String, Job>()

    fun state(model: GemmaModel): StateFlow<State> = states.getValue(model.id).asStateFlow()

    fun file(model: GemmaModel) = File(dir, model.file)
    private fun partFile(model: GemmaModel) = File(dir, model.file + ".part")
    private fun hashFile(model: GemmaModel) = File(dir, model.file + ".sha256")

    private fun initialState(model: GemmaModel): State = when {
        file(model).exists() -> State.Ready
        partFile(model).exists() -> State.Paused(partFile(model).length(), model.approxBytes)
        else -> State.NotDownloaded
    }

    fun isOnUnmeteredNetwork(): Boolean {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    fun freeBytes(): Long = StatFs(dir.absolutePath).availableBytes

    fun start(model: GemmaModel, allowMobileData: Boolean) {
        if (jobs[model.id]?.isActive == true) return
        val flow = states.getValue(model.id)
        if (!allowMobileData && !isOnUnmeteredNetwork()) {
            flow.value = State.Failed("Connect to Wi-Fi first, or allow mobile data (this download is several GB).")
            return
        }
        jobs[model.id] = scope.launch {
            try {
                download(model, flow)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "Download failed: ${e.javaClass.simpleName}")
                flow.value = State.Failed("Download stopped (${e.javaClass.simpleName}). Tap Download to resume.")
            }
        }
    }

    fun pause(model: GemmaModel) {
        jobs.remove(model.id)?.cancel()
        val part = partFile(model)
        states.getValue(model.id).value = State.Paused(part.length(), model.approxBytes)
    }

    fun delete(model: GemmaModel) {
        jobs.remove(model.id)?.cancel()
        file(model).delete(); partFile(model).delete(); hashFile(model).delete()
        states.getValue(model.id).value = State.NotDownloaded
    }

    private suspend fun download(model: GemmaModel, flow: MutableStateFlow<State>) {
        // 1. Ask the server for the exact size and the SHA-256 (published on the redirect response).
        val head = (URL(model.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        val total: Long
        val sha: String?
        try {
            head.connect()
            if (head.responseCode == 401 || head.responseCode == 403) {
                flow.value = State.Failed("The model page asks for a sign-in (code ${head.responseCode}); it can't be downloaded from the app.")
                return
            }
            if (head.responseCode == 404) {
                flow.value = State.Failed("The model file was not found on the server (it may have moved).")
                return
            }
            total = head.getHeaderField("X-Linked-Size")?.toLongOrNull()
                ?: head.getHeaderField("Content-Length")?.toLongOrNull()?.takeIf { it > 1_000_000 }
                ?: model.approxBytes
            sha = GemmaCatalog.sha256FromEtag(head.getHeaderField("X-Linked-Etag"))
        } finally {
            head.disconnect()
        }
        sha?.let { hashFile(model).writeText(it) }

        // 2. Space check (the whole file plus a margin, minus what is already downloaded).
        val part = partFile(model)
        val have = if (part.exists()) part.length() else 0L
        val need = total - have + 300_000_000L
        if (freeBytes() < need) {
            flow.value = State.Failed("Not enough free space: %.1f GB needed, %.1f GB free.".format(need / 1e9, freeBytes() / 1e9))
            return
        }

        // 3. Download (resuming), following the redirect to the file host.
        if (have < total) {
            val conn = (URL(model.url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 20_000
                readTimeout = 30_000
                if (have > 0) setRequestProperty("Range", "bytes=$have-")
            }
            try {
                val code = conn.responseCode
                val resumed = code == HttpURLConnection.HTTP_PARTIAL
                if (code != HttpURLConnection.HTTP_OK && !resumed) {
                    flow.value = State.Failed("The server answered with code $code.")
                    return
                }
                var written = if (resumed) have else 0L
                FileOutputStream(part, resumed).use { out ->
                    conn.inputStream.use { input ->
                        val buf = ByteArray(256 * 1024)
                        var lastReport = 0L
                        var windowStart = System.currentTimeMillis()
                        var windowBytes = 0L
                        var rate = 0L
                        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            written += n
                            windowBytes += n
                            val now = System.currentTimeMillis()
                            if (now - windowStart >= 1_000) {
                                rate = windowBytes * 1000 / (now - windowStart)
                                windowStart = now; windowBytes = 0
                            }
                            if (now - lastReport >= 250) {
                                lastReport = now
                                flow.value = State.Downloading(written, total, rate)
                            }
                        }
                    }
                }
                if (written < total) {
                    flow.value = State.Paused(written, total)
                    return
                }
            } finally {
                conn.disconnect()
            }
        }

        // 4. Check the file before it is ever loaded.
        flow.value = State.Verifying
        if (part.length() != total) {
            part.delete()
            flow.value = State.Failed("The download was incomplete or damaged. Please download again.")
            return
        }
        val expected = sha ?: hashFile(model).takeIf { it.exists() }?.readText()
        if (expected != null) {
            val actual = sha256(part)
            if (actual != expected) {
                part.delete()
                flow.value = State.Failed("The file did not match its published fingerprint and was deleted. Please download again.")
                return
            }
        }
        if (!part.renameTo(file(model))) {
            flow.value = State.Failed("Could not save the model file.")
            return
        }
        flow.value = State.Ready
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        RandomAccessFile(f, "r").use { raf ->
            val buf = ByteArray(1024 * 1024)
            while (true) {
                val n = raf.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object { const val TAG = "GemmaDownloader" }
}
