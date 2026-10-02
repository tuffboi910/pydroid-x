package com.pydroidx.app
import org.junit.Assert.*
import org.junit.Test
class PythonDelimiterGuidesTest {
    @Test fun nestedPairsIgnoreCommentsAndQuotedBrackets() {
        val source = "x = ([{\"key\": \"(\"}]) # ("
        val pairs = PythonDelimiterGuides.pairs(source)
        assertEquals(5, pairs.size)
        assertEquals('(', source[pairs.last().open])
        assertEquals(')', source[pairs.last().close])
    }
    @Test fun escapedQuotesAndTripleStrings() {
        val source = "\"a\\\"b\" + \"\"\"many\nlines[]\"\"\""
        val pairs = PythonDelimiterGuides.pairs(source)
        assertEquals(2, pairs.size)
        assertEquals(3, pairs.last().width)
    }
    @Test fun incompleteAndMismatchedPairsAreNotConnected() {
        assertTrue(PythonDelimiterGuides.pairs("([)] # {} ").isEmpty())
        assertTrue(PythonDelimiterGuides.pairs("\"unfinished").isEmpty())
    }
}
