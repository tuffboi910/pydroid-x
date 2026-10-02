package com.pydroidx.app

import android.util.AtomicFile
import java.io.File
import java.io.IOException

/** Small, bounded recovery snapshots kept alongside each project. */
internal object ProjectFileHistory {
    private const val MAX_VERSIONS = 12
    private const val MAX_FILE_BYTES = 1_000_000L
    private const val MIN_INTERVAL_MS = 60_000L

    fun versions(file: File): List<File> = File(File(file.parentFile, ".history"), file.name)
        .listFiles()?.filter { it.isFile && it.name.endsWith(".py") }
        ?.sortedByDescending { it.name } ?: emptyList()

    fun checkpoint(file: File, oldBytes: ByteArray) {
        if (oldBytes.isEmpty() || oldBytes.size > MAX_FILE_BYTES) return
        val previous = versions(file)
        val now = System.currentTimeMillis()
        if (previous.firstOrNull()?.nameWithoutExtension?.toLongOrNull()?.let { now - it < MIN_INTERVAL_MS } == true) return
        writeVersion(file, oldBytes, now)
    }

    fun recoveryPoint(file: File, content: ByteArray) {
        if (content.size > MAX_FILE_BYTES) throw IOException("File is too large for a recovery point")
        val previous = versions(file)
        val now = maxOf(System.currentTimeMillis(),
            (previous.firstOrNull()?.nameWithoutExtension?.toLongOrNull() ?: 0L) + 1)
        writeVersion(file, content, now)
    }

    private fun writeVersion(file: File, oldBytes: ByteArray, now: Long) {
        val dir = File(File(file.parentFile, ".history"), file.name)
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Could not create file history")
        val version = File(dir, "$now.py")
        val atomic = AtomicFile(version)
        val stream = atomic.startWrite()
        try {
            stream.write(oldBytes)
            atomic.finishWrite(stream)
        } catch (error: Throwable) {
            atomic.failWrite(stream)
            throw error
        }
        versions(file).drop(MAX_VERSIONS).forEach { it.delete() }
    }
}
