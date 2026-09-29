package com.pydroidx.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ConsoleSearchTest {
    @Test fun findsErrorsWithCorrectLinesAndBoundsResults() {
        val output = "ready\nERROR: bad\ncontinuing\nerror: next\n"
        assertEquals(listOf(ConsoleSearchHit(2, "ERROR: bad"), ConsoleSearchHit(4, "error: next")),
            ConsoleSearch.find(output, "error"))
        assertEquals(1, ConsoleSearch.find(output, "error", 1).size)
        assertEquals(emptyList<ConsoleSearchHit>(), ConsoleSearch.find(output, "  "))
    }

    @Test fun searchHitOpensBoundedOutputFromThatLine() {
        val output = "ready\ntrace\nERROR: bad\nmore output\n"
        assertEquals("[Viewing retained output from line 3]\nERROR: bad\nmore",
            ConsoleSearch.windowFromLine(output, 3, 15))
        assertEquals(null, ConsoleSearch.windowFromLine(output, 10))
    }
}
