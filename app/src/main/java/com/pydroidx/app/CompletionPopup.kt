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

/** Draw and hit-test share the caller-owned bounds; drawing reuses its hot-path objects. */
object CompletionPopup {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(232, 237, 244)
        typeface = Typeface.MONOSPACE
    }
    private val detail = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(165, 170, 180)
    }
    private val path = Path()
    private val fontMetrics = Paint.FontMetrics()
    private val docsWhitespace = Regex("\\s+")
    private var cachedDocsSignature: String? = null
    private var cachedDocsText: String? = null
    private var cachedDocsType: String? = null
    private var cachedDocsDescription = ""
    private var cachedDocsWidth = -1
    private var cachedDocsLines = -1
    private var cachedDocsDensity = 0f
    private var cachedLayoutDescription: String? = null
    private var cachedDocsLayout: StaticLayout? = null

    fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
        caretX: Float,
        caretTop: Float,
        caretBottom: Float,
        density: Float,
        items: List<CompletionItem>,
        selected: Int,
        boundsOut: RectF
    ): Float? {
        boundsOut.setEmpty()
        val margin = 6 * density
        if (items.isEmpty() || width < 100 * density || caretBottom < 0 || caretTop > height) return null
        val below = (height - caretBottom - margin * 2).coerceAtLeast(0f)
        val above = (caretTop - margin * 2).coerceAtLeast(0f)
        val available = maxOf(below, above)
        val rowHeight = minOf(22 * density, available / items.size)
        if (rowHeight < 18 * density) return null
        val docsHeight = minOf(54 * density, available - rowHeight * items.size).coerceAtLeast(0f)
        val popupHeight = rowHeight * items.size + docsHeight
        val popupWidth = minOf(190 * density, width - margin * 2)
        val left = (caretX - 12 * density).coerceIn(margin, width - margin - popupWidth)
        val top = if (below >= popupHeight) caretBottom + margin else caretTop - margin - popupHeight
        val right = left + popupWidth
        val bottom = top + popupHeight

        fill.style = Paint.Style.FILL
        fill.color = Color.rgb(34, 37, 42)
        fill.strokeWidth = density
        canvas.drawRoundRect(left, top, right, bottom, 5 * density, 5 * density, fill)
        fill.style = Paint.Style.STROKE
        fill.color = Color.rgb(53, 57, 64)
        canvas.drawRoundRect(left, top, right, bottom, 5 * density, 5 * density, fill)
        canvas.save()
        canvas.clipRect(left, top, right, bottom)
        try {
            label.textSize = 11 * density
            detail.textSize = 8.5f * density
            label.getFontMetrics(fontMetrics)
            items.forEachIndexed { index, item ->
                val rowTop = top + index * rowHeight
                if (index == selected) {
                    fill.style = Paint.Style.FILL
                    fill.color = Color.rgb(51, 58, 69)
                    canvas.drawRoundRect(left + 3 * density, rowTop + density, right - 3 * density,
                        rowTop + rowHeight - density, 3 * density, 3 * density, fill)
                }
                val baseline = rowTop + (rowHeight - fontMetrics.ascent - fontMetrics.descent) / 2
                val cx = left + 12 * density
                val cy = rowTop + rowHeight / 2
                path.rewind()
                path.moveTo(cx, cy - 5 * density)
                path.lineTo(cx + 4.5f * density, cy - 2.5f * density)
                path.lineTo(cx + 4.5f * density, cy + 2.5f * density)
                path.lineTo(cx, cy + 5 * density)
                path.lineTo(cx - 4.5f * density, cy + 2.5f * density)
                path.lineTo(cx - 4.5f * density, cy - 2.5f * density)
                path.close()
                path.moveTo(cx - 4.5f * density, cy - 2.5f * density)
                path.lineTo(cx, cy)
                path.lineTo(cx + 4.5f * density, cy - 2.5f * density)
                path.moveTo(cx, cy)
                path.lineTo(cx, cy + 5 * density)
                fill.style = Paint.Style.STROKE
                fill.strokeWidth = 1.1f * density
                fill.color = Color.rgb(168, 199, 250)
                canvas.drawPath(path, fill)

                val typeWidth = detail.measureText(item.type)
                val labelWidth = (popupWidth - typeWidth - 37 * density).coerceAtLeast(1f)
                val shownLabel = TextUtils.ellipsize(item.label, label, labelWidth, TextUtils.TruncateAt.END)
                canvas.drawText(shownLabel, 0, shownLabel.length, left + 23 * density, baseline, label)
                canvas.drawText(item.type, right - typeWidth - 6 * density, baseline, detail)
            }
            if (docsHeight >= 27 * density) {
                val docsTop = top + rowHeight * items.size
                fill.style = Paint.Style.STROKE
                fill.color = Color.rgb(53, 57, 64)
                fill.strokeWidth = density
                canvas.drawLine(left + 6 * density, docsTop, right - 6 * density, docsTop, fill)
                val item = items[selected.coerceIn(items.indices)]
                val description = descriptionFor(item)
                detail.color = Color.rgb(199, 210, 222)
                detail.textSize = 8.5f * density
                val contentHeight = docsHeight - 17 * density
                val maxLines = (contentHeight / detail.fontSpacing).toInt().coerceAtLeast(1)
                val docsWidth = (popupWidth - 12 * density).toInt().coerceAtLeast(1)
                var layout = cachedDocsLayout
                if (layout == null || cachedLayoutDescription !== description ||
                    cachedDocsWidth != docsWidth || cachedDocsLines != maxLines ||
                    cachedDocsDensity != density || cachedDocsSignature != item.signature || cachedDocsText != item.doc) {
                    layout = StaticLayout.Builder.obtain(description, 0, description.length, detail, docsWidth)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
                        .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).build()
                    cachedDocsLayout = layout
                    cachedDocsWidth = docsWidth
                    cachedDocsLines = maxLines
                    cachedDocsDensity = density
                    cachedDocsSignature = item.signature
                    cachedDocsText = item.doc
                    cachedLayoutDescription = description
                }
                canvas.save()
                canvas.clipRect(left + 6 * density, docsTop + 4 * density,
                    right - 6 * density, bottom - 14 * density)
                canvas.translate(left + 6 * density, docsTop + 4 * density)
                layout.draw(canvas)
                canvas.restore()
                detail.textSize = 7.5f * density
                detail.color = Color.rgb(165, 170, 180)
                val hint = "Tap a suggestion · Tab to accept"
                canvas.drawText(hint, right - detail.measureText(hint) - 6 * density, bottom - 4 * density, detail)
            }
        } finally {
            canvas.restore()
        }
        boundsOut.set(left, top, right, top + rowHeight * items.size)
        return rowHeight
    }

    private fun descriptionFor(item: CompletionItem): String {
        if (cachedDocsSignature == item.signature && cachedDocsText == item.doc && cachedDocsType == item.type) {
            return cachedDocsDescription
        }
        val docs = item.doc.replace(docsWhitespace, " ")
        cachedDocsDescription = when {
            item.signature.isNotBlank() && docs.isNotBlank() -> "${item.signature}\n$docs"
            item.signature.isNotBlank() -> item.signature
            docs.isNotBlank() -> docs
            else -> "Python ${item.type}"
        }
        cachedDocsSignature = item.signature
        cachedDocsText = item.doc
        cachedDocsType = item.type
        return cachedDocsDescription
    }
}
