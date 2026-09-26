package com.pydroidx.app

internal object AiEndpointPolicy {
    const val DEFAULT_OPENAI_ENDPOINT = "https://api.openai.com/v1/chat/completions"

    fun normalizeForStorage(provider: String, endpoint: String): String {
        val trimmed = endpoint.trim()
        return if (provider == "Custom") trimmed
        else trimmed.takeIf { it.startsWith("https://") } ?: DEFAULT_OPENAI_ENDPOINT
    }

    fun requireSafeCustom(endpoint: String): String =
        endpoint.trim().takeIf { it.startsWith("https://") }
            ?: throw IllegalArgumentException("Custom provider needs an https endpoint")
}
