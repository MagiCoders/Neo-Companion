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
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        AppTrail.add("MEMORY", "system asks to trim, level $level")
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) PhoneListener.release()
    }
}
