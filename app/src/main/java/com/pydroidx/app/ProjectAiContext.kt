package com.pydroidx.app

import java.io.File

/** Explicitly shared project sources. Call only on the AI worker thread. */
internal object ProjectAiContext {
    fun build(root: File, currentPath: String, currentText: String,
              maxFiles: Int = 12, maxCharacters: Int = 24_000): String {
        val files = ProjectWorkspace.pythonFiles(root, maxFiles.coerceAtLeast(1) * 20)
            .mapNotNull { file -> ProjectWorkspace.relativePath(root, file)?.let { it to file } }
            .sortedBy { it.first }
        val budget = maxCharacters.coerceIn(1, 50_000)
        val output = StringBuilder()
        fun append(path: String, source: String, fileLimit: Int) {
            if (output.length >= budget) return
            val header = "\n\n# File: $path\n"
            val available = budget - output.length - header.length
            if (available <= 0) return
            output.append(header).append(source.take(minOf(available, fileLimit)))
        }
        append(currentPath, currentText, maxOf(1, budget / 2))
        var count = 1
        for ((path, file) in files) {
            if (path == currentPath || count >= maxFiles || output.length >= budget) continue
            if (file.length() > 100_000L) continue
            val source = runCatching { file.bufferedReader(Charsets.UTF_8).use { it.readText() } }.getOrNull() ?: continue
            append(path, source, 6_000)
            count++
        }
        return output.toString()
    }
}
