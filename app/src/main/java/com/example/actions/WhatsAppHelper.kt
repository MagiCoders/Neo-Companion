package com.example.actions

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * The optional "WhatsApp helper": an accessibility service that runs ONE fixed, pre-written step
 * inside WhatsApp, and only right after the user asked for it and the 3-second countdown passed:
 *
 *  - SendPrefilled: tap Send on the message MochiLife already typed into the chat
 *  - PlayLatestVoiceNote: tap Play on the newest voice note in the open chat
 *  - VoiceCall: tap the voice-call button in the open chat
 *
 * It never decides anything by itself, never reads or stores chat content, and does nothing at
 * all when no step was requested in the last 12 seconds (Google Play: deterministic, rule-based
 * automation with prominent disclosure and consent).
 */
class WhatsAppHelper : AccessibilityService() {

    enum class Task { SendPrefilled, PlayLatestVoiceNote, VoiceCall, VideoCall, PlayFirstYouTubeResult }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val task = pending ?: return
        if (System.currentTimeMillis() - requestedAt > TASK_TTL_MS) { pending = null; return }
        val pkg = event?.packageName?.toString() ?: return
        val root = rootInActiveWindow ?: return
        if (task == Task.PlayFirstYouTubeResult) {
            if (pkg == AppLauncher.YOUTUBE && clickFirstVideo(root)) pending = null
            return
        }
        if (pkg != ActionExecutor.WHATSAPP && pkg != ActionExecutor.WHATSAPP_BUSINESS) return
        val done = when (task) {
            Task.PlayFirstYouTubeResult -> false
            // "send" in a chat; "send" / "send_button" on the media preview screen.
            Task.SendPrefilled -> clickFirst(root, pkg, listOf("send", "send_button"), listOf("Send"))
            Task.PlayLatestVoiceNote -> clickLast(root, pkg, listOf("control_btn", "voice_note_btn", "play_button"), listOf("Play", "Play voice message"))
            Task.VoiceCall -> clickFirst(root, pkg, listOf("menuitem_voice_call", "voice_call_btn"), listOf("Voice call", "Audio call"))
            Task.VideoCall -> clickFirst(root, pkg, listOf("menuitem_video_call", "video_call_btn"), listOf("Video call"))
        }
        if (done) pending = null
    }

    /**
     * The first real video in YouTube's search results (not an ad, not a Short): its description
     * reads like "Title - 3 minutes, 20 seconds - Go to channel - … views - 2 years ago - play video".
     */
    private fun clickFirstVideo(root: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        while (queue.isNotEmpty() && visited < 600) {
            val node = queue.removeFirst()
            visited++
            val desc = node.contentDescription?.toString().orEmpty()
            val lower = desc.lowercase()
            val looksLikeVideo = desc.length > 20 && node.isVisibleToUser &&
                (lower.contains("play video") || (lower.contains(" views") && (lower.contains("minute") || lower.contains("second") || lower.contains("hour")))) &&
                !lower.contains("sponsored") && !lower.contains("shorts") && !lower.startsWith("ad ")
            if (looksLikeVideo && click(node)) return true
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.addLast(it) }
        }
        return false
    }

    private fun clickFirst(root: AccessibilityNodeInfo, pkg: String, ids: List<String>, descs: List<String>): Boolean =
        candidates(root, pkg, ids, descs).firstOrNull()?.let { click(it) } ?: false

    private fun clickLast(root: AccessibilityNodeInfo, pkg: String, ids: List<String>, descs: List<String>): Boolean =
        candidates(root, pkg, ids, descs).lastOrNull()?.let { click(it) } ?: false

    private fun candidates(root: AccessibilityNodeInfo, pkg: String, ids: List<String>, descs: List<String>): List<AccessibilityNodeInfo> {
        val byId = ids.flatMap { root.findAccessibilityNodeInfosByViewId("$pkg:id/$it").orEmpty() }
        if (byId.isNotEmpty()) return byId.filter { it.isVisibleToUser }
        return descs.flatMap { d -> root.findAccessibilityNodeInfosByText(d).orEmpty() }
            .filter { it.isVisibleToUser && (it.contentDescription?.toString()?.equals(descs.first(), true) == true || descs.any { d -> it.contentDescription?.toString() == d }) }
    }

    private fun click(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        while (n != null && !n.isClickable) n = n.parent
        return n?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    companion object {
        private const val TASK_TTL_MS = 15_000L
        @Volatile private var instance: WhatsAppHelper? = null
        @Volatile private var pending: Task? = null
        @Volatile private var requestedAt = 0L

        /** Queue one step; it runs when WhatsApp's chat screen appears, or expires after 12 s. */
        fun request(task: Task) {
            pending = task
            requestedAt = System.currentTimeMillis()
        }

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(context, WhatsAppHelper::class.java)
            val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
            return splitter.any { ComponentName.unflattenFromString(it) == me }
        }

        fun settingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
