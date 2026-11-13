package com.example

import android.app.Application
import android.content.ComponentCallbacks2
import com.example.ai.offline.PhoneListener
import com.example.diag.AppTrail
import com.example.diag.CrashRecorder

class MochiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashRecorder.install(this)
        AppTrail.init(this)
        // One-time clean-up of the test voices that 3.1 and 3.2 may have left on the phone.
        Thread {
            runCatching {
                val root = java.io.File(filesDir, "offline-speech")
                listOf("espeak-ng-data", "voice-en", "voice-ar", "voice-fa").forEach { java.io.File(root, it).deleteRecursively() }
            }
        }.start()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        AppTrail.add("MEMORY", "system asks to trim, level $level")
        // Levels 20 (UI hidden) and 40 (background) arrive every time the app leaves the screen: keep the model then.
        if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL || level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) PhoneListener.release()
    }
}
