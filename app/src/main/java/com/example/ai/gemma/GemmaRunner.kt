package com.example.ai.gemma

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Debug
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/**
 * Runs a downloaded Gemma model on the phone with LiteRT-LM, fully offline. One thread owns the
 * engine and conversation (the library requires that); loading, chatting, testing and closing
 * all happen on it. Measures what matters for deciding whether Gemma can be Mochi's free
 * on-phone brain: load time, time to first word, words per second, memory and battery heat.
 */
@OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)
class GemmaRunner(context: Context) {

    enum class Processor(val label: String) { CPU("CPU"), GPU("GPU") }

    sealed class State {
        object Empty : State()
        data class Loading(val model: GemmaModel, val processor: Processor) : State()
        data class Loaded(val model: GemmaModel, val processor: Processor, val loadSeconds: Double) : State()
        data class Failed(val reason: String) : State()
    }

    data class Stats(
        val timeToFirstTokenSec: Double = 0.0,
        val decodeTokensPerSec: Double = 0.0,
        val prefillTokensPerSec: Double = 0.0,
        val memoryMb: Int = 0,
        val batteryTempC: Double = 0.0
    ) {
        fun summary(): String =
            "first word in %.1f s · %.1f tokens/s writing · %.0f tokens/s reading · memory %d MB · battery %.1f °C"
                .format(timeToFirstTokenSec, decodeTokensPerSec, prefillTokensPerSec, memoryMb, batteryTempC)
    }

    private val appContext = context.applicationContext
    private val thread = Executors.newSingleThreadExecutor { r -> Thread(r, "gemma").apply { priority = Thread.NORM_PRIORITY } }
    private val dispatcher = thread.asCoroutineDispatcher()
    private var engine: Engine? = null
    private var chat: Conversation? = null

    private val _state = MutableStateFlow<State>(State.Empty)
    val state: StateFlow<State> = _state.asStateFlow()
    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats.asStateFlow()

    suspend fun load(model: GemmaModel, file: File, processor: Processor) = withContext(dispatcher) {
        closeAll()
        _state.value = State.Loading(model, processor)
        val started = System.currentTimeMillis()
        try {
            // Lets the library report its own speed figures (time to first word, words per second).
            runCatching { com.google.ai.edge.litertlm.ExperimentalFlags.enableBenchmark = true }
            // With its audio part switched on, Gemma can hear a recording directly (no speech
            // recogniser needed). If this model or phone can't, load it for text only.
            val e = try {
                newEngine(file, processor, withAudio = true).also { hearsAudio = true }
            } catch (t: Throwable) {
                if (t is OutOfMemoryError) throw t
                Log.w(TAG, "Audio part unavailable (${t.javaClass.simpleName}); loading for text only")
                hearsAudio = false
                newEngine(file, processor, withAudio = false)
            }
            engine = e
            chat = null
            _state.value = State.Loaded(model, processor, (System.currentTimeMillis() - started) / 1000.0)
            _stats.value = Stats(memoryMb = memoryMb(), batteryTempC = batteryTemp())
        } catch (t: Throwable) {
            Log.w(TAG, "Load failed: ${t.javaClass.simpleName}: ${t.message}")
            closeAll()
            val why = t.message?.take(160) ?: t.javaClass.simpleName
            _state.value = State.Failed(
                when {
                    why.contains("OpenCL", true) -> "This phone's graphics chip can't run it (OpenCL missing). Use CPU."
                    t is OutOfMemoryError || why.contains("memory", true) -> "Not enough memory to load ${model.label}. Close other apps or try E2B."
                    else -> "Could not load ${model.label} on ${processor.label}: $why"
                }
            )
        }
    }

    /** True when the loaded model can hear recordings (its audio part loaded). */
    @Volatile var hearsAudio = false
        private set

    private fun newEngine(file: File, processor: Processor, withAudio: Boolean): Engine {
        val e = Engine(
            EngineConfig(
                modelPath = file.absolutePath,
                backend = if (processor == Processor.GPU) Backend.GPU() else Backend.CPU(),
                visionBackend = null,
                audioBackend = if (withAudio) Backend.CPU() else null,
                maxNumTokens = null,
                maxNumImages = null,
                cacheDir = File(appContext.cacheDir, "gemma").apply { mkdirs() }.absolutePath
            )
        )
        try { e.initialize() } catch (t: Throwable) { try { e.close() } catch (_: Throwable) {}; throw t }
        return e
    }

    /**
     * The Lab chat. LiteRT-LM allows only one open conversation per loaded model, so the chat is
     * opened when needed and closed whenever a test or an answer needs its own conversation.
     */
    private fun chatOrOpen(): Conversation =
        chat ?: (engine ?: throw IllegalStateException("Load a model first.")).createConversation(chatConfig()).also { chat = it }

    private fun closeChat() {
        try { chat?.cancelProcess() } catch (_: Throwable) {}
        try { chat?.close() } catch (_: Throwable) {}
        chat = null
    }

    /** Runs [block] in its own fresh conversation (closing the chat first). Gemma thread only. */
    private fun <T> oneOff(config: ConversationConfig, block: (Conversation) -> T): T {
        val e = engine ?: throw IllegalStateException("Load a model first.")
        closeChat()
        val conv = e.createConversation(config)
        try { return block(conv) } finally { try { conv.close() } catch (_: Throwable) {} }
    }

    /** The reply as it is written, piece by piece. */
    fun send(text: String): Flow<String> = flow {
        val c = chatOrOpen()
        val started = System.currentTimeMillis()
        var first = 0L
        var pieces = 0
        try {
            emitAll(c.sendMessageAsync(text).map { m ->
                if (first == 0L) first = System.currentTimeMillis()
                pieces++
                m.toString()
            })
        } finally {
            // Our own measurement (pieces are about one token each); the library's figures win when it has them.
            val writing = (System.currentTimeMillis() - first).coerceAtLeast(1L) / 1000.0
            measured = Stats(
                timeToFirstTokenSec = if (first > 0) (first - started) / 1000.0 else 0.0,
                decodeTokensPerSec = if (pieces > 1) (pieces - 1) / writing else 0.0
            )
            refreshStats(c)
        }
    }.flowOn(dispatcher)

    /**
     * One answer streamed piece by piece in a fresh conversation (the offline voice test times the
     * first sentence with it).
     */
    fun stream(systemPrompt: String, text: String): Flow<String> = flow {
        val e = engine ?: throw IllegalStateException("Load a model first.")
        closeChat()
        val conv = e.createConversation(answerConfig(systemPrompt))
        try {
            emitAll(conv.sendMessageAsync(text).map { it.toString() })
        } finally { try { conv.close() } catch (_: Throwable) {} }
    }.flowOn(dispatcher)

    @Volatile private var measured = Stats()

    /** Stops a reply being written. */
    suspend fun stop() = withContext(dispatcher) { chat?.cancelProcess() }

    /** Clears the chat (new conversation, same loaded model). */
    suspend fun newChat() = withContext(dispatcher) { closeChat() }

    /**
     * Runs the quality test: each request in a fresh conversation, asked for a JSON answer.
     * [onEach] gets every result as it comes.
     */
    suspend fun runBench(onEach: (GemmaBench.Outcome) -> Unit) = withContext(dispatcher) {
        if (engine == null) throw IllegalStateException("Load a model first.")
        val config = ConversationConfig(
            systemInstruction = Contents.of(GemmaBench.SYSTEM_PROMPT),
            samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0, seed = 1)
        )
        for (case in GemmaBench.CASES) {
            try {
                val outcome = oneOff(config) { conv ->
                    val t0 = System.currentTimeMillis()
                    val answer = conv.sendMessage(case.text).toString()
                    refreshStats(conv)
                    GemmaBench.score(case, answer, (System.currentTimeMillis() - t0) / 1000.0)
                }
                onEach(outcome)
            } catch (t: Throwable) {
                Log.w(TAG, "Test request failed: ${t.javaClass.simpleName}: ${t.message}")
                onEach(GemmaBench.Outcome(case, "error: ${t.javaClass.simpleName}: ${t.message?.take(120).orEmpty()}", null, null, false, 0.0))
            }
        }
    }

    /** One answer in a fresh conversation (the on-phone engine; the chat in the Lab is kept apart). */
    suspend fun answerOnce(systemPrompt: String, text: String): String = withContext(dispatcher) {
        oneOff(answerConfig(systemPrompt)) { it.sendMessage(text).toString() }
    }

    /**
     * Gemma listens to [wav] (a 16 kHz mono WAV recording) and answers [instruction] about it, in a
     * fresh conversation. Needs [hearsAudio].
     */
    suspend fun answerAudio(systemPrompt: String, wav: ByteArray, instruction: String): String = withContext(dispatcher) {
        if (!hearsAudio) throw IllegalStateException("This model was loaded without hearing.")
        oneOff(answerConfig(systemPrompt)) { conv ->
            conv.sendMessage(Contents.of(Content.AudioBytes(wav), Content.Text(instruction))).toString()
        }
    }

    private fun answerConfig(systemPrompt: String) = ConversationConfig(
        systemInstruction = Contents.of(systemPrompt),
        samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.4, seed = 0)
    )

    suspend fun unload() = withContext(dispatcher) {
        closeAll()
        _state.value = State.Empty
        _stats.value = Stats()
    }

    private fun chatConfig() = ConversationConfig(
        systemInstruction = Contents.of(
            "You are Mochi, a warm, playful companion on the user's phone. Answer briefly (one to three short sentences), " +
                "in the language the user writes in. Arabic: use the user's everyday dialect when they use one."
        ),
        samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7, seed = 0)
    )

    private fun refreshStats(c: Conversation) {
        val b = try { c.getBenchmarkInfo() } catch (_: Throwable) { null }
        _stats.value = Stats(
            timeToFirstTokenSec = b?.timeToFirstTokenInSecond?.takeIf { it > 0 } ?: measured.timeToFirstTokenSec,
            decodeTokensPerSec = b?.lastDecodeTokensPerSecond?.takeIf { it > 0 } ?: measured.decodeTokensPerSec,
            prefillTokensPerSec = b?.lastPrefillTokensPerSecond ?: 0.0,
            memoryMb = memoryMb(),
            batteryTempC = batteryTemp()
        )
    }

    private fun closeAll() {
        closeChat()
        hearsAudio = false
        try { engine?.close() } catch (_: Throwable) {}
        engine = null
    }

    private fun memoryMb(): Int = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss / 1024

    private fun batteryTemp(): Double {
        val i = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
    }

    private companion object { const val TAG = "GemmaRunner" }
}
