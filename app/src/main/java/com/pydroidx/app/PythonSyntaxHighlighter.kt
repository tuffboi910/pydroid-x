package com.pydroidx.app

internal data class SyntaxPalette(
    val text: Int,
    val comment: Int,
    val string: Int,
    val number: Int,
    val keyword: Int,
    val function: Int,
    val variable: Int
)

internal data class SyntaxRange(val start: Int, val end: Int, val color: Int)

/** Pure lexer so full-file scanning can run away from the Android UI thread. */
internal object PythonSyntaxHighlighter {
    private val keywords = setOf(
        "and", "as", "assert", "async", "await", "break", "case", "class", "continue",
        "def", "del", "elif", "else", "except", "finally", "for", "from", "global",
        "if", "import", "in", "is", "lambda", "match", "nonlocal", "not", "or", "pass",
        "raise", "return", "try", "while", "with", "yield"
    )
    private val constants = setOf("True", "False", "None", "NotImplemented", "Ellipsis")
    private val builtins = setOf(
        "abs", "all", "any", "bin", "bool", "bytearray", "bytes", "callable", "chr", "classmethod",
        "compile", "complex", "delattr", "dict", "dir", "divmod", "enumerate", "eval", "exec", "filter",
        "float", "format", "frozenset", "getattr", "globals", "hasattr", "hash", "help", "hex", "id",
        "input", "int", "isinstance", "issubclass", "iter", "len", "list", "locals", "map", "max", "memoryview",
        "min", "next", "object", "oct", "open", "ord", "pow", "print", "property", "range", "repr", "reversed",
        "round", "set", "setattr", "slice", "sorted", "staticmethod", "str", "sum", "super", "tuple", "type", "vars", "zip"
    )

    fun ranges(source: String, palette: SyntaxPalette): List<SyntaxRange> {
        val result = ArrayList<SyntaxRange>()
        fun add(start: Int, end: Int, color: Int) {
            if (end > start) result += SyntaxRange(start, end, color)
        }
        var index = 0
        while (index < source.length) {
            if ((index and 1023) == 0 && Thread.currentThread().isInterrupted) return emptyList()
            val start = index
            when {
                source[index] == '#' -> {
                    while (index < source.length && source[index] != '\n') {
                        index++
                        if ((index and 1023) == 0 && Thread.currentThread().isInterrupted) return emptyList()
                    }
                    add(start, index, palette.comment)
                }
                source[index] == '\'' || source[index] == '"' -> {
                    val quote = source[index]
                    val triple = index + 2 < source.length && source[index + 1] == quote && source[index + 2] == quote
                    index += if (triple) 3 else 1
                    while (index < source.length) {
                        if ((index and 1023) == 0 && Thread.currentThread().isInterrupted) return emptyList()
                        if (source[index] == '\\') {
                            index = (index + 2).coerceAtMost(source.length)
                            continue
                        }
                        if (triple && index + 2 < source.length && source[index] == quote &&
                            source[index + 1] == quote && source[index + 2] == quote) {
                            index += 3
                            break
                        }
                        if (!triple && source[index] == quote) {
                            index++
                            break
                        }
                        index++
                    }
                    add(start, index, palette.string)
                }
                source[index].isDigit() -> {
                    while (index < source.length &&
                        (source[index].isDigit() || source[index] in ".xXabcdefABCDEF_")) {
                        index++
                        if ((index and 1023) == 0 && Thread.currentThread().isInterrupted) return emptyList()
                    }
                    add(start, index, palette.number)
                }
                source[index].isLetter() || source[index] == '_' -> {
                    while (index < source.length && (source[index].isLetterOrDigit() || source[index] == '_')) {
                        index++
                        if ((index and 1023) == 0 && Thread.currentThread().isInterrupted) return emptyList()
                    }
                    val word = source.substring(start, index)
                    var lookAhead = index
                    while (lookAhead < source.length && source[lookAhead].isWhitespace()) lookAhead++
                    val next = source.getOrNull(lookAhead)
                    val color = when {
                        word in keywords -> palette.keyword
                        word in constants -> 0xFF569CD6.toInt()
                        word in builtins || next == '(' -> palette.function
                        else -> palette.variable
                    }
                    add(start, index, color)
                }
                else -> index++
            }
        }
        return result
    }
}
