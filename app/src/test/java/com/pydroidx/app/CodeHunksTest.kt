package com.pydroidx.app

import org.junit.Assert.assertEquals
import org.junit.Test

class CodeHunksTest {
    @Test fun independentEditsCanBeAcceptedSeparately() {
        val before = "first\nkeep one\nkeep two\nlast\n"
        val after = "FIRST\nkeep one\nkeep two\nLAST\n"
        val hunks = CodeHunks.between(before, after)
        assertEquals(2, hunks.size)
        assertEquals("FIRST\nkeep one\nkeep two\nlast\n", CodeHunks.apply(before, hunks, setOf(0)))
        assertEquals(after, CodeHunks.apply(before, hunks, setOf(0, 1)))
        assertEquals(before, CodeHunks.apply(before, hunks, emptySet()))
    }

    @Test fun insertionAndDeletionPreserveFinalNewline() {
        val before = "a\nb\nc\n"
        val after = "a\ninsert\nc\n"
        val hunks = CodeHunks.between(before, after)
        assertEquals(after, CodeHunks.apply(before, hunks, hunks.indices.toSet()))
    }

    @Test fun largePreviewFallsBackToOneSelectableEdit() {
        val before = (0..350).joinToString("\n")
        val after = before.replace("42", "forty two")
        val hunks = CodeHunks.between(before, after)
        assertEquals(1, hunks.size)
        assertEquals(after, CodeHunks.apply(before, hunks, setOf(0)))
    }
}
