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
        return File(root, ".recovery/drafts/$path.draft")
    }

    /** Coalesces rapid edits; the latest snapshot is journaled on the file worker. */
    fun journalDraft(root: File, file: File, content: String) {
        val draft = draftFile(root, file) ?: return
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
            } catch (_: IOException) {
                // Keep the previous atomic draft and retry on the next edit.
            } finally {
                draftScheduled.remove(key)
                if (completed) draftSnapshots[key]?.let { journalDraft(root, file, it) }
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
}
