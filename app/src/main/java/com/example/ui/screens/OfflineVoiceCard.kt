package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ai.gemma.OfflineVoiceLab
import com.example.ai.offline.OfflineCatalog
import com.example.ai.offline.OfflinePack
import com.example.ai.offline.OfflinePackDownloader
import com.example.ui.theme.MochiSpacing
import com.example.ui.theme.MochiType

/**
 * Gemma Lab → Offline voice (test only): checks Google's on-phone speech, downloads the open
 * listener and voices, and times one full offline turn (hold to talk, release).
 */
@Composable
internal fun OfflineVoiceCard(p: SuitePalette, accent: Color, allowMobile: Boolean) {
    val context = LocalContext.current
    val test = remember { OfflineVoiceLab.get(context) }
    val recording by test.recording.collectAsState()
    val busy by test.busy.collectAsState()
    val report by test.report.collectAsState()
    val google by test.googleStatus.collectAsState()
    var language by remember { mutableStateOf("en") }
    var listenerId by remember { mutableStateOf(OfflineCatalog.LISTENER_BASE.id) }
    val listener = OfflineCatalog.LISTENERS.first { it.id == listenerId }
    val currentLanguage by rememberUpdatedState(language)
    val currentListener by rememberUpdatedState(listener)
    val currentBusy by rememberUpdatedState(busy)
    DisposableEffect(Unit) { onDispose { test.cancelRecording() } }

    LabCard(p) {
        Text("Offline voice (test)", style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold), color = p.textPrimary)
        Text(
            "Times an offline turn: Whisper writes down what you say, then Gemma answers.",
            style = MochiType.Label, color = p.textTertiary
        )

        Text("Google's on-phone speech", style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold), color = p.textPrimary)
        Chip("Check this phone", false, accent, p) { test.checkGoogle() }
        if (google.isNotBlank()) Text(google, style = MochiType.Label, color = p.textSecondary)

        Text("Listener (Whisper)", style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold), color = p.textPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(MochiSpacing.xs)) {
            OfflineCatalog.LISTENERS.forEach { pack -> Chip(pack.label.substringBefore(" ("), listenerId == pack.id, accent, p) { listenerId = pack.id } }
        }
        OfflineCatalog.LISTENERS.forEach { PackRow(it, test.packs, allowMobile, p, accent) }

        Text("Try a turn", style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold), color = p.textPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(MochiSpacing.xs)) {
            listOf("en" to "English", "ar" to "عربي", "fa" to "فارسی").forEach { (code, name) ->
                Chip(name, language == code, accent, p) { language = code }
            }
        }
        Text(
            text = when { busy -> "Working…"; recording -> "Listening… release when done"; else -> "Hold to talk" },
            style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(if (recording) Color(0xFFE57373) else accent.copy(alpha = if (busy) 0.4f else 1f))
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        if (currentBusy) return@detectTapGestures
                        test.startRecording()
                        val released = try { tryAwaitRelease() } catch (c: kotlinx.coroutines.CancellationException) { test.cancelRecording(); throw c }
                        if (released) test.stopAndRun(currentLanguage, currentListener) else test.cancelRecording()
                    })
                }
                .padding(vertical = 14.dp)
        )
        if (report.isNotBlank()) {
            Text(report, style = MochiType.Label, color = p.textPrimary)
            Chip("Copy report", false, accent, p) {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("Offline voice report", report))
            }
        }
    }
}

@Composable
private fun PackRow(pack: OfflinePack, packs: OfflinePackDownloader, allowMobile: Boolean, p: SuitePalette, accent: Color) {
    val state by packs.state(pack).collectAsState()
    Text("${pack.label} · about %.0f MB".format(pack.approxBytes / 1e6), style = MochiType.Label, color = p.textSecondary)
    when (val s = state) {
        is OfflinePackDownloader.State.Downloading -> {
            LinearProgressIndicator(progress = { (s.bytes.toFloat() / s.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = accent)
            Text("%.0f of %.0f MB · %.1f MB/s".format(s.bytes / 1e6, s.total / 1e6, s.bytesPerSecond / 1e6), style = MochiType.Label, color = p.textTertiary)
        }
        OfflinePackDownloader.State.Verifying -> Text("Checking fingerprint…", style = MochiType.Label, color = p.textTertiary)
        is OfflinePackDownloader.State.Failed -> Text(s.reason, style = MochiType.Label, color = Color(0xFFE57373))
        else -> {}
    }
    Row(horizontalArrangement = Arrangement.spacedBy(MochiSpacing.xs)) {
        when (state) {
            OfflinePackDownloader.State.Ready -> Chip("Delete", false, accent, p) { packs.delete(pack) }
            is OfflinePackDownloader.State.Downloading, OfflinePackDownloader.State.Verifying -> Chip("Stop", false, accent, p) { packs.delete(pack) }
            else -> Chip("Download", true, accent, p) { packs.start(pack, allowMobile) }
        }
    }
}
