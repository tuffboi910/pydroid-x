package com.pydroidx.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.app.Application

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28], application=Application::class)
class CompletionPopupTest {
    @Test fun compactPopupKeepsSuggestionsTappable() {
        val canvas = Canvas(Bitmap.createBitmap(400, 700, Bitmap.Config.ARGB_8888))
        val entries = listOf("pass", "pow()", "print()").map {
            CompletionItem(it, it, 0, 1, 0, "function", "", "Short description")
        }
        val bounds = RectF()
        val rowHeight = CompletionPopup.draw(canvas, 400, 700, 120f, 220f, 244f, 1f, entries, 0, bounds)
        assertNotNull(rowHeight)
        assertTrue(bounds.width() <= 190f)
        assertTrue(bounds.height() <= 66f)
        assertEquals(22f, rowHeight!!, 0.1f)
        assertTrue(bounds.contains(bounds.centerX(), bounds.top + rowHeight * 2.5f))
    }
}
