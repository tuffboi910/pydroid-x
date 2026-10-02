package com.pydroidx.app

internal data class ConsoleAnsiSegment(val text: String, val foreground: Int?, val bold: Boolean)

/** Parses visible ANSI output without letting terminal control sequences reach the UI. */
internal object ConsoleAnsi {
    private val normal = intArrayOf(0xFF17171B.toInt(), 0xFFDC646D.toInt(), 0xFF78D991.toInt(),
        0xFFE8C46A.toInt(), 0xFF81A8F8.toInt(), 0xFFC79AED.toInt(), 0xFF6DCDDC.toInt(), 0xFFD6D9DF.toInt())
    private val bright = intArrayOf(0xFF777985.toInt(), 0xFFFF8793.toInt(), 0xFFA5EDB3.toInt(),
        0xFFFFDC91.toInt(), 0xFFACC4FF.toInt(), 0xFFE4B9FF.toInt(), 0xFFA6EDF5.toInt(), 0xFFFFFFFF.toInt())

    fun parse(source: String): List<ConsoleAnsiSegment> {
        val visible = StringBuilder(source.length)
        val colors = IntArray(source.length + 1)
        val weights = BooleanArray(source.length + 1)
        var color: Int? = null
        var bold = false
        var cursor = 0
        fun lineStart(): Int = visible.lastIndexOf("\n", cursor - 1) + 1
        fun lineEnd(): Int = visible.indexOf("\n", cursor).let { if (it < 0) visible.length else it }
        fun put(char: Char) {
            if (cursor < visible.length && visible[cursor] == '\n') {
                visible.insert(cursor, char)
                System.arraycopy(colors, cursor, colors, cursor + 1, visible.length - cursor - 1)
                System.arraycopy(weights, cursor, weights, cursor + 1, visible.length - cursor - 1)
            } else if (cursor < visible.length) visible.setCharAt(cursor, char)
            else visible.append(char)
            colors[cursor] = color ?: 0
            weights[cursor] = bold
            cursor++
        }
        fun erase(mode: Int) {
            val start = lineStart()
            val end = lineEnd()
            val from = if (mode == 0) cursor else start
            val until = if (mode == 1) (cursor + 1).coerceAtMost(end) else end
            for (position in from until until) {
                visible.setCharAt(position, ' ')
                colors[position] = color ?: 0
                weights[position] = bold
            }
        }
        var index = 0
        while (index < source.length) {
            val char = source[index]
            if (char == '\u001b' && index + 1 < source.length) {
                val kind = source[index + 1]
                if (kind == '[') {
                    var end = index + 2
                    while (end < source.length && end - index <= 64 && source[end] !in '@'..'~') end++
                    if (end < source.length && end - index <= 64) {
                        val command = source[end]
                        val params = source.substring(index + 2, end).split(';').map { it.toIntOrNull() ?: 0 }
                        if (command == 'm') {
                            var p = 0
                            while (p < params.size) {
                                val value = params[p]
                                when {
                                    value == 0 -> { color = null; bold = false }
                                    value == 1 -> bold = true
                                    value == 22 -> bold = false
                                    value == 39 -> color = null
                                    value in 30..37 -> color = normal[value - 30]
                                    value in 90..97 -> color = bright[value - 90]
                                    value == 38 && p + 4 < params.size && params[p + 1] == 2 -> {
                                        color = 0xFF000000.toInt() or (params[p + 2].coerceIn(0, 255) shl 16) or
                                            (params[p + 3].coerceIn(0, 255) shl 8) or params[p + 4].coerceIn(0, 255)
                                        p += 4
                                    }
                                }
                                p++
                            }
                        } else {
                            val amount = params.firstOrNull()?.takeIf { it > 0 } ?: 1
                            when (command) {
                                'K' -> erase(params.firstOrNull() ?: 0)
                                'D' -> cursor = (cursor - amount).coerceAtLeast(lineStart())
                                'C' -> cursor = (cursor + amount).coerceAtMost(lineEnd())
                                'G' -> cursor = (lineStart() + amount - 1).coerceAtMost(lineEnd())
                                'A' -> repeat(amount.coerceAtMost(100)) {
                                    val start = lineStart()
                                    if (start > 0) cursor = (visible.lastIndexOf("\n", start - 2) + 1)
                                        .coerceAtLeast(0).plus(cursor - start).coerceAtMost(start - 1)
                                }
                                'B' -> repeat(amount.coerceAtMost(100)) {
                                    val endOfLine = lineEnd()
                                    if (endOfLine < visible.length) cursor =
                                        (endOfLine + 1 + cursor - lineStart()).coerceAtMost(
                                            visible.indexOf("\n", endOfLine + 1).let { if (it < 0) visible.length else it })
                                }
                            }
                        }
                        index = end + 1
                        continue
                    }
                    if (end >= source.length) break
                } else if (kind == ']') {
                    var end = index + 2
                    while (end < source.length && end - index <= 4_096 && source[end] != '\u0007' &&
                        !(source[end] == '\u001b' && end + 1 < source.length && source[end + 1] == '\\')) end++
                    if (end < source.length && end - index <= 4_096) {
                        index = end + if (source[end] == '\u0007') 1 else 2
                        continue
                    }
                    if (end >= source.length) break
                }
            }
            when {
                char == '\r' -> cursor = lineStart()
                char == '\b' -> cursor = (cursor - 1).coerceAtLeast(lineStart())
                char == '\n' -> {
                    cursor = lineEnd()
                    if (cursor < visible.length && visible[cursor] == '\n') cursor++ else put('\n')
                }
                char >= ' ' || char == '\t' -> put(char)
            }
            index++
        }
        val result = ArrayList<ConsoleAnsiSegment>()
        var start = 0
        while (start < visible.length) {
            var end = start + 1
            while (end < visible.length && colors[end] == colors[start] && weights[end] == weights[start]) end++
            result.add(ConsoleAnsiSegment(visible.substring(start, end),
                colors[start].takeIf { it != 0 }, weights[start]))
            start = end
        }
        return result
    }
}
