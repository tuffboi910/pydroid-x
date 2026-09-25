package com.pydroidx.app

internal data class EditorSnapshot(val text: String, val cursor: Int)

internal class EditorHistory(private val limit: Int = 100) {
    private val undo = ArrayDeque<EditorSnapshot>()
    private val redo = ArrayDeque<EditorSnapshot>()

    fun record(snapshot: EditorSnapshot) {
        if (undo.lastOrNull() == snapshot) return
        undo.addLast(snapshot)
        while (undo.size > limit) undo.removeFirst()
        redo.clear()
    }

    fun undo(current: EditorSnapshot): EditorSnapshot? {
        val previous = undo.removeLastOrNull() ?: return null
        redo.addLast(current)
        return previous
    }

    fun redo(current: EditorSnapshot): EditorSnapshot? {
        val next = redo.removeLastOrNull() ?: return null
        undo.addLast(current)
        return next
    }

    fun clear() {
        undo.clear()
        redo.clear()
    }
}
