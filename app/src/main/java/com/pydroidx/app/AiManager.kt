package com.pydroidx.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureAiKeyStore(private val context: Context) {
    private val alias = "pydroid_x_ai_key"
    private val prefs = context.getSharedPreferences("ai_secure", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
            generateKey()
        }
    }

    fun save(value: String, slot: Int = 0) {
        if (value.isBlank()) { clear(slot); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key())
        prefs.edit()
            .putString("value_$slot",Base64.encodeToString(cipher.doFinal(value.toByteArray()),Base64.NO_WRAP))
            .putString("iv_$slot",Base64.encodeToString(cipher.iv,Base64.NO_WRAP))
            .apply()
    }

    fun load(slot: Int = 0): String? = runCatching {
        val legacyValue = if (slot == 0) prefs.getString("value",null) else null
        val legacyIv = if (slot == 0) prefs.getString("iv",null) else null
        val encrypted = Base64.decode(prefs.getString("value_$slot",legacyValue),Base64.NO_WRAP)
        val iv = Base64.decode(prefs.getString("iv_$slot",legacyIv),Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,iv))
        String(cipher.doFinal(encrypted))
    }.getOrNull()

    fun clear(slot: Int = 0) {
        val editor=prefs.edit().remove("value_$slot").remove("iv_$slot")
        if (slot == 0) editor.remove("value").remove("iv")
        editor.apply()
    }

}

private class ProviderHttpException(val code: Int, message: String) : IllegalStateException(message)

object AiClient {
    fun chat(providerSetting: String, endpoint: String, apiKey: String, modelSetting: String, prompt: String, code: String?, history: List<AiMessage> = emptyList(), revealDelayMs: Long = 18L, onStatus: (String) -> Unit = {}, onPartial: (String) -> Unit = {}): String {
        val promptText = buildString {
            val context = history.filter { it.text.isNotBlank() }.takeLast(12)
            if (context.isNotEmpty()) {
                append("Recent conversation context:\n")
                context.forEach {
                    append(if (it.fromUser) "User: " else "Helper: ")
                        .append(it.text.take(12_000))
                        .append("\n")
                }
                append("\nCurrent request:\n")
            }
            append(prompt.take(120_000))
            if (code != null) append("\n\nExplicitly shared current file:\n```python\n").append(code.take(120_000)).append("\n```")
        }
        val provider = if (providerSetting == "Auto") when {
            apiKey.startsWith("AIza") -> "Gemini"
            apiKey.startsWith("sk-ant-") -> "Claude"
            apiKey.startsWith("sk-or-") -> "OpenRouter"
            apiKey.startsWith("gsk_") -> "Groq"
            else -> "OpenAI"
        } else providerSetting
        val defaultModel = when (provider) {
            "Gemini" -> "gemini-3.6-flash"
            "Claude" -> "claude-3-5-haiku-latest"
            "OpenRouter" -> "openai/gpt-4o-mini"
            "Groq" -> "llama-3.3-70b-versatile"
            else -> "gpt-4o-mini"
        }
        val model = modelSetting.trim().takeUnless { it.isEmpty() || (provider != "OpenAI" && it == "gpt-4o-mini") } ?: defaultModel
        if (provider == "Gemini") return revealWords(geminiWithFallback(apiKey, model, promptText, onStatus), onPartial, revealDelayMs)
        if (provider == "Claude") return revealWords(claude(apiKey, model, promptText), onPartial, revealDelayMs)
        val safeEndpoint = when (provider) {
            "OpenRouter" -> "https://openrouter.ai/api/v1/chat/completions"
            "Groq" -> "https://api.groq.com/openai/v1/chat/completions"
            "Custom" -> AiEndpointPolicy.requireSafeCustom(endpoint)
            else -> "https://api.openai.com/v1/chat/completions"
        }
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
            .put(JSONObject().put("role", "user").put("content", promptText))
        val body = JSONObject().put("model", model).put("messages", messages).put("temperature", 0.2).put("stream", true)
        val connection = (URL(safeEndpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
        connection.outputStream.use { it.write(body.toString().toByteArray()) }
        if (connection.responseCode !in 200..299) {
            val response = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val message = runCatching { JSONObject(response).getJSONObject("error").getString("message") }.getOrNull()
            throw IllegalStateException(message ?: "Provider returned HTTP ${connection.responseCode}")
        }
        val answer = StringBuilder()
        val plainResponse = StringBuilder()
        connection.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (!line.startsWith("data:")) {
                    plainResponse.append(line)
                    return@forEach
                }
                val payload = line.removePrefix("data:").trim()
                if (payload.isEmpty() || payload == "[DONE]") return@forEach
                val delta = runCatching {
                    JSONObject(payload).getJSONArray("choices").getJSONObject(0)
                        .optJSONObject("delta")?.optString("content").orEmpty()
                }.getOrDefault("")
                if (delta.isNotEmpty()) {
                    answer.append(delta)
                    if (revealDelayMs > 0) onPartial(answer.toString())
                }
            }
        }
        if (answer.isNotEmpty()) return answer.toString()
        val nonStreaming = runCatching {
            JSONObject(plainResponse.toString()).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
        }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Provider returned an empty or unsupported response")
        return revealWords(nonStreaming,onPartial,revealDelayMs)
    }

    private const val SYSTEM_PROMPT = """You are Astro, PY4U's concise Python coding assistant. Explain clearly. If the user asks to fix, add, remove, refactor, or otherwise change their code, return the COMPLETE updated current file in exactly one fenced python block; the app will ask the user before applying it, so never claim it was already applied. When a short lesson would genuinely help, end with exactly [TEACH:short topic]. Do not add a teaching offer to every reply."""

    private fun revealWords(answer: String, onPartial: (String) -> Unit, delayMs: Long): String {
        if (delayMs <= 0) return answer
        val chunks = Regex("\\S+\\s*").findAll(answer).map { it.value }
        val visible = StringBuilder()
        chunks.forEach {
            visible.append(it)
            onPartial(visible.toString())
            try { Thread.sleep(delayMs) } catch (_: InterruptedException) { return answer }
        }
        return answer
    }

    private fun geminiWithFallback(apiKey: String, requestedModel: String, prompt: String, onStatus: (String) -> Unit): String {
        val models = listOf(requestedModel, "gemini-3.6-flash", "gemini-3.5-flash", "gemini-2.5-flash").distinct()
        var lastFailure: Throwable? = null
        models.forEachIndexed { modelIndex, candidate ->
            repeat(2) { attempt ->
                try {
                    if (modelIndex > 0 && attempt == 0) onStatus("Switching to $candidate…")
                    else if (attempt > 0) onStatus("Retrying $candidate…")
                    return gemini(apiKey, candidate, prompt)
                } catch (failure: Throwable) {
                    lastFailure = failure
                    val retryable = failure !is ProviderHttpException ||
                        failure.code in listOf(404, 408, 429, 500, 502, 503, 504)
                    if (!retryable) throw failure
                    if (attempt == 0) try { Thread.sleep(700) } catch (_: InterruptedException) { throw failure }
                }
            }
        }
        throw lastFailure ?: IllegalStateException("No Gemini model was available")
    }

    private fun gemini(apiKey: String, model: String, prompt: String): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
        val response = post(url, body, mapOf("Content-Type" to "application/json"))
        return JSONObject(response).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
    }

    private fun claude(apiKey: String, model: String, prompt: String): String {
        val body = JSONObject().put("model", model).put("max_tokens", 2048)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        val response = post("https://api.anthropic.com/v1/messages", body, mapOf(
            "Content-Type" to "application/json", "x-api-key" to apiKey, "anthropic-version" to "2023-06-01"
        ))
        return JSONObject(response).getJSONArray("content").getJSONObject(0).getString("text")
    }

    private fun post(url: String, body: JSONObject, headers: Map<String,String>): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 20_000; readTimeout = 60_000; doOutput = true
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        connection.outputStream.use { it.write(body.toString().toByteArray()) }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (connection.responseCode !in 200..299) {
            val message = runCatching { JSONObject(response).getJSONObject("error").optString("message") }.getOrNull()
            throw ProviderHttpException(connection.responseCode, message?.takeIf { it.isNotBlank() } ?: "Provider returned HTTP ${connection.responseCode}")
        }
        return response
    }
}
