package com.neontides.nativeapp.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class OnlineAiClient(private val settings: SecureAiSettings) {

    data class Result(val text: String, val engine: String)

    suspend fun generate(prompt: String): Result? = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        if (settings.hasGemini()) {
            runCatching { callGemini(prompt) }
                .onFailure { errors += "Gemini: ${friendlyError(it)}" }
                .getOrNull()?.let { return@withContext Result(it, "Gemini") }
        }
        if (settings.hasOpenAi()) {
            runCatching { callOpenAi(prompt) }
                .onFailure { errors += "OpenAI: ${friendlyError(it)}" }
                .getOrNull()?.let { return@withContext Result(it, "OpenAI") }
        }
        if (errors.isNotEmpty()) error(errors.joinToString(" · "))
        null
    }

    private fun friendlyError(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            "HTTP 400" in message || "HTTP 401" in message || "HTTP 403" in message -> "chiave non valida o senza autorizzazione"
            "HTTP 429" in message -> "quota o limite richieste esaurito"
            "timeout" in message.lowercase() -> "connessione scaduta"
            else -> message.take(120).ifBlank { "servizio non raggiungibile" }
        }
    }

    private fun callGemini(prompt: String): String {
        val body = JSONObject()
            .put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            ))
            .put("generationConfig", JSONObject()
                .put("responseMimeType", "application/json")
                .put("maxOutputTokens", 220)
                .put("temperature", 0.75)
            )
        val response = post(
            url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent",
            headers = mapOf("x-goog-api-key" to settings.geminiKey),
            body = body
        )
        return JSONObject(response)
            .getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
            .getString("text")
    }

    private fun callOpenAi(prompt: String): String {
        val body = JSONObject()
            .put("model", "gpt-4o-mini")
            .put("input", prompt)
            .put("store", false)
            .put("max_output_tokens", 220)
            .put("text", JSONObject().put("format", JSONObject().put("type", "json_object")))
        val response = post(
            url = "https://api.openai.com/v1/responses",
            headers = mapOf("Authorization" to "Bearer ${settings.openAiKey}"),
            body = body
        )
        val output = JSONObject(response).getJSONArray("output")
        for (i in 0 until output.length()) {
            val content = output.getJSONObject(i).optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val item = content.getJSONObject(j)
                if (item.optString("type") == "output_text") return item.getString("text")
            }
        }
        error("OpenAI non ha restituito testo")
    }

    private fun post(url: String, headers: Map<String, String>, body: JSONObject): String {
        val connection = URL(url).openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 6_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("HTTP $code: ${text.take(180)}")
            return text
        } finally {
            connection.disconnect()
        }
    }
}
