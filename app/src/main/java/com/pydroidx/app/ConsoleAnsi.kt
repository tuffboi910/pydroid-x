package com.pydroidx.app

internal data class ConsoleAnsiSegment(val text: String, val foreground: Int?, val bold: Boolean)

/** Parses visible ANSI output without letting terminal control sequences reach the UI. */
internal object ConsoleAnsi {
    private val normal = intArrayOf(0xFF17171B.toInt(), 0xFFDC646D.toInt(), 0xFF78D991.toInt(),
        0xFFE8C46A.toInt(), 0xFF81A8F8.toInt(), 0xFFC79AED.toInt(), 0xFF6DCDDC.toInt(), 0xFFD6D9DF.toInt())
    private val bright = intArrayOf(0xFF777985.toInt(), 0xFFFF8793.toInt(), 0xFFA5EDB3.toInt(),
        0xFFFFDC91.toInt(), 0xFFACC4FF.toInt(), 0xFFE4B9FF.toInt(), 0xFFA6EDF5.toInt(), 0xFFFFFFFF.toInt())

    fun parse(source: String): List<ConsoleAnsiSegment> {
        val result = ArrayList<ConsoleAnsiSegment>()
        val pending = StringBuilder()
        var color: Int? = null
        var bold = false
        fun flush() {
            if (pending.isNotEmpty()) {
                result.add(ConsoleAnsiSegment(pending.toString(), color, bold))
                pending.clear()
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
                        if (source[end] == 'm') {
                            flush()
                            val params = source.substring(index + 2, end).split(';').map { it.toIntOrNull() ?: 0 }
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
            if (char >= ' ' || char == '\n' || char == '\t') pending.append(char)
            index++
        }
        flush()
        return result
    }
}
