package com.example.ai.gemma

import android.content.Context

/** Process-wide Gemma objects, shared by Gemma Lab and the on-phone engine (a download keeps going when the screen is left). */
object GemmaLab {
    @Volatile private var downloader: GemmaDownloader? = null
    @Volatile private var runner: GemmaRunner? = null
    fun downloader(context: Context) = downloader ?: synchronized(this) { downloader ?: GemmaDownloader(context).also { downloader = it } }
    fun runner(context: Context) = runner ?: synchronized(this) { runner ?: GemmaRunner(context).also { runner = it } }
}
