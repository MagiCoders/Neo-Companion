package com.example.actions

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import java.text.Normalizer
import java.util.Locale

/** A person from the phone's address book. Nothing here is invented: every field comes from Contacts. */
data class PhoneContact(
    val contactId: Long,
    val displayName: String,
    val phones: List<String>,
    /** ContactsContract.Data row for "WhatsApp voice call" if WhatsApp synced this contact. */
    val whatsAppCallDataId: Long?,
    /** ContactsContract.Data row for "WhatsApp video call" if available. */
    val whatsAppVideoDataId: Long?
) {
    val primaryPhone: String? get() = phones.firstOrNull()
}

sealed interface ContactLookup {
    data class Found(val contact: PhoneContact) : ContactLookup
    data class Ambiguous(val query: String, val candidates: List<PhoneContact>) : ContactLookup
    data class NotFound(val query: String) : ContactLookup
    data object NoPermission : ContactLookup
    /** The user dictated a phone number instead of a name. */
    data class RawNumber(val number: String) : ContactLookup
}

/**
 * Finds contacts by what the user said ("Mom", "Neo Smith", "أمي", "+44 7700 900123").
 * Matching order: exact name → nickname → name starts with → name contains; relationship words
 * ("mom", "ماما", "maman"…) are expanded to the names people commonly save them under.
 */
class ContactResolver(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    fun resolve(spoken: String): ContactLookup {
        val query = spoken.trim()
        if (query.isEmpty()) return ContactLookup.NotFound(query)
        PhoneNumbers.extract(query)?.let { return ContactLookup.RawNumber(it) }
        if (!hasPermission()) return ContactLookup.NoPermission

        val terms = (listOf(query) + RelationshipAliases.expand(query)).distinct()
        val all = loadContactsMatching(terms)
        if (all.isEmpty()) return ContactLookup.NotFound(query)

        val q = normalize(query)
        val aliasNorms = terms.map(::normalize).toSet()
        val exact = all.filter { normalize(it.displayName) in aliasNorms }
        val ranked = when {
            exact.isNotEmpty() -> exact
            else -> {
                val starts = all.filter { c -> aliasNorms.any { normalize(c.displayName).startsWith(it) } }
                starts.ifEmpty { all.filter { c -> normalize(c.displayName).contains(q) }.ifEmpty { all } }
            }
        }.filter { it.phones.isNotEmpty() }

        return when (ranked.size) {
            0 -> ContactLookup.NotFound(query)
            1 -> ContactLookup.Found(ranked.first())
            else -> ContactLookup.Ambiguous(query, ranked.take(5))
        }
    }

    private fun loadContactsMatching(terms: List<String>): List<PhoneContact> {
        val resolver = context.contentResolver
        val ids = linkedSetOf<Long>()

        // 1) Display names.
        for (term in terms) {
            resolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID),
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ? AND ${ContactsContract.Contacts.HAS_PHONE_NUMBER} = 1",
                arrayOf("%${escapeLike(term)}%"),
                null
            )?.use { c -> while (c.moveToNext()) ids += c.getLong(0) }
        }
        // 2) Nicknames.
        for (term in terms) {
            resolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data.CONTACT_ID),
                "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Nickname.NAME} LIKE ?",
                arrayOf(ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE, "%${escapeLike(term)}%"),
                null
            )?.use { c -> while (c.moveToNext()) ids += c.getLong(0) }
        }
        return ids.take(25).mapNotNull(::loadContact)
    }

    private fun loadContact(contactId: Long): PhoneContact? {
        val resolver = context.contentResolver
        var name: String? = null
        val phones = linkedSetOf<String>()
        var waCall: Long? = null
        var waVideo: Long? = null
        resolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.Data._ID,
                ContactsContract.Data.MIMETYPE,
                ContactsContract.Data.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER
            ),
            "${ContactsContract.Data.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null
        )?.use { c ->
            while (c.moveToNext()) {
                val dataId = c.getLong(0)
                val mime = c.getString(1) ?: continue
                if (name == null) name = c.getString(2)
                when (mime) {
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                        val normalized = c.getString(4)
                        val raw = c.getString(3)
                        (normalized ?: raw)?.let { PhoneNumbers.clean(it) }?.takeIf { it.length >= 6 }?.let { phones += it }
                    }
                    WHATSAPP_VOIP_CALL_MIME -> waCall = dataId
                    WHATSAPP_VIDEO_CALL_MIME -> waVideo = dataId
                }
            }
        }
        val display = name ?: return null
        return PhoneContact(contactId, display, phones.toList(), waCall, waVideo)
    }

    fun dataUri(dataId: Long): Uri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, dataId)

    private fun escapeLike(s: String) = s.replace("%", "").replace("_", "")

    companion object {
        const val WHATSAPP_VOIP_CALL_MIME = "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
        const val WHATSAPP_VIDEO_CALL_MIME = "vnd.android.cursor.item/vnd.com.whatsapp.video.call"

        fun normalize(s: String): String =
            Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFKD)
                .replace(Regex("\\p{Mn}+"), "")
                .replace(Regex("[^\\p{L}\\p{N} ]"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
    }
}

object PhoneNumbers {
    /** Keeps digits and a leading +. */
    fun clean(raw: String): String {
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        return if (trimmed.startsWith("+")) "+$digits" else digits
    }

    /** Returns a phone number if the text is essentially just a number (7–15 digits). */
    fun extract(text: String): String? {
        val candidate = text.filter { it.isDigit() || it == '+' || it == ' ' || it == '-' || it == '(' || it == ')' }
        if (candidate.length < text.trim().length - 2) return null
        val cleaned = clean(candidate)
        val digitCount = cleaned.count { it.isDigit() }
        return if (digitCount in 7..15) cleaned else null
    }

    /** wa.me wants the international number without "+" or leading zeros. */
    fun forWhatsApp(number: String): String = number.filter { it.isDigit() }.trimStart('0')
}

/**
 * Relationship words in the languages the app supports, mapped to the names people usually save
 * those contacts under. This is a language dictionary, not contact data.
 */
object RelationshipAliases {
    private val groups: List<Set<String>> = listOf(
        setOf("mom", "mother", "mum", "mommy", "mama", "mamma", "ma", "ماما", "أمي", "امي", "أم", "الوالدة", "مامان", "مادر", "maman", "mère", "mamá", "madre", "mutter", "mutti", "anne", "мама", "お母さん", "母", "엄마", "妈妈"),
        setOf("dad", "father", "papa", "daddy", "baba", "بابا", "أبي", "ابي", "أب", "الوالد", "پدر", "père", "papá", "padre", "vater", "baba", "папа", "お父さん", "父", "아빠", "爸爸"),
        setOf("wife", "زوجتي", "همسر", "femme", "esposa", "frau", "жена", "妻", "아내", "老婆"),
        setOf("husband", "زوجي", "شوهر", "mari", "esposo", "marido", "mann", "муж", "夫", "남편", "老公"),
        setOf("brother", "bro", "أخي", "اخي", "برادر", "frère", "hermano", "bruder", "брат", "兄", "형", "오빠", "哥哥"),
        setOf("sister", "sis", "أختي", "اختي", "خواهر", "sœur", "hermana", "schwester", "сестра", "姉", "누나", "언니", "姐姐"),
        setOf("grandma", "grandmother", "nana", "granny", "جدتي", "تيتا", "مادربزرگ", "abuela", "oma", "бабушка"),
        setOf("grandpa", "grandfather", "جدي", "پدربزرگ", "abuelo", "opa", "дедушка")
    )

    fun expand(spoken: String): List<String> {
        val words = spoken.lowercase(Locale.ROOT)
            .replace(Regex("^(my|ma|mi|mon|mein|meine)\\s+"), "")
            .trim()
        val group = groups.firstOrNull { g -> g.any { it.equals(words, ignoreCase = true) } } ?: return emptyList()
        return group.toList()
    }
}
