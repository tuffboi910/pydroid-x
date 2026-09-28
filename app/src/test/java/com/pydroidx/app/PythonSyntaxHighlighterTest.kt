package com.pydroidx.app

import org.junit.Assert.assertTrue
import org.junit.Test

class PythonSyntaxHighlighterTest {
    private val palette = SyntaxPalette(1, 2, 3, 4, 5, 6, 7)

    @Test fun assignsPythonTokenClassesAndPreservesExactOffsets() {
        val source = "def greet(name):\n    return print('hi', name)  # note\n"
        val ranges = PythonSyntaxHighlighter.ranges(source, palette)
        fun colored(token: String, color: Int): Boolean = ranges.any {
            it.color == color && source.substring(it.start, it.end) == token
        }
        assertTrue(colored("def", palette.keyword))
        assertTrue(colored("greet", palette.function))
        assertTrue(colored("print", palette.function))
        assertTrue(colored("'hi'", palette.string))
        assertTrue(colored("# note", palette.comment))
    }

    @Test fun longUnterminatedStringsAndCommentsStayWithinSource() {
        val source = "value = 'unfinished\\\n# comment without newline"
        val ranges = PythonSyntaxHighlighter.ranges(source, palette)
        assertTrue(ranges.all { it.start >= 0 && it.start < it.end && it.end <= source.length })
    }

    @Test fun interruptedLargeScanStopsEarly() {
        val source = "name = 1\n".repeat(20_000)
        Thread.currentThread().interrupt()
        val ranges = try { PythonSyntaxHighlighter.ranges(source, palette) }
        finally { Thread.interrupted() }
        assertTrue(ranges.isEmpty())
    }
}
