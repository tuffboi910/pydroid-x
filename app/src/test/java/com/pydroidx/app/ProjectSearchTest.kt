package com.pydroidx.app

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectSearchTest {
    @Test fun findsMatchesAcrossPythonFilesCaseInsensitively() {
        val dir = Files.createTempDirectory("py4u-search").toFile()
        try {
            java.io.File(dir, "main.py").writeText("print(\"Hello Astro\")\nvalue = 1\n")
            java.io.File(dir, "tools.py").writeText("def astro_helper():\n    return \"ASTRO\"\n")
            java.io.File(dir, "notes.txt").writeText("astro should be ignored")
            val hits = ProjectSearch.search(dir, "astro")
            assertEquals(3, hits.size)
            assertEquals(listOf("main.py", "tools.py", "tools.py"), hits.map { it.fileName })
            assertEquals(listOf(1, 1, 2), hits.map { it.line })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun returnsEveryOccurrenceOnTheSameLine() {
        val dir = Files.createTempDirectory("py4u-search-repeat").toFile()
        try {
            java.io.File(dir, "main.py").writeText("name + name + NAME")
            val hits = ProjectSearch.search(dir, "name")
            assertEquals(listOf(1, 8, 15), hits.map { it.column })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun respectsResultLimitAndBlankQueries() {
        val dir = Files.createTempDirectory("py4u-search-limit").toFile()
        try {
            java.io.File(dir, "main.py").writeText((1..20).joinToString("\n") { "needle $it" })
            assertEquals(4, ProjectSearch.search(dir, "needle", 4).size)
            assertTrue(ProjectSearch.search(dir, "   ").isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }
}
