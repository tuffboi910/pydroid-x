package com.pydroidx.app

/** Incremental newline offsets used by the editor gutter. */
internal class LineBreakIndex {
    private var offsets = IntArray(16)
    var size: Int = 0
        private set

    fun rebuild(source: CharSequence) {
        var count = 0
        for (index in source.indices) if (source[index] == '\n') count++
        ensureCapacity(count)
        var write = 0
        for (index in source.indices) if (source[index] == '\n') offsets[write++] = index
        size = write
    }

    /** Apply a TextWatcher edit [start, start + before) -> [start, start + count). */
    fun update(source: CharSequence, start: Int, before: Int, count: Int) {
        val editStart = start.coerceIn(0, source.length)
        val removedEnd = (start + before).coerceAtLeast(editStart)
        val insertedEnd = (start + count).coerceIn(editStart, source.length)
        val first = lowerBound(editStart)
        val afterRemoved = lowerBound(removedEnd)

        var insertedBreaks = 0
        for (index in editStart until insertedEnd) if (source[index] == '\n') insertedBreaks++
        val nextSize = size - (afterRemoved - first) + insertedBreaks
        ensureCapacity(nextSize)

        val nextTailStart = first + insertedBreaks
        System.arraycopy(offsets, afterRemoved, offsets, nextTailStart, size - afterRemoved)
        var write = first
        for (index in editStart until insertedEnd) {
            if (source[index] == '\n') offsets[write++] = index
        }
        val delta = count - before
        for (index in nextTailStart until nextSize) offsets[index] += delta
        size = nextSize
    }

    /** Number of newline characters strictly before [offset]. */
    fun countBefore(offset: Int): Int = lowerBound(offset)

    fun offsetAt(index: Int): Int = offsets[index]

    private fun lowerBound(value: Int): Int {
        var low = 0
        var high = size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (offsets[middle] < value) low = middle + 1 else high = middle
        }
        return low
    }

    private fun ensureCapacity(required: Int) {
        if (required <= offsets.size) return
        var capacity = offsets.size
        while (capacity < required) capacity = (capacity * 2).coerceAtLeast(required)
        offsets = offsets.copyOf(capacity)
    }
}
