package com.neontides.nativeapp.ai

import ai.mlc.mlcllm.MLCEngine
import ai.mlc.mlcllm.OpenAIProtocol
import ai.mlc.mlcllm.OpenAIProtocol.ChatCompletionMessage
import ai.mlc.mlcllm.OpenAIProtocol.ChatCompletionRole
import com.neontides.nativeapp.BuildConfig
import kotlinx.coroutines.runBlocking
import java.util.Locale

internal interface LocalAiRuntime {
    val backend: LocalAiBackend
    fun load(model: LocalModel): Boolean
    fun isLoaded(model: LocalModel): Boolean
    fun prepareConversation(context: String): Boolean
    fun isConversationPrepared(): Boolean
    fun rewindConversation(): Boolean
    fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        onPartial: ((String) -> Unit)? = null
    ): String
    fun unload(): Boolean
}

internal object LocalRuntimeDiagnostics {
    private val entries = ArrayDeque<String>()

    @Volatile
    var currentBackend: String = "nessuno"
        private set

    @Volatile
    var lastError: String = "nessuno"
        private set

    @Volatile
    var loadedBackend: LocalAiBackend? = null
        private set

    @Synchronized
    fun record(backend: LocalAiBackend, message: String) {
        currentBackend = backend.label
        if (message.startsWith("ERROR")) lastError = message
        entries.addLast("${backend.name}: $message")
        while (entries.size > 30) entries.removeFirst()
    }

    fun markLoaded(backend: LocalAiBackend) {
        loadedBackend = backend
        currentBackend = backend.label
        lastError = "nessuno"
    }

    fun markUnloaded(backend: LocalAiBackend) {
        if (loadedBackend == backend) loadedBackend = null
    }

    @Synchronized
    fun report(): String = buildString {
        appendLine("Backend runtime: $currentBackend")
        appendLine("Backend caricato: ${loadedBackend?.label ?: "nessuno"}")
        appendLine("Runtime MLC nell'APK: ${if (BuildConfig.MLC_RUNTIME_PACKAGED) "SÌ" else "NO"}")
        appendLine("Ultimo errore: $lastError")
        if (entries.isNotEmpty()) {
            appendLine("Registro backend:")
            entries.forEach { appendLine(it) }
        }
    }.trimEnd()

    @Synchronized
    fun clear() {
        entries.clear()
        lastError = "nessuno"
    }
}

internal class LlamaCppRuntime : LocalAiRuntime {
    override val backend = LocalAiBackend.LLAMA_CPP
    private var loadedPath: String? = null

    override fun load(model: LocalModel): Boolean = runCatching {
        require(model.backend == backend)
        require(NativeLlama.libraryLoaded()) { "Libreria llama.cpp non caricata" }
        if (NativeLlama.isModelLoaded()) NativeLlama.unloadModel()
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
        val started = System.nanoTime()
        val loaded = NativeLlama.loadModel(model.path.absolutePath, 1536, threads)
        loadedPath = model.path.absolutePath.takeIf { loaded }
        if (loaded) LocalRuntimeDiagnostics.markLoaded(backend)
        val elapsed = (System.nanoTime() - started) / 1_000_000L
        LocalRuntimeDiagnostics.record(backend, "LOAD ${if (loaded) "OK" else "FAILED"} · ${elapsed} ms · thread=$threads")
        loaded
    }.getOrElse {
        loadedPath = null
        LocalRuntimeDiagnostics.markUnloaded(backend)
        LocalRuntimeDiagnostics.record(backend, "ERROR load: ${it.message}")
        false
    }

    override fun isLoaded(model: LocalModel): Boolean =
        loadedPath == model.path.absolutePath &&
            NativeLlama.libraryLoaded() &&
            runCatching { NativeLlama.isModelLoaded() }.getOrDefault(false)

    override fun prepareConversation(context: String): Boolean =
        NativeLlama.prepareConversation(context)

    override fun isConversationPrepared(): Boolean =
        runCatching { NativeLlama.isConversationPrepared() }.getOrDefault(false)

    override fun rewindConversation(): Boolean =
        runCatching { NativeLlama.rewindConversation() }.getOrDefault(false)

    override fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        onPartial: ((String) -> Unit)?
    ): String = if (onPartial == null) {
        NativeLlama.generate(prompt, maxTokens, temperature).trim()
    } else {
        NativeLlama.generateStreaming(
            prompt,
            maxTokens,
            temperature,
            NativeStreamCallback(onPartial)
        ).trim()
    }

    override fun unload(): Boolean = runCatching {
        if (NativeLlama.libraryLoaded() && NativeLlama.isModelLoaded()) NativeLlama.unloadModel()
        loadedPath = null
        LocalRuntimeDiagnostics.markUnloaded(backend)
        LocalRuntimeDiagnostics.record(backend, "UNLOAD OK")
        true
    }.getOrElse {
        LocalRuntimeDiagnostics.record(backend, "ERROR unload: ${it.message}")
        false
    }
}

internal class MlcLlmRuntime(
    private val modelManager: ModelManager
) : LocalAiRuntime {
    override val backend = LocalAiBackend.MLC_LLM
    private var engine: MLCEngine? = null
    private var loadedPath: String? = null
    private var preparedContext: String = ""

    override fun load(model: LocalModel): Boolean = runCatching {
        require(BuildConfig.MLC_RUNTIME_PACKAGED) {
            "Questa APK non contiene il runtime MLC: usa la build dual-engine"
        }
        require(model.backend == backend)
        val runtimeConfig = requireNotNull(modelManager.mlcRuntimeConfig()) {
            "Configurazione mlc-app-config.json non disponibile"
        }
        val started = System.nanoTime()
        val activeEngine = engine ?: MLCEngine().also { engine = it }
        if (loadedPath != null) activeEngine.unload()
        activeEngine.reload(model.path.absolutePath, runtimeConfig.modelLib)
        loadedPath = model.path.absolutePath
        preparedContext = ""
        LocalRuntimeDiagnostics.markLoaded(backend)
        val elapsed = (System.nanoTime() - started) / 1_000_000L
        LocalRuntimeDiagnostics.record(
            backend,
            "LOAD OK · ${elapsed} ms · model_lib=${runtimeConfig.modelLib}"
        )
        true
    }.getOrElse {
        loadedPath = null
        preparedContext = ""
        LocalRuntimeDiagnostics.markUnloaded(backend)
        LocalRuntimeDiagnostics.record(backend, "ERROR load: ${it.message}")
        false
    }

    override fun isLoaded(model: LocalModel): Boolean =
        loadedPath == model.path.absolutePath && engine != null

    override fun prepareConversation(context: String): Boolean {
        if (loadedPath == null || engine == null) return false
        preparedContext = context
        LocalRuntimeDiagnostics.record(backend, "CACHE OK · caratteri=${context.length}")
        return true
    }

    override fun isConversationPrepared(): Boolean =
        loadedPath != null && preparedContext.isNotBlank()

    // MLC riceve a ogni richiesta i messaggi system/user completi. Il cache
    // interno è gestito dall'engine e non va ricostruito dalla logica di gioco.
    override fun rewindConversation(): Boolean = isConversationPrepared()

    override fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        onPartial: ((String) -> Unit)?
    ): String = runBlocking {
        val activeEngine = requireNotNull(engine) { "MLC non caricato" }
        require(loadedPath != null) { "Modello MLC non caricato" }
        val messages = buildList {
            if (preparedContext.isNotBlank()) {
                add(ChatCompletionMessage(ChatCompletionRole.system, preparedContext))
            }
            add(ChatCompletionMessage(ChatCompletionRole.user, prompt))
        }
        val started = System.nanoTime()
        val output = StringBuilder()
        val channel = activeEngine.chat.completions.create(
            messages = messages,
            max_tokens = maxTokens,
            stream = true,
            stream_options = OpenAIProtocol.StreamOptions(include_usage = true),
            temperature = temperature,
            top_p = 0.9f
        )
        for (response in channel) {
            val fragment = response.choices.firstOrNull()?.delta?.content?.asText().orEmpty()
            if (fragment.isNotEmpty()) {
                output.append(fragment)
                onPartial?.invoke(output.toString())
            }
        }
        val elapsed = (System.nanoTime() - started) / 1_000_000L
        val charsPerSecond = if (elapsed > 0L) output.length * 1000.0 / elapsed else 0.0
        LocalRuntimeDiagnostics.record(
            backend,
            "GENERATE OK · ${elapsed} ms · ${output.length} caratteri · " +
                "${String.format(Locale.US, "%.1f", charsPerSecond)} char/s"
        )
        output.toString().trim()
    }

    override fun unload(): Boolean = runCatching {
        engine?.unload()
        loadedPath = null
        preparedContext = ""
        LocalRuntimeDiagnostics.markUnloaded(backend)
        LocalRuntimeDiagnostics.record(backend, "UNLOAD OK")
        true
    }.getOrElse {
        LocalRuntimeDiagnostics.record(backend, "ERROR unload: ${it.message}")
        false
    }
}
