package com.pydroidx.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class ProjectTextSearchTest {
    @Test fun searchesCurrentUnsavedTextAndOtherFilesWithCorrectLines() {
        val dir = Files.createTempDirectory("py4u-search").toFile()
        try {
            dir.resolve("main.py").writeText("stale")
            dir.resolve("other.py").writeText("first\nprint('Needle')\n")
            dir.resolve("notes.txt").writeText("needle")
            assertEquals(listOf(
                ProjectSearchHit("main.py", 2, "needle = 1"),
                ProjectSearchHit("other.py", 2, "print('Needle')")
            ), ProjectTextSearch.search(dir, "needle", "main.py", "# header\nneedle = 1\n"))
        } finally { dir.deleteRecursively() }
    }

    @Test fun boundsOutputAndSkipsEmptyQuery() {
        val dir = Files.createTempDirectory("py4u-search").toFile()
        try {
            dir.resolve("main.py").writeText("x\nx\nx")
            assertEquals(2, ProjectTextSearch.search(dir, "x", "main.py", "x\nx\nx", 2).size)
            assertEquals(emptyList<ProjectSearchHit>(), ProjectTextSearch.search(dir, "  ", "main.py", "x"))
        } finally { dir.deleteRecursively() }
    }
}
