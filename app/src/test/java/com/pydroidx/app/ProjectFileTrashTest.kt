package com.pydroidx.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectFileTrashTest {
    @Test fun sanitizesPythonNamesWithoutAllowingPaths() {
        assertEquals("evil.py",ProjectFileTrash.safePythonFileName("../evil.py"))
        assertEquals("My file.py",ProjectFileTrash.safePythonFileName(" My:file?.PY "))
    }

    @Test fun choosesNonConflictingDuplicateNames() {
        val dir=Files.createTempDirectory("py4u-duplicate").toFile()
        try {
            val source=File(dir,"main.py").apply { writeText("x=1") }
            File(dir,"main_copy.py").writeText("old")
            assertEquals("main_copy_2.py",ProjectFileTrash.duplicateTarget(dir,source).name)
        } finally { dir.deleteRecursively() }
    }

    @Test fun trashAndRestorePreserveHistory() {
        val dir=Files.createTempDirectory("py4u-trash").toFile()
        try {
            val source=File(dir,"main.py").apply { writeText("print('ok')") }
            val history=File(File(dir,".history"),"main.py").apply { mkdirs() }
            File(history,"1.py").writeText("old")
            val trashed=ProjectFileTrash.move(source)
            assertTrue(!source.exists())
            assertEquals("main.py",ProjectFileTrash.list(dir).single().originalName)
            assertTrue(File(File(File(dir,".trash"),".history"),trashed.name).isDirectory)

            val restored=ProjectFileTrash.restore(dir,trashed.name)
            assertEquals("main.py",restored.name)
            assertTrue(restored.isFile)
            assertTrue(File(File(dir,".history"),"main.py").isDirectory)
            assertTrue(ProjectFileTrash.list(dir).isEmpty())
        } finally { dir.deleteRecursively() }
    }
}
