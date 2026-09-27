package com.pydroidx.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils

/** Draw and hit-test use the exact same viewport-space rectangle. */
object CompletionPopup {
    private var cachedDocsKey: String? = null
    private var cachedDocsLayout: StaticLayout? = null
    fun draw(canvas: Canvas, width: Int, height: Int, caretX: Float, caretTop: Float,
             caretBottom: Float, density: Float, items: List<CompletionItem>, selected: Int): Pair<RectF, Float>? {
        val d = density
        val margin = 6 * d
        if (items.isEmpty() || width < 100 * d || caretBottom < 0 || caretTop > height) return null
        val below = (height - caretBottom - margin * 2).coerceAtLeast(0f)
        val above = (caretTop - margin * 2).coerceAtLeast(0f)
        val available = maxOf(below, above)
        val rowHeight = minOf(22 * d, available / items.size)
        if (rowHeight < 18 * d) return null
        val docsHeight = minOf(54 * d, available - rowHeight * items.size).coerceAtLeast(0f)
        val popupHeight = rowHeight * items.size + docsHeight
        val popupWidth = minOf(190 * d, width - margin * 2)
        val left = (caretX - 12 * d).coerceIn(margin, width - margin - popupWidth)
        val top = if (below >= popupHeight) caretBottom + margin else caretTop - margin - popupHeight
        val bounds = RectF(left, top, left + popupWidth, top + popupHeight)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 35, 43) }
        canvas.drawRoundRect(bounds, 5 * d, 5 * d, fill)
        fill.style = Paint.Style.STROKE; fill.strokeWidth = d; fill.color = Color.rgb(67, 77, 90)
        canvas.drawRoundRect(bounds, 5 * d, 5 * d, fill)
        canvas.save()
        canvas.clipRect(bounds)
        try {
            val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(232, 237, 244); textSize = 11 * d; typeface = Typeface.MONOSPACE
            }
            val detail = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(166, 189, 214); textSize = 8.5f * d
            }
            items.forEachIndexed { index, item ->
                val rowTop = top + index * rowHeight
                if (index == selected) {
                    fill.style = Paint.Style.FILL; fill.color = Color.rgb(9, 78, 135)
                    canvas.drawRoundRect(left + 3*d, rowTop + 1*d, left + popupWidth - 3*d,
                        rowTop + rowHeight - 1*d, 3*d, 3*d, fill)
                }
                val baseline = rowTop + (rowHeight - label.fontMetrics.ascent - label.fontMetrics.descent) / 2
                val cx = left + 12*d; val cy = rowTop + rowHeight/2
                val cube = Path().apply {
                    moveTo(cx, cy-5*d); lineTo(cx+4.5f*d, cy-2.5f*d); lineTo(cx+4.5f*d, cy+2.5f*d)
                    lineTo(cx, cy+5*d); lineTo(cx-4.5f*d, cy+2.5f*d); lineTo(cx-4.5f*d, cy-2.5f*d); close()
                    moveTo(cx-4.5f*d, cy-2.5f*d); lineTo(cx, cy); lineTo(cx+4.5f*d, cy-2.5f*d)
                    moveTo(cx, cy); lineTo(cx, cy+5*d)
                }
                fill.style = Paint.Style.STROKE; fill.strokeWidth = 1.1f*d; fill.color = Color.rgb(191, 132, 239)
                canvas.drawPath(cube, fill)
                val typeWidth = detail.measureText(item.type)
                val labelWidth = (popupWidth - typeWidth - 37*d).coerceAtLeast(1f)
                canvas.drawText(TextUtils.ellipsize(item.label, label, labelWidth, TextUtils.TruncateAt.END).toString(),
                    left+23*d, baseline, label)
                canvas.drawText(item.type, left+popupWidth-typeWidth-6*d, baseline, detail)
            }
            if (docsHeight >= 27*d) {
                val docsTop = top + rowHeight * items.size
                fill.color = Color.rgb(67, 77, 90); fill.strokeWidth = d
                canvas.drawLine(left+6*d, docsTop, left+popupWidth-6*d, docsTop, fill)
                val item = items[selected.coerceIn(items.indices)]
                val description = listOf(item.signature, item.doc.replace(Regex("\\s+"), " "))
                    .filter { it.isNotBlank() }.joinToString("\n").ifBlank { "Python ${item.type}" }
                detail.color = Color.rgb(199, 210, 222); detail.textSize = 8.5f*d
                val contentHeight = docsHeight - 17*d
                val maxLines = (contentHeight / (detail.fontSpacing)).toInt().coerceAtLeast(1)
                val docsWidth = (popupWidth-12*d).toInt().coerceAtLeast(1)
                val docsKey = "$description|$docsWidth|$maxLines|$d"
                val layout = if (cachedDocsKey == docsKey) cachedDocsLayout!! else
                    StaticLayout.Builder.obtain(description, 0, description.length, detail, docsWidth)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
                        .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).build().also {
                            cachedDocsKey = docsKey; cachedDocsLayout = it
                        }
                canvas.save()
                canvas.clipRect(left+6*d, docsTop+4*d, left+popupWidth-6*d, top+popupHeight-14*d)
                canvas.translate(left+6*d, docsTop+4*d); layout.draw(canvas); canvas.restore()
                detail.textSize = 7.5f*d; detail.color = Color.rgb(144, 186, 225)
                val hint = "Tap a suggestion · Tab to accept"
                canvas.drawText(hint, left+popupWidth-detail.measureText(hint)-6*d, top+popupHeight-4*d, detail)
            }
        } finally { canvas.restore() }
        return RectF(left, top, left+popupWidth, top+rowHeight*items.size) to rowHeight
    }
}
