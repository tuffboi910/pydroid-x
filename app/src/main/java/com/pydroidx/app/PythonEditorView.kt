package com.pydroidx.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.EditText
import java.io.File

internal class PythonEditorView(context: Context) : EditText(context) {
    var onCodeChanged: ((String) -> Unit)? = null
    var requestSmartCompletion: ((String, Int, (CompletionResult) -> Unit) -> Unit)? = null
    var requestCodeDiagnostics: ((String, (List<CodeDiagnostic>) -> Unit) -> Unit)? = null
    private var applyingHighlight = false
    private var applyingHistory = false
    private var beforeEdit = EditorSnapshot("", 0)
    private var shouldRecordHistory = false
    private var lastHistoryCaptureAt = 0L
    private var loadedRevision = -1
    private val history = EditorHistory()
    private var highlightingEnabled = true
    private var highlightDelayMs = 220L
    private var autocompleteEnabled = true
    private var ghostAlpha = 122
    private val completionSession = CompletionSession()
    private val completionItems get() = completionSession.items
    private var selectedCompletion = 0
    private var readyForSelectionChanges = false
    private var popupOwnsGesture = false
    private var popupTouchMoved = false
    private var completionPopupBounds: android.graphics.RectF? = null
    private var completionPopupRowHeight = 0f
    private var completionTouchIndex = -1
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchStartScrollX = 0
    private var touchAxis = 0
    private var diagnostics: List<CodeDiagnostic> = emptyList()
    private var renderSource = ""
    private var lineBreakOffsets = IntArray(0)
    private fun updateRenderSource(source: String) {
        renderSource = source
        lineBreakOffsets = source.indices.asSequence().filter { source[it] == '\n' }.toList().toIntArray()
    }
    private fun newlineCountBefore(offset: Int): Int {
        val index = lineBreakOffsets.binarySearch(offset)
        return if (index >= 0) index else -index - 1
    }
    private var showLineNumbers = true
    private var showCurrentLine = true
    private var userPadding = 20
    private var editorTextColor=AndroidColor.rgb(212,212,212)
    private var commentColor=AndroidColor.rgb(106,153,85)
    private var stringColor=AndroidColor.rgb(206,145,120)
    private var numberColor=AndroidColor.rgb(181,206,168)
    private var keywordColor=AndroidColor.rgb(197,134,192)
    private var functionColor=AndroidColor.rgb(220,220,170)
    private var variableColor=AndroidColor.rgb(156,220,254)
    private var lastPreferenceSignature: String? = null
    private val gutterWidth get() = if(showLineNumbers) (52 * resources.displayMetrics.density).toInt() else 0
    private val keywords = setOf(
        "and", "as", "assert", "async", "await", "break", "case", "class", "continue",
        "def", "del", "elif", "else", "except", "finally", "for", "from", "global",
        "if", "import", "in", "is", "lambda", "match", "nonlocal", "not", "or", "pass",
        "raise", "return", "try", "while", "with", "yield"
    )
    private val constants = setOf("True", "False", "None", "NotImplemented", "Ellipsis")
    private val builtins = setOf(
        "abs","all","any","bin","bool","bytearray","bytes","callable","chr","classmethod",
        "compile","complex","delattr","dict","dir","divmod","enumerate","eval","exec","filter",
        "float","format","frozenset","getattr","globals","hasattr","hash","help","hex","id",
        "input","int","isinstance","issubclass","iter","len","list","locals","map","max","memoryview",
        "min","next","object","oct","open","ord","pow","print","property","range","repr","reversed",
        "round","set","setattr","slice","sorted","staticmethod","str","sum","super","tuple","type","vars","zip"
    )
    private val highlightRunnable = Runnable { highlightNow() }
    private val diagnosticsRunnable = Runnable {
        val snapshot = text.toString()
        if (snapshot.isBlank()) {
            diagnostics = emptyList()
            invalidate()
            return@Runnable
        }
        requestCodeDiagnostics?.invoke(snapshot) { issues ->
            post {
                if (text.toString() != snapshot) return@post
                diagnostics = issues
                invalidate()
            }
        }
    }
    private val completionRunnable = Runnable {
        if (!autocompleteEnabled || !hasFocus() || selectionStart != selectionEnd) return@Runnable
        val snapshot = text.toString()
        val cursor = selectionStart
        if (snapshot.isEmpty() || snapshot.length > 200_000 || cursor !in 1..snapshot.length) return@Runnable
        if (!(snapshot[cursor - 1].isLetterOrDigit() || snapshot[cursor - 1] in "_.")) return@Runnable
        val token = completionSession.generation
        requestSmartCompletion?.invoke(snapshot, cursor) { result ->
            post {
                if (hasFocus() && autocompleteEnabled && text.toString() == snapshot &&
                    selectionStart == cursor && selectionEnd == cursor &&
                    completionSession.offer(token, snapshot, cursor, result.items)) {
                    selectedCompletion = 0
                    invalidate()
                }
            }
        }
    }

    private fun clearCompletion() {
        removeCallbacks(completionRunnable)
        completionSession.clear()
        completionPopupBounds = null
        invalidate()
    }

    private fun scheduleCompletion() {
        clearCompletion()
        if (autocompleteEnabled && hasFocus()) postDelayed(completionRunnable, CompletionSession.DELAY_MS)
    }

    override fun onSelectionChanged(start: Int, end: Int) {
        super.onSelectionChanged(start, end)
        // TextView invokes this during construction, before Kotlin fields exist.
        if (readyForSelectionChanges && !applyingHighlight && !applyingHistory) scheduleCompletion()
    }

    override fun onDetachedFromWindow() {
        clearCompletion()
        removeCallbacks(highlightRunnable)
        removeCallbacks(diagnosticsRunnable)
        super.onDetachedFromWindow()
    }

    init {
        setBackgroundColor(AndroidColor.rgb(11, 15, 20))
        setTextColor(AndroidColor.rgb(212, 212, 212))
        setHintTextColor(AndroidColor.DKGRAY)
        typeface = Typeface.MONOSPACE
        textSize = 16f
        gravity = Gravity.TOP or Gravity.START
        setPadding(20, 12, 20, 12)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        isSingleLine = false
        setHorizontallyScrolling(true)
        isVerticalScrollBarEnabled = true
        isHorizontalScrollBarEnabled = true
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (!applyingHighlight && !applyingHistory) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    val length = s?.length ?: 0
                    val bulkEdit = count > 1 || after > 1
                    shouldRecordHistory = length <= 20_000 || bulkEdit || now - lastHistoryCaptureAt >= 350L
                    if (shouldRecordHistory) {
                        beforeEdit = EditorSnapshot(s?.toString().orEmpty(), selectionStart.coerceAtLeast(0))
                        lastHistoryCaptureAt = now
                    }
                }
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!applyingHighlight && !applyingHistory) {
                    if (shouldRecordHistory) history.record(beforeEdit)
                    shouldRecordHistory = false
                    val source = s?.toString().orEmpty()
                    updateRenderSource(source)
                    onCodeChanged?.invoke(source)
                    removeCallbacks(highlightRunnable)
                    postDelayed(highlightRunnable, highlightDelayMs)
                    scheduleCompletion()
                    removeCallbacks(diagnosticsRunnable)
                    diagnostics = emptyList()
                    invalidate()
                    val safeStart = start.coerceIn(0,s?.length ?: 0)
                    val safeEnd = (start + count).coerceIn(safeStart,s?.length ?: safeStart)
                    val inserted = s?.subSequence(safeStart,safeEnd)?.toString().orEmpty()
                    if (inserted.contains('\n')) postDelayed(diagnosticsRunnable, 120)
                }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        readyForSelectionChanges = true
        setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) scheduleCompletion() else clearCompletion()
            if (!hasFocus && text.isNotBlank()) {
                removeCallbacks(diagnosticsRunnable)
                postDelayed(diagnosticsRunnable, 120)
            }
        }
    }

    fun setCodeIfDifferent(value: String, revision: Int) {
        if (loadedRevision != revision) {
            history.clear()
            diagnostics = emptyList()
            clearCompletion()
            loadedRevision = revision
        }
        if (text.toString() == value) return
        clearCompletion()
        applyingHighlight = true
        val cursor = selectionStart.coerceAtLeast(0).coerceAtMost(value.length)
        setText(value)
        updateRenderSource(value)
        setSelection(cursor)
        applyingHighlight = false
        highlightNow()
    }

    private fun restoreHistory(snapshot: EditorSnapshot?) {
        snapshot ?: return
        clearCompletion()
        diagnostics = emptyList()
        applyingHistory = true
        setText(snapshot.text)
        updateRenderSource(snapshot.text)
        setSelection(snapshot.cursor.coerceIn(0, snapshot.text.length))
        applyingHistory = false
        onCodeChanged?.invoke(snapshot.text)
        highlightNow()
    }

    fun undoCode() {
        restoreHistory(history.undo(EditorSnapshot(text.toString(), selectionStart.coerceAtLeast(0))))
    }

    fun redoCode() {
        restoreHistory(history.redo(EditorSnapshot(text.toString(), selectionStart.coerceAtLeast(0))))
    }

    fun insertAtCursor(value: String) {
        val start = selectionStart.coerceAtLeast(0)
        val end = selectionEnd.coerceAtLeast(0)
        text.replace(minOf(start, end), maxOf(start, end), value)
    }

    fun applyPreferences(font: Float, wrap: Boolean, syntax: Boolean, family: String, spacing: Float,
                         padding: Float, highlightDelay: Float, cursor: String, autocomplete: Boolean,
                         ghostBrightness: Float, lineNumbers: Boolean, currentLine: Boolean,
                         customFontPath: String, palette: List<String>) {
        val signature = listOf(
            font,wrap,syntax,family,spacing,padding,highlightDelay,cursor,autocomplete,
            ghostBrightness,lineNumbers,currentLine,customFontPath,palette.joinToString(",")
        ).joinToString("|")
        if (signature == lastPreferenceSignature) return
        lastPreferenceSignature = signature
        textSize = font
        setHorizontallyScrolling(!wrap)
        isHorizontalScrollBarEnabled = !wrap
        highlightingEnabled = syntax
        highlightDelayMs = highlightDelay.toLong()
        autocompleteEnabled = autocomplete
        ghostAlpha = (ghostBrightness * 255).toInt().coerceIn(0,210)
        showLineNumbers = lineNumbers
        showCurrentLine = currentLine
        fun parsed(index:Int,fallback:Int)=runCatching{AndroidColor.parseColor(palette[index])}.getOrDefault(fallback)
        editorTextColor=parsed(0,AndroidColor.rgb(212,212,212));commentColor=parsed(1,AndroidColor.rgb(106,153,85))
        stringColor=parsed(2,AndroidColor.rgb(206,145,120));numberColor=parsed(3,AndroidColor.rgb(181,206,168))
        keywordColor=parsed(4,AndroidColor.rgb(197,134,192));functionColor=parsed(5,AndroidColor.rgb(220,220,170))
        variableColor=parsed(6,AndroidColor.rgb(156,220,254));setTextColor(editorTextColor)
        typeface = if(customFontPath.isNotBlank() && File(customFontPath).exists()) {
            runCatching { Typeface.createFromFile(customFontPath) }.getOrDefault(Typeface.MONOSPACE)
        } else when(family) { "Sans" -> Typeface.SANS_SERIF; "Serif" -> Typeface.SERIF; else -> Typeface.MONOSPACE }
        setLineSpacing(0f, spacing)
        val pad = padding.toInt()
        userPadding = pad
        setPadding(gutterWidth + pad, pad / 2, pad, pad / 2)
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val cursorColor = when(cursor) { "Magenta" -> AndroidColor.rgb(255,77,255); "Green" -> AndroidColor.rgb(0,230,118); "White" -> AndroidColor.WHITE; else -> AndroidColor.rgb(0,229,255) }
            textCursorDrawable = GradientDrawable().apply { setColor(cursorColor); setSize(4, (this@PythonEditorView.textSize * 1.25f).toInt()) }
        }
        if (syntax) highlightNow() else {
            val editable = text
            editable?.getSpans(0, editable.length, ForegroundColorSpan::class.java)?.forEach { editable.removeSpan(it) }
            setTextColor(editorTextColor)
        }
        scheduleCompletion()
    }

    fun acceptGhostSuggestion(): Boolean = acceptCompletion(selectedCompletion)

    private fun completionIndexAt(x: Float, y: Float): Int {
        val bounds = completionPopupBounds ?: return -1
        if (!bounds.contains(x, y) || completionPopupRowHeight <= 0f) return -1
        val index = ((y - bounds.top) / completionPopupRowHeight).toInt()
        return index.takeIf { it in completionItems.indices } ?: -1
    }

    private fun acceptCompletion(index: Int): Boolean {
        val item = completionItems.getOrNull(index) ?: return false
        val edit = completionSession.edit(index, text.toString(), selectionStart, selectionEnd) ?: return false
        clearCompletion()
        beginBatchEdit()
        try {
            text.replace(item.replaceStart, item.replaceEnd, item.insertText)
            setSelection(edit.cursor)
        } finally { endBatchEdit() }
        clearCompletion()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            touchStartX = event.x; touchStartY = event.y
            touchStartScrollX = scrollX
            touchAxis = 0
            completionTouchIndex = completionIndexAt(event.x, event.y)
            popupOwnsGesture = completionTouchIndex >= 0
            popupTouchMoved = false
            parent?.requestDisallowInterceptTouchEvent(true)
            if (popupOwnsGesture) {
                selectedCompletion = completionTouchIndex
                invalidate()
                return true
            }
            clearCompletion()
        }
        if (popupOwnsGesture) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(event.x - touchStartX) > touchSlop ||
                        kotlin.math.abs(event.y - touchStartY) > touchSlop) popupTouchMoved = true
                }
                MotionEvent.ACTION_UP -> {
                    val index = completionTouchIndex
                    popupOwnsGesture = false
                    if (!popupTouchMoved && completionIndexAt(event.x, event.y) == index) {
                        performClick()
                        acceptCompletion(index)
                    }
                }
                MotionEvent.ACTION_CANCEL -> popupOwnsGesture = false
            }
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            val dx = kotlin.math.abs(event.x - touchStartX)
            val dy = kotlin.math.abs(event.y - touchStartY)
            if (touchAxis == 0 && maxOf(dx, dy) > touchSlop) touchAxis = if (dx > dy * 1.5f) 2 else 1
        }
        val handled = super.onTouchEvent(event)
        if (touchAxis == 1 && scrollX != touchStartScrollX) scrollTo(touchStartScrollX, scrollY)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) touchAxis = 0
        return handled
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (completionItems.isNotEmpty()) {
            when (keyCode) {
                KeyEvent.KEYCODE_TAB -> if (acceptGhostSuggestion()) return true
                KeyEvent.KEYCODE_DPAD_DOWN -> { selectedCompletion = (selectedCompletion + 1) % completionItems.size; invalidate(); return true }
                KeyEvent.KEYCODE_DPAD_UP -> { selectedCompletion = (selectedCompletion + completionItems.size - 1) % completionItems.size; invalidate(); return true }
                KeyEvent.KEYCODE_ESCAPE -> { clearCompletion(); return true }
            }
        }
        return super.onKeyDown(keyCode,event)
    }

    override fun onDraw(canvas: Canvas) {
        val editorLayout = layout
        canvas.save()
        canvas.translate(scrollX.toFloat(), scrollY.toFloat())
        if (editorLayout != null && editorLayout.lineCount > 0) {
            if (showCurrentLine && selectionStart >= 0) {
                val activeLine = editorLayout.getLineForOffset(selectionStart.coerceAtMost(text.length))
                val top = editorLayout.getLineTop(activeLine) + totalPaddingTop - scrollY
                val bottom = editorLayout.getLineBottom(activeLine) + totalPaddingTop - scrollY
                canvas.drawRect(gutterWidth.toFloat(), top.toFloat(), width.toFloat(), bottom.toFloat(), Paint().apply {
                    color = AndroidColor.rgb(24, 32, 42)
                })
            }
            if (showLineNumbers) {
                canvas.drawRect(0f, 0f, gutterWidth.toFloat(), height.toFloat(), Paint().apply {
                    color = AndroidColor.rgb(14, 19, 26)
                })
                canvas.drawRect((gutterWidth - 1).toFloat(), 0f, gutterWidth.toFloat(), height.toFloat(), Paint().apply {
                    color = AndroidColor.rgb(35, 35, 35)
                })
            }
        }
        canvas.restore()
        canvas.save()
        canvas.clipRect((scrollX + gutterWidth).toFloat(), scrollY.toFloat(),
            (scrollX + width).toFloat(), (scrollY + height).toFloat())
        super.onDraw(canvas)
        canvas.restore()
        canvas.save()
        canvas.translate(scrollX.toFloat(), scrollY.toFloat())
        try {
        if (editorLayout != null && editorLayout.lineCount > 0) {
            val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.argb(72, 120, 132, 150)
                strokeWidth = resources.displayMetrics.density
            }
            val spaceWidth = paint.measureText(" ")
            val first = editorLayout.getLineForVertical((scrollY - totalPaddingTop).coerceAtLeast(0))
            val last = editorLayout.getLineForVertical((scrollY + height - totalPaddingTop).coerceAtLeast(0))
            val source = renderSource
            for (lineIndex in first..last.coerceAtMost(editorLayout.lineCount - 1)) {
                val start = editorLayout.getLineStart(lineIndex)
                val end = editorLayout.getLineEnd(lineIndex).coerceAtMost(source.length)
                val lineText = source.substring(start, end).trimEnd('\n')
                val top = editorLayout.getLineTop(lineIndex) + totalPaddingTop - scrollY
                val bottom = editorLayout.getLineBottom(lineIndex) + totalPaddingTop - scrollY
                IndentationGuide.columns(lineText).forEach { column ->
                    val x = totalPaddingLeft - scrollX + column * spaceWidth
                    if (x >= gutterWidth) canvas.drawLine(x, top.toFloat(), x, bottom.toFloat(), guidePaint)
                }
            }
        }

        if (editorLayout != null && editorLayout.lineCount > 0 && diagnostics.isNotEmpty()) {
            val errorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.rgb(255, 69, 88)
                strokeWidth = 2.2f * resources.displayMetrics.density
                style = Paint.Style.STROKE
            }
            diagnostics.forEach { issue ->
                val start = issue.start.coerceIn(0, text.length)
                val end = issue.end.coerceIn(start, text.length)
                if (end > start) {
                    val firstLine = editorLayout.getLineForOffset(start)
                    val lastLine = editorLayout.getLineForOffset((end - 1).coerceAtLeast(start))
                    for (line in firstLine..lastLine) {
                        val segmentStart = maxOf(start, editorLayout.getLineStart(line))
                        val segmentEnd = minOf(end, editorLayout.getLineEnd(line))
                        val left = editorLayout.getPrimaryHorizontal(segmentStart) + totalPaddingLeft - scrollX
                        val right = editorLayout.getPrimaryHorizontal(segmentEnd) + totalPaddingLeft - scrollX
                        val y = editorLayout.getLineBottom(line) + totalPaddingTop - scrollY - 2f
                        var x = left
                        var up = true
                        while (x < right) {
                            val next = minOf(x + 4f * resources.displayMetrics.density, right)
                            canvas.drawLine(x, y + if(up) 0f else 2f, next, y + if(up) 2f else 0f, errorPaint)
                            up = !up
                            x = next
                        }
                    }
                }
            }
        }
        if (editorLayout != null && editorLayout.lineCount > 0 && showLineNumbers) {
            val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.rgb(110, 118, 129)
                textSize = this@PythonEditorView.textSize * 0.78f
                typeface = Typeface.MONOSPACE
                textAlign = Paint.Align.RIGHT
            }
            val first = editorLayout.getLineForVertical((scrollY - totalPaddingTop).coerceAtLeast(0))
            val last = editorLayout.getLineForVertical((scrollY + height - totalPaddingTop).coerceAtLeast(0))
            val right = gutterWidth - (10 * resources.displayMetrics.density)
            val source = renderSource
            val firstVisual = first.coerceAtMost(editorLayout.lineCount - 1)
            val firstOffset = editorLayout.getLineStart(firstVisual)
            var logicalLine = newlineCountBefore(firstOffset) + 1
            for (visualLine in firstVisual..last.coerceAtMost(editorLayout.lineCount - 1)) {
                val lineStart = editorLayout.getLineStart(visualLine)
                val startsLogicalLine = lineStart == 0 || source.getOrNull(lineStart - 1) == '\n'
                if (visualLine != firstVisual && startsLogicalLine) logicalLine++
                if (!startsLogicalLine) continue
                val baseline = editorLayout.getLineBaseline(visualLine) + totalPaddingTop - scrollY
                canvas.drawText(logicalLine.toString(), right, baseline.toFloat(), numberPaint)
            }
        }
        completionPopupBounds = null
        if (completionItems.isEmpty() || selectionStart < 0 || selectionStart != selectionEnd) return
        val currentLayout = editorLayout ?: return
        val cursor = selectionStart.coerceAtMost(text.length)
        val line = currentLayout.getLineForOffset(cursor)
        val x = currentLayout.getPrimaryHorizontal(cursor) + totalPaddingLeft - scrollX
        val caretTop = currentLayout.getLineTop(line) + totalPaddingTop - scrollY
        val caretBottom = currentLayout.getLineBottom(line) + totalPaddingTop - scrollY
        val item = completionItems[selectedCompletion.coerceIn(completionItems.indices)]
        val prefix = text.substring(item.replaceStart, cursor)
        if (item.replaceEnd == cursor && item.insertText.startsWith(prefix) &&
            (cursor == text.length || text[cursor] == '\n')) {
            val ghost = Paint(paint).apply { color = AndroidColor.argb(ghostAlpha, 174, 187, 204) }
            canvas.drawText(item.insertText.removePrefix(prefix).substringBefore('\n'), x,
                (currentLayout.getLineBaseline(line) + totalPaddingTop - scrollY).toFloat(), ghost)
        }
        val popup = CompletionPopup.draw(canvas, width, height, x, caretTop.toFloat(), caretBottom.toFloat(),
            resources.displayMetrics.density, completionItems, selectedCompletion)
        completionPopupBounds = popup?.first
        completionPopupRowHeight = popup?.second ?: 0f
        } finally { canvas.restore() }
    }

    private fun highlightNow() {
        if (!highlightingEnabled) return
        val editable = text ?: return
        val source = renderSource
        applyingHighlight = true
        beginBatchEdit()
        try {
        editable.getSpans(0, editable.length, ForegroundColorSpan::class.java).forEach(editable::removeSpan)
        if (source.length > 250_000) {
            setTextColor(editorTextColor)
            return
        }
        fun color(start: Int, end: Int, value: Int) {
            if (end > start) editable.setSpan(ForegroundColorSpan(value), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        var i = 0
        while (i < source.length) {
            val start = i
            when {
                source[i] == '#' -> {
                    while (i < source.length && source[i] != '\n') i++
                    color(start, i, commentColor)
                }
                source[i] == '\'' || source[i] == '"' -> {
                    val quote = source[i]
                    val triple = i + 2 < source.length && source[i + 1] == quote && source[i + 2] == quote
                    i += if (triple) 3 else 1
                    while (i < source.length) {
                        if (source[i] == '\\') { i = (i + 2).coerceAtMost(source.length); continue }
                        if (triple && i + 2 < source.length && source[i] == quote && source[i + 1] == quote && source[i + 2] == quote) { i += 3; break }
                        if (!triple && source[i] == quote) { i++; break }
                        i++
                    }
                    color(start, i, stringColor)
                }
                source[i].isDigit() -> {
                    while (i < source.length && (source[i].isDigit() || source[i] in ".xXabcdefABCDEF_")) i++
                    color(start, i, numberColor)
                }
                source[i].isLetter() || source[i] == '_' -> {
                    while (i < source.length && (source[i].isLetterOrDigit() || source[i] == '_')) i++
                    val word = source.substring(start, i)
                    var lookAhead = i
                    while (lookAhead < source.length && source[lookAhead].isWhitespace()) lookAhead++
                    val next = source.getOrNull(lookAhead)
                    val tokenColor = when {
                        word in keywords -> keywordColor
                        word in constants -> AndroidColor.rgb(86, 156, 214)
                        word in builtins || next == '(' -> functionColor
                        else -> variableColor
                    }
                    color(start, i, tokenColor)
                }
                else -> i++
            }
        }
        } finally {
            endBatchEdit()
            applyingHighlight = false
        }
    }
}
