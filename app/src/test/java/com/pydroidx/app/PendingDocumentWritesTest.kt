package com.pydroidx.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingDocumentWritesTest {
    @Test fun openingDuringQueuedSaveUsesNewestText() {
        val pending = PendingDocumentWrites()
        val file = File("/project/main.py")
        pending.mark(file, "first")
        pending.mark(file, "second 🐍")
        pending.completed(file, "first", true)
        assertEquals("second 🐍", pending.read(file))
        pending.completed(file, "second 🐍", true)
        assertNull(pending.read(file))
    }

    @Test fun failedSaveRetainsRecoveryText() {
        val pending = PendingDocumentWrites()
        val file = File("/project/main.py")
        pending.mark(file, "unsaved")
        pending.completed(file, "unsaved", false)
        assertEquals("unsaved", pending.read(file))
    }
}
