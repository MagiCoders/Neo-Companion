package com.example.bridge

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** One incoming chat message, taken from the messaging app's own notification. */
data class IncomingMessage(
    val key: String,
    val app: ChatApp,
    val sender: String,
    val text: String,
    val timeMillis: Long,
    val isVoiceNote: Boolean,
    val isMedia: Boolean,
    /** The app's own "Reply" action from the notification (null if the app offers none). */
    internal val reply: Notification.Action?
) {
    val canReply: Boolean get() = reply != null
}

enum class ChatApp(val label: String, val packages: Set<String>) {
    WHATSAPP("WhatsApp", setOf("com.whatsapp", "com.whatsapp.w4b")),
    TELEGRAM("Telegram", setOf("org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram")),
    MESSAGES("Messages", setOf("com.google.android.apps.messaging", "com.samsung.android.messaging")),
    SIGNAL("Signal", setOf("org.thoughtcrime.securesms")),
    MESSENGER("Messenger", setOf("com.facebook.orca")),
    INSTAGRAM("Instagram", setOf("com.instagram.android"));

    companion object {
        fun from(pkg: String): ChatApp? = entries.firstOrNull { pkg in it.packages }
    }
}

/**
 * In-memory inbox fed by [MessageListenerService]. Nothing is written to disk or sent to the AI:
 * messages are only read aloud to the user and replied to through the app's own reply action.
 */
object MessageInbox {
    private const val MAX = 60
    private val _messages = MutableStateFlow<List<IncomingMessage>>(emptyList())
    val messages: StateFlow<List<IncomingMessage>> = _messages.asStateFlow()
    private var lastReadAt = 0L

    internal fun add(msgs: List<IncomingMessage>) {
        if (msgs.isEmpty()) return
        val existing = _messages.value
        val merged = (existing + msgs.filter { m -> existing.none { it.key == m.key && it.text == m.text && it.timeMillis == m.timeMillis } })
            .sortedBy { it.timeMillis }
            .takeLast(MAX)
        _messages.value = merged
    }

    fun clear() {
        _messages.value = emptyList()
        lastReadAt = 0L
    }

    fun unread(): List<IncomingMessage> = _messages.value.filter { it.timeMillis > lastReadAt }

    fun markRead() {
        lastReadAt = System.currentTimeMillis()
    }

    /** Messages matching a spoken filter: a person ("Mom"), an app ("WhatsApp") or empty for all unread. */
    fun find(filter: String, onlyUnread: Boolean): List<IncomingMessage> {
        val pool = if (onlyUnread) unread() else _messages.value
        val f = filter.trim().lowercase(Locale.ROOT)
        if (f.isEmpty() || f in setOf("all", "everyone", "new", "messages")) return pool
        val app = ChatApp.entries.firstOrNull { it.label.lowercase(Locale.ROOT) == f || f.contains(it.label.lowercase(Locale.ROOT)) }
        return if (app != null) pool.filter { it.app == app } else pool.filter { it.sender.lowercase(Locale.ROOT).contains(f) }
    }

    fun latestFrom(sender: String): IncomingMessage? =
        _messages.value.lastOrNull { it.canReply && it.sender.lowercase(Locale.ROOT).contains(sender.trim().lowercase(Locale.ROOT)) }

    /** A spoken summary of messages, newest last, grouped by person. */
    fun summarise(list: List<IncomingMessage>): String {
        if (list.isEmpty()) return "No new messages."
        val parts = list.takeLast(10).groupBy { it.sender to it.app }.map { (who, msgs) ->
            val (sender, app) = who
            val body = msgs.joinToString(". ") { m ->
                when {
                    m.isVoiceNote -> "a voice note"
                    m.isMedia -> "a photo or file"
                    else -> m.text
                }
            }
            "$sender on ${app.label}: $body"
        }
        val count = list.size
        return (if (count == 1) "One new message. " else "$count new messages. ") + parts.joinToString(". ")
    }

    /**
     * Replies through the messaging app's own notification reply (the same path watches and car
     * systems use). The message is really sent; WhatsApp does not open.
     */
    fun reply(context: Context, message: IncomingMessage, text: String): Boolean {
        val action = message.reply ?: return false
        val inputs = action.remoteInputs ?: return false
        return try {
            val intent = Intent()
            val results = Bundle().apply { inputs.forEach { putCharSequence(it.resultKey, text) } }
            RemoteInput.addResultsToIntent(inputs, intent, results)
            action.actionIntent.send(context, 0, intent)
            true
        } catch (e: PendingIntent.CanceledException) {
            Log.w("MessageInbox", "Reply action expired")
            false
        }
    }

    fun hasAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun accessSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
            ComponentName(context, MessageListenerService::class.java).flattenToString()
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** Receives chat notifications (only from the messaging apps listed in [ChatApp]). */
class MessageListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        activeNotifications?.forEach { handle(it) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handle(sbn)
    }

    private fun handle(sbn: StatusBarNotification) {
        val app = ChatApp.from(sbn.packageName) ?: return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val reply = n.actions?.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true }
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        val out = if (style != null && style.messages.isNotEmpty()) {
            val conversation = style.conversationTitle?.toString()
            style.messages.takeLast(5).mapNotNull { m ->
                val text = m.text?.toString()?.trim().orEmpty()
                if (text.isEmpty()) return@mapNotNull null
                val person = m.person?.name?.toString() ?: conversation ?: "Someone"
                val sender = if (conversation != null && conversation != person) "$person in $conversation" else person
                build(sbn.key, app, sender, text, m.timestamp, reply)
            }
        } else {
            val extras = n.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
            if (title.isEmpty() || text.isEmpty()) emptyList() else listOf(build(sbn.key, app, title, text, sbn.postTime, reply))
        }
        MessageInbox.add(out)
    }

    private fun build(key: String, app: ChatApp, sender: String, text: String, time: Long, reply: Notification.Action?): IncomingMessage {
        val lower = text.lowercase(Locale.ROOT)
        val voice = text.contains("🎤") || lower.contains("voice message") || lower.contains("audio message") || lower.startsWith("🎵")
        val media = !voice && (text.contains("📷") || text.contains("🎥") || text.contains("📄") || lower == "photo" || lower == "video" || lower.contains("sent a photo") || lower.contains("sent an attachment"))
        return IncomingMessage(key, app, sender.take(80), text.take(1_000), time, voice, media, reply)
    }
}
