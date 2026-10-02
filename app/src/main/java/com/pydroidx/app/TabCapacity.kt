package com.pydroidx.app

/** Selects an unpinned background tab when the open-file limit is reached. */
internal object TabCapacity {
    fun eviction(open: List<String>, pinned: Collection<String>, selected: String): String? =
        open.firstOrNull { it != selected && it !in pinned }
}
