package com.pydroidx.app

import java.io.File

data class ProjectBrowserEntry(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val modified: Long
)

object ProjectWorkspace {
    val templates = listOf("Blank", "Hello World", "CLI App", "Calculator", "Automation", "Guessing Game", "CSV Data")
    private val ignoredDirectories = setOf(".history", ".recovery", "__pycache__", ".git", ".venv", "venv", "node_modules")

    /** Resolves a project-relative path without allowing traversal or symlinks outside the project. */
    fun resolvePath(root: File, relativePath: String): File? {
        val rootPath = runCatching { root.canonicalFile }.getOrNull() ?: return null
        if (relativePath.isEmpty()) return rootPath
        val normalized = normalizePath(relativePath) ?: return null
        val resolved = runCatching { File(rootPath, normalized).canonicalFile }.getOrNull() ?: return null
        return resolved.takeIf { it.path.startsWith(rootPath.path + File.separator) }
    }

    fun normalizePath(path: String): String? {
        val portable = path.replace('\\', '/')
        if (portable.isBlank() || portable.startsWith('/') || Regex("^[A-Za-z]:").containsMatchIn(portable)) return null
        val parts = portable.split('/')
        if (parts.any { it.isBlank() || it == "." || it == ".." }) return null
        return parts.joinToString("/")
    }

    fun relativePath(root: File, file: File): String? {
        val rootPath = runCatching { root.canonicalFile }.getOrNull() ?: return null
        val filePath = runCatching { file.canonicalFile }.getOrNull() ?: return null
        if (!filePath.path.startsWith(rootPath.path + File.separator)) return null
        return filePath.path.removePrefix(rootPath.path + File.separator).replace(File.separatorChar, '/')
    }

    /** Lists only navigable folders and Python files; callers should run filesystem work on IO. */
    fun entries(root: File, folder: String, limit: Int = 500): List<ProjectBrowserEntry> {
        val directory = resolvePath(root, folder) ?: return emptyList()
        if (!directory.isDirectory) return emptyList()
        val listed = directory.listFiles().orEmpty().mapNotNull { child ->
            val relative = relativePath(root, child) ?: return@mapNotNull null
            val isDirectory = child.isDirectory
            if (isDirectory && (child.name in ignoredDirectories || child.name.startsWith('.'))) return@mapNotNull null
            if (!isDirectory && (!child.isFile || !child.extension.equals("py", true))) return@mapNotNull null
            ProjectBrowserEntry(child.name, relative, isDirectory, child.lastModified())
        }
        val maximum = limit.coerceIn(1, 2_000)
        val folders = listed.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
        val files = listed.filterNot { it.isDirectory }.sortedBy { it.name.lowercase() }
        val visibleFolders = folders.take(minOf(folders.size, maximum / 4))
        return visibleFolders + files.take(maximum - visibleFolders.size)
    }

    /** Bounded recursive Python file enumeration for Quick Open and project analysis. */
    fun pythonFiles(root: File, limit: Int = 5_000): List<File> {
        val base = runCatching { root.canonicalFile }.getOrNull() ?: return emptyList()
        val pending = ArrayDeque<File>().apply { add(base) }
        val visited = hashSetOf(base.path)
        val result = ArrayList<File>()
        val maximum = limit.coerceIn(1, 20_000)
        while (pending.isNotEmpty() && result.size < maximum) {
            val directory = pending.removeLast()
            for (child in directory.listFiles().orEmpty()) {
                val relative = relativePath(base, child) ?: continue
                if (child.isDirectory) {
                    if (child.name in ignoredDirectories || child.name.startsWith('.') || visited.size >= maximum) continue
                    if (visited.add(child.canonicalPath)) pending.add(child)
                } else if (child.isFile && child.extension.equals("py", true)) {
                    result.add(child)
                    if (result.size >= maximum) break
                }
            }
        }
        return result
    }

    fun folders(root: File, limit: Int = 2_000): List<String> {
        val base = runCatching { root.canonicalFile }.getOrNull() ?: return emptyList()
        val pending = ArrayDeque<File>().apply { add(base) }
        val visited = hashSetOf(base.path)
        val result = arrayListOf("")
        val maximum = limit.coerceIn(1, 10_000)
        while (pending.isNotEmpty() && result.size < maximum) {
            val directory = pending.removeLast()
            for (child in directory.listFiles().orEmpty()) {
                if (!child.isDirectory || child.name in ignoredDirectories || child.name.startsWith('.')) continue
                val relative = relativePath(base, child) ?: continue
                val canonical = child.canonicalPath
                if (visited.add(canonical)) {
                    result.add(relative)
                    pending.add(child)
                    if (result.size >= maximum) break
                }
            }
        }
        return listOf("") + result.drop(1).sorted()
    }

    fun templateSource(template: String): String = when (template) {
        "Blank" -> ""
        "CLI App" -> """while True:
    choice = input("1. Say hello  2. Exit: ")
    if choice == "1":
        print("Hello!")
    elif choice == "2":
        break
    else:
        print("Choose 1 or 2")
"""
        "Calculator" -> """first = float(input("First number: "))
operator = input("Operation (+, -, *, /): ")
second = float(input("Second number: "))

if operator == "+":
    print(first + second)
elif operator == "-":
    print(first - second)
elif operator == "*":
    print(first * second)
elif operator == "/" and second != 0:
    print(first / second)
else:
    print("Unknown operation or division by zero")
"""
        "Automation" -> """from pathlib import Path

for file in Path(".").iterdir():
    if file.is_file():
        print(file.name, file.stat().st_size, "bytes")
"""
        "Guessing Game" -> """import random

answer = random.randint(1, 10)
while True:
    guess = int(input("Guess 1 to 10: "))
    if guess == answer:
        print("You got it!")
        break
    print("Try again")
"""
        "CSV Data" -> """import csv

rows = [{"name": "Alex", "score": 8}, {"name": "Sam", "score": 10}]
with open("scores.csv", "w", newline="", encoding="utf-8") as file:
    writer = csv.DictWriter(file, fieldnames=["name", "score"])
    writer.writeheader()
    writer.writerows(rows)

with open("scores.csv", newline="", encoding="utf-8") as file:
    for row in csv.DictReader(file):
        print(row["name"], row["score"])
"""
        else -> "print(\"Hello world!\")\n"
    }

    fun safeName(value: String): String = value.trim()
        .replace(Regex("[^A-Za-z0-9 _-]+"), " ")
        .replace(Regex(" +"), " ")
        .trim(' ', '.', '_', '-')
        .take(40)
        .ifBlank { "Project" }

    fun nextName(existing: Collection<String>, base: String = "Project"): String {
        val cleanBase = safeName(base)
        if (cleanBase !in existing) return cleanBase
        var number = 2
        while ("$cleanBase $number" in existing) number++
        return "$cleanBase $number"
    }
}
