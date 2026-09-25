package com.pydroidx.app

import org.junit.Assert.assertEquals
import org.junit.Test

class IndentationGuideTest {
    @Test fun returnsEveryCompletedIndentLevel() {
        assertEquals(listOf(4, 8), IndentationGuide.columns("        print('hi')"))
    }

    @Test fun expandsTabsToPythonTabStops() {
        assertEquals(listOf(4, 8), IndentationGuide.columns("		value"))
        assertEquals(listOf(4, 8), IndentationGuide.columns("  	    value"))
    }

    @Test fun ignoresBlankAndTopLevelLines() {
        assertEquals(emptyList<Int>(), IndentationGuide.columns("    "))
        assertEquals(emptyList<Int>(), IndentationGuide.columns("print('hi')"))
    }
}
