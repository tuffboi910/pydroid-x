package com.pydroidx.app

/** A contiguous edit in the original file, with replacement lines from Astro. */
internal data class CodeHunk(val from: Int, val until: Int, val replacement: List<String>, val before: List<String>)

internal object CodeHunks {
    fun between(original: String, proposed: String): List<CodeHunk> {
        val old = original.split('\n')
        val new = proposed.split('\n')
        if (original == proposed) return emptyList()
        // The quadratic matcher runs only on modest previews. Large files remain one safe edit.
        if (old.size.toLong() * new.size > 80_000L) {
            var prefix = 0
            while (prefix < old.size && prefix < new.size && old[prefix] == new[prefix]) prefix++
            var suffix = 0
            while (suffix < old.size - prefix && suffix < new.size - prefix &&
                old[old.lastIndex - suffix] == new[new.lastIndex - suffix]) suffix++
            return listOf(CodeHunk(prefix, old.size - suffix,
                new.subList(prefix, new.size - suffix), old.subList(prefix, old.size - suffix)))
        }
        val lengths = Array(old.size + 1) { IntArray(new.size + 1) }
        for (i in old.indices.reversed()) for (j in new.indices.reversed()) {
            lengths[i][j] = if (old[i] == new[j]) 1 + lengths[i + 1][j + 1]
                else maxOf(lengths[i + 1][j], lengths[i][j + 1])
        }
        val edits = mutableListOf<CodeHunk>()
        var i = 0
        var j = 0
        while (i < old.size || j < new.size) {
            if (i < old.size && j < new.size && old[i] == new[j]) { i++; j++; continue }
            val start = i
            val added = mutableListOf<String>()
            while (i < old.size || j < new.size) {
                if (i < old.size && j < new.size && old[i] == new[j]) break
                if (j < new.size && (i == old.size || lengths[i][j + 1] >= lengths[i + 1][j])) {
                    added.add(new[j++])
                } else i++
            }
            edits.add(CodeHunk(start, i, added, old.subList(start, i)))
        }
        return if (edits.size <= 40) edits else listOf(CodeHunk(0, old.size, new, old))
    }

    fun apply(original: String, hunks: List<CodeHunk>, selected: Set<Int>): String {
        val lines = original.split('\n')
        val result = mutableListOf<String>()
        var cursor = 0
        for ((index, hunk) in hunks.withIndex()) {
            require(hunk.from >= cursor && hunk.until <= lines.size)
            result.addAll(lines.subList(cursor, hunk.from))
            result.addAll(if (index in selected) hunk.replacement else lines.subList(hunk.from, hunk.until))
            cursor = hunk.until
        }
        result.addAll(lines.subList(cursor, lines.size))
        return result.joinToString("\n")
    }
}
