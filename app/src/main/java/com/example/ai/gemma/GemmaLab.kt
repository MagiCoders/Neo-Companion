package com.example.ai.gemma

import android.content.Context

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
