package com.example.diag

import android.app.ActivityManager

/** Readable names for Android's process importance (how Android ranks the app right now). */
object Importance {
    fun name(importance: Int): String = when (importance) {
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "on screen"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "listening service"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "visible"
        else -> "background ($importance)"
    }

    /** This app's current importance, or "unknown". */
    fun mine(): String = try {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        name(info.importance)
    } catch (_: Throwable) { "unknown" }
}
