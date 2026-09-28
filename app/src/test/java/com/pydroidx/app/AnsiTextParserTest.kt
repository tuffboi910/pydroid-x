package com.pydroidx.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnsiTextParserTest {
    @Test fun plainTextPassesThrough() {
        assertEquals(listOf(AnsiSegment("hello")),AnsiTextParser.parse("hello"))
    }

    @Test fun colorsAndResetAreParsed() {
        val parts=AnsiTextParser.parse("A\u001B[31mRED\u001B[0mZ")
        assertEquals("A",parts[0].text)
        assertEquals(AnsiColor.DEFAULT,parts[0].color)
        assertEquals("RED",parts[1].text)
        assertEquals(AnsiColor.RED,parts[1].color)
        assertEquals("Z",parts[2].text)
        assertEquals(AnsiColor.DEFAULT,parts[2].color)
    }

    @Test fun boldCanBeEnabledAndDisabled() {
        val parts=AnsiTextParser.parse("\u001B[1mB\u001B[22mN")
        assertTrue(parts[0].bold)
        assertFalse(parts[1].bold)
    }

    @Test fun combinedCodesWork() {
        val parts=AnsiTextParser.parse("\u001B[1;36mcyan")
        assertEquals(AnsiColor.CYAN,parts.single().color)
        assertTrue(parts.single().bold)
    }
}
