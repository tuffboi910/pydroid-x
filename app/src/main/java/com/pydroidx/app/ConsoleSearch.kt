package com.pydroidx.app

internal data class ConsoleSearchHit(val line: Int, val preview: String)

internal object ConsoleSearch {
    fun find(output: String, query: String, limit: Int = 100): List<ConsoleSearchHit> {
        val needle = query.trim()
        if (needle.isEmpty() || limit <= 0) return emptyList()
        val hits = ArrayList<ConsoleSearchHit>(minOf(limit, 32))
        for ((index, line) in output.lineSequence().withIndex()) {
            if (Thread.currentThread().isInterrupted || hits.size >= limit) break
            if (line.contains(needle, ignoreCase = true)) {
                hits.add(ConsoleSearchHit(index + 1, line.trim().take(200)))
            }
        }
        return hits
    }
}
