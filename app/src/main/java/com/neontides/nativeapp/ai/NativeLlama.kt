package com.neontides.nativeapp.ai

fun interface NativeStreamCallback {
    fun onToken(text: String)
}

object NativeLlama {
    private var loaded = false

    init {
        try {
            System.loadLibrary("neontides_llm")
            loaded = true
        } catch (_: Throwable) {
            loaded = false
        }
    }

    fun libraryLoaded(): Boolean = loaded

    external fun loadModel(path: String, contextSize: Int, threads: Int): Boolean
    external fun isModelLoaded(): Boolean
    external fun prepareConversation(context: String): Boolean
    external fun isConversationPrepared(): Boolean
    external fun rewindConversation(): Boolean
    external fun generate(prompt: String, maxTokens: Int, temperature: Float): String
    external fun generateStreaming(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        callback: NativeStreamCallback
    ): String
    external fun getDiagnostics(): String
    external fun clearDiagnostics()
    external fun unloadModel()
}
