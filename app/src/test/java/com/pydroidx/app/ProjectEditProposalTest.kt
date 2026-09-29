package com.pydroidx.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectEditProposalTest {
    @Test fun onlyExplicitSharedFilesCanBeProposed() {
        val sources = mapOf("main.py" to "old\n", "pkg/tool.py" to "tool\n")
        val answer = "File: main.py\n```python\nnew\n```\nFile: pkg/tool.py\n```python\nbetter\n```\n"
        assertEquals(listOf("main.py", "pkg/tool.py"), ProjectEditProposal.parse(answer, sources).map { it.path })
        assertEquals("new\n", ProjectEditProposal.parse(answer, sources)[0].proposed)
        assertTrue(ProjectEditProposal.parse(answer + "File: ../secret.py\n```python\nx\n```\n", sources).isEmpty())
        assertTrue(ProjectEditProposal.parse(answer + "File: main.py\n```python\nx\n```\n", sources).isEmpty())
    }
}
