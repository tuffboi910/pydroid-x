package com.pydroidx.app

data class ConsoleInputState(val text: String, val cursor: Int)

object ConsoleInputController {
    fun insert(state: ConsoleInputState, value: String): ConsoleInputState {
        val cursor = state.cursor.coerceIn(0, state.text.length)
        return ConsoleInputState(
            state.text.substring(0, cursor) + value + state.text.substring(cursor),
            cursor + value.length
        )
    }

    fun move(state: ConsoleInputState, direction: Int, byWord: Boolean = false): ConsoleInputState {
        val cursor = state.cursor.coerceIn(0, state.text.length)
        if (!byWord) return state.copy(cursor = (cursor + direction).coerceIn(0, state.text.length))
        if (direction < 0) {
            var next = cursor
            while (next > 0 && state.text[next - 1].isWhitespace()) next--
            while (next > 0 && !state.text[next - 1].isWhitespace()) next--
            return state.copy(cursor = next)
        }
        var next = cursor
        while (next < state.text.length && !state.text[next].isWhitespace()) next++
        while (next < state.text.length && state.text[next].isWhitespace()) next++
        return state.copy(cursor = next)
    }

    fun home(state: ConsoleInputState) = state.copy(cursor = 0)
    fun end(state: ConsoleInputState) = state.copy(cursor = state.text.length)
}

class ConsoleInputHistory(private val limit: Int = 50) {
    private val entries = mutableListOf<String>()
    private var index = 0

    fun record(value: String) {
        if (value.isBlank()) return
        if (entries.lastOrNull() != value) entries += value
        while (entries.size > limit) entries.removeAt(0)
        index = entries.size
    }

    fun previous(): String? {
        if (entries.isEmpty()) return null
        index = (index - 1).coerceAtLeast(0)
        return entries[index]
    }

    fun next(): String? {
        if (entries.isEmpty()) return null
        if (index >= entries.lastIndex) {
            index = entries.size
            return ""
        }
        index++
        return entries[index]
    }
}
