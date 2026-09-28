package com.pydroidx.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeEditHunksTest {
    @Test fun separatesDistantChanges() {
        val old="a\nb\nc\nd\ne\n"
        val newer="a\nB\nc\nd\nE\n"
        val hunks=CodeEditHunks.diff(old,newer)
        assertEquals(2,hunks.size)
        assertEquals(listOf("b"),hunks[0].removed)
        assertEquals(listOf("B"),hunks[0].added)
        assertEquals(listOf("e"),hunks[1].removed)
        assertEquals(listOf("E"),hunks[1].added)
    }

    @Test fun appliesOnlySelectedHunks() {
        val old="a\nb\nc\nd\ne\n"
        val newer="a\nB\nc\nd\nE\n"
        assertEquals("a\nB\nc\nd\ne\n",CodeEditHunks.applySelected(old,newer,setOf(0)))
        assertEquals("a\nb\nc\nd\nE\n",CodeEditHunks.applySelected(old,newer,setOf(1)))
    }

    @Test fun handlesInsertionAndDeletion() {
        val old="a\nb\nc"
        val newer="a\nx\nb"
        val hunks=CodeEditHunks.diff(old,newer)
        assertTrue(hunks.isNotEmpty())
        assertEquals(newer,CodeEditHunks.applySelected(old,newer,hunks.indices.toSet()))
    }

    @Test fun unchangedTextHasNoHunks() {
        assertTrue(CodeEditHunks.diff("x\n","x\n").isEmpty())
    }
}
