package com.pydroidx.app

import org.junit.Assert.assertEquals
import org.junit.Test

class TabCapacityTest {
    @Test fun evictsOnlyUnpinnedBackgroundFile() {
        val tabs = listOf("pinned.py", "free.py", "selected.py")
        assertEquals("free.py", TabCapacity.eviction(tabs, setOf("pinned.py"), "selected.py"))
        assertEquals(null, TabCapacity.eviction(tabs, setOf("pinned.py", "free.py"), "selected.py"))
    }
}
