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
    fun visibleTail(maxCharacters: Int = 32_000): String {
        var start = (content.length - maxCharacters.coerceAtLeast(1)).coerceAtLeast(0)
        if (start > 0 && start < content.length && Character.isLowSurrogate(content[start])) start--
        val marker = if (truncated || start > 0) "[Earlier output retained for Copy]\n" else ""
        return marker + content.substring(start)
    }

    @Synchronized
    fun clear() {
        content.clear()
        truncated = false
    }
}
