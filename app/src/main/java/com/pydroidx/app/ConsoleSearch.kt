package com.pydroidx.app

internal data class ConsoleSearchHit(val line: Int, val preview: String)

internal object ConsoleSearch {
    fun windowFromLine(output: String, line: Int, maxCharacters: Int = 32_000): String? {
        if (line < 1) return null
        var start = 0
        repeat(line - 1) {
            val next = output.indexOf('\n', start)
            if (next < 0) return null
            start = next + 1
        }
        return "[Viewing retained output from line $line]\n" +
            output.substring(start, (start + maxCharacters.coerceAtLeast(1)).coerceAtMost(output.length))
    }

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
