package com.pydroidx.app

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Mirror of writes still queued or failed on the serialized writer. */
internal class PendingDocumentWrites {
    private val pending = ConcurrentHashMap<String, String>()

    fun mark(file: File, source: String) { pending[file.absolutePath] = source }

    fun read(file: File): String? = pending[file.absolutePath]

    fun completed(file: File, source: String, succeeded: Boolean) {
        if (succeeded) pending.remove(file.absolutePath, source)
    }
}
