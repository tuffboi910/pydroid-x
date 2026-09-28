package com.pydroidx.app

enum class AnsiColor { DEFAULT, BLACK, RED, GREEN, YELLOW, BLUE, MAGENTA, CYAN, WHITE }

data class AnsiSegment(
    val text: String,
    val color: AnsiColor = AnsiColor.DEFAULT,
    val bold: Boolean = false
)

internal object AnsiTextParser {
    private val csi = Regex("\u001B\\[([0-9;]*)m")

    fun parse(source: String): List<AnsiSegment> {
        if ('\u001B' !in source) return listOf(AnsiSegment(source))
        val result=mutableListOf<AnsiSegment>()
        var color=AnsiColor.DEFAULT
        var bold=false
        var cursor=0
        csi.findAll(source).forEach { match ->
            if(match.range.first>cursor) {
                result += AnsiSegment(source.substring(cursor,match.range.first),color,bold)
            }
            val codes=match.groupValues[1].takeIf(String::isNotBlank)
                ?.split(';')?.mapNotNull(String::toIntOrNull)
                ?: listOf(0)
            codes.forEach { code ->
                when(code) {
                    0 -> { color=AnsiColor.DEFAULT;bold=false }
                    1 -> bold=true
                    22 -> bold=false
                    30 -> color=AnsiColor.BLACK
                    31 -> color=AnsiColor.RED
                    32 -> color=AnsiColor.GREEN
                    33 -> color=AnsiColor.YELLOW
                    34 -> color=AnsiColor.BLUE
                    35 -> color=AnsiColor.MAGENTA
                    36 -> color=AnsiColor.CYAN
                    37 -> color=AnsiColor.WHITE
                    39 -> color=AnsiColor.DEFAULT
                    90 -> color=AnsiColor.BLACK
                    91 -> color=AnsiColor.RED
                    92 -> color=AnsiColor.GREEN
                    93 -> color=AnsiColor.YELLOW
                    94 -> color=AnsiColor.BLUE
                    95 -> color=AnsiColor.MAGENTA
                    96 -> color=AnsiColor.CYAN
                    97 -> color=AnsiColor.WHITE
                }
            }
            cursor=match.range.last+1
        }
        if(cursor<source.length) result += AnsiSegment(source.substring(cursor),color,bold)
        return result.ifEmpty { listOf(AnsiSegment("")) }
    }
}
