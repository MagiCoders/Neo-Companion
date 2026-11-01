package com.example.actions

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import java.util.Locale

/**
 * Opens real apps by name ("open YouTube", "افتح واتساب"). Installed apps are opened directly; a
 * known app that isn't installed is shown in Google Play so the user can tap Install (Google
 * Play doesn't allow apps to install other apps by themselves).
 */
class AppLauncher(private val context: Context) {

    /** A well-known app: its package and the names people use for it (any language). */
    data class KnownApp(val pkg: String, val label: String, val names: List<String>)

    sealed class Result {
        data class Opened(val label: String) : Result()
        /** Not installed: Google Play was opened on the app's page (or a search for it). */
        data class InPlayStore(val label: String) : Result()
        data class Failed(val reason: String) : Result()
    }

    fun open(spokenName: String): Result {
        val name = spokenName.trim()
        if (name.isEmpty()) return Result.Failed("Which app should I open?")
        val known = findKnown(name)
        if (known != null && isInstalled(known.pkg)) {
            return launchPackage(known.pkg, known.label)
        }
        findInstalledByLabel(name)?.let { (pkg, label) -> return launchPackage(pkg, label) }
        return openInPlayStore(known?.pkg, known?.label ?: name)
    }

    /** Opens the app's page in Google Play (or a Play search when the package is unknown). */
    fun openInPlayStore(pkg: String?, label: String): Result {
        val market = if (pkg != null) "market://details?id=$pkg" else "market://search?q=${Uri.encode(label)}&c=apps"
        val web = if (pkg != null) "https://play.google.com/store/apps/details?id=$pkg"
        else "https://play.google.com/store/search?q=${Uri.encode(label)}&c=apps"
        return when {
            start(Intent(Intent.ACTION_VIEW, Uri.parse(market)).setPackage(PLAY_STORE)) -> Result.InPlayStore(label)
            start(Intent(Intent.ACTION_VIEW, Uri.parse(web))) -> Result.InPlayStore(label)
            else -> Result.Failed("I couldn't open Google Play.")
        }
    }

    /** YouTube search inside the YouTube app; Google Play if it isn't installed. */
    fun youtube(query: String): Result {
        if (!isInstalled(YOUTUBE)) return openInPlayStore(YOUTUBE, "YouTube")
        val q = query.trim()
        if (q.isEmpty()) return launchPackage(YOUTUBE, "YouTube")
        val search = Intent(Intent.ACTION_SEARCH).setPackage(YOUTUBE).putExtra(SearchManager.QUERY, q)
        if (start(search)) return Result.Opened("YouTube")
        val results = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(q)}")).setPackage(YOUTUBE)
        return if (start(results)) Result.Opened("YouTube") else launchPackage(YOUTUBE, "YouTube")
    }

    /** Plays one video in the YouTube app (it starts playing on its own). */
    fun playVideo(videoId: String): Result {
        if (!isInstalled(YOUTUBE)) return openInPlayStore(YOUTUBE, "YouTube")
        val watch = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId")).setPackage(YOUTUBE)
        return if (start(watch) || start(Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId")))) Result.Opened("YouTube")
        else Result.Failed("I couldn't open the video.")
    }

    /** An https link opened inside the given app when it is installed (app links), else null. */
    fun openLinkInApp(pkg: String, url: String): Boolean =
        isInstalled(pkg) && start(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg))

    fun isInstalled(pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0); true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun launchPackage(pkg: String, label: String): Result {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return Result.Failed("I couldn't open $label.")
        return if (start(intent)) Result.Opened(label) else Result.Failed("I couldn't open $label.")
    }

    /** Launcher apps whose name matches what was said (needs the launcher <queries> entry). */
    private fun findInstalledByLabel(name: String): Pair<String, String>? {
        val wanted = normalise(name)
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = try {
            context.packageManager.queryIntentActivities(launcher, 0)
        } catch (_: Exception) {
            emptyList()
        }
        val labelled = apps.map { it.activityInfo.packageName to it.loadLabel(context.packageManager).toString() }
            .filter { it.first != context.packageName }
        return labelled.firstOrNull { normalise(it.second) == wanted }
            ?: labelled.firstOrNull { normalise(it.second).startsWith(wanted) || wanted.startsWith(normalise(it.second)) }
            ?: labelled.firstOrNull { wanted.length >= 4 && normalise(it.second).contains(wanted) }
    }

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    companion object {
        const val PLAY_STORE = "com.android.vending"
        const val YOUTUBE = "com.google.android.youtube"

        val KNOWN = listOf(
            KnownApp(YOUTUBE, "YouTube", listOf("youtube", "you tube", "يوتيوب", "یوتیوب")),
            KnownApp("com.whatsapp", "WhatsApp", listOf("whatsapp", "whats app", "واتساب", "واتس اب", "واتس‌اپ")),
            KnownApp("org.telegram.messenger", "Telegram", listOf("telegram", "تيليجرام", "تلگرام")),
            KnownApp("com.instagram.android", "Instagram", listOf("instagram", "insta", "انستغرام", "انستقرام", "اینستاگرام")),
            KnownApp("com.zhiliaoapp.musically", "TikTok", listOf("tiktok", "tik tok", "تيك توك", "تیک تاک")),
            KnownApp("com.facebook.katana", "Facebook", listOf("facebook", "فيسبوك", "فیسبوک")),
            KnownApp("com.facebook.orca", "Messenger", listOf("messenger", "ماسنجر")),
            KnownApp("com.twitter.android", "X", listOf("twitter", "x", "تويتر", "توییتر")),
            KnownApp("com.snapchat.android", "Snapchat", listOf("snapchat", "snap", "سناب", "سناب شات", "اسنپ چت")),
            KnownApp("com.spotify.music", "Spotify", listOf("spotify", "سبوتيفاي", "اسپاتیفای")),
            KnownApp("com.netflix.mediaclient", "Netflix", listOf("netflix", "نتفليكس", "نتفلیکس")),
            KnownApp("com.google.android.gm", "Gmail", listOf("gmail", "email", "جيميل", "جیمیل")),
            KnownApp("com.google.android.apps.maps", "Google Maps", listOf("maps", "google maps", "خرائط", "نقشه")),
            KnownApp("com.android.chrome", "Chrome", listOf("chrome", "كروم", "کروم")),
            KnownApp("com.reddit.frontpage", "Reddit", listOf("reddit", "ريديت")),
            KnownApp("com.linkedin.android", "LinkedIn", listOf("linkedin", "لينكدإن", "لینکدین")),
            KnownApp("com.google.android.apps.photos", "Google Photos", listOf("photos", "gallery", "صور", "گالری")),
            KnownApp("com.shazam.android", "Shazam", listOf("shazam", "شازام")),
            KnownApp(PLAY_STORE, "Google Play", listOf("play store", "google play", "متجر", "پلی استور"))
        )

        fun findKnown(name: String): KnownApp? {
            val n = normalise(name)
            return KNOWN.firstOrNull { app -> app.names.any { normalise(it) == n } }
                ?: KNOWN.firstOrNull { app -> app.names.any { alias -> normalise(alias).length >= 3 && n.contains(normalise(alias)) } }
        }

        fun normalise(s: String): String =
            s.lowercase(Locale.ROOT).replace(Regex("[\\s\\-_.'’]+"), " ").trim()
                .removePrefix("the ").removeSuffix(" app").trim()
    }
}
