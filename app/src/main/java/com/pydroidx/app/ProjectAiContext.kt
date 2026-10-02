package com.pydroidx.app

import java.io.File

/** Explicitly shared project sources. Call only on the AI worker thread. */
internal object ProjectAiContext {
    /** Only complete files may be offered for an apply-capable project edit. */
    fun editableSources(root: File, currentPath: String, currentText: String,
                        maxCharacters: Int = 18_000): Map<String, String> {
        val budget = maxCharacters.coerceIn(1_500, 24_000)
        val sources = linkedMapOf<String, String>()
        if (currentText.length + currentPath.length + 32 > budget) return emptyMap()
        sources[currentPath] = currentText
        var used = currentText.length + currentPath.length + 32
        for (file in ProjectWorkspace.pythonFiles(root, 300).sortedBy { it.path }) {
            val path = ProjectWorkspace.relativePath(root, file) ?: continue
            if (path == currentPath || sources.size >= 8 || file.length() > 8_000L) continue
            val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: continue
            if (used + text.length + path.length + 32 > budget) continue
            sources[path] = text
            used += text.length + path.length + 32
        }
        return sources
    }

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
