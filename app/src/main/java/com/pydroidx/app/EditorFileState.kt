package com.pydroidx.app

data class EditorFileState(
    val selectionStart: Int = 0,
    val selectionEnd: Int = 0,
    val scrollX: Int = 0,
    val scrollY: Int = 0
)

internal object EditorFileStateCodec {
    fun encode(state: EditorFileState): String =
        listOf(state.selectionStart,state.selectionEnd,state.scrollX,state.scrollY)
            .joinToString(",")

    fun decode(value: String?): EditorFileState? {
        if (value.isNullOrBlank()) return null
        val parts=value.split(',')
        if (parts.size!=4) return null
        val numbers=parts.map { it.toIntOrNull() ?: return null }
        if (numbers.any { it<0 }) return null
        return EditorFileState(
            selectionStart=numbers[0],
            selectionEnd=numbers[1],
            scrollX=numbers[2],
            scrollY=numbers[3]
        )
    }
}
