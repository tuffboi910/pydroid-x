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
    fun chat(endpoint: String, apiKey: String, model: String, prompt: String, code: String?): String {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", "You are PyDroid X's concise Python coding assistant. Explain clearly and never claim code was changed."))
            .put(JSONObject().put("role", "user").put("content", buildString {
                append(prompt)
                if (code != null) append("\n\nExplicitly shared current file:\n```python\n").append(code).append("\n```")
            }))
        val body = JSONObject().put("model", model).put("messages", messages).put("temperature", 0.2)
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
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
}
