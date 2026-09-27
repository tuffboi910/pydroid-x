package com.pydroidx.app

data class CompletionItem(
    val label: String, val insertText: String, val replaceStart: Int, val replaceEnd: Int,
    val cursorBack: Int, val type: String, val signature: String, val doc: String
)
data class CompletionResult(val items: List<CompletionItem>)

/** Results belong to one immutable editor state, never just to a matching prefix. */
internal class CompletionSession {
    var generation = 0L
        private set
    private var source: String? = null
    private var cursor = -1
    var items: List<CompletionItem> = emptyList()
        private set

    fun clear() { generation++; source = null; cursor = -1; items = emptyList() }

    fun offer(token: Long, snapshot: String, position: Int, values: List<CompletionItem>): Boolean {
        if (token != generation) return false
        source = snapshot; cursor = position
        items = values.filter { it.replaceStart in 0..position && it.replaceEnd in position..snapshot.length }
        return true
    }

    fun edit(index: Int, current: String, start: Int, end: Int): EditorSnapshot? {
        if (current != source || start != cursor || end != cursor) return null
        val item = items.getOrNull(index) ?: return null
        val result = current.replaceRange(item.replaceStart, item.replaceEnd, item.insertText)
        val next = item.replaceStart + item.insertText.length - item.cursorBack
        return EditorSnapshot(result, next.coerceIn(item.replaceStart, result.length))
    }

    companion object { const val DELAY_MS = 2_000L }
}
