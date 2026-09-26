package com.pydroidx.app

internal class ConsoleOutputBuffer(
    private val maxCharacters: Int = 200_000,
    private val retainedCharacters: Int = 160_000
) {
    private val content = StringBuilder()
    private var truncated = false

    @Synchronized
    fun append(value: String) {
        if (value.isEmpty()) return
        content.append(value)
        if (content.length > maxCharacters) {
            val keep = retainedCharacters.coerceIn(1, maxCharacters)
            content.delete(0, content.length - keep)
            truncated = true
        }
    }

    @Synchronized
    fun snapshot(): String =
        if (truncated) "[Earlier output truncated]\n$content" else content.toString()

    @Synchronized
    fun clear() {
        content.clear()
        truncated = false
    }
}
