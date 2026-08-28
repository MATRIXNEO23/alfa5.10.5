package ai.mlc.mlcllm

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel

/**
 * API di compilazione compatibile con mlc4j. La build dual-engine sostituisce
 * interamente questo modulo con dist/lib/mlc4j, generato da mlc_llm package.
 */
class MLCEngine {
    val chat = Chat()

    init {
        error("Runtime MLC non incluso in questa build")
    }

    fun reload(modelPath: String, modelLib: String) = Unit
    fun reset() = Unit
    fun unload() = Unit
}

class Chat {
    val completions = Completions()
}

class Completions {
    suspend fun create(
        messages: List<OpenAIProtocol.ChatCompletionMessage>,
        model: String? = null,
        max_tokens: Int? = null,
        stream: Boolean = true,
        stream_options: OpenAIProtocol.StreamOptions? = null,
        temperature: Float? = null,
        top_p: Float? = null
    ): ReceiveChannel<OpenAIProtocol.ChatCompletionStreamResponse> =
        Channel<OpenAIProtocol.ChatCompletionStreamResponse>().apply { close() }
}

object OpenAIProtocol {
    enum class ChatCompletionRole { system, user, assistant, tool }

    data class ChatCompletionMessageContent(val text: String? = null) {
        fun asText(): String = text.orEmpty()
    }

    data class ChatCompletionMessage(
        val role: ChatCompletionRole,
        val content: ChatCompletionMessageContent? = null
    ) {
        constructor(role: ChatCompletionRole, content: String) :
            this(role, ChatCompletionMessageContent(content))
    }

    data class StreamOptions(val include_usage: Boolean = false)

    data class ChatCompletionStreamResponseChoice(
        val delta: ChatCompletionMessage
    )

    data class ChatCompletionStreamResponse(
        val choices: List<ChatCompletionStreamResponseChoice> = emptyList(),
        val usage: Any? = null
    )
}
