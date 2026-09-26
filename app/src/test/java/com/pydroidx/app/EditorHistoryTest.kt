package com.pydroidx.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditorHistoryTest {
    @Test fun undoAndRedoRestoreTextAndCursor() {
        val history = EditorHistory()
        history.record(EditorSnapshot("a", 1))
        history.record(EditorSnapshot("ab", 2))

        val undone = history.undo(EditorSnapshot("abc", 3))
        assertEquals(EditorSnapshot("ab", 2), undone)
        assertEquals(EditorSnapshot("abc", 3), history.redo(undone!!))
    }

    @Test fun newEditClearsRedoBranch() {
        val history = EditorHistory()
        history.record(EditorSnapshot("first", 5))
        val old = history.undo(EditorSnapshot("second", 6))!!
        history.record(old)
        assertNull(history.redo(EditorSnapshot("replacement", 11)))
    }

    @Test fun historyIsBoundedAndDuplicateSafe() {
        val history = EditorHistory(limit = 2)
        history.record(EditorSnapshot("a", 1))
        history.record(EditorSnapshot("a", 1))
        history.record(EditorSnapshot("ab", 2))
        history.record(EditorSnapshot("abc", 3))
        assertEquals(EditorSnapshot("abc", 3), history.undo(EditorSnapshot("abcd", 4)))
        assertEquals(EditorSnapshot("ab", 2), history.undo(EditorSnapshot("abc", 3)))
        assertNull(history.undo(EditorSnapshot("ab", 2)))
    }

    @Test fun largeFilesAreBoundedByCharacterBudget() {
        val history = EditorHistory(limit = 100, maxCharacters = 12)
        history.record(EditorSnapshot("111111", 6))
        history.record(EditorSnapshot("222222", 6))
        history.record(EditorSnapshot("333333", 6))

        assertEquals(EditorSnapshot("333333", 6), history.undo(EditorSnapshot("444444", 6)))
        assertEquals(EditorSnapshot("222222", 6), history.undo(EditorSnapshot("333333", 6)))
        assertNull(history.undo(EditorSnapshot("222222", 6)))
    }
}
