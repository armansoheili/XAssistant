package com.xcomment.android.ai

import com.xcomment.android.data.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/** Outcome of a reply-generation request. */
sealed interface AiResult {
    data class Success(val ideas: List<String>) : AiResult
    data class Failure(val message: String) : AiResult
}

/**
 * Talks to an OpenAI-compatible chat-completions endpoint.
 * Blocking — call from a background thread.
 */
object AiClient {

    fun generate(post: String, settings: Settings): AiResult {
        if (post.isBlank()) return AiResult.Failure("No post text detected.")
        if (!settings.hasApiKey) return AiResult.Failure("API key is missing. Open XComment and add it.")
        if (settings.apiUrl.isBlank() || settings.model.isBlank()) {
            return AiResult.Failure("API URL or model is missing in settings.")
        }

        return runCatching {
            val payload = buildPayload(post, settings)
            val raw = post(settings, payload)
            val content = extractContent(raw)
            val ideas = parseComments(content, settings.count)
            if (ideas.isEmpty()) {
                AiResult.Failure("Could not parse the AI response. Try a different model.")
            } else {
                AiResult.Success(ideas)
            }
        }.getOrElse { e ->
            AiResult.Failure(friendlyError(e))
        }
    }

    private fun buildPayload(post: String, settings: Settings): String {
        val system = buildString {
            append("You write short, natural, human-sounding replies for X (Twitter). ")
            append("Tone: ${settings.tone.promptHint}. ")
            append("Write in ${settings.language.promptHint}. ")
            append("Each reply must be under 240 characters, with no surrounding quotes and no hashtags unless clearly relevant. ")
            append("Return STRICT JSON only, exactly: {\"comments\":[")
            append((1..settings.count).joinToString(",") { "\"reply$it\"" })
            append("]}")
        }
        return JSONObject().apply {
            put("model", settings.model)
            put("temperature", 0.6)
            put("max_tokens", 70 * settings.count + 60)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", system))
                put(JSONObject().put("role", "user").put("content", post.take(1200)))
            })
        }.toString()
    }

    private fun post(settings: Settings, body: String): String {
        val conn = (URL(settings.apiUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 25_000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
            doOutput = true
        }
        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use(BufferedReader::readText) }.orEmpty()
            if (code !in 200..299) {
                throw RuntimeException("API error $code: ${extractApiError(raw)}")
            }
            return raw
        } finally {
            conn.disconnect()
        }
    }

    private fun extractContent(raw: String): String =
        JSONObject(raw)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")

    private fun extractApiError(raw: String): String = runCatching {
        JSONObject(raw).getJSONObject("error").optString("message").ifBlank { raw.take(180) }
    }.getOrElse { raw.take(180) }

    private fun parseComments(content: String, limit: Int): List<String> {
        val cleaned = content.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        return runCatching {
            val start = cleaned.indexOf('{')
            val end = cleaned.lastIndexOf('}')
            val json = if (start in 0 until end) cleaned.substring(start, end + 1) else cleaned
            val arr = JSONObject(json).getJSONArray("comments")
            (0 until arr.length()).mapNotNull { i ->
                when (val item = arr.get(i)) {
                    is String -> item.trim()
                    is JSONObject -> item.optString("text").ifBlank { item.optString("reply") }.trim()
                    else -> item.toString().trim()
                }
            }
        }.getOrElse {
            // Fallback: treat each meaningful line as a suggestion.
            cleaned.lines()
                .map { it.trim().trimStart('-', '•', '*', ' ').trim('"').trim() }
                .filter { it.length > 3 && !it.startsWith("{") && !it.startsWith("}") }
        }.map(::stripQuotes).filter { it.isNotBlank() }.distinct().take(limit)
    }

    private fun stripQuotes(s: String): String =
        s.trim().removeSurrounding("\"").removeSurrounding("“", "”").trim()

    private fun friendlyError(e: Throwable): String = when (e) {
        is java.net.UnknownHostException -> "No internet connection."
        is java.net.SocketTimeoutException -> "The AI request timed out. Try again."
        else -> e.message ?: e.javaClass.simpleName
    }
}
