package com.pydroidx.app

object ProjectWorkspace {
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
