package com.pydroidx.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TerminalSessionTest {
    @Test fun cdStaysInsideProject() {
        val root = Files.createTempDirectory("py4u-terminal").toFile()
        val child = File(root, "src").apply { mkdirs() }
        val session = TerminalSession(root)
        val result = session.execute("cd src")
        assertEquals(0, result.exitCode)
        assertEquals(child.canonicalFile, session.currentDirectory().canonicalFile)
        assertEquals("~/src", result.cwd)
    }

    @Test fun cdCannotEscapeProject() {
        val root = Files.createTempDirectory("py4u-terminal").toFile()
        val session = TerminalSession(root)
        val result = session.execute("cd ..")
        assertEquals(1, result.exitCode)
        assertTrue(result.output.contains("outside"))
        assertEquals(root.canonicalFile, session.currentDirectory().canonicalFile)
    }

    @Test fun clearIsHandledWithoutLaunchingShell() {
        val root = Files.createTempDirectory("py4u-terminal").toFile()
        val result = TerminalSession(root).execute("clear")
        assertTrue(result.clear)
        assertEquals("", result.output)
    }
}
