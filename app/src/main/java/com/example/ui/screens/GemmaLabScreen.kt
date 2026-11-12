package com.example.ui.screens

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.gemma.GemmaBench
import com.example.ai.gemma.GemmaCatalog
import com.example.ai.gemma.GemmaDownloader
import com.example.ai.gemma.GemmaModel
import com.example.ai.gemma.GemmaRunner
import com.example.ui.theme.LocalCharacterAccent
import com.example.ui.theme.MochiShapes
import com.example.ui.theme.MochiSpacing
import com.example.ui.theme.MochiType
import kotlinx.coroutines.launch

/** Process-wide Lab objects, so a download keeps going when the screen is left. */
object GemmaLab {
    @Volatile private var downloader: GemmaDownloader? = null
    @Volatile private var runner: GemmaRunner? = null
    fun downloader(context: Context) = downloader ?: synchronized(this) { downloader ?: GemmaDownloader(context).also { downloader = it } }
    fun runner(context: Context) = runner ?: synchronized(this) { runner ?: GemmaRunner(context).also { runner = it } }
}

/**
 * Gemma Lab: download Gemma 4 E2B or E4B, run it fully offline on this phone, chat with it by
 * text or voice, and measure it (speed, memory, heat, and a 16-request quality test in English,
 * Arabic and Persian). Nothing here is sent anywhere; the model runs on the phone.
 */
@Composable
fun GemmaLabScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val p = rememberSuiteColors()
    val accent = LocalCharacterAccent.current
    val scope = rememberCoroutineScope()
    val downloader = remember { GemmaLab.downloader(context) }
    val runner = remember { GemmaLab.runner(context) }
    val runnerState by runner.state.collectAsState()
    val stats by runner.stats.collectAsState()
    val totalRam = remember {
        ActivityManager.MemoryInfo().also { (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }.totalMem
    }
    var allowMobile by remember { mutableStateOf(false) }
    var processor by remember { mutableStateOf(GemmaRunner.Processor.CPU) }
    val messages = remember { mutableStateListOf<Pair<Boolean, String>>() } // (fromUser, text)
    var input by remember { mutableStateOf("") }
    var replying by remember { mutableStateOf(false) }
    val bench = remember { mutableStateListOf<GemmaBench.Outcome>() }
    var benchRunning by remember { mutableStateOf(false) }
    var voiceLang by remember { mutableStateOf("en-US") }

    fun sendText(text: String) {
        if (text.isBlank() || replying || runnerState !is GemmaRunner.State.Loaded) return
        messages += true to text.trim()
        messages += false to ""
        replying = true
        scope.launch {
            try {
                runner.send(text.trim()).collect { piece ->
                    val last = messages.lastIndex
                    messages[last] = false to (messages[last].second + piece)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                messages[messages.lastIndex] = false to "(error: ${e.javaClass.simpleName})"
            } finally {
                replying = false
            }
        }
    }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!heard.isNullOrBlank()) sendText(heard)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = MochiSpacing.screenPadding, vertical = MochiSpacing.sm)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MochiSpacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MochiSpacing.md)) {
            BackPillButton(palette = p, onClick = onBack)
            Text("Gemma Lab", style = MochiType.Title, color = p.textPrimary)
        }
        Text(
            "Test Google's Gemma 4 running entirely on this phone: no internet after the download, nothing sent anywhere. " +
                "This phone has %.1f GB of memory and %.1f GB free storage.".format(totalRam / 1e9, downloader.freeBytes() / 1e9),
            style = MochiType.Body, color = p.textSecondary
        )

        // ---- Models ----
        for (model in GemmaCatalog.MODELS) {
            ModelCard(model, downloader, runnerState, processor, totalRam, allowMobile, p, accent,
                onUse = { scope.launch { messages.clear(); bench.clear(); runner.load(model, downloader.file(model), processor) } })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = allowMobile, onCheckedChange = { allowMobile = it })
            Text("Allow downloading on mobile data", style = MochiType.Label, color = p.textSecondary)
        }

        // ---- Engine ----
        LabCard(p) {
            Text("Run on", style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold), color = p.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(MochiSpacing.xs)) {
                GemmaRunner.Processor.entries.forEach { proc ->
                    Chip(proc.label, selected = processor == proc, accent = accent, p = p) { processor = proc }
                }
            }
            Text(
                "CPU works on every phone. GPU is usually faster but not every phone supports it. Changing this takes effect the next time you tap Use.",
                style = MochiType.Label, color = p.textTertiary
            )
            val status = when (val s = runnerState) {
                GemmaRunner.State.Empty -> "No model loaded."
                is GemmaRunner.State.Loading -> "Loading ${s.model.label} on ${s.processor.label}… (this can take up to a minute)"
                is GemmaRunner.State.Loaded -> "${s.model.label} loaded on ${s.processor.label} in %.1f s.".format(s.loadSeconds)
                is GemmaRunner.State.Failed -> s.reason
            }
            Text(status, style = MochiType.Body, color = if (runnerState is GemmaRunner.State.Failed) Color(0xFFE57373) else p.textPrimary)
            if (runnerState is GemmaRunner.State.Loaded) {
                Text(stats.summary(), style = MochiType.Label, color = p.textTertiary)
                Row(horizontalArrangement = Arrangement.spacedBy(MochiSpacing.xs)) {
                    Chip("Unload", false, accent, p) { scope.launch { runner.unload(); messages.clear() } }
                    Chip("New chat", false, accent, p) { scope.launch { runner.newChat(); messages.clear() } }
                }
            }
        }

        // ---- Chat ----
        if (runnerState is GemmaRunner.State.Loaded) {
            LabCard(p) {
                Text("Chat (offline)", style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold), color = p.textPrimary)
                messages.forEach { (fromUser, text) ->
                    Text(
                        (if (fromUser) "You: " else "Gemma: ") + text.ifEmpty { "…" },
                        style = MochiType.Body,
                        color = if (fromUser) p.textSecondary else p.textPrimary
                    )
                }
                OutlinedTextField(
                    value = input, onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(), placeholder = { Text("Type in any language") }, singleLine = false
                )
                Row(horizontalArrangement = Arrangement.spacedBy(MochiSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Chip(if (replying) "Stop" else "Send", true, accent, p) {
                        if (replying) scope.launch { runner.stop() } else { sendText(input); input = "" }
                    }
                    Chip("Speak", false, accent, p) {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, voiceLang)
                        runCatching { speech.launch(intent) }
                    }
                    listOf("en-US" to "EN", "ar-IQ" to "عربي", "fa-IR" to "فارسی").forEach { (tag, name) ->
                        Chip(name, voiceLang == tag, accent, p) { voiceLang = tag }
                    }
                }
                Text("Voice is turned into text by the phone first, then Gemma answers.", style = MochiType.Label, color = p.textTertiary)
            }

            // ---- Quality test ----
            LabCard(p) {
                Text("Quality test", style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold), color = p.textPrimary)
                Text(
                    "${GemmaBench.CASES.size} everyday requests in English, Arabic and Persian. Gemma must say what each one asks for (call, alarm, YouTube…).",
                    style = MochiType.Label, color = p.textTertiary
                )
                Chip(if (benchRunning) "Testing… ${bench.size}/${GemmaBench.CASES.size}" else "Run the test", true, accent, p) {
                    if (benchRunning) return@Chip
                    bench.clear()
                    benchRunning = true
                    scope.launch {
                        try { runner.runBench { outcome -> bench += outcome } } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                        } finally { benchRunning = false }
                    }
                }
                if (bench.isNotEmpty()) {
                    val passed = bench.count { it.passed }
                    Text(
                        "Passed $passed of ${bench.size} · average %.1f s per request".format(bench.map { it.seconds }.average()),
                        style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold), color = p.textPrimary
                    )
                    bench.forEach { o ->
                        Text(
                            (if (o.passed) "✓ " else "✗ ") + "[${o.case.language}] ${o.case.text} → ${o.action ?: "?"}" +
                                (o.target?.let { " ($it)" } ?: "") + if (o.passed) "" else "  (expected ${o.case.action})",
                            style = MochiType.Label,
                            color = if (o.passed) p.textSecondary else Color(0xFFE57373)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.padding(bottom = MochiSpacing.md))
    }
}

@Composable
private fun ModelCard(
    model: GemmaModel,
    downloader: GemmaDownloader,
    runnerState: GemmaRunner.State,
    processor: GemmaRunner.Processor,
    totalRam: Long,
    allowMobile: Boolean,
    p: SuitePalette,
    accent: Color,
    onUse: () -> Unit
) {
    val state by downloader.state(model).collectAsState()
    val fit = GemmaCatalog.fit(model, totalRam)
    LabCard(p) {
        Text(model.label, style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold), color = p.textPrimary)
        Text(
            "About %.1f GB download · ${fit.label}".format(model.approxBytes / 1e9),
            style = MochiType.Label,
            color = if (fit == GemmaCatalog.Fit.GOOD) p.textSecondary else Color(0xFFE0A050)
        )
        when (val s = state) {
            is GemmaDownloader.State.Downloading -> {
                LinearProgressIndicator(progress = { (s.bytes.toFloat() / s.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = accent)
                Text(
                    "%.2f of %.2f GB · %.1f MB/s".format(s.bytes / 1e9, s.total / 1e9, s.bytesPerSecond / 1e6),
                    style = MochiType.Label, color = p.textTertiary
                )
            }
            is GemmaDownloader.State.Paused -> Text("Paused at %.2f GB. Tap Download to continue.".format(s.bytes / 1e9), style = MochiType.Label, color = p.textTertiary)
            GemmaDownloader.State.Verifying -> Text("Checking the file's fingerprint… (about a minute)", style = MochiType.Label, color = p.textTertiary)
            GemmaDownloader.State.Ready -> Text("Downloaded and verified.", style = MochiType.Label, color = p.textTertiary)
            is GemmaDownloader.State.Failed -> Text(s.reason, style = MochiType.Label, color = Color(0xFFE57373))
            GemmaDownloader.State.NotDownloaded -> {}
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MochiSpacing.xs)) {
            when (state) {
                is GemmaDownloader.State.Downloading -> Chip("Pause", false, accent, p) { downloader.pause(model) }
                GemmaDownloader.State.Verifying -> {}
                GemmaDownloader.State.Ready -> {
                    val loadedHere = (runnerState as? GemmaRunner.State.Loaded)?.let { it.model == model && it.processor == processor } == true
                    val loading = runnerState is GemmaRunner.State.Loading
                    if (!loadedHere && !loading) Chip("Use", true, accent, p, onUse)
                    Chip("Delete", false, accent, p) { downloader.delete(model) }
                }
                else -> if (fit != GemmaCatalog.Fit.TOO_SMALL) Chip("Download", true, accent, p) { downloader.start(model, allowMobile) }
            }
        }
    }
}

@Composable
private fun LabCard(p: SuitePalette, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MochiShapes.card)
            .background(p.card)
            .border(1.dp, p.hairline, MochiShapes.card)
            .padding(MochiSpacing.md),
        verticalArrangement = Arrangement.spacedBy(MochiSpacing.xs)
    ) { content() }
}

@Composable
private fun Chip(label: String, selected: Boolean, accent: Color, p: SuitePalette, onClick: () -> Unit) {
    Text(
        text = label,
        style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold),
        color = if (selected) Color.White else p.textPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) accent else accent.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}
