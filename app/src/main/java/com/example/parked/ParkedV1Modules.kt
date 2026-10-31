package com.example.parked

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.PersonReceiptShare
import com.example.data.ReceiptSplitResult
import kotlin.math.roundToInt

/**
 * Parked modules preserved from pre-v1 for post-v1 roadmap:
 * 1. Legacy procedural character preset definitions
 * 2. Bio-Mood heuristic stress/harmony analyzer
 * 3. Multi-person receipt splitter engine
 * 4. Direct SIM & social media calling bridge
 *
 * Per DESIGN.md and CHARACTERS.md, these are removed from v1 navigation
 * so v1 stays focused on the 3D Stage, Character Picker, Conversation sheet,
 * and Tools grid.
 */
object ParkedV1Modules {

    data class BioMoodAnalysis(
        val stressPercent: Int,
        val harmonyLabel: String,
        val suggestedBreathingSeconds: Int
    )

    fun analyzeBioMood(userText: String): BioMoodAnalysis {
        val lower = userText.lowercase()
        val highStressKeywords = listOf("stressed", "angry", "overwhelmed", "anxious", "tired", "deadline", "urgent")
        val matches = highStressKeywords.count { lower.contains(it) }
        val stress = (18 + matches * 18).coerceIn(10, 92)
        val label = when {
            stress >= 65 -> "Elevated tension"
            stress >= 35 -> "Moderate focus"
            else -> "Calm balance"
        }
        return BioMoodAnalysis(
            stressPercent = stress,
            harmonyLabel = label,
            suggestedBreathingSeconds = if (stress >= 50) 60 else 30
        )
    }

    fun calculateReceiptSplit(
        rawOrders: String,
        taxTipPercent: Double
    ): ReceiptSplitResult {
        val shares = mutableListOf<PersonReceiptShare>()
        val priceRegex = Regex("""\$?(\d+(?:\.\d{1,2})?)""")
        rawOrders.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEachIndexed { idx, line ->
            val match = priceRegex.findAll(line).lastOrNull()
            val sub = match?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 12.0
            val parts = line.split(":", limit = 2)
            val person = if (parts.size == 2) parts[0].trim() else "Person ${idx + 1}"
            val itemDesc = if (parts.size == 2) {
                parts[1].replace(priceRegex, "").trim().ifEmpty { "Order item" }
            } else {
                line.replace(priceRegex, "").trim().ifEmpty { "Order item" }
            }
            val taxTip = ((sub * (taxTipPercent / 100.0)) * 100.0).roundToInt() / 100.0
            val total = ((sub + taxTip) * 100.0).roundToInt() / 100.0
            shares.add(PersonReceiptShare(person, itemDesc, sub, taxTip, total))
        }
        val grandTotal = (shares.sumOf { it.totalOwed } * 100.0).roundToInt() / 100.0
        val summary = buildString {
            appendLine("Receipt split (${taxTipPercent.toInt()}% tax and tip)")
            shares.forEach { s ->
                appendLine("${s.personName}: $${"%.2f".format(s.totalOwed)} (${s.itemsOrdered})")
            }
            append("Total: $${"%.2f".format(grandTotal)}")
        }
        return ReceiptSplitResult(
            restaurantOrTitle = "Table receipt split",
            currencySymbol = "$",
            shares = shares,
            grandTotal = grandTotal,
            shareableGroupText = summary
        )
    }

    fun placeDialerCall(context: Context, phoneNumber: String) {
        val digits = phoneNumber.filter { it.isDigit() || it == '+' }.ifEmpty { "+15550192834" }
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
