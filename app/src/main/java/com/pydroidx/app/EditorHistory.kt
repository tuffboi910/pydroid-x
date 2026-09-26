package com.pydroidx.app

internal data class EditorSnapshot(val text: String, val cursor: Int)

internal class EditorHistory(
    private val limit: Int = 100,
    private val maxCharacters: Int = 400_000
) {
    private val undo = ArrayDeque<EditorSnapshot>()
    private val redo = ArrayDeque<EditorSnapshot>()
    private var undoCharacters = 0
    private var redoCharacters = 0

    fun record(snapshot: EditorSnapshot) {
        if (undo.lastOrNull() == snapshot) return
        addUndo(snapshot)
        redo.clear()
        redoCharacters = 0
    }

    fun undo(current: EditorSnapshot): EditorSnapshot? {
        val previous = undo.removeLastOrNull() ?: return null
        undoCharacters -= previous.text.length
        addRedo(current)
        return previous
    }

    fun redo(current: EditorSnapshot): EditorSnapshot? {
        val next = redo.removeLastOrNull() ?: return null
        redoCharacters -= next.text.length
        addUndo(current)
        return next
    }

    fun clear() {
        undo.clear()
        redo.clear()
        undoCharacters = 0
        redoCharacters = 0
    }

    private fun addUndo(snapshot: EditorSnapshot) {
        undo.addLast(snapshot)
        undoCharacters += snapshot.text.length
        while (undo.size > limit || (undoCharacters > maxCharacters && undo.size > 1)) {
            undoCharacters -= undo.removeFirst().text.length
        }
    }

    private fun addRedo(snapshot: EditorSnapshot) {
        redo.addLast(snapshot)
        redoCharacters += snapshot.text.length
        while (redo.size > limit || (redoCharacters > maxCharacters && redo.size > 1)) {
            redoCharacters -= redo.removeFirst().text.length
        }
    }
}
