package com.pydroidx.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleOutputBufferTest {
    @Test fun preservesNormalOutput() {
        val buffer = ConsoleOutputBuffer(maxCharacters = 20, retainedCharacters = 12)
        buffer.append("hello")
        buffer.append(" world")
        assertEquals("hello world", buffer.snapshot())
    }

    @Test fun boundsLargeOutputAndKeepsNewestText() {
        val buffer = ConsoleOutputBuffer(maxCharacters = 10, retainedCharacters = 6)
        buffer.append("1234567890")
        buffer.append("ABC")
        val value = buffer.snapshot()
        assertTrue(value.startsWith("[Earlier output truncated]\n"))
        assertTrue(value.endsWith("890ABC"))
    }

    @Test fun clearRemovesTruncationState() {
        val buffer = ConsoleOutputBuffer(maxCharacters = 4, retainedCharacters = 2)
        buffer.append("abcdef")
        buffer.clear()
        buffer.append("ok")
        assertEquals("ok", buffer.snapshot())
    }
}
