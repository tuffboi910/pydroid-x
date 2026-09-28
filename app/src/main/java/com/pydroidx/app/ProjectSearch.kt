package com.pydroidx.app

import java.io.File

data class ProjectSearchHit(
    val fileName: String,
    val line: Int,
    val column: Int,
    val lineText: String
)

/**
 * Bounded, case-insensitive project search kept off the UI thread by the caller.
 * PY4U currently exposes root-level Python files, so this deliberately mirrors that scope.
 */
internal object ProjectSearch {
    private const val MAX_RESULTS = 250
    private const val MAX_FILE_BYTES = 2_000_000L

    fun search(projectDir: File, query: String, maxResults: Int = MAX_RESULTS): List<ProjectSearchHit> {
        val needle = query.trim()
        if (needle.isEmpty() || !projectDir.isDirectory || maxResults <= 0) return emptyList()

        val results = ArrayList<ProjectSearchHit>(minOf(maxResults, 32))
        val files = projectDir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension.equals("py", ignoreCase = true) && it.length() <= MAX_FILE_BYTES }
            ?.sortedBy { it.name.lowercase() }
            ?.toList()
            .orEmpty()

        for (file in files) {
            file.bufferedReader().use { reader ->
                var lineNumber = 1
                while (results.size < maxResults) {
                    val line = reader.readLine() ?: break
                    var from = 0
                    while (from <= line.length && results.size < maxResults) {
                        val index = line.indexOf(needle, startIndex = from, ignoreCase = true)
                        if (index < 0) break
                        results += ProjectSearchHit(file.name, lineNumber, index + 1, line)
                        from = index + needle.length.coerceAtLeast(1)
                    }
                    lineNumber++
                }
            }
            if (results.size >= maxResults) break
        }
        return results
    }
}
