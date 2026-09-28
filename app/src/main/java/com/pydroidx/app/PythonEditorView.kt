package com.pydroidx.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
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
import android.view.inputmethod.BaseInputConnection
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.EditText
import java.io.File
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal class PythonEditorView(context: Context) : EditText(context) {
    var onCodeChanged: ((String) -> Unit)? = null
    var onFindRequested: ((Boolean) -> Unit)? = null
    var onSaveRequested: (() -> Unit)? = null
    var onRunRequested: (() -> Unit)? = null
    var onDiagnosticTap: ((CodeDiagnostic) -> Unit)? = null
    var requestSmartCompletion: ((String, Int, (CompletionResult) -> Unit) -> Unit)? = null
    var requestCodeDiagnostics: ((String, (List<CodeDiagnostic>) -> Unit) -> Unit)? = null
    private var applyingHighlight = false
    private var applyingHistory = false
    private var beforeEdit = EditorSnapshot("", 0)
    private var shouldRecordHistory = false
    private var lastHistoryCaptureAt = 0L
    private var hasHistoryCapture = false
    private var loadedRevision = -1
    private val history = EditorHistory()
    private var highlightingEnabled = true
    private var highlightGeneration = 0L
    private var highlightTask: Future<*>? = null
    private val syntaxSpans = ArrayList<ForegroundColorSpan>()
    private var cachedSyntaxRanges: List<SyntaxRange>? = null
    private var syntaxWindowStart = 0
    private var syntaxWindowEnd = 0
    private val visibleSyntaxRunnable = Runnable {
        cachedSyntaxRanges?.let(::applyVisibleSyntax)
    }
    private var highlightDelayMs = 220L
    private var autocompleteEnabled = true
    private var ghostAlpha = 122
    private val completionSession = CompletionSession()
    private val completionItems get() = completionSession.items
    private var selectedCompletion = 0
    private var readyForSelectionChanges = false
    private var popupOwnsGesture = false
    private var popupTouchMoved = false
    private val completionPopupBounds = RectF()
    private var completionPopupRowHeight = 0f
    private var completionTouchIndex = -1
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchStartScrollX = 0
    private var touchAxis = 0
    private var diagnostics: List<CodeDiagnostic> = emptyList()
    private var renderSource: CharSequence = ""
    private val lineBreakIndex = LineBreakIndex()
    private var lastModelSnapshot: String? = null
    private var codeDirty = false
    private val codeSyncRunnable = Runnable { syncCodeChange() }
    private var applyingSmartEdit = false
    private var batchHistoryEdit = false
    private var batchHistoryCaptured = false
    private var indentationWidth = 4
    private var pendingTypedEdit: PendingTypedEdit? = null
    private var pendingPairDeleteAt: Int? = null
    private data class PendingTypedEdit(
        val start: Int,
        val characterAhead: Char?,
        val linePrefix: String,
        val typedCharacter: Char?
    )
    private fun updateRenderSource(source: CharSequence) {
        renderSource = source
        lineBreakIndex.rebuild(source)
    }
    private fun updateRenderSource(source: CharSequence, start: Int, before: Int, count: Int) {
        renderSource = source
        lineBreakIndex.update(source, start, before, count)
    }
    private fun syncCodeChange() {
        if (!codeDirty) return
        codeDirty = false
        val snapshot = text?.toString().orEmpty()
        lastModelSnapshot = snapshot
        onCodeChanged?.invoke(snapshot)
    }
    fun flushCodeChange() {
        removeCallbacks(codeSyncRunnable)
        syncCodeChange()
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
    private val editorDensity = context.resources.displayMetrics.density
    private val activeLinePaint = Paint().apply { color = AndroidColor.rgb(24, 32, 42) }
    private val gutterPaint = Paint().apply { color = AndroidColor.rgb(14, 19, 26) }
    private val gutterDividerPaint = Paint().apply { color = AndroidColor.rgb(35, 35, 35) }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(72, 120, 132, 150)
        strokeWidth = editorDensity
    }
    private val errorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(255, 69, 88)
        strokeWidth = 2.2f * editorDensity
        style = Paint.Style.STROKE
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(110, 118, 129)
        textSize = 12.5f
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.RIGHT
    }
    private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gutterWidth get() = if(showLineNumbers) (52 * resources.displayMetrics.density).toInt() else 0
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
        completionPopupBounds.setEmpty()
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
        flushCodeChange()
        clearCompletion()
        removeCallbacks(highlightRunnable)
        removeCallbacks(diagnosticsRunnable)
        removeCallbacks(codeSyncRunnable)
        removeCallbacks(visibleSyntaxRunnable)
        highlightGeneration++
        highlightTask?.cancel(true)
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
                if (!applyingHighlight && !applyingHistory && !applyingSmartEdit) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    val bulkEdit = count > 1 || after > 1
                    shouldRecordHistory = if (batchHistoryEdit) !batchHistoryCaptured else
                        bulkEdit || !hasHistoryCapture || now - lastHistoryCaptureAt >= HISTORY_CAPTURE_INTERVAL_MS
                    if (shouldRecordHistory) {
                        beforeEdit = EditorSnapshot(s?.toString().orEmpty(), selectionStart.coerceAtLeast(0))
                        lastHistoryCaptureAt = now
                        hasHistoryCapture = true
                        if (batchHistoryEdit) batchHistoryCaptured = true
                    }
                    pendingPairDeleteAt = if (count == 1 && after == 0 && selectionStart == start + 1 && s != null &&
                        matchingCloser(s.getOrNull(start), s.getOrNull(start + 1))) start else null
                    pendingTypedEdit = if (count == 0 && after == 1 && selectionStart == start && selectionEnd == start && s != null) {
                        val lineStart = (s.lastIndexOf('\n', (start - 1).coerceAtLeast(0)) + 1).coerceAtMost(start)
                        PendingTypedEdit(start, s.getOrNull(start), s.subSequence(lineStart, start).toString(), null)
                    } else null
                }
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (applyingSmartEdit) {
                    updateRenderSource(s ?: "", start, before, count)
                    invalidate()
                    return
                }
                if (!applyingHighlight && !applyingHistory) {
                    if (shouldRecordHistory) history.record(beforeEdit)
                    shouldRecordHistory = false
                    highlightGeneration++
                    highlightTask?.cancel(true)
                    cachedSyntaxRanges = null
                    val source = s ?: ""
                    if (before == 0 && count == 1) {
                        val typed = pendingTypedEdit
                        if (typed != null && typed.start == start) pendingTypedEdit = typed.copy(typedCharacter = source.getOrNull(start))
                    } else {
                        pendingTypedEdit = null
                    }
                    updateRenderSource(source, start, before, count)
                    codeDirty = true
                    removeCallbacks(codeSyncRunnable)
                    postDelayed(codeSyncRunnable, CODE_SYNC_DELAY_MS)
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
            override fun afterTextChanged(s: Editable?) {
                if (applyingSmartEdit || applyingHighlight || applyingHistory) return
                // An IME can replace provisional text repeatedly. Never insert a
                // closer or delete its neighbor while that text is composing.
                if (s != null && BaseInputConnection.getComposingSpanStart(s) >= 0) {
                    pendingPairDeleteAt = null
                    pendingTypedEdit = null
                    return
                }
                val deletePairAt = pendingPairDeleteAt
                pendingPairDeleteAt = null
                if (deletePairAt != null && s != null && deletePairAt in 0 until s.length) {
                    applyingSmartEdit = true
                    try {
                        s.delete(deletePairAt, deletePairAt + 1)
                        setSelection(deletePairAt.coerceAtMost(s.length))
                    } finally { applyingSmartEdit = false }
                    pendingTypedEdit = null
                    return
                }
                val typed = pendingTypedEdit
                pendingTypedEdit = null
                val character = typed?.typedCharacter ?: return
                val source = s ?: return
                if (character in CLOSING_CHARACTERS && typed.characterAhead == character) {
                    applyingSmartEdit = true
                    try {
                        source.delete(typed.start, typed.start + 1)
                        setSelection((typed.start + 1).coerceAtMost(source.length))
                    } finally { applyingSmartEdit = false }
                    return
                }
                val close = PAIRS[character]
                if (close != null && isCodeContext(typed.linePrefix)) {
                    if (character !in QUOTE_CHARACTERS || quoteCanOpen(typed.linePrefix)) {
                        if (typed.characterAhead != close) {
                            applyingSmartEdit = true
                            try { source.insert(typed.start + 1, close.toString()) }
                            finally { applyingSmartEdit = false }
                        }
                        setSelection(typed.start + 1)
                    }
                    return
                }
                if (character == '\n') {
                    val indent = typed.linePrefix.takeWhile { it == ' ' || it == '\t' }
                    val addLevel = isCodeContext(typed.linePrefix) && typed.linePrefix.trimEnd().endsWith(":")
                    val indentation = indent + if (addLevel) " ".repeat(indentationWidth) else ""
                    if (indentation.isNotEmpty()) {
                        applyingSmartEdit = true
                        try {
                            source.insert(typed.start + 1, indentation)
                            setSelection(typed.start + 1 + indentation.length)
                        } finally { applyingSmartEdit = false }
                    }
                }
            }
        })
        readyForSelectionChanges = true
        setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) scheduleCompletion() else clearCompletion()
            if (!hasFocus) flushCodeChange()
            if (!hasFocus && text.isNotBlank()) {
                removeCallbacks(diagnosticsRunnable)
                postDelayed(diagnosticsRunnable, 120)
            }
        }
    }

    fun setCodeIfDifferent(value: String, revision: Int) {
        val revisionChanged = loadedRevision != revision
        if (revisionChanged) {
            removeCallbacks(codeSyncRunnable)
            codeDirty = false
            history.clear()
            hasHistoryCapture = false
            beforeEdit = EditorSnapshot("", 0)
            diagnostics = emptyList()
            highlightGeneration++
            highlightTask?.cancel(true)
            cachedSyntaxRanges = null
            clearCompletion()
            loadedRevision = revision
        }
        // Compose can call this on every recomposition. The TextWatcher stores the
        // exact String instance for native edits, so avoid allocating a full copy
        // and scanning the document on the hot path.
        if (!revisionChanged && codeDirty) return
        if (!revisionChanged && (value === lastModelSnapshot || renderSource === value)) return
        if (renderSource.length == value.length && renderSource.contentEquals(value)) {
            lastModelSnapshot = value
            if (revisionChanged && highlightingEnabled) highlightNow()
            return
        }
        removeCallbacks(codeSyncRunnable)
        codeDirty = false
        highlightGeneration++
        highlightTask?.cancel(true)
        cachedSyntaxRanges = null
        clearSyntaxSpans()
        clearCompletion()
        applyingHighlight = true
        val cursor = selectionStart.coerceAtLeast(0).coerceAtMost(value.length)
        setText(value)
        updateRenderSource(text ?: value)
        lastModelSnapshot = value
        setSelection(cursor)
        applyingHighlight = false
        highlightNow()
    }

    private fun restoreHistory(snapshot: EditorSnapshot?) {
        snapshot ?: return
        clearCompletion()
        diagnostics = emptyList()
        applyingHistory = true
        removeCallbacks(codeSyncRunnable)
        codeDirty = false
        highlightGeneration++
        highlightTask?.cancel(true)
        clearSyntaxSpans()
        setText(snapshot.text)
        updateRenderSource(text ?: snapshot.text)
        setSelection(snapshot.cursor.coerceIn(0, snapshot.text.length))
        applyingHistory = false
        lastModelSnapshot = snapshot.text
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

    fun insertPair(open: String, close: String) {
        if (open.length != 1 || close.length != 1) return
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(start, text.length)
        val selected = text.subSequence(start, end).toString()
        beginBatchEdit()
        try {
            text.replace(start, end, open + selected + close)
            if (selected.isEmpty()) setSelection(start + 1)
            else setSelection(start + 1, start + 1 + selected.length)
        } finally {
            endBatchEdit()
        }
    }

    fun insertClosingOrSkip(close: String) {
        if (close.length != 1) return
        val cursor = selectionStart
        if (cursor == selectionEnd && cursor in 0 until text.length && text[cursor].toString() == close) {
            setSelection(cursor + 1)
        } else {
            insertAtCursor(close)
        }
    }

    fun indentSelection(outdent: Boolean = false): Boolean {
        val editable = text ?: return false
        val selectionA = selectionStart.coerceAtLeast(0).coerceAtMost(editable.length)
        val selectionB = selectionEnd.coerceAtLeast(0).coerceAtMost(editable.length)
        val start = minOf(selectionA, selectionB)
        val end = maxOf(selectionA, selectionB)
        if (start == end && !outdent) return false
        val firstLine = lineStart(editable, start)
        val endProbe = if (end > start && editable.getOrNull(end - 1) == '\n') end - 1 else end
        val lastLine = lineStart(editable, endProbe)
        val edits = mutableListOf<LineEdit>()
        var cursor = firstLine
        while (cursor <= lastLine) {
            if (!outdent) {
                edits += LineEdit(cursor, 0, " ".repeat(indentationWidth))
            } else {
                var whitespace = 0
                while (cursor + whitespace < editable.length &&
                    (editable[cursor + whitespace] == ' ' || editable[cursor + whitespace] == '\t')) whitespace++
                val remove = if (whitespace > 0 && editable[cursor] == '\t') 1 else minOf(indentationWidth, whitespace)
                if (remove > 0) edits += LineEdit(cursor, remove, "")
            }
            val newline = editable.indexOf("\n", cursor)
            if (newline < 0) break
            cursor = newline + 1
        }
        return applyLineEdits(editable, edits, selectionA, selectionB)
    }

    fun toggleCommentSelection(): Boolean {
        val editable = text ?: return false
        val selectionA = selectionStart.coerceAtLeast(0).coerceAtMost(editable.length)
        val selectionB = selectionEnd.coerceAtLeast(0).coerceAtMost(editable.length)
        val start = minOf(selectionA, selectionB)
        val end = maxOf(selectionA, selectionB)
        val firstLine = lineStart(editable, start)
        val endProbe = if (end > start && editable.getOrNull(end - 1) == '\n') end - 1 else end
        val lastLine = lineStart(editable, endProbe)
        val lines = mutableListOf<Pair<Int, Int>>()
        var cursor = firstLine
        while (cursor <= lastLine) {
            val newline = editable.indexOf("\n", cursor)
            val lineEnd = if (newline < 0) editable.length else newline
            lines += cursor to lineEnd
            if (newline < 0) break
            cursor = newline + 1
        }
        val nonBlank = lines.filter { (lineStart, lineEnd) ->
            (lineStart until lineEnd).any { !editable[it].isWhitespace() }
        }
        if (nonBlank.isEmpty()) return false
        val removeComments = nonBlank.all { (lineStart, lineEnd) ->
            val marker = firstNonWhitespace(editable, lineStart, lineEnd)
            marker < lineEnd && editable[marker] == '#'
        }
        val edits = nonBlank.mapNotNull { (lineStart, lineEnd) ->
            val marker = firstNonWhitespace(editable, lineStart, lineEnd)
            if (removeComments) {
                if (marker >= lineEnd || editable[marker] != '#') null
                else LineEdit(marker, if (marker + 1 < lineEnd && editable[marker + 1] == ' ') 2 else 1, "")
            } else LineEdit(marker, 0, "# ")
        }
        return applyLineEdits(editable, edits, selectionA, selectionB)
    }

    private data class LineEdit(val start: Int, val removeLength: Int, val insert: String)

    private fun applyLineEdits(editable: Editable, edits: List<LineEdit>, selectionA: Int, selectionB: Int): Boolean {
        if (edits.isEmpty()) return false
        fun adjusted(position: Int): Int {
            var result = position
            for (edit in edits) {
                if (edit.start > position) continue
                result += edit.insert.length
                result -= minOf(edit.removeLength, (position - edit.start).coerceAtLeast(0))
            }
            return result.coerceIn(0, editable.length + edits.sumOf { it.insert.length - it.removeLength })
        }
        val newA = adjusted(selectionA)
        val newB = adjusted(selectionB)
        batchHistoryEdit = true
        batchHistoryCaptured = false
        beginBatchEdit()
        try {
            edits.asReversed().forEach { edit ->
                editable.replace(edit.start, edit.start + edit.removeLength, edit.insert)
            }
            setSelection(newA, newB)
        } finally {
            endBatchEdit()
            batchHistoryEdit = false
            batchHistoryCaptured = false
        }
        return true
    }

    private fun lineStart(source: CharSequence, offset: Int): Int =
        (source.lastIndexOf('\n', (offset - 1).coerceAtLeast(0)) + 1).coerceAtMost(offset)

    private fun firstNonWhitespace(source: CharSequence, start: Int, end: Int): Int {
        var index = start
        while (index < end && source[index].isWhitespace()) index++
        return index
    }

    private fun isCodeContext(prefix: String): Boolean {
        var quote: Char? = null
        var escaped = false
        for (character in prefix) {
            if (quote != null) {
                if (escaped) escaped = false
                else if (character == '\\') escaped = true
                else if (character == quote) quote = null
            } else when (character) {
                '#' -> return false
                '\'', '"' -> quote = character
            }
        }
        return quote == null
    }

    private fun quoteCanOpen(prefix: String): Boolean =
        prefix.isEmpty() || prefix.last().isWhitespace() || prefix.last() in "=([{,:"

    private fun matchingCloser(open: Char?, close: Char?): Boolean = when (open) {
        '(' -> close == ')'
        '[' -> close == ']'
        '{' -> close == '}'
        else -> open != null && open in QUOTE_CHARACTERS && close == open
    }

    fun applyPreferences(font: Float, wrap: Boolean, syntax: Boolean, family: String, spacing: Float,
                         padding: Float, highlightDelay: Float, cursor: String, autocomplete: Boolean,
                         ghostBrightness: Float, lineNumbers: Boolean, currentLine: Boolean,
                         customFontPath: String, palette: List<String>, tabWidth: Int = 4) {
        val signature = listOf(
            font,wrap,syntax,family,spacing,padding,highlightDelay,cursor,autocomplete,
            ghostBrightness,lineNumbers,currentLine,customFontPath,palette.joinToString(","),tabWidth
        ).joinToString("|")
        if (signature == lastPreferenceSignature) return
        lastPreferenceSignature = signature
        textSize = font
        numberPaint.textSize = font * 0.78f
        setHorizontallyScrolling(!wrap)
        isHorizontalScrollBarEnabled = !wrap
        highlightingEnabled = syntax
        highlightDelayMs = highlightDelay.toLong()
        autocompleteEnabled = autocomplete
        indentationWidth = tabWidth.coerceIn(1, 8)
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
            highlightGeneration++
            highlightTask?.cancel(true)
            clearSyntaxSpans()
            setTextColor(editorTextColor)
        }
        scheduleCompletion()
    }

    fun acceptGhostSuggestion(): Boolean = acceptCompletion(selectedCompletion)

    private fun completionIndexAt(x: Float, y: Float): Int {
        val bounds = completionPopupBounds
        if (bounds.isEmpty) return -1
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
        if (event.actionMasked == MotionEvent.ACTION_UP && touchAxis == 0 &&
            kotlin.math.abs(event.x - touchStartX) <= touchSlop &&
            kotlin.math.abs(event.y - touchStartY) <= touchSlop) {
            diagnosticAt(event.x, event.y)?.let { onDiagnosticTap?.invoke(it) }
        }
        if (touchAxis == 1 && scrollX != touchStartScrollX) scrollTo(touchStartScrollX, scrollY)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) touchAxis = 0
        return handled
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    private fun diagnosticAt(x: Float, y: Float): CodeDiagnostic? {
        val currentLayout = layout ?: return null
        if (diagnostics.isEmpty()) return null
        val contentY = (y + scrollY - totalPaddingTop).toInt().coerceIn(0, currentLayout.height.coerceAtLeast(0))
        val line = currentLayout.getLineForVertical(contentY)
        val offset = currentLayout.getOffsetForHorizontal(line, x + scrollX - totalPaddingLeft)
        return diagnostics.firstOrNull { offset >= it.start && offset <= it.end }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event?.isCtrlPressed == true && keyCode == KeyEvent.KEYCODE_Z) {
            if (event.isShiftPressed) redoCode() else undoCode()
            return true
        }
        if (event?.isCtrlPressed == true && keyCode == KeyEvent.KEYCODE_S) {
            flushCodeChange()
            onSaveRequested?.invoke()
            return true
        }
        if (event?.isCtrlPressed == true && keyCode == KeyEvent.KEYCODE_ENTER) {
            flushCodeChange()
            onRunRequested?.invoke()
            return true
        }
        if (event?.isCtrlPressed == true && keyCode == KeyEvent.KEYCODE_F) {
            onFindRequested?.invoke(false)
            return true
        }
        if (event?.isCtrlPressed == true && keyCode == KeyEvent.KEYCODE_H) {
            onFindRequested?.invoke(true)
            return true
        }
        if (event?.isCtrlPressed == true && keyCode == KeyEvent.KEYCODE_SLASH) {
            if (toggleCommentSelection()) return true
        }
        if (keyCode == KeyEvent.KEYCODE_TAB && completionItems.isNotEmpty() && acceptGhostSuggestion()) return true
        if (keyCode == KeyEvent.KEYCODE_TAB) {
            if (event?.isShiftPressed == true) {
                if (indentSelection(outdent = true)) return true
            } else if (indentSelection() || run { insertAtCursor(" ".repeat(indentationWidth)); true }) {
                return true
            }
        }
        if (keyCode == KeyEvent.KEYCODE_DEL && selectionStart == selectionEnd &&
            selectionStart > 0 && selectionStart < text.length &&
            matchingCloser(text[selectionStart - 1], text[selectionStart])) {
            val cursor = selectionStart
            beginBatchEdit()
            try {
                text.delete(cursor - 1, cursor + 1)
                setSelection(cursor - 1)
            } finally { endBatchEdit() }
            return true
        }
        if (completionItems.isNotEmpty()) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> { selectedCompletion = (selectedCompletion + 1) % completionItems.size; invalidate(); return true }
                KeyEvent.KEYCODE_DPAD_UP -> { selectedCompletion = (selectedCompletion + completionItems.size - 1) % completionItems.size; invalidate(); return true }
                KeyEvent.KEYCODE_ESCAPE -> { clearCompletion(); return true }
            }
        }
        return super.onKeyDown(keyCode,event)
    }

    fun findNext(query: String): Boolean {
        if (query.isEmpty()) return false
        val source = text.toString()
        val start = selectionEnd.coerceIn(0, source.length)
        val next = source.indexOf(query, start, ignoreCase = true)
            .takeIf { it >= 0 } ?: source.indexOf(query, 0, ignoreCase = true)
        if (next < 0) return false
        setSelection(next, next + query.length)
        requestFocus()
        return true
    }

    fun replaceSelection(query: String, replacement: String): Boolean {
        if (query.isEmpty()) return false
        val start = selectionStart
        val end = selectionEnd
        if (start < 0 || end <= start || !text.subSequence(start, end).toString().equals(query, true)) {
            return findNext(query)
        }
        text.replace(start, end, replacement)
        setSelection((start + replacement.length).coerceAtMost(text.length))
        return true
    }

    fun replaceAllMatches(query: String, replacement: String): Int {
        if (query.isEmpty()) return 0
        val source = text.toString()
        var count = 0
        var from = 0
        val result = StringBuilder(source.length)
        while (from < source.length) {
            val hit = source.indexOf(query, from, ignoreCase = true)
            if (hit < 0) break
            result.append(source, from, hit).append(replacement)
            from = hit + query.length
            count++
        }
        if (count == 0) return 0
        result.append(source, from, source.length)
        val cursor = selectionStart.coerceAtLeast(0)
        text.replace(0, text.length, result)
        setSelection(cursor.coerceAtMost(text.length))
        return count
    }

    fun goToLine(number: Int) {
        val target = number.coerceAtLeast(1)
        var line = 1
        var offset = 0
        while (offset < text.length && line < target) {
            if (text[offset] == '\n') line++
            offset++
        }
        setSelection(offset)
        requestFocus()
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
                canvas.drawRect(gutterWidth.toFloat(), top.toFloat(), width.toFloat(), bottom.toFloat(), activeLinePaint)
            }
            if (showLineNumbers) {
                canvas.drawRect(0f, 0f, gutterWidth.toFloat(), height.toFloat(), gutterPaint)
                canvas.drawRect((gutterWidth - 1).toFloat(), 0f, gutterWidth.toFloat(), height.toFloat(), gutterDividerPaint)
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
            val spaceWidth = paint.measureText(" ")
            val first = editorLayout.getLineForVertical((scrollY - totalPaddingTop).coerceAtLeast(0))
            val last = editorLayout.getLineForVertical((scrollY + height - totalPaddingTop).coerceAtLeast(0))
            val source = renderSource
            for (lineIndex in first..last.coerceAtMost(editorLayout.lineCount - 1)) {
                val start = editorLayout.getLineStart(lineIndex)
                val end = editorLayout.getLineEnd(lineIndex).coerceAtMost(source.length)
                val top = editorLayout.getLineTop(lineIndex) + totalPaddingTop - scrollY
                val bottom = editorLayout.getLineBottom(lineIndex) + totalPaddingTop - scrollY
                var column = 0
                var nextGuide = indentationWidth
                for (offset in start until end) {
                    when (source[offset]) {
                        ' ' -> column++
                        '\t' -> column += indentationWidth - (column % indentationWidth)
                        else -> break
                    }
                    while (nextGuide <= column) {
                        val x = totalPaddingLeft - scrollX + nextGuide * spaceWidth
                        if (x >= gutterWidth) canvas.drawLine(x, top.toFloat(), x, bottom.toFloat(), guidePaint)
                        nextGuide += indentationWidth
                    }
                }
            }
        }

        if (editorLayout != null && editorLayout.lineCount > 0 && diagnostics.isNotEmpty()) {
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
            val first = editorLayout.getLineForVertical((scrollY - totalPaddingTop).coerceAtLeast(0))
            val last = editorLayout.getLineForVertical((scrollY + height - totalPaddingTop).coerceAtLeast(0))
            val right = gutterWidth - (10 * resources.displayMetrics.density)
            val source = renderSource
            val firstVisual = first.coerceAtMost(editorLayout.lineCount - 1)
            val firstOffset = editorLayout.getLineStart(firstVisual)
            var logicalLine = lineBreakIndex.countBefore(firstOffset) + 1
            for (visualLine in firstVisual..last.coerceAtMost(editorLayout.lineCount - 1)) {
                val lineStart = editorLayout.getLineStart(visualLine)
                val startsLogicalLine = lineStart == 0 || source.getOrNull(lineStart - 1) == '\n'
                if (visualLine != firstVisual && startsLogicalLine) logicalLine++
                if (!startsLogicalLine) continue
                val baseline = editorLayout.getLineBaseline(visualLine) + totalPaddingTop - scrollY
                canvas.drawText(logicalLine.toString(), right, baseline.toFloat(), numberPaint)
            }
        }
        completionPopupBounds.setEmpty()
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
            ghostPaint.set(paint)
            ghostPaint.color = AndroidColor.argb(ghostAlpha, 174, 187, 204)
            canvas.drawText(item.insertText.removePrefix(prefix).substringBefore('\n'), x,
                (currentLayout.getLineBaseline(line) + totalPaddingTop - scrollY).toFloat(), ghostPaint)
        }
        completionPopupRowHeight = CompletionPopup.draw(canvas, width, height, x,
            caretTop.toFloat(), caretBottom.toFloat(), resources.displayMetrics.density,
            completionItems, selectedCompletion, completionPopupBounds) ?: 0f
        } finally { canvas.restore() }
    }

    private fun clearSyntaxSpans() {
        cachedSyntaxRanges = null
        removeCallbacks(visibleSyntaxRunnable)
        val editable = text ?: return
        if (syntaxSpans.isEmpty()) return
        applyingHighlight = true
        beginBatchEdit()
        try {
            syntaxSpans.forEach(editable::removeSpan)
            syntaxSpans.clear()
        } finally {
            endBatchEdit()
            applyingHighlight = false
        }
    }

    private fun syntaxWindow(): Pair<Int, Int> {
        val currentLayout = layout ?: return 0 to minOf(text.length, 12_000)
        val screen = height.coerceAtLeast(1)
        val top = (scrollY - totalPaddingTop - screen).coerceAtLeast(0)
        val bottom = (scrollY - totalPaddingTop + screen * 2).coerceAtMost(currentLayout.height)
        val first = currentLayout.getLineForVertical(top)
        val last = currentLayout.getLineForVertical(bottom.coerceAtLeast(0))
        return currentLayout.getLineStart(first) to currentLayout.getLineEnd(last)
    }

    private fun applyVisibleSyntax(ranges: List<SyntaxRange>) {
        val editable = text ?: return
        val (first, last) = syntaxWindow()
        syntaxWindowStart = first
        syntaxWindowEnd = last
        applyingHighlight = true
        beginBatchEdit()
        try {
            syntaxSpans.forEach(editable::removeSpan)
            syntaxSpans.clear()
            // Lexer output is ordered; skip tokens before the prefetched viewport.
            var low = 0
            var high = ranges.size
            while (low < high) {
                val mid = (low + high) ushr 1
                if (ranges[mid].end <= first) low = mid + 1 else high = mid
            }
            while (low < ranges.size && ranges[low].start < last) {
                val range = ranges[low++]
                if (range.start >= 0 && range.end <= editable.length && range.end > range.start) {
                    val span = ForegroundColorSpan(range.color)
                    editable.setSpan(span, range.start, range.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    syntaxSpans += span
                }
            }
        } finally {
            endBatchEdit()
            applyingHighlight = false
        }
    }

    override fun onScrollChanged(horizontal: Int, vertical: Int, oldHorizontal: Int, oldVertical: Int) {
        super.onScrollChanged(horizontal, vertical, oldHorizontal, oldVertical)
        if (cachedSyntaxRanges != null) {
            val (first, last) = syntaxWindow()
            if (first < syntaxWindowStart || last > syntaxWindowEnd) {
                removeCallbacks(visibleSyntaxRunnable)
                postDelayed(visibleSyntaxRunnable, 32)
            }
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (cachedSyntaxRanges != null) {
            removeCallbacks(visibleSyntaxRunnable)
            post(visibleSyntaxRunnable)
        }
    }

    private fun highlightNow() {
        val editable = text ?: return
        if (!highlightingEnabled) return
        val source = renderSource.toString()
        val generation = ++highlightGeneration
        highlightTask?.cancel(true)
        cachedSyntaxRanges = null
        if (source.length > 250_000) {
            clearSyntaxSpans()
            setTextColor(editorTextColor)
            return
        }
        val palette = SyntaxPalette(editorTextColor, commentColor, stringColor, numberColor,
            keywordColor, functionColor, variableColor)
        highlightTask = SYNTAX_EXECUTOR.submit {
            val ranges = PythonSyntaxHighlighter.ranges(source, palette)
            post {
                if (generation != highlightGeneration || !highlightingEnabled || text !== editable) return@post
                cachedSyntaxRanges = ranges
                applyVisibleSyntax(ranges)
            }
        }
    }

    private companion object {
        const val HISTORY_CAPTURE_INTERVAL_MS = 400L
        const val CODE_SYNC_DELAY_MS = 120L
        val SYNTAX_EXECUTOR = ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue(),
            { task -> Thread(task, "PY4U-Syntax").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 } }
        )
        val PAIRS = mapOf('(' to ')', '[' to ']', '{' to '}', '"' to '"', '\'' to '\'')
        val CLOSING_CHARACTERS = setOf(')', ']', '}', '"', '\'')
        val QUOTE_CHARACTERS = setOf('"', '\'')
    }
}
