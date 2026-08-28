package com.neontides.nativeapp.ai

import android.app.Service
import android.content.Intent
import android.os.IBinder
import ai.mlc.mlcllm.MLCEngine
import ai.mlc.mlcllm.OpenAIProtocol
import ai.mlc.mlcllm.OpenAIProtocol.ChatCompletionMessage
import ai.mlc.mlcllm.OpenAIProtocol.ChatCompletionRole
import com.neontides.nativeapp.BuildConfig
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Processo separato per MLC/driver GPU. Se il runtime nativo termina questo
 * processo, il processo principale rimane vivo e puo annullare la selezione.
 */
class MlcRuntimeService : Service() {
    private val runtimeLock = Any()
    private var engine: MLCEngine? = null
    private var loadedPath: String? = null
    private var lastLoadError: String = ""

    private val binder = object : MlcRuntimeBinder() {
        override fun loadModel(modelPath: String, modelLib: String): Boolean =
            synchronized(runtimeLock) {
                val completed = AtomicBoolean(false)
                val watchdog = Thread({
                    try {
                        Thread.sleep(45_000L)
                        if (!completed.get()) {
                            android.os.Process.killProcess(android.os.Process.myPid())
                        }
                    } catch (_: InterruptedException) {
                        // Caricamento completato prima del limite.
                    }
                }, "mlc-load-watchdog").apply {
                    isDaemon = true
                    start()
                }
                runCatching {
                    require(BuildConfig.MLC_RUNTIME_PACKAGED) {
                        "Runtime MLC non incluso nell'APK"
                    }
                    val activeEngine = engine ?: MLCEngine().also { engine = it }
                    if (loadedPath != null) activeEngine.unload()
                    activeEngine.reload(modelPath, modelLib)
                    loadedPath = modelPath
                    lastLoadError = ""
                    true
                }.getOrElse {
                    loadedPath = null
                    lastLoadError = it.message ?: it.javaClass.simpleName
                    false
                }.also {
                    completed.set(true)
                    watchdog.interrupt()
                }
            }

        override fun isModelLoaded(modelPath: String): Boolean =
            synchronized(runtimeLock) { loadedPath == modelPath && engine != null }

        override fun generate(
            context: String,
            prompt: String,
            maxTokens: Int,
            temperature: Float
        ): String = synchronized(runtimeLock) {
            runBlocking {
                val activeEngine = requireNotNull(engine) { "MLC non caricato" }
                require(loadedPath != null) { "Modello MLC non caricato" }
                val messages = buildList {
                    if (context.isNotBlank()) {
                        add(ChatCompletionMessage(ChatCompletionRole.system, context))
                    }
                    add(ChatCompletionMessage(ChatCompletionRole.user, prompt))
                }
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
                    output.append(
                        response.choices.firstOrNull()?.delta?.content?.asText().orEmpty()
                    )
                }
                output.toString().trim()
            }
        }

        override fun unloadModel(): Boolean = synchronized(runtimeLock) {
            runCatching {
                engine?.unload()
                engine = null
                loadedPath = null
                true
            }.getOrElse {
                engine = null
                loadedPath = null
                false
            }
        }

        override fun lastError(): String = synchronized(runtimeLock) { lastLoadError }
    }

    override fun onCreate() {
        super.onCreate()
        // Questa istanza vive esclusivamente in :mlc_runtime. Se OpenCL/TVM
        // provoca un segnale fatale, termina solo questo processo di prova.
        runCatching {
            if (NativeLlama.libraryLoaded()) NativeLlama.installProcessCrashGuard()
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        synchronized(runtimeLock) {
            runCatching { engine?.unload() }
            engine = null
            loadedPath = null
        }
        super.onDestroy()
    }
}
