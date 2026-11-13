package com.example.ai.gemma

import android.content.Context
import kotlinx.coroutines.launch

/** Process-wide Gemma objects, shared by Gemma Lab and the on-phone engine (a download keeps going when the screen is left). */
object GemmaLab {
    @Volatile private var downloader: GemmaDownloader? = null
    @Volatile private var runner: GemmaRunner? = null
    fun downloader(context: Context) = downloader ?: synchronized(this) { downloader ?: GemmaDownloader(context).also { downloader = it } }
    fun runner(context: Context) = runner ?: synchronized(this) { runner ?: GemmaRunner(context).also { runner = it } }
}

/** The offline voice test, shared while the app runs (models stay loaded between tries). */
object OfflineVoiceLab {
    @Volatile private var test: com.example.ai.offline.OfflineVoiceTest? = null
    fun get(context: android.content.Context) = test ?: synchronized(this) { test ?: com.example.ai.offline.OfflineVoiceTest(context).also { test = it } }
}

/** Frees on-phone models when the Lab is left (unless phone mode needs them). Own scope: the Lab's is gone. */
object LabCleanup {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
    fun run(context: android.content.Context) {
        val app = context.applicationContext
        if (com.example.ai.VoiceCommandService.shared(app).phoneOnlyNow()) return
        com.example.ai.offline.PhoneListener.release()
        scope.launch { GemmaLab.runner(app).unload() }
    }
}
