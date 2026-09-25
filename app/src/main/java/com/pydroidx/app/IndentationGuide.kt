package com.pydroidx.app

internal object IndentationGuide {
    fun columns(line: String, tabSize: Int = 4): List<Int> {
        if (line.isBlank()) return emptyList()
        var width = 0
        for (character in line) {
            when (character) {
                ' ' -> width++
                '	' -> width += tabSize - (width % tabSize)
                else -> break
            }
        }
        return (tabSize..width step tabSize).toList()
    }
}
