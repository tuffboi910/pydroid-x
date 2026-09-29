package com.pydroidx.app

internal data class ProjectEdit(val path: String, val original: String, val proposed: String)

/** Accepts explicit complete-file proposals only for files shared in this request. */
internal object ProjectEditProposal {
    private val block = Regex("(?m)^File: ([^\\r\\n]+)\\r?\\n```(?:python|py)\\r?\\n([\\s\\S]*?)\\r?\\n```(?:\\r?\\n|$)", RegexOption.IGNORE_CASE)

    fun parse(answer: String, sources: Map<String, String>): List<ProjectEdit> {
        val found = mutableListOf<ProjectEdit>()
        val seen = hashSetOf<String>()
        for (match in block.findAll(answer)) {
            val path = match.groupValues[1].trim()
            val original = sources[path] ?: return emptyList()
            if (!seen.add(path) || found.size >= 8) return emptyList()
            val proposed = match.groupValues[2] + "\n"
            if (proposed.length > 100_000) return emptyList()
            if (proposed != original) found.add(ProjectEdit(path, original, proposed))
        }
        return found
    }
}
