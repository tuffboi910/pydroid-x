package com.pydroidx.app

import java.io.File
import java.util.concurrent.TimeUnit

data class TerminalResult(
    val output: String,
    val cwd: String,
    val clear: Boolean = false,
    val exitCode: Int = 0
)

class TerminalSession(private val root: File) {
    private var cwd: File = root.canonicalFile

    fun currentDirectory(): File = cwd

    fun prompt(): String {
        val base = root.canonicalFile
        val current = cwd.canonicalFile
        val relative = runCatching { current.relativeTo(base).path }.getOrDefault(current.path)
        return if (relative.isBlank()) "~" else "~/${relative.replace(File.separatorChar, '/')}"
    }

    fun execute(rawCommand: String, timeoutSeconds: Long = 20): TerminalResult {
        val command = rawCommand.trim()
        if (command.isEmpty()) return TerminalResult("", prompt())
        if (command == "clear") return TerminalResult("", prompt(), clear = true)

        if (command == "cd" || command.startsWith("cd ")) {
            val requested = command.removePrefix("cd").trim().ifBlank { "." }
            val target = resolveWithinRoot(requested)
                ?: return TerminalResult("cd: path is outside this project\n", prompt(), exitCode = 1)
            if (!target.exists()) return TerminalResult("cd: no such file or directory: $requested\n", prompt(), exitCode = 1)
            if (!target.isDirectory) return TerminalResult("cd: not a directory: $requested\n", prompt(), exitCode = 1)
            cwd = target.canonicalFile
            return TerminalResult("", prompt())
        }

        val process = ProcessBuilder("/system/bin/sh", "-c", command)
            .directory(cwd)
            .redirectErrorStream(true)
            .apply {
                environment()["HOME"] = root.canonicalPath
                environment()["PWD"] = cwd.canonicalPath
            }
            .start()

        val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            return TerminalResult("Command timed out after ${timeoutSeconds}s\n", prompt(), exitCode = 124)
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }.take(200_000)
        return TerminalResult(output, prompt(), exitCode = process.exitValue())
    }

    private fun resolveWithinRoot(path: String): File? {
        val base = root.canonicalFile
        val candidate = when {
            path == "~" -> base
            path.startsWith("~/") -> File(base, path.removePrefix("~/"))
            File(path).isAbsolute -> File(path)
            else -> File(cwd, path)
        }.canonicalFile
        return candidate.takeIf { it == base || it.path.startsWith(base.path + File.separator) }
    }
}
