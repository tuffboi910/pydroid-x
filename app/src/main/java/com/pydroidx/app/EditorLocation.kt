package com.pydroidx.app

/** Selection offsets are UTF-16 indices, matching Android EditText. */
internal data class EditorLocation(val start: Int, val end: Int, val scrollX: Int, val scrollY: Int) {
    fun encode(): String = "$start,$end,$scrollX,$scrollY"

    companion object {
        fun decode(value: String): EditorLocation? {
            val parts = value.split(',').map { it.toIntOrNull() }
            if (parts.size != 4 || parts.any { it == null }) return null
            return EditorLocation(parts[0]!!, parts[1]!!, parts[2]!!, parts[3]!!)
        }
    }
}
