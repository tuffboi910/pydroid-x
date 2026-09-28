package com.pydroidx.app

internal object OpenFileTabsCodec {
    private const val SEPARATOR = "\u001F"

    fun encode(files: Collection<String>): String =
        files.asSequence()
            .filter { it.isNotBlank() && SEPARATOR !in it }
            .distinct()
            .joinToString(SEPARATOR)

    fun decode(value: String?): List<String> =
        value.orEmpty()
            .split(SEPARATOR)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
}
