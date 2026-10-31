package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.example.actions.ActionExecutor
import com.example.actions.WhatsAppHelper
import com.example.bridge.MessageInbox
import com.example.ui.theme.LocalCharacterAccent
import com.example.ui.theme.MochiShapes
import com.example.ui.theme.MochiTokens
import com.example.ui.theme.MochiType

/**
 * Prominent disclosure shown BEFORE sending the user to the notification-access or accessibility
 * settings screen (required by Google Play for both). The text says exactly what is read, what is
 * done with it, and what is never done.
 */
object AccessDisclosure {
    const val READ_MESSAGES_TITLE = "Read messages"
    const val WHATSAPP_HELPER_TITLE = "WhatsApp helper"

    data class Copy(val title: String, val body: String)

    fun copyFor(kind: String): Copy? = when (kind) {
        ActionExecutor.NOTIFICATION_ACCESS, READ_MESSAGES_TITLE -> Copy(
            "Let MochiLife read your message notifications",
            "MochiLife uses notification access to read new messages from WhatsApp, Telegram, Messages, " +
                "Signal, Messenger and Instagram aloud when you ask, and to send the replies you dictate " +
                "through the app's own Reply button.\n\nMessages stay in memory on this phone only. They are " +
                "never saved, never uploaded and never sent to the AI. Notifications from other apps are ignored.\n\n" +
                "On the next screen, turn on notification access for MochiLife."
        )
        ActionExecutor.ACCESSIBILITY, WHATSAPP_HELPER_TITLE -> Copy(
            "Turn on the WhatsApp helper",
            "The WhatsApp helper uses Android's Accessibility service to press ONE button in WhatsApp " +
                "for you, only right after you ask and the 3-second countdown ends: Send (for a message you " +
                "dictated), Play (for the newest voice note) or Voice call.\n\nIt does nothing in any other app, " +
                "never reads, stores or uploads your chats, and makes no decisions by itself. You can turn it off " +
                "at any time in Settings → Accessibility.\n\nOn the next screen, open \"Installed apps\" → " +
                "MochiLife WhatsApp helper and turn it on."
        )
        else -> null
    }

    fun isSpecialAccess(kind: String) = copyFor(kind) != null

    fun isGranted(context: Context, kind: String): Boolean = when (kind) {
        ActionExecutor.NOTIFICATION_ACCESS, READ_MESSAGES_TITLE -> MessageInbox.hasAccess(context)
        ActionExecutor.ACCESSIBILITY, WHATSAPP_HELPER_TITLE -> WhatsAppHelper.isEnabled(context)
        else -> false
    }

    fun openSettings(context: Context, kind: String) {
        val primary = when (kind) {
            ActionExecutor.NOTIFICATION_ACCESS, READ_MESSAGES_TITLE ->
                if (Build.VERSION.SDK_INT >= 30) MessageInbox.accessSettingsIntent(context)
                else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            else -> WhatsAppHelper.settingsIntent()
        }
        try {
            context.startActivity(primary)
        } catch (_: Exception) {
            val fallback = if (kind == ActionExecutor.ACCESSIBILITY || kind == WHATSAPP_HELPER_TITLE) Settings.ACTION_ACCESSIBILITY_SETTINGS
            else Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            try { context.startActivity(Intent(fallback).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
        }
    }
}

@Composable
fun AccessDisclosureDialog(kind: String, onAgree: () -> Unit, onDismiss: () -> Unit) {
    val copy = AccessDisclosure.copyFor(kind) ?: return
    val accent = LocalCharacterAccent.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MochiTokens.sheet,
        titleContentColor = MochiTokens.textPrimary,
        textContentColor = MochiTokens.textSecondary,
        shape = MochiShapes.sheet,
        title = { Text(copy.title, style = MochiType.Title) },
        text = { Text(copy.body, style = MochiType.Body) },
        confirmButton = {
            TextButton(onClick = onAgree) {
                Text("Agree and continue", style = MochiType.Label, color = MochiTokens.accentInk(accent))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("No thanks", style = MochiType.Label, color = MochiTokens.textSecondary)
            }
        }
    )
}
