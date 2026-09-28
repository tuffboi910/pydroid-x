package com.pydroidx.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleSearchTest {
    @Test fun findsCaseInsensitiveMatchesWithLocations() {
        val hits=ConsoleSearch.search("Ready\nERROR one\nerror two","error")
        assertEquals(2,hits.size)
        assertEquals(listOf(2,3),hits.map { it.line })
        assertEquals(listOf(1,1),hits.map { it.column })
    }

    @Test fun findsRepeatedMatchesOnOneLine() {
        val hits=ConsoleSearch.search("x x X","x")
        assertEquals(listOf(1,3,5),hits.map { it.column })
    }

    @Test fun blankQueryAndLimitsAreBounded() {
        assertTrue(ConsoleSearch.search("abc"," ").isEmpty())
        assertEquals(2,ConsoleSearch.search("a a a","a",2).size)
    }
}
