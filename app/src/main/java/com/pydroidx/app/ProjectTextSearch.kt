package com.pydroidx.app

import java.io.File

internal data class ProjectSearchHit(val fileName: String, val line: Int, val preview: String)

/** Bounded scan of local Python sources. Call from a worker, never the UI thread. */
internal object ProjectTextSearch {
    fun search(directory: File, query: String, currentFile: String, currentText: String,
               limit: Int = 200): List<ProjectSearchHit> {
        val needle = query.trim()
        if (needle.isEmpty() || limit <= 0) return emptyList()
        val files = ProjectWorkspace.pythonFiles(directory).mapNotNull { file ->
            ProjectWorkspace.relativePath(directory, file)?.let { it to file }
        }.sortedBy { it.first }
        val hits = ArrayList<ProjectSearchHit>(minOf(limit, 32))
        for ((relative, file) in files) {
            if (Thread.currentThread().isInterrupted) break
            if (relative != currentFile && file.length() > 1_000_000L) continue
            val source = if (relative == currentFile) currentText else runCatching {
                file.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.getOrNull() ?: continue
            if (source.length > 1_000_000) continue
            source.lineSequence().forEachIndexed { index, line ->
                if (hits.size < limit && line.contains(needle, ignoreCase = true)) {
                    hits.add(ProjectSearchHit(relative, index + 1, line.trim().take(160)))
                }
            }
            if (hits.size >= limit) break
        }
        return hits
    }
}
