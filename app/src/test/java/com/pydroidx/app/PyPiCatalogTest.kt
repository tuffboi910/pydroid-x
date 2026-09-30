package com.pydroidx.app

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PyPiCatalogTest {
    @Test fun prefixSearchIsBoundedAndReadsRealStoredNames() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("pypi-catalog.db")
        val catalog = PyPiCatalog(context)
        catalog.writableDatabase.execSQL("INSERT INTO projects(name) VALUES ('requests'),('rich'),('rustworkx')")
        assertEquals(3, catalog.count())
        assertEquals(listOf("requests","rich"), catalog.search("re") + catalog.search("ri"))
        assertEquals(emptyList<String>(), catalog.search("r"))
        catalog.close()
    }
}
