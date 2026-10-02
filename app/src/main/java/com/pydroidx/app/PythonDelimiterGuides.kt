package com.pydroidx.app

internal data class DelimiterPair(val open: Int, val close: Int, val width: Int = 1)

/** Linear scan off the UI thread; comments and escaped/triple strings are respected. */
internal object PythonDelimiterGuides {
    fun pairs(source: String): List<DelimiterPair> {
        val result = ArrayList<DelimiterPair>()
        val stack = ArrayList<Pair<Char, Int>>()
        var i = 0
        while (i < source.length) {
            val c = source[i]
            if (c == '#') {
                while (i < source.length && source[i] != '\n') i++
                continue
            }
            if (c == '\'' || c == '"') {
                val start = i
                val width = if (source.getOrNull(i + 1) == c && source.getOrNull(i + 2) == c) 3 else 1
                i += width
                while (i < source.length) {
                    if (source[i] == '\\') { i += 2; continue }
                    if (width == 1 && source[i] == '\n') break
                    if (source[i] == c && (width == 1 ||
                        source.getOrNull(i + 1) == c && source.getOrNull(i + 2) == c)) {
                        result += DelimiterPair(start, i, width)
                        i += width
                        break
                    }
                    i++
                }
                continue
            }
            if (c in "([{ " && c != ' ') stack += c to i
            else if (c in ")]}") {
                val expected = when (c) { ')' -> '('; ']' -> '['; else -> '{' }
                if (stack.lastOrNull()?.first == expected) {
                    result += DelimiterPair(stack.removeAt(stack.lastIndex).second, i)
                } else stack.clear()
            }
            i++
        }
        return result
    }
}
