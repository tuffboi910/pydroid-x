package com.pydroidx.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ConsoleInputControllerTest {
    @Test fun insertsAtCursorWithoutLosingText() {
        assertEquals(ConsoleInputState("pri\tnt", 4), ConsoleInputController.insert(ConsoleInputState("print", 3), "\t"))
    }

    @Test fun arrowsStayInsideInputBounds() {
        assertEquals(0, ConsoleInputController.move(ConsoleInputState("abc", 0), -1).cursor)
        assertEquals(3, ConsoleInputController.move(ConsoleInputState("abc", 3), 1).cursor)
    }

    @Test fun ctrlArrowMovesByWord() {
        val text = "hello   python world"
        assertEquals(8, ConsoleInputController.move(ConsoleInputState(text, 0), 1, true).cursor)
        assertEquals(15, ConsoleInputController.move(ConsoleInputState(text, text.length), -1, true).cursor)
    }

    @Test fun homeAndEndMoveToEdges() {
        val state = ConsoleInputState("python", 3)
        assertEquals(0, ConsoleInputController.home(state).cursor)
        assertEquals(6, ConsoleInputController.end(state).cursor)
    }

    @Test fun historyNavigatesAndSkipsConsecutiveDuplicates() {
        val history = ConsoleInputHistory()
        history.record("first")
        history.record("second")
        history.record("second")
        assertEquals("second", history.previous())
        assertEquals("first", history.previous())
        assertEquals("second", history.next())
        assertEquals("", history.next())
    }
}
