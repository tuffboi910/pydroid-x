package com.pydroidx.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ProjectAiContextTest {
    @Test fun editableSourcesContainOnlyCompleteFilesWithinBudget() {
        val root = Files.createTempDirectory("astro-editable").toFile()
        try {
            root.resolve("main.py").writeText("old")
            root.resolve("small.py").writeText("small source")
            root.resolve("large.py").writeText("x".repeat(9_000))
            val sources = ProjectAiContext.editableSources(root, "main.py", "live buffer", maxCharacters=1_500)
            assertTrue(sources["main.py"] == "live buffer")
            assertTrue(sources["small.py"] == "small source")
            assertFalse(sources.containsKey("large.py"))
            assertTrue(ProjectAiContext.editableSources(root, "main.py", "x".repeat(2_000), 1_500).isEmpty())
        } finally { root.deleteRecursively() }
    }

    @Test fun explicitContextUsesLiveCurrentBufferAndBoundedNestedFiles() {
        val root = Files.createTempDirectory("astro-project").toFile()
        try {
            root.resolve("main.py").writeText("old content")
            root.resolve("src").mkdir()
            root.resolve("src/helper.py").writeText("def useful(): return 42")
            root.resolve(".history").mkdir()
            root.resolve(".history/secret.py").writeText("hidden")
            val context = ProjectAiContext.build(root, "main.py", "live buffer", maxCharacters = 500)
            assertTrue(context.contains("# File: main.py\nlive buffer"))
            assertTrue(context.contains("# File: src/helper.py"))
            assertFalse(context.contains("old content"))
            assertFalse(context.contains("hidden"))
        } finally { root.deleteRecursively() }
    }

    @Test fun contextNeverExceedsBudget() {
        val root = Files.createTempDirectory("astro-budget").toFile()
        try {
            root.resolve("other.py").writeText("x".repeat(1000))
            val context = ProjectAiContext.build(root, "main.py", "y".repeat(1000), maxCharacters = 120)
            assertTrue(context.length <= 120)
            assertTrue(context.contains("# File: other.py"))
        } finally { root.deleteRecursively() }
    }
}
