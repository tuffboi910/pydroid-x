package com.pydroidx.app

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.isModelLoaded
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Imports GGUF into private storage, then streams tokens through llama.cpp on this device. */
object LocalAiRuntime {
    private var loadedPath: String? = null
    private var loadedContext = 0
    private var loadedThreads = 0

    fun importModel(context: Context, uri: Uri, onProgress: (String) -> Unit): File {
        val resolver = context.contentResolver
        val rawName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "model.gguf"
        require(rawName.endsWith(".gguf", true)) { "Choose a .gguf model file" }
        val size = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
        }
        val dir = File(context.filesDir, "models").apply { mkdirs() }
        if (size != null) require(StatFs(dir.path).availableBytes > size + 64L * 1024 * 1024) {
            "Not enough storage to import this model"
        }
        val safeName = rawName.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").take(100)
        val destination = File(dir, "${System.currentTimeMillis()}-$safeName")
        val temp = File.createTempFile("import-", ".part", dir)
        try {
            resolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Could not open model file" }
                val magic = ByteArray(4)
                require(input.read(magic) == 4 && String(magic, Charsets.US_ASCII) == "GGUF") {
                    "This file is not a GGUF model"
                }
                FileOutputStream(temp).use { output ->
                    output.write(magic)
                    val buffer = ByteArray(1024 * 1024)
                    var copied = 4L
                    var lastUpdate = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (copied - lastUpdate >= 32L * 1024 * 1024) {
                            onProgress(if (size != null && size > 0) "Importing ${copied * 100 / size}%" else "Importing ${copied / 1048576} MB")
                            lastUpdate = copied
                        }
                    }
                    output.fd.sync()
                }
                require(temp.length() > 1024) { "Model file is incomplete" }
                require(destination == temp || temp.renameTo(destination)) { "Could not save model" }
            }
            return destination
        } finally { temp.delete() }
    }

    @Synchronized fun chat(context: Context, path: String, prompt: String, code: String?, history: List<AiMessage>, contextSize: Int, threads: Int, responseTokens: Int, onStatus: (String) -> Unit, onPartial: (String) -> Unit): String = runBlocking {
        require(path.endsWith(".gguf", true) && File(path).isFile) { "Choose an imported GGUF model in AI settings" }
        val engine = AiChat.getInferenceEngine(context)
        engine.state.first { it !is InferenceEngine.State.Initializing && it !is InferenceEngine.State.Uninitialized }
        if (engine.state.value is InferenceEngine.State.Error) throw IllegalStateException("On-device runtime could not start")
        if (loadedPath != path || loadedContext != contextSize || loadedThreads != threads || !engine.state.value.isModelLoaded) {
            if (engine.state.value.isModelLoaded || engine.state.value is InferenceEngine.State.Error) engine.cleanUp()
            onStatus("Loading on-device model…")
            engine.loadModel(path, contextSize, threads)
            loadedPath = path
            loadedContext = contextSize
            loadedThreads = threads
            engine.setSystemPrompt("You are Astro, a helpful Python coding assistant. Give accurate, concise answers. When asked to change code, include a complete python fenced code block for the proposed file.")
        }
        val message = buildString {
            history.takeLast(8).forEach { append(if (it.fromUser) "User: " else "Assistant: ").append(it.text.take(4000)).append("\n") }
            if (code != null) append("Current Python file:\n```python\n").append(code.take(12000)).append("\n```\n")
            append("User: ").append(prompt).append("\nAssistant:")
        }
        onStatus("Generating on device…")
        val answer = StringBuilder()
        engine.sendUserPrompt(message, responseTokens).collect { token ->
            answer.append(token)
            onPartial(answer.toString())
        }
        answer.toString().takeIf { it.isNotBlank() } ?: error("The model produced no response")
    }
}
