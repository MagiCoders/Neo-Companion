package com.example.actions

import android.Manifest
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.example.data.CompanionActionType
import com.example.data.CompanionChatTurn

/** What the companion should say/do after trying an action. Never claims success it didn't get. */
sealed interface ActionOutcome {
    val spoken: String

    data class Done(override val spoken: String) : ActionOutcome
    data class NeedsContactChoice(val query: String, val candidates: List<PhoneContact>) : ActionOutcome {
        override val spoken: String =
            "I found ${candidates.size} contacts for \"$query\": " +
                candidates.joinToString(", ") { it.displayName } + ". Which one?"
    }
    data class NeedsPermission(val permission: String, override val spoken: String) : ActionOutcome
    data class Failed(override val spoken: String) : ActionOutcome
}

/** An action that has been checked and is ready to run after the countdown. */
data class PreparedAction(
    val turn: CompanionChatTurn,
    val contact: PhoneContact? = null,
    val number: String? = null
) {
    /** What the countdown card shows, e.g. "Call Mom · +44 7700 900123". */
    val title: String
        get() {
            val who = contact?.displayName ?: number ?: turn.actionRecipient
            return when (turn.actionType) {
                CompanionActionType.SIM_CALL -> "Call $who"
                CompanionActionType.WHATSAPP_CALL, CompanionActionType.SOCIAL_CALL -> "WhatsApp call · $who"
                CompanionActionType.WHATSAPP -> "WhatsApp message · $who"
                CompanionActionType.WHATSAPP_READ_CHAT -> "Open WhatsApp chat · $who"
                CompanionActionType.TELEGRAM -> "Telegram · $who"
                CompanionActionType.YOUTUBE -> "YouTube · ${turn.actionPayload}"
                CompanionActionType.BROWSER_SEARCH -> "Search · ${turn.actionPayload}"
                CompanionActionType.SOCIAL_SEARCH -> "${turn.actionRecipient.ifBlank { "News" }} · ${turn.actionPayload}"
                CompanionActionType.OPEN_MEDIA -> "Play · ${turn.actionPayload}"
                else -> turn.actionType.label
            }
        }
}

sealed interface Preparation {
    data class Ready(val action: PreparedAction) : Preparation
    data class Blocked(val outcome: ActionOutcome) : Preparation
}

/**
 * Turns a companion intent into a real Android action.
 * Step 1 [prepare]: resolve the contact/number and check permissions — before any countdown starts.
 * Step 2 [execute]: launch it, and report honestly what happened.
 */
class ActionExecutor(private val context: Context) {

    private val contacts = ContactResolver(context)

    fun prepare(turn: CompanionChatTurn): Preparation {
        val needsPerson = turn.actionType in PERSON_ACTIONS
        if (!needsPerson) {
            return if (turn.actionType in QUERY_ACTIONS && turn.actionPayload.isBlank()) {
                Preparation.Blocked(ActionOutcome.Failed("What should I search for?"))
            } else {
                Preparation.Ready(PreparedAction(turn))
            }
        }
        if (turn.actionRecipient.isBlank()) {
            return Preparation.Blocked(ActionOutcome.Failed("Who should I contact?"))
        }
        return when (val found = contacts.resolve(turn.actionRecipient)) {
            is ContactLookup.Found -> Preparation.Ready(PreparedAction(turn, found.contact, found.contact.primaryPhone))
            is ContactLookup.RawNumber -> Preparation.Ready(PreparedAction(turn, number = found.number))
            is ContactLookup.Ambiguous -> Preparation.Blocked(ActionOutcome.NeedsContactChoice(found.query, found.candidates))
            is ContactLookup.NotFound -> Preparation.Blocked(
                ActionOutcome.Failed("I couldn't find \"${found.query}\" in your contacts. Say the name as it's saved, or the number.")
            )
            ContactLookup.NoPermission -> Preparation.Blocked(
                ActionOutcome.NeedsPermission(
                    Manifest.permission.READ_CONTACTS,
                    "I need access to your contacts to find ${turn.actionRecipient}."
                )
            )
        }
    }

    fun execute(action: PreparedAction): ActionOutcome {
        val turn = action.turn
        return when (turn.actionType) {
            CompanionActionType.SIM_CALL -> simCall(action)
            CompanionActionType.WHATSAPP_CALL, CompanionActionType.SOCIAL_CALL -> whatsAppCall(action)
            CompanionActionType.WHATSAPP -> whatsAppMessage(action)
            CompanionActionType.WHATSAPP_READ_CHAT -> whatsAppOpenChat(action)
            CompanionActionType.WHATSAPP_CONTACTS -> openApp(WHATSAPP, "WhatsApp")
            CompanionActionType.TELEGRAM -> telegram(action)
            CompanionActionType.YOUTUBE -> youtube(turn.actionPayload)
            CompanionActionType.BROWSER_SEARCH -> webSearch(turn.actionPayload)
            CompanionActionType.SOCIAL_SEARCH -> socialSearch(turn.actionRecipient, turn.actionPayload)
            CompanionActionType.OPEN_MEDIA -> playMedia(turn.actionPayload)
            CompanionActionType.WHATS_PLAYING -> whatsPlaying()
            CompanionActionType.NAVIGATE_ROUTE,
            CompanionActionType.SPEAK_LOUD,
            CompanionActionType.NONE -> ActionOutcome.Done("")
        }
    }

    // --- Calls -----------------------------------------------------------------------------------

    private fun simCall(action: PreparedAction): ActionOutcome {
        val number = action.number ?: return ActionOutcome.Failed("${action.contact?.displayName ?: "That contact"} has no phone number.")
        val canCallDirectly = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        val intent = Intent(if (canCallDirectly) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
        return launch(intent, "I couldn't open the phone app.") {
            if (canCallDirectly) "Calling ${who(action)}." else "The dialer is ready for ${who(action)} — tap Call."
        }
    }

    private fun whatsAppCall(action: PreparedAction): ActionOutcome {
        if (!isInstalled(WHATSAPP)) return ActionOutcome.Failed("WhatsApp isn't installed on this phone.")
        val dataId = action.contact?.whatsAppCallDataId
        if (dataId != null) {
            // The same contact-card entry WhatsApp adds to your address book ("Voice call +44…").
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contacts.dataUri(dataId), ContactResolver.WHATSAPP_VOIP_CALL_MIME)
                setPackage(WHATSAPP)
            }
            return launch(intent, "WhatsApp didn't accept the call.") { "Starting a WhatsApp call with ${who(action)}." }
        }
        // No WhatsApp call entry for this contact: open the chat so one tap starts the call.
        val number = action.number ?: return ActionOutcome.Failed("${who(action)} has no phone number.")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${PhoneNumbers.forWhatsApp(number)}")).setPackage(WHATSAPP)
        return launch(intent, "I couldn't open WhatsApp.") {
            "I opened ${who(action)}'s chat — tap the call button. (Their WhatsApp call shortcut isn't in your contacts yet.)"
        }
    }

    // --- Messages --------------------------------------------------------------------------------

    private fun whatsAppMessage(action: PreparedAction): ActionOutcome {
        val pkg = when {
            isInstalled(WHATSAPP) -> WHATSAPP
            isInstalled(WHATSAPP_BUSINESS) -> WHATSAPP_BUSINESS
            else -> return ActionOutcome.Failed("WhatsApp isn't installed on this phone.")
        }
        val number = action.number ?: return ActionOutcome.Failed("${who(action)} has no phone number.")
        val text = action.turn.actionPayload.trim()
        val uri = Uri.parse("https://wa.me/${PhoneNumbers.forWhatsApp(number)}" + if (text.isNotEmpty()) "?text=${Uri.encode(text)}" else "")
        return launch(Intent(Intent.ACTION_VIEW, uri).setPackage(pkg), "I couldn't open WhatsApp.") {
            if (text.isNotEmpty()) "Your message to ${who(action)} is ready in WhatsApp." else "Opened ${who(action)}'s chat."
        }
    }

    private fun whatsAppOpenChat(action: PreparedAction): ActionOutcome {
        if (!isInstalled(WHATSAPP)) return ActionOutcome.Failed("WhatsApp isn't installed on this phone.")
        val number = action.number ?: return ActionOutcome.Failed("${who(action)} has no phone number.")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${PhoneNumbers.forWhatsApp(number)}")).setPackage(WHATSAPP)
        return launch(intent, "I couldn't open WhatsApp.") { "Opened your chat with ${who(action)}." }
    }

    private fun telegram(action: PreparedAction): ActionOutcome {
        if (!isInstalled(TELEGRAM)) return ActionOutcome.Failed("Telegram isn't installed on this phone.")
        val number = action.number ?: return ActionOutcome.Failed("${who(action)} has no phone number.")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?phone=${PhoneNumbers.forWhatsApp(number)}")).setPackage(TELEGRAM)
        return launch(intent, "I couldn't open Telegram.") { "Opened ${who(action)} in Telegram." }
    }

    // --- Search & media --------------------------------------------------------------------------

    private fun youtube(query: String): ActionOutcome {
        val uri = Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query.trim())}")
        return launch(Intent(Intent.ACTION_VIEW, uri), "I couldn't open YouTube.") { "Here's YouTube for \"${query.trim()}\"." }
    }

    private fun webSearch(queryOrUrl: String): ActionOutcome {
        val clean = queryOrUrl.trim()
        val uri = if (looksLikeDomain(clean)) {
            Uri.parse(if (clean.startsWith("https://")) clean else "https://" + clean.removePrefix("http://"))
        } else {
            Uri.parse("https://www.google.com/search?q=${Uri.encode(clean)}")
        }
        if (uri.scheme != "https") return ActionOutcome.Failed("I only open secure (https) links.")
        val opened = launch(Intent(Intent.ACTION_VIEW, uri), "") { "Searching for \"$clean\"." }
        if (opened is ActionOutcome.Done) return opened
        val fallback = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, clean)
        return launch(fallback, "I couldn't find a browser.") { "Searching for \"$clean\"." }
    }

    private fun socialSearch(platform: String, query: String): ActionOutcome {
        val q = Uri.encode(query.trim())
        val p = platform.lowercase()
        val url = when {
            "instagram" in p -> "https://www.instagram.com/explore/search/keyword/?q=$q"
            "tiktok" in p -> "https://www.tiktok.com/search?q=$q"
            "reddit" in p -> "https://www.reddit.com/search/?q=$q"
            p == "x" || "twitter" in p -> "https://x.com/search?q=$q"
            "facebook" in p -> "https://www.facebook.com/search/top?q=$q"
            else -> "https://news.google.com/search?q=$q"
        }
        return launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)), "I couldn't open that.") { "Opening results for \"${query.trim()}\"." }
    }

    private fun playMedia(query: String): ActionOutcome {
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            putExtra(SearchManager.QUERY, query.trim())
        }
        val played = launch(intent, "") { "Playing \"${query.trim()}\"." }
        return if (played is ActionOutcome.Done) played else youtube(query)
    }

    private fun whatsPlaying(): ActionOutcome {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        return if (am?.isMusicActive == true) {
            ActionOutcome.Done("Something is playing right now. I can't see the title yet — that needs notification access, which is coming soon.")
        } else {
            ActionOutcome.Done("Nothing is playing right now.")
        }
    }

    private fun openApp(pkg: String, label: String): ActionOutcome {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: return ActionOutcome.Failed("$label isn't installed on this phone.")
        return launch(intent, "I couldn't open $label.") { "Opened $label." }
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private inline fun launch(intent: Intent, failure: String, success: () -> String): ActionOutcome = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        ActionOutcome.Done(success())
    } catch (_: ActivityNotFoundException) {
        ActionOutcome.Failed(failure)
    } catch (_: SecurityException) {
        ActionOutcome.Failed(failure)
    }

    private fun who(action: PreparedAction) = action.contact?.displayName ?: action.number ?: action.turn.actionRecipient

    private fun isInstalled(pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0); true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun looksLikeDomain(s: String) =
        s.startsWith("https://") || s.startsWith("http://") || Regex("^[\\w-]+(\\.[\\w-]+)+(/\\S*)?$").matches(s)

    companion object {
        const val WHATSAPP = "com.whatsapp"
        const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"
        const val TELEGRAM = "org.telegram.messenger"

        val PERSON_ACTIONS = setOf(
            CompanionActionType.SIM_CALL,
            CompanionActionType.WHATSAPP_CALL,
            CompanionActionType.SOCIAL_CALL,
            CompanionActionType.WHATSAPP,
            CompanionActionType.WHATSAPP_READ_CHAT,
            CompanionActionType.TELEGRAM
        )
        val QUERY_ACTIONS = setOf(
            CompanionActionType.YOUTUBE,
            CompanionActionType.BROWSER_SEARCH,
            CompanionActionType.SOCIAL_SEARCH,
            CompanionActionType.OPEN_MEDIA
        )
    }
}
