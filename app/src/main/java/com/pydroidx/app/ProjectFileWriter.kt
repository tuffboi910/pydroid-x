package com.pydroidx.app

import android.util.AtomicFile
import java.io.File
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
                val atomic = AtomicFile(file)
                val stream = atomic.startWrite()
                try {
                    stream.write(content.toByteArray(StandardCharsets.UTF_8))
                    atomic.finishWrite(stream)
                } catch (error: Throwable) {
                    atomic.failWrite(stream)
                    throw error
                }
            }
            complete(result)
        }
    }
}
