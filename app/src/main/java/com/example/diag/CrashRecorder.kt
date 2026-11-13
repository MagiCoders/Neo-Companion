package com.example.diag

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Writes the last app error to the phone before the process dies, so Diagnostics can show it. */
object CrashRecorder {
    private fun file(context: Context) = File(context.filesDir, "diag/last_crash.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val f = file(app).also { it.parentFile?.mkdirs() }
                val info = runCatching { app.packageManager.getPackageInfo(app.packageName, 0) }.getOrNull()
                val text = buildString {
                    appendLine("Time: " + SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()))
                    appendLine("App: ${info?.versionName}")
                    appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Thread: ${t.name}")
                    appendLine(Log.getStackTraceString(e).take(6000))
                    appendLine("Trail:")
                    AppTrail.tail(40).forEach { appendLine(it) }
                }
                FileOutputStream(f).use { it.write(text.toByteArray()); it.fd.sync() }
            } catch (_: Throwable) {
                // Nothing more can be done while the process is dying.
            }
            previous?.uncaughtException(t, e)
        }
    }

    fun lastCrashText(context: Context): String? = file(context).takeIf { it.exists() }?.readText()

    fun clear(context: Context) { file(context).delete() }
}
