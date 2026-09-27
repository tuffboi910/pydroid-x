package com.pydroidx.app

import org.junit.Assert.*
import org.junit.Test

class CompletionSessionTest {
    private val valueError = CompletionItem("ValueError()", "ValueError()", 0, 2, 1, "class", "", "")
    @Test fun insertsSelectedItemAndCorrectsCase() {
        val session = CompletionSession()
        session.offer(0, "va", 2, listOf(valueError.copy(label="vars()", insertText="vars()"), valueError))
        assertEquals(EditorSnapshot("ValueError()", 11), session.edit(1, "va", 2, 2))
    }
    @Test fun staleResponsesCannotResurrectAfterAnEditAndUndo() {
        val session = CompletionSession()
        val token = session.generation
        session.clear()
        assertFalse(session.offer(token, "va", 2, listOf(valueError)))
        assertNull(session.edit(0, "va", 2, 2))
    }
    @Test fun rejectsMovedCursorSelectedTextAndChangedSource() {
        val session = CompletionSession()
        session.offer(0, "va", 2, listOf(valueError))
        assertNull(session.edit(0, "va", 1, 1))
        assertNull(session.edit(0, "va", 0, 2))
        assertNull(session.edit(0, "vb", 2, 2))
    }
    @Test fun rejectsInvalidReplacementBounds() {
        val session = CompletionSession()
        session.offer(0, "va", 2, listOf(valueError.copy(replaceEnd=99), valueError.copy(replaceStart=-1)))
        assertTrue(session.items.isEmpty())
    }
    @Test fun replacesWholeTokenWithoutTouchingSurroundingCode() {
        val session = CompletionSession()
        val item = valueError.copy(replaceStart=6, replaceEnd=9)
        session.offer(0, "raise val # reason", 8, listOf(item))
        assertEquals(EditorSnapshot("raise ValueError() # reason", 17), session.edit(0, "raise val # reason", 8, 8))
    }
    @Test fun configuredDelayIsOneSecond() { assertEquals(1000L, CompletionSession.DELAY_MS) }
}
