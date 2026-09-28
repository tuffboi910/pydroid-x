package com.pydroidx.app

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class LineBreakIndexTest {
    @Test fun incrementalEditsMatchFullRebuild() {
        val random = Random(19)
        val source = StringBuilder("first\nsecond\nthird")
        val index = LineBreakIndex().apply { rebuild(source) }
        repeat(2_000) {
            val start = random.nextInt(source.length + 1)
            val before = random.nextInt(source.length - start + 1)
            val insert = listOf("x", "\n", "a\nb", "", "\n\n")[random.nextInt(5)]
            source.replace(start, start + before, insert)
            index.update(source, start, before, insert.length)

            val expected = source.indices.filter { source[it] == '\n' }
            assertEquals(expected.size, index.size)
            expected.forEachIndexed { position, offset -> assertEquals(offset, index.offsetAt(position)) }
            for (offset in 0..source.length) {
                assertEquals(expected.count { it < offset }, index.countBefore(offset))
            }
        }
    }

    @Test fun manyLineDocumentCanAppendWithoutRebuildingOffsets() {
        val source = StringBuilder((0 until 10_000).joinToString("\n") { "print($it)" })
        val index = LineBreakIndex().apply { rebuild(source) }
        val oldSize = index.size
        source.append("x")
        index.update(source, source.length - 1, 0, 1)
        assertEquals(oldSize, index.size)
        assertEquals(9_999, index.countBefore(source.length))
    }
}
