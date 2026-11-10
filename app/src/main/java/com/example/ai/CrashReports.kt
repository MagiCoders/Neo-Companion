package com.example.ai

import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Crash and error reports (Firebase Crashlytics). Reports hold the error, the phone model and the
 * app version, never what the user said or any contact, message or account details. The user can
 * switch them off in Settings; the choice is stored by Crashlytics itself.
 */
object CrashReports {
    private fun crashlytics(): FirebaseCrashlytics? = try { FirebaseCrashlytics.getInstance() } catch (_: Throwable) { null }

    fun setEnabled(enabled: Boolean) {
        crashlytics()?.isCrashlyticsCollectionEnabled = enabled
    }

    /** A problem that did not crash the app (for example a stuck microphone the watchdog reset). */
    fun nonFatal(what: String) {
        crashlytics()?.recordException(IllegalStateException(what.take(200)))
    }
}
