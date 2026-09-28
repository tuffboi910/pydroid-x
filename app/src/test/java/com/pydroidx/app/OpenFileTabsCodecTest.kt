package com.pydroidx.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenFileTabsCodecTest {
    @Test fun roundTripsAndRemovesDuplicates() {
        val encoded=OpenFileTabsCodec.encode(listOf("main.py","tools.py","main.py"))
        assertEquals(listOf("main.py","tools.py"),OpenFileTabsCodec.decode(encoded))
    }

    @Test fun emptyPersistenceProducesNoTabs() {
        assertTrue(OpenFileTabsCodec.decode(null).isEmpty())
        assertTrue(OpenFileTabsCodec.decode("").isEmpty())
    }
}
