package com.pydroidx.app

import com.tensai.llamakt.ChatMessage as LlamaChatMessage
import com.tensai.llamakt.LlamaEngine
import com.tensai.llamakt.SamplingParams
import com.tensai.llamakt.TokenCallback
import java.io.File

data class LocalAiConfig(
    val backend: String = "CPU",
    val gpuLayers: Int = 99,
    val contextTokens: Int = 4096,
    val cpuThreads: Int = 0,
    val maxTokens: Int = 512,
    val temperature: Float = 0.7f,
    val topP: Float = 0.95f,
    val minP: Float = 0.05f,
    val topK: Int = 40
) {
    fun normalized(): LocalAiConfig = copy(
        backend = backend.trim().ifBlank { "CPU" },
        gpuLayers = gpuLayers.coerceIn(0, 999),
        contextTokens = contextTokens.coerceIn(512, 32768),
        cpuThreads = cpuThreads.coerceIn(-1, 32),
        maxTokens = maxTokens.coerceIn(16, 8192),
        temperature = temperature.coerceIn(0f, 5f),
        topP = topP.coerceIn(0f, 1f),
        minP = minP.coerceIn(0f, 1f),
        topK = topK.coerceIn(0, 500)
    )

    fun effectiveGpuLayers(): Int = if (backend.equals("GPU", true)) gpuLayers.coerceAtLeast(1) else 0
}

data class LocalModelInfo(
    val name: String,
    val architecture: String,
    val quant: String,
    val parameters: Long,
    val contextLength: Long,
    val fileSizeBytes: Long
)

class LocalAiEngine {
    private var engine: LlamaEngine? = null
    private var loadedSignature: String? = null

    fun inspect(path: String): LocalModelInfo? {
        val file = File(path)
        if (!file.isFile) return null
        val metadata = LlamaEngine.readMetadata(path) ?: return null
        return LocalModelInfo(
            name = metadata.name.ifBlank { file.nameWithoutExtension },
            architecture = metadata.architecture,
            quant = metadata.quantLabel,
            parameters = metadata.paramCount,
            contextLength = metadata.contextLength,
            fileSizeBytes = metadata.fileSizeBytes
        )
    }

    fun availableBackends(): List<String> {
        val probe = LlamaEngine()
        return try {
            probe.availableBackends().map { info ->
                listOf(info.type, info.description, info.name).filter { it.isNotBlank() }.joinToString(" • ")
            }
        } finally {
            runCatching { probe.free() }
        }
    }

    @Synchronized
    fun unload() {
        engine?.let { runCatching { it.interrupt() }; runCatching { it.free() } }
        engine = null
        loadedSignature = null
    }

    fun interrupt() {
        engine?.let { runCatching { it.interrupt() } }
    }

    @Synchronized
    fun ensureLoaded(path: String, rawConfig: LocalAiConfig, onStatus: (String) -> Unit = {}) {
        val config = rawConfig.normalized()
        val model = File(path)
        require(model.isFile) { "Choose a GGUF model first" }

        val signature = listOf(
            model.canonicalPath, model.length(), model.lastModified(),
            config.backend, config.gpuLayers, config.contextTokens, config.cpuThreads
        ).joinToString("|")
        if (engine != null && loadedSignature == signature) return

        unload()
        onStatus("Checking local AI backends…")
        val candidate = LlamaEngine()
        try {
            val backends = candidate.availableBackends()
            val backendText = backends.joinToString(" ") {
                "${it.type} ${it.description} ${it.name}"
            }.lowercase()

            when {
                config.backend.equals("GPU", true) &&
                    backends.none { it.type.equals("gpu", true) || it.type.equals("igpu", true) ||
                        it.description.contains("Vulkan", true) || it.description.contains("OpenCL", true) } ->
                    error("No supported GPU backend was detected on this device")
                config.backend.equals("Hexagon", true) && !backendText.contains("hexagon") ->
                    error("Hexagon backend is not available in this build. CPU and GPU remain available.")
                config.backend.equals("Hexagon", true) ->
                    error("Hexagon was detected, but this llama.cpp binding cannot route model layers to it yet.")
            }

            onStatus("Loading ${model.name}…")
            candidate.load(
                path = model.canonicalPath,
                nGpuLayers = config.effectiveGpuLayers(),
                nCtx = config.contextTokens,
                nThreads = config.cpuThreads
            )
            engine = candidate
            loadedSignature = signature
            onStatus("Local model ready • ${candidate.activeBackend()}")
        } catch (failure: Throwable) {
            runCatching { candidate.free() }
            throw failure
        }
    }

    fun chat(
        path: String,
        rawConfig: LocalAiConfig,
        prompt: String,
        history: List<AiMessage>,
        code: String?,
        onStatus: (String) -> Unit = {},
        onPartial: (String) -> Unit = {}
    ): String {
        val config = rawConfig.normalized()
        ensureLoaded(path, config, onStatus)
        val active = engine ?: error("Local model failed to load")

        val messages = mutableListOf<LlamaChatMessage>()
        messages += LlamaChatMessage(
            "system",
            "You are Astro, a concise coding assistant inside PY4U. Help with Python and programming. " +
                "When code is shared, preserve the user's intent and do not invent missing files."
        )
        history.takeLast(16).forEach { message ->
            if (message.text.isNotBlank()) {
                messages += LlamaChatMessage(if (message.fromUser) "user" else "assistant", message.text)
            }
        }
        val userContent = buildString {
            append(prompt)
            if (!code.isNullOrBlank()) {
                append("\n\nCurrent Python file:\n```python\n")
                append(code.take(100_000))
                append("\n```")
            }
        }
        messages += LlamaChatMessage("user", userContent)

        val formatted = active.formatChat(messages, enableThinking = false)
        val buffer = StringBuilder()
        val result = active.completion(
            formatted,
            SamplingParams(
                nPredict = config.maxTokens,
                temperature = config.temperature,
                topK = config.topK,
                topP = config.topP,
                minP = config.minP
            ),
            TokenCallback { token ->
                buffer.append(token)
                onPartial(buffer.toString())
            }
        )
        if (result < 0) error("Local inference failed")
        return buffer.toString().trimEnd()
    }
}
