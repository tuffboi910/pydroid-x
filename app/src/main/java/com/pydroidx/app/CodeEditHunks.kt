package com.pydroidx.app

data class CodeEditHunk(
    val oldStart: Int,
    val oldEndExclusive: Int,
    val removed: List<String>,
    val added: List<String>
) {
    val displayStart: Int get() = oldStart + 1
    val displayEnd: Int get() = maxOf(displayStart, oldEndExclusive)
}

internal object CodeEditHunks {
    private const val MAX_DP_CELLS = 250_000

    fun diff(original: String, proposed: String): List<CodeEditHunk> {
        if (original == proposed) return emptyList()
        val old = splitLines(original)
        val new = splitLines(proposed)
        if (old.size * new.size > MAX_DP_CELLS) return singleHunk(old,new)

        val dp = Array(old.size + 1) { IntArray(new.size + 1) }
        for (i in old.lastIndex downTo 0) {
            for (j in new.lastIndex downTo 0) {
                dp[i][j] = if (old[i] == new[j]) 1 + dp[i+1][j+1]
                else maxOf(dp[i+1][j], dp[i][j+1])
            }
        }

        val hunks = mutableListOf<CodeEditHunk>()
        var i=0
        var j=0
        var hunkOldStart=-1
        val removed=mutableListOf<String>()
        val added=mutableListOf<String>()

        fun flush() {
            if (hunkOldStart < 0) return
            hunks += CodeEditHunk(hunkOldStart,hunkOldStart+removed.size,removed.toList(),added.toList())
            hunkOldStart=-1
            removed.clear()
            added.clear()
        }

        while(i<old.size || j<new.size) {
            if(i<old.size && j<new.size && old[i]==new[j]) {
                flush()
                i++; j++
            } else {
                if(hunkOldStart<0) hunkOldStart=i
                if(j<new.size && (i==old.size || dp[i][j+1] >= dp[i+1][j])) {
                    added += new[j++]
                } else if(i<old.size) {
                    removed += old[i++]
                }
            }
        }
        flush()
        return hunks
    }

    fun applySelected(original: String, proposed: String, selected: Set<Int>): String {
        val hunks=diff(original,proposed)
        if(hunks.isEmpty() || selected.isEmpty()) return original
        val lines=splitLines(original).toMutableList()
        hunks.withIndex().filter { it.index in selected }.asReversed().forEach { indexed ->
            val h=indexed.value
            repeat(h.oldEndExclusive-h.oldStart) { lines.removeAt(h.oldStart) }
            lines.addAll(h.oldStart,h.added)
        }
        return joinLines(lines, original.endsWith("\n") || proposed.endsWith("\n"))
    }

    private fun singleHunk(old: List<String>, new: List<String>): List<CodeEditHunk> {
        var prefix=0
        while(prefix<old.size && prefix<new.size && old[prefix]==new[prefix]) prefix++
        var suffix=0
        while(suffix<old.size-prefix && suffix<new.size-prefix &&
            old[old.lastIndex-suffix]==new[new.lastIndex-suffix]) suffix++
        return listOf(CodeEditHunk(
            prefix,
            old.size-suffix,
            old.subList(prefix,old.size-suffix),
            new.subList(prefix,new.size-suffix)
        ))
    }

    private fun splitLines(text: String): List<String> {
        if(text.isEmpty()) return emptyList()
        val normalized=text.removeSuffix("\n")
        return if(normalized.isEmpty()) emptyList() else normalized.split("\n")
    }

    private fun joinLines(lines: List<String>, trailingNewline: Boolean): String {
        val body=lines.joinToString("\n")
        return if(trailingNewline && body.isNotEmpty()) "$body\n" else body
    }
}
