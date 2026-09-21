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

    fun save(value: String) {
        if (value.isBlank()) { clear(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        prefs.edit()
            .putString("value", Base64.encodeToString(cipher.doFinal(value.toByteArray()), Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun load(): String? = runCatching {
        val encrypted = Base64.decode(prefs.getString("value", null), Base64.NO_WRAP)
        val iv = Base64.decode(prefs.getString("iv", null), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(encrypted))
    }.getOrNull()

    fun clear() = prefs.edit().clear().apply()
}

object AiClient {
    fun chat(providerSetting: String, endpoint: String, apiKey: String, modelSetting: String, prompt: String, code: String?): String {
        val promptText = buildString {
            append(prompt)
            if (code != null) append("\n\nExplicitly shared current file:\n```python\n").append(code).append("\n```")
        }
        val provider = if (providerSetting == "Auto") when {
            apiKey.startsWith("AIza") -> "Gemini"
            apiKey.startsWith("sk-ant-") -> "Claude"
            apiKey.startsWith("sk-or-") -> "OpenRouter"
            apiKey.startsWith("gsk_") -> "Groq"
            else -> "OpenAI"
        } else providerSetting
        val defaultModel = when (provider) {
            "Gemini" -> "gemini-2.5-flash"
            "Claude" -> "claude-3-5-haiku-latest"
            "OpenRouter" -> "openai/gpt-4o-mini"
            "Groq" -> "llama-3.3-70b-versatile"
            else -> "gpt-4o-mini"
        }
        val model = modelSetting.trim().takeUnless { it.isEmpty() || (provider != "OpenAI" && it == "gpt-4o-mini") } ?: defaultModel
        if (provider == "Gemini") return gemini(apiKey, model, promptText)
        if (provider == "Claude") return claude(apiKey, model, promptText)
        val safeEndpoint = when (provider) {
            "OpenRouter" -> "https://openrouter.ai/api/v1/chat/completions"
            "Groq" -> "https://api.groq.com/openai/v1/chat/completions"
            "Custom" -> endpoint.trim().takeIf { it.startsWith("https://") }
                ?: throw IllegalArgumentException("Custom provider needs an https endpoint")
            else -> "https://api.openai.com/v1/chat/completions"
        }
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", "You are PyDroid X's concise Python coding assistant. Explain clearly and never claim code was changed."))
            .put(JSONObject().put("role", "user").put("content", promptText))
        val body = JSONObject().put("model", model).put("messages", messages).put("temperature", 0.2)
        val connection = (URL(safeEndpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
        connection.outputStream.use { it.write(body.toString().toByteArray()) }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (connection.responseCode !in 200..299) {
            val message = runCatching { JSONObject(response).getJSONObject("error").getString("message") }.getOrNull()
            throw IllegalStateException(message ?: "Provider returned HTTP ${connection.responseCode}")
        }
        return JSONObject(response).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
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
            throw IllegalStateException(message?.takeIf { it.isNotBlank() } ?: "Provider returned HTTP ${connection.responseCode}")
        }
        return response
    }
}
