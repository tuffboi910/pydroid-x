package com.pydroidx.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.io.File
import java.nio.file.Files
import org.junit.Test

class ProjectWorkspaceTest {
    @Test fun removesUnsafePathCharacters() {
        assertEquals("My Project", ProjectWorkspace.safeName(" ../My/Project? "))
    }

    @Test fun fallsBackForEmptyNames() {
        assertEquals("Project", ProjectWorkspace.safeName("../"))
    }

    @Test fun createsTheNextAvailableProjectName() {
        assertEquals("Project 4", ProjectWorkspace.nextName(listOf("Project", "Project 2", "Project 3")))
    }

    @Test fun preservesAFreeCustomName() {
        assertEquals("Snake Game", ProjectWorkspace.nextName(listOf("Project"), "Snake Game"))
    }

    @Test fun projectPathsRejectTraversalAndNormalizeSeparators() {
        val root = Files.createTempDirectory("py4u-path-").toFile()
        assertNull(ProjectWorkspace.resolvePath(root, "../outside.py"))
        assertNull(ProjectWorkspace.resolvePath(root, "folder/../../outside.py"))
        assertNull(ProjectWorkspace.resolvePath(root, "/outside.py"))
        assertEquals("src/tool.py", ProjectWorkspace.normalizePath("src\\tool.py"))
        assertEquals(root.canonicalFile, ProjectWorkspace.resolvePath(root, ""))
    }

    @Test fun browserListsNestedPythonFilesAndFoldersButHidesRecoveryData() {
        val root = Files.createTempDirectory("py4u-browser-").toFile()
        File(root, "src").mkdir()
        File(root, "src/tool.py").writeText("print('ok')")
        File(root, "notes.txt").writeText("not editable")
        File(root, ".history/main.py").apply { parentFile.mkdirs(); writeText("hidden") }

        val rootEntries = ProjectWorkspace.entries(root, "")
        assertEquals(listOf("src"), rootEntries.map { it.name })
        assertTrue(rootEntries.single().isDirectory)
        assertEquals(listOf("tool.py"), ProjectWorkspace.entries(root, "src").map { it.name })
        assertEquals(listOf("src/tool.py"), ProjectWorkspace.pythonFiles(root).mapNotNull {
            ProjectWorkspace.relativePath(root, it)
        })
    }

    @Test fun projectPathsRejectSymlinksThatLeaveTheRoot() {
        val root = Files.createTempDirectory("py4u-link-root-").toFile()
        val outside = Files.createTempDirectory("py4u-link-outside-").toFile()
        File(outside, "secret.py").writeText("pass")
        val link = File(root, "outside")
        Files.createSymbolicLink(link.toPath(), outside.toPath())
        assertNull(ProjectWorkspace.resolvePath(root, "outside/secret.py"))
        assertFalse(ProjectWorkspace.entries(root, "").any { it.name == "outside" })
    }
}
