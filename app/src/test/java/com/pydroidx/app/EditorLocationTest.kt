package com.pydroidx.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditorLocationTest {
    @Test fun retainsUtf16SelectionAndScroll() {
        val location = EditorLocation(3, 8, 12, 640)
        assertEquals(location, EditorLocation.decode(location.encode()))
    }

    @Test fun ignoresCorruptSavedLocations() {
        assertNull(EditorLocation.decode("broken"))
        assertNull(EditorLocation.decode("1,2,x,4"))
    }
}
