package com.pydroidx.app

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProjectFileWriterTest {
    @Test fun writesInSubmissionOrderAndPreservesUnicode() {
        val dir = Files.createTempDirectory("py4u-writer-").toFile()
        val file = File(dir, "main.py")
        val finished = CountDownLatch(3)
        val results = mutableListOf<Result<Unit>>()
        listOf("old", "second", "print('🐍')\n").forEach { content ->
            ProjectFileWriter.enqueue(file, content) { result ->
                synchronized(results) { results.add(result) }
                finished.countDown()
            }
        }
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        assertTrue(results.all { it.isSuccess })
        assertEquals("print('🐍')\n", file.readText())
    }
}
