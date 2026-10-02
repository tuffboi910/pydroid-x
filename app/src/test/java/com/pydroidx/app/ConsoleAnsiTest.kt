package com.pydroidx.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleAnsiTest {
    @Test fun rendersColorAndResetWithoutEscapeCharacters() {
        val parts = ConsoleAnsi.parse("before \u001b[31mred\u001b[0m after")
        assertEquals("before red after", parts.joinToString("") { it.text })
        assertEquals(0xFFDC646D.toInt(), parts.first { it.text == "red" }.foreground)
        assertEquals(null, parts.last().foreground)
    }

    @Test fun handlesRgbBoldAndControlSequences() {
        val parts = ConsoleAnsi.parse("\u001b[1;38;2;12;34;56mX\u001b[2K\u001b]0;title\u0007Y\u001b[0m")
        assertEquals(" Y", parts.joinToString("") { it.text })
        assertEquals(0xFF0C2238.toInt(), parts.first().foreground)
        assertTrue(parts.first().bold)
    }

    @Test fun incompleteEscapeDoesNotLeakIntoVisibleText() {
        assertEquals("ready", ConsoleAnsi.parse("ready\u001b[38;2;").joinToString("") { it.text })
    }

    @Test fun carriageReturnAndEraseRenderTheLatestProgress() {
        val text = "Downloading 12%\r\u001b[2KDownloading 90%\nDone"
        assertEquals("Downloading 90%\nDone", ConsoleAnsi.parse(text).joinToString("") { it.text }.trimStart())
        assertEquals("xZz", ConsoleAnsi.parse("xyz\b\bZ").joinToString("") { it.text })
    }

    @Test fun cursorMotionCanCorrectEarlierLineWithoutLosingColor() {
        val text = "first\nsecond\u001b[1A\r\u001b[31mFIRST\u001b[0m"
        val segments = ConsoleAnsi.parse(text)
        assertEquals("FIRST\nsecond", segments.joinToString("") { it.text })
        assertEquals(0xFFDC646D.toInt(), segments.first { "FIRST" in it.text }.foreground)
    }
}
