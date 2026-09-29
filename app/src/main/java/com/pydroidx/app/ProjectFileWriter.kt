package com.pydroidx.app

import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap

/** Serializes saves across activity recreation and keeps the previous file on write failure. */
internal object ProjectFileWriter {
    private val draftSnapshots = ConcurrentHashMap<String, String>()
    private val draftScheduled = ConcurrentHashMap.newKeySet<String>()
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "PY4U-FileWriter").apply { isDaemon = true }
    }

    private fun draftFile(root: File, file: File): File? {
        val path = ProjectWorkspace.relativePath(root, file) ?: return null
        if (!path.endsWith(".py", ignoreCase = true)) return null
        return ProjectWorkspace.resolvePath(root, ".recovery/drafts/$path.draft")
    }

    /** Coalesces rapid edits; the latest snapshot is journaled on the file worker. */
    fun journalDraft(root: File, file: File, content: String, failed: (String) -> Unit = {}) {
        val draft = draftFile(root, file) ?: run {
            failed("Draft location is outside the project")
            return
        }
        val key = draft.absolutePath
        draftSnapshots[key] = content
        if (!draftScheduled.add(key)) return
        executor.execute {
            var completed = false
            try {
                while (true) {
                    val snapshot = draftSnapshots[key] ?: break
                    val bytes = snapshot.toByteArray(StandardCharsets.UTF_8)
                    if (bytes.size > 2_000_000) {
                        draftSnapshots.remove(key, snapshot)
                        failed("Draft exceeds the 2 MB recovery limit")
                        break
                    }
                    if (!draft.parentFile.isDirectory && !draft.parentFile.mkdirs()) {
                        throw IOException("Could not create draft directory")
                    }
                    val atomic = AtomicFile(draft)
                    val stream = atomic.startWrite()
                    try { stream.write(bytes); atomic.finishWrite(stream) }
                    catch (error: Throwable) { atomic.failWrite(stream); throw error }
                    draftSnapshots.remove(key, snapshot)
                }
                completed = true
            } catch (error: Exception) {
                // Keep the previous atomic draft and retry on the next edit.
                failed(error.message ?: "Draft storage is unavailable")
            } finally {
                draftScheduled.remove(key)
                if (completed) draftSnapshots[key]?.let { journalDraft(root, file, it, failed) }
            }
        }
    }

    fun recoverDraft(root: File, file: File): String? {
        val draft = draftFile(root, file) ?: return null
        return draftSnapshots[draft.absolutePath] ?: draft.takeIf { it.isFile && it.length() <= 2_000_000 }
            ?.let { runCatching { AtomicFile(it).openRead().bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() } }.getOrNull() }
    }

    fun discardDraft(root: File, file: File) {
        val draft = draftFile(root, file) ?: return
        draftSnapshots.remove(draft.absolutePath)
        executor.execute { draft.delete() }
    }

    fun clearSavedDraft(root: File, file: File, saved: String) {
        val draft = draftFile(root, file) ?: return
        executor.execute {
            if (draftSnapshots[draft.absolutePath] != null) return@execute
            val existing = recoverDraft(root, file)
            if (existing == saved) draft.delete()
        }
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

    fun afterQueuedWrites(complete: () -> Unit) { executor.execute(complete) }

    fun rename(source: File, target: File, complete: (Result<Unit>) -> Unit) {
        relocate(source, target, "rename", complete)
    }

    fun move(source: File, target: File, complete: (Result<Unit>) -> Unit) {
        relocate(source, target, "move", complete)
    }

    private fun relocate(source: File, target: File, action: String, complete: (Result<Unit>) -> Unit) {
        executor.execute {
            complete(runCatching {
                if (!source.isFile || target.exists() || !target.parentFile.isDirectory || !source.renameTo(target)) {
                    throw IOException("Could not $action ${source.name} to ${target.name}")
                }
                try {
                    moveHistory(source, target)
                } catch (error: Throwable) {
                    runCatching { moveHistory(target, source) }
                    target.renameTo(source)
                    throw error
                }
            })
        }
    }

    fun moveToRecovery(source: File, projectRoot: File, complete: (Result<File>) -> Unit) {
        executor.execute {
            complete(runCatching {
                val relative = ProjectWorkspace.relativePath(projectRoot, source)
                    ?: throw IOException("File is outside this project")
                if (!source.isFile) throw IOException("File no longer exists")
                val recoveryRoot = File(projectRoot, ".recovery/deleted")
                val parentPath = relative.substringBeforeLast('/', "")
                val parent = if (parentPath.isEmpty()) recoveryRoot else File(recoveryRoot, parentPath)
                if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Could not create recovery folder")
                var target = File(parent, "${System.currentTimeMillis()}_${source.name}")
                var suffix = 2
                while (target.exists()) target = File(parent, "${System.currentTimeMillis()}_${suffix++}_${source.name}")
                if (!source.renameTo(target)) throw IOException("Could not move ${source.name} to recovery")
                try {
                    moveHistory(source, target)
                } catch (error: Throwable) {
                    runCatching { moveHistory(target, source) }
                    target.renameTo(source)
                    throw error
                }
                pruneRecovery(recoveryRoot, keep = 20)
                target
            })
        }
    }

    private fun moveHistory(source: File, target: File) {
        val sourceHistory = File(File(source.parentFile, ".history"), source.name)
        if (!sourceHistory.isDirectory) return
        val targetHistory = File(File(target.parentFile, ".history"), target.name)
        if (!targetHistory.exists()) {
            if (!targetHistory.parentFile.isDirectory && !targetHistory.parentFile.mkdirs()) {
                throw IOException("Could not create file history folder")
            }
            if (!sourceHistory.renameTo(targetHistory)) throw IOException("Could not move file history")
            return
        }
        if (!targetHistory.isDirectory) throw IOException("File history destination is unavailable")
        for (version in sourceHistory.listFiles().orEmpty().filter { it.isFile }) {
            var destination = File(targetHistory, version.name)
            if (destination.exists()) {
                if (destination.length() == version.length() &&
                    destination.inputStream().use { left -> version.inputStream().use { right -> left.readBytes().contentEquals(right.readBytes()) } }) continue
                val stem = version.nameWithoutExtension
                var suffix = 2
                destination = File(targetHistory, "${stem}_moved_${suffix++}.py")
                while (destination.exists()) destination = File(targetHistory, "${stem}_moved_${suffix++}.py")
            }
            version.copyTo(destination, overwrite = false)
        }
        if (!sourceHistory.deleteRecursively() && sourceHistory.exists()) throw IOException("Could not finish moving file history")
    }

    private fun pruneRecovery(root: File, keep: Int) {
        val files = root.walkTopDown().onEnter { it.name != ".history" }
            .filter { it.isFile && it.extension.equals("py", true) }
            .sortedByDescending { it.lastModified() }.toList()
        files.drop(keep).forEach { old ->
            old.delete()
            File(File(old.parentFile, ".history"), old.name).deleteRecursively()
        }
    }

    fun duplicate(source: File, target: File, complete: (Result<Unit>) -> Unit) {
        executor.execute {
            complete(runCatching {
                if (target.exists() || !source.isFile) throw IOException("Could not copy ${source.name}")
                val bytes = AtomicFile(source).openRead().use { it.readBytes() }
                val atomic = AtomicFile(target)
                val stream = atomic.startWrite()
                try {
                    stream.write(bytes)
                    atomic.finishWrite(stream)
                } catch (error: Throwable) {
                    atomic.failWrite(stream)
                    throw error
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

    /** All targets are checked before the first write; recovery points cover rollback failures. */
    fun applyProjectEdits(root: File, edits: List<ProjectEdit>, complete: (Result<Unit>) -> Unit) {
        executor.execute {
            complete(runCatching {
                if (edits.isEmpty() || edits.size > 8 || edits.map { it.path }.distinct().size != edits.size)
                    throw IOException("Invalid project edit selection")
                val targets = edits.map { edit ->
                    val target = ProjectWorkspace.resolvePath(root, edit.path)
                        ?.takeIf { it.isFile && it.extension.equals("py", true) }
                        ?: throw IOException("File is no longer available: ${edit.path}")
                    val bytes = AtomicFile(target).openRead().use { it.readBytes() }
                    if (!bytes.contentEquals(edit.original.toByteArray(StandardCharsets.UTF_8)))
                        throw IOException("File changed since Astro read it: ${edit.path}")
                    target to bytes
                }
                targets.forEach { (file, original) -> ProjectFileHistory.recoveryPoint(file, original) }
                val written = mutableListOf<Pair<File, ByteArray>>()
                try {
                    edits.zip(targets).forEach { (edit, target) ->
                        val (file, original) = target
                        writeAtomic(file, edit.proposed.toByteArray(StandardCharsets.UTF_8))
                        written.add(file to original)
                    }
                } catch (failure: Throwable) {
                    val rollbackErrors = written.reversed().mapNotNull { (file, original) ->
                        runCatching { writeAtomic(file, original) }.exceptionOrNull()
                    }
                    if (rollbackErrors.isNotEmpty()) throw IOException(
                        "Some edits could not be rolled back; restore them from History", failure)
                    throw failure
                }
            })
        }
    }

    private fun writeAtomic(file: File, bytes: ByteArray) {
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try { stream.write(bytes); atomic.finishWrite(stream) }
        catch (error: Throwable) { atomic.failWrite(stream); throw error }
    }
}
