package com.pydroidx.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditorFileStateTest {
    @Test fun stateRoundTrips() {
        val state=EditorFileState(17,21,12,900)
        assertEquals(state,EditorFileStateCodec.decode(EditorFileStateCodec.encode(state)))
    }

    @Test fun invalidPersistenceIsIgnored() {
        assertNull(EditorFileStateCodec.decode(null))
        assertNull(EditorFileStateCodec.decode("1,2,3"))
        assertNull(EditorFileStateCodec.decode("1,nope,3,4"))
        assertNull(EditorFileStateCodec.decode("-1,0,0,0"))
    }
}
