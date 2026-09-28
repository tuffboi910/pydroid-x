package com.pydroidx.app

import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

/** Serializes saves across activity recreation and keeps the previous file on write failure. */
internal object ProjectFileWriter {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "PY4U-FileWriter").apply { isDaemon = true }
    }

    fun enqueue(file: File, content: String, complete: (Result<Unit>) -> Unit) {
        executor.execute {
            val result = runCatching {
                val bytes = content.toByteArray(StandardCharsets.UTF_8)
                val atomic = AtomicFile(file)
                if (file.isFile && file.length() <= 1_000_000L) {
                    val oldBytes = atomic.openRead().use { it.readBytes() }
                    if (oldBytes.contentEquals(bytes)) return@runCatching
                    ProjectFileHistory.checkpoint(file, oldBytes)
                }
                val stream = atomic.startWrite()
                try {
                    stream.write(bytes)
                    atomic.finishWrite(stream)
                } catch (error: Throwable) {
                    atomic.failWrite(stream)
                    throw error
                }
            }
            complete(result)
        }
    }

    fun rename(source: File, target: File, complete: (Result<Unit>) -> Unit) {
        executor.execute {
            complete(runCatching {
                if (target.exists() || !source.renameTo(target)) {
                    throw IOException("Could not rename ${source.name} to ${target.name}")
                }
                val historyRoot = File(source.parentFile, ".history")
                val oldHistory = File(historyRoot, source.name)
                val newHistory = File(historyRoot, target.name)
                if (oldHistory.isDirectory && !newHistory.exists() && !oldHistory.renameTo(newHistory)) {
                    throw IOException("Renamed ${source.name}, but its history could not be moved")
                }
            })
        }
    }

    fun checkpoint(file: File, content: String, complete: (Result<Unit>) -> Unit) {
        executor.execute {
            complete(runCatching {
                ProjectFileHistory.recoveryPoint(file, content.toByteArray(StandardCharsets.UTF_8))
            })
        }
    }
}
