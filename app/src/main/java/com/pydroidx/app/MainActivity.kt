package com.pydroidx.app

import android.os.Bundle
import android.content.Context
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
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaquo.python.Python
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

class IdeViewModel : ViewModel() {
    @Volatile var code = "print(\"Hello Andrew\")\nname = input(\"What is your name? \")\nprint(\"Hello\", name)\n"
    var editorRevision by mutableIntStateOf(0)
        private set
    var output by mutableStateOf("")
    var running by mutableStateOf(false)
    var waitingInput by mutableStateOf(false)
    var input by mutableStateOf("")
    var runtimeVersion by mutableStateOf("Loading Python…")
    var aiPrompt by mutableStateOf("")
    var aiReply by mutableStateOf("Ask about Python, your error, or your selected code")
    var aiBusy by mutableStateOf(false)
    var aiEndpoint by mutableStateOf("https://api.openai.com/v1/chat/completions")
    var aiModel by mutableStateOf("gpt-4o-mini")
    var aiProvider by mutableStateOf("Auto")
    var shareCode by mutableStateOf(false)
    var hasAiKey by mutableStateOf(false)
    var editorFontSize by mutableFloatStateOf(16f)
    var terminalFontSize by mutableFloatStateOf(13f)
    var uiScale by mutableFloatStateOf(1f)
    var wordWrap by mutableStateOf(true)
    var syntaxHighlighting by mutableStateOf(true)
    var autoSave by mutableStateOf(true)
    var fontName by mutableStateOf("Monospace")
    var lineSpacing by mutableFloatStateOf(1.12f)
    var editorPadding by mutableFloatStateOf(20f)
    var typingAnimation by mutableStateOf(false)
    var animationDuration by mutableFloatStateOf(120f)
    var highlightDelay by mutableFloatStateOf(220f)
    var autosaveDelay by mutableFloatStateOf(500f)
    var showToolbar by mutableStateOf(true)
    var showPageDots by mutableStateOf(true)
    var cursorStyle by mutableStateOf("Cyan")
    var autocomplete by mutableStateOf(true)
    var ghostBrightness by mutableFloatStateOf(0.48f)
    var lineNumbers by mutableStateOf(true)
    var highlightCurrentLine by mutableStateOf(true)
    private val stdin = LinkedBlockingQueue<String?>()
    @Volatile private var worker: Thread? = null
    private var autosaveJob: Job? = null
    lateinit var projectDir: File
    private lateinit var aiKeys: SecureAiKeyStore
    private lateinit var settings: android.content.SharedPreferences

    fun initialize(context: Context) {
        aiKeys = SecureAiKeyStore(context.applicationContext)
        settings = context.getSharedPreferences("ide_settings", Context.MODE_PRIVATE)
        editorFontSize = settings.getFloat("editor_font", 16f)
        terminalFontSize = settings.getFloat("terminal_font", 13f)
        uiScale = settings.getFloat("ui_scale", 1f)
        wordWrap = settings.getBoolean("word_wrap", true)
        syntaxHighlighting = settings.getBoolean("syntax", true)
        autoSave = settings.getBoolean("autosave", true)
        fontName = settings.getString("font", "Monospace") ?: "Monospace"
        lineSpacing = settings.getFloat("line_spacing", 1.12f)
        editorPadding = settings.getFloat("editor_padding", 20f)
        typingAnimation = settings.getBoolean("typing_animation", false)
        animationDuration = settings.getFloat("animation_duration", 120f)
        highlightDelay = settings.getFloat("highlight_delay", 220f)
        autosaveDelay = settings.getFloat("autosave_delay", 500f)
        showToolbar = settings.getBoolean("toolbar", true)
        showPageDots = settings.getBoolean("page_dots", true)
        cursorStyle = settings.getString("cursor", "Cyan") ?: "Cyan"
        autocomplete = settings.getBoolean("autocomplete", true)
        ghostBrightness = settings.getFloat("ghost_brightness", 0.48f)
        aiEndpoint = settings.getString("ai_endpoint", null)
            ?.trim()?.takeIf { it.startsWith("https://") }
            ?: "https://api.openai.com/v1/chat/completions"
        aiModel = settings.getString("ai_model", "gpt-4o-mini")
            ?.trim()?.takeIf { it.isNotEmpty() } ?: "gpt-4o-mini"
        aiProvider = settings.getString("ai_provider", "Auto") ?: "Auto"
        lineNumbers = settings.getBoolean("line_numbers", true)
        highlightCurrentLine = settings.getBoolean("current_line", true)
        hasAiKey = !aiKeys.load().isNullOrBlank()
        projectDir = File(context.filesDir, "projects/default").apply { mkdirs() }
        val main = File(projectDir, "main.py")
        if (main.exists()) code = main.readText() else main.writeText(code)
        editorRevision++
        thread { runtimeVersion = Python.getInstance().getModule("runner").callAttr("version").toString() }
    }
    fun saveAiSettings(key: String, endpoint: String, model: String, provider: String = aiProvider) {
        if (key.isNotBlank()) aiKeys.save(key)
        aiProvider = provider
        aiEndpoint = endpoint.trim().takeIf { it.startsWith("https://") }
            ?: "https://api.openai.com/v1/chat/completions"
        aiModel = model.trim().ifEmpty { "gpt-4o-mini" }
        settings.edit().putString("ai_endpoint", aiEndpoint).putString("ai_model", aiModel)
            .putString("ai_provider", aiProvider).apply()
        hasAiKey = !aiKeys.load().isNullOrBlank()
    }
    fun removeAiKey() { aiKeys.clear(); hasAiKey = false }
    fun askAi(testOnly: Boolean = false) {
        if (aiBusy) return
        val key = aiKeys.load()
        if (key.isNullOrBlank()) { aiReply = "Open AI settings and add your API key"; return }
        val question = if (testOnly) "Reply with: Connection successful" else aiPrompt.trim()
        if (question.isBlank()) return
        if (!testOnly) aiPrompt = ""
        aiBusy = true
        aiReply = if (testOnly) "Testing connection…" else "Thinking…"
        thread(name = "PyDroidX-AI") {
            val result = runCatching { AiClient.chat(aiProvider, aiEndpoint, key, aiModel, question, if (!testOnly && shareCode) code else null) }
            aiReply = result.getOrElse { "AI error: ${it.message ?: "Request failed"}" }
            aiBusy = false
        }
    }
    fun requestCompletion(source: String, cursor: Int, deliver: (String, Int) -> Unit) {
        if (!autocomplete || !::projectDir.isInitialized) return
        thread(name="PyDroidX-Jedi") {
            runCatching {
                val raw = Python.getInstance().getModule("runner")
                    .callAttr("complete", source, cursor, projectDir.absolutePath).toString()
                val json = JSONObject(raw)
                val suffix = json.optString("suffix")
                if (suffix.isNotEmpty()) deliver(suffix, json.optInt("cursor_back", 0))
            }
        }
    }
    fun updateCode(value: String) {
        code = value
        if (!autoSave) return
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(autosaveDelay.toLong())
            val snapshot = code
            launch(Dispatchers.IO) {
                if (::projectDir.isInitialized) File(projectDir, "main.py").writeText(snapshot)
            }
        }
    }
    fun saveAppearance() {
        if (!::settings.isInitialized) return
        settings.edit().putFloat("editor_font",editorFontSize).putFloat("terminal_font",terminalFontSize)
            .putFloat("ui_scale",uiScale).putBoolean("word_wrap",wordWrap)
            .putBoolean("syntax",syntaxHighlighting).putBoolean("autosave",autoSave).apply()
        settings.edit().putString("font",fontName).putFloat("line_spacing",lineSpacing)
            .putFloat("editor_padding",editorPadding).putBoolean("typing_animation",typingAnimation)
            .putFloat("animation_duration",animationDuration).putFloat("highlight_delay",highlightDelay)
            .putFloat("autosave_delay",autosaveDelay).putBoolean("toolbar",showToolbar)
            .putBoolean("page_dots",showPageDots).putString("cursor",cursorStyle).apply()
        settings.edit().putBoolean("autocomplete",autocomplete).putFloat("ghost_brightness",ghostBrightness).apply()
        settings.edit().putBoolean("line_numbers",lineNumbers).putBoolean("current_line",highlightCurrentLine).apply()
    }
    fun save() {
        autosaveJob?.cancel()
        val snapshot = code
        viewModelScope.launch(Dispatchers.IO) {
            if (::projectDir.isInitialized) File(projectDir, "main.py").writeText(snapshot)
        }
    }
    fun run() {
        if (running) return
        save(); output = ""; running = true
        worker = thread(name = "PyDroidX-Python") {
            Python.getInstance().getModule("runner").callAttr("run_code", code, "main.py", projectDir.absolutePath, Bridge())
        }
    }
    fun submitInput() { if (waitingInput) { stdin.offer(input); input = ""; waitingInput = false } }
    @Suppress("DEPRECATION") fun stop() { stdin.offer(null); worker?.interrupt(); running = false; waitingInput = false }
    inner class Bridge {
        fun write(text: String, error: Boolean) { output += text }
        fun readLine(): String? { waitingInput = true; return try { stdin.take() } catch (_: InterruptedException) { null } }
        fun exited(code: Int) { output += "\n[Process exited with code $code]\n"; running = false; waitingInput = false }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { val vm: IdeViewModel = viewModel(); LaunchedEffect(Unit) { vm.initialize(applicationContext) }; PyDroidX(vm) }
    }
}

private class PythonEditorView(context: Context) : EditText(context) {
    var onCodeChanged: ((String) -> Unit)? = null
    var requestSmartCompletion: ((String, Int, (String, Int) -> Unit) -> Unit)? = null
    private var applyingHighlight = false
    private var highlightingEnabled = true
    private var highlightDelayMs = 220L
    private var autocompleteEnabled = true
    private var ghostAlpha = 122
    private var ghostSuffix: String? = null
    private var ghostCursorBack = 0
    private var ghostPrefixStart = 0
    private var showLineNumbers = true
    private var showCurrentLine = true
    private var userPadding = 20
    private val gutterWidth get() = if(showLineNumbers) (52 * resources.displayMetrics.density).toInt() else 0
    private val completions = linkedMapOf(
        "print" to "print()", "input" to "input()", "range" to "range()", "len" to "len()",
        "str" to "str()", "int" to "int()", "float" to "float()", "list" to "list()",
        "dict" to "dict()", "set" to "set()", "tuple" to "tuple()", "bool" to "bool()",
        "open" to "open()", "sum" to "sum()", "min" to "min()", "max" to "max()",
        "abs" to "abs()", "all" to "all()", "any" to "any()", "enumerate" to "enumerate()",
        "zip" to "zip()", "map" to "map()", "filter" to "filter()", "sorted" to "sorted()",
        "reversed" to "reversed()", "type" to "type()", "isinstance" to "isinstance()",
        "super" to "super()", "property" to "property()", "format" to "format()",
        "import" to "import ", "from" to "from ", "return" to "return ",
        "def" to "def function():\n    pass", "class" to "class Name:\n    pass",
        "if" to "if condition:\n    pass", "elif" to "elif condition:\n    pass",
        "else" to "else:\n    pass", "for" to "for item in items:\n    pass",
        "while" to "while condition:\n    pass", "try" to "try:\n    pass\nexcept Exception:\n    pass"
    )
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
    private val completionRunnable = Runnable {
        if (!autocompleteEnabled) return@Runnable
        val snapshot = text.toString()
        val cursor = selectionStart
        if (cursor < 0) return@Runnable
        requestSmartCompletion?.invoke(snapshot, cursor) { suffix, cursorBack ->
            post {
                if (text.toString() == snapshot && selectionStart == cursor && suffix.isNotEmpty()) {
                    ghostSuffix = suffix
                    ghostCursorBack = cursorBack
                    invalidate()
                }
            }
        }
    }

    init {
        setBackgroundColor(AndroidColor.BLACK)
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
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!applyingHighlight) {
                    onCodeChanged?.invoke(s?.toString().orEmpty())
                    removeCallbacks(highlightRunnable)
                    postDelayed(highlightRunnable, highlightDelayMs)
                    post { updateGhostSuggestion() }
                    removeCallbacks(completionRunnable)
                    postDelayed(completionRunnable, 140)
                }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    fun setCodeIfDifferent(value: String) {
        if (text.toString() == value) return
        applyingHighlight = true
        val cursor = selectionStart.coerceAtLeast(0).coerceAtMost(value.length)
        setText(value)
        setSelection(cursor)
        applyingHighlight = false
        highlightNow()
    }

    fun insertAtCursor(value: String) {
        val start = selectionStart.coerceAtLeast(0)
        val end = selectionEnd.coerceAtLeast(0)
        text.replace(minOf(start, end), maxOf(start, end), value)
    }

    fun applyPreferences(font: Float, wrap: Boolean, syntax: Boolean, family: String, spacing: Float,
                         padding: Float, animateTyping: Boolean, animationMs: Float,
                         highlightDelay: Float, cursor: String, autocomplete: Boolean,
                         ghostBrightness: Float, lineNumbers: Boolean, currentLine: Boolean) {
        textSize = font
        setHorizontallyScrolling(!wrap)
        isHorizontalScrollBarEnabled = !wrap
        highlightingEnabled = syntax
        highlightDelayMs = highlightDelay.toLong()
        autocompleteEnabled = autocomplete
        ghostAlpha = (ghostBrightness * 255).toInt().coerceIn(35,210)
        showLineNumbers = lineNumbers
        showCurrentLine = currentLine
        typeface = when(family) { "Sans" -> Typeface.SANS_SERIF; "Serif" -> Typeface.SERIF; else -> Typeface.MONOSPACE }
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
            setTextColor(AndroidColor.rgb(212,212,212))
        }
        updateGhostSuggestion()
    }

    private fun updateGhostSuggestion() {
        if (!autocompleteEnabled || !hasFocus()) { ghostSuffix=null; invalidate(); return }
        val cursor = selectionStart
        if (cursor < 0 || cursor > text.length) return
        var start = cursor
        while (start > 0 && (text[start-1].isLetterOrDigit() || text[start-1]=='_')) start--
        val prefix = text.substring(start,cursor)
        val projectNames = Regex("\\b(?:def|class)\\s+([A-Za-z_]\\w*)|\\b([A-Za-z_]\\w*)\\s*=")
            .findAll(text).flatMap { it.groupValues.drop(1).asSequence() }.filter { it.isNotEmpty() }
        val localMatch = if (prefix.isNotEmpty()) completions.entries.firstOrNull {
            it.key.startsWith(prefix, ignoreCase = false) && it.key != prefix
        } else null
        val projectMatch = if (prefix.isNotEmpty()) projectNames.firstOrNull { it.startsWith(prefix) && it != prefix } else null
        ghostPrefixStart = start
        ghostSuffix = localMatch?.value?.removePrefix(prefix) ?: projectMatch?.removePrefix(prefix)
        ghostCursorBack = if (ghostSuffix?.endsWith("()") == true) 1 else 0
        invalidate()
    }

    fun acceptGhostSuggestion(): Boolean {
        val suffix = ghostSuffix ?: return false
        val cursor = selectionStart.coerceAtLeast(0)
        text.insert(cursor,suffix)
        val newCursor = cursor + suffix.length - ghostCursorBack
        setSelection(newCursor.coerceAtMost(text.length))
        ghostSuffix=null
        invalidate()
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if(keyCode==KeyEvent.KEYCODE_TAB && acceptGhostSuggestion()) return true
        return super.onKeyDown(keyCode,event)
    }

    override fun onDraw(canvas: Canvas) {
        val editorLayout = layout
        if (editorLayout != null) {
            if (showCurrentLine && selectionStart >= 0) {
                val activeLine = editorLayout.getLineForOffset(selectionStart.coerceAtMost(text.length))
                val top = editorLayout.getLineTop(activeLine) + totalPaddingTop - scrollY
                val bottom = editorLayout.getLineBottom(activeLine) + totalPaddingTop - scrollY
                canvas.drawRect(gutterWidth.toFloat(), top.toFloat(), width.toFloat(), bottom.toFloat(), Paint().apply {
                    color = AndroidColor.rgb(8, 13, 20)
                })
            }
            if (showLineNumbers) {
                canvas.drawRect(0f, 0f, gutterWidth.toFloat(), height.toFloat(), Paint().apply {
                    color = AndroidColor.rgb(3, 3, 3)
                })
                canvas.drawRect((gutterWidth - 1).toFloat(), 0f, gutterWidth.toFloat(), height.toFloat(), Paint().apply {
                    color = AndroidColor.rgb(35, 35, 35)
                })
            }
        }
        super.onDraw(canvas)
        if (editorLayout != null && showLineNumbers) {
            val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.rgb(110, 118, 129)
                textSize = this@PythonEditorView.textSize * 0.78f
                typeface = Typeface.MONOSPACE
                textAlign = Paint.Align.RIGHT
            }
            val first = editorLayout.getLineForVertical((scrollY - totalPaddingTop).coerceAtLeast(0))
            val last = editorLayout.getLineForVertical((scrollY + height - totalPaddingTop).coerceAtLeast(0))
            val right = gutterWidth - (10 * resources.displayMetrics.density)
            for (lineNumber in first..last.coerceAtMost(editorLayout.lineCount - 1)) {
                val baseline = editorLayout.getLineBaseline(lineNumber) + totalPaddingTop - scrollY
                canvas.drawText((lineNumber + 1).toString(), right, baseline.toFloat(), numberPaint)
            }
        }
        val suffix=ghostSuffix ?: return
        val cursor=selectionStart
        val currentLayout=editorLayout ?: return
        if(cursor<0 || cursor>text.length) return
        val line=currentLayout.getLineForOffset(cursor)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=AndroidColor.argb(ghostAlpha,190,200,210)
            textSize=this@PythonEditorView.textSize
            typeface=this@PythonEditorView.typeface
        }
        val x=currentLayout.getPrimaryHorizontal(cursor)+totalPaddingLeft-scrollX
        val y=currentLayout.getLineBaseline(line).toFloat()+totalPaddingTop-scrollY
        canvas.drawText(suffix.substringBefore('\n'),x,y,paint)
    }

    private fun highlightNow() {
        if (!highlightingEnabled) return
        val editable = text ?: return
        val source = editable.toString()
        applyingHighlight = true
        editable.getSpans(0, editable.length, ForegroundColorSpan::class.java).forEach(editable::removeSpan)
        fun color(start: Int, end: Int, value: Int) {
            if (end > start) editable.setSpan(ForegroundColorSpan(value), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        var i = 0
        while (i < source.length) {
            val start = i
            when {
                source[i] == '#' -> {
                    while (i < source.length && source[i] != '\n') i++
                    color(start, i, AndroidColor.rgb(106, 153, 85))
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
                    color(start, i, AndroidColor.rgb(206, 145, 120))
                }
                source[i].isDigit() -> {
                    while (i < source.length && (source[i].isDigit() || source[i] in ".xXabcdefABCDEF_")) i++
                    color(start, i, AndroidColor.rgb(181, 206, 168))
                }
                source[i].isLetter() || source[i] == '_' -> {
                    while (i < source.length && (source[i].isLetterOrDigit() || source[i] == '_')) i++
                    val word = source.substring(start, i)
                    var lookAhead = i
                    while (lookAhead < source.length && source[lookAhead].isWhitespace()) lookAhead++
                    val next = source.getOrNull(lookAhead)
                    val tokenColor = when {
                        word in keywords -> AndroidColor.rgb(197, 134, 192)
                        word in constants -> AndroidColor.rgb(86, 156, 214)
                        word in builtins || next == '(' -> AndroidColor.rgb(220, 220, 170)
                        else -> AndroidColor.rgb(156, 220, 254)
                    }
                    color(start, i, tokenColor)
                }
                else -> i++
            }
        }
        applyingHighlight = false
    }
}

@Composable fun PyDroidX(vm: IdeViewModel) {
    val bg = Color.Black
    val text = Color(0xFFE6F5FF)
    val accent = Color(0xFF00E5FF)
    val revision = vm.editorRevision
    val pager = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()
    val pages = listOf("CODE", "TERMINAL", "AI", "SETTINGS")
    var editorView by remember { mutableStateOf<PythonEditorView?>(null) }
    var showAiSettings by remember { mutableStateOf(false) }
    var keyDraft by remember { mutableStateOf("") }
    var endpointDraft by remember { mutableStateOf(vm.aiEndpoint) }
    var modelDraft by remember { mutableStateOf(vm.aiModel) }
    var providerDraft by remember { mutableStateOf(vm.aiProvider) }
    var showAdvancedAi by remember { mutableStateOf(false) }

    MaterialTheme(colorScheme = darkColorScheme(primary=accent,background=bg,surface=bg)) {
        Column(Modifier.fillMaxSize().background(bg).imePadding()) {
            Row(
                Modifier.fillMaxWidth().height((68 * vm.uiScale).dp).padding(start=12.dp,end=12.dp,top=12.dp,bottom=6.dp),
                horizontalArrangement=Arrangement.spacedBy(10.dp),
                verticalAlignment=androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("PyDroid X",color=text,fontSize=(18*vm.uiScale).sp,modifier=Modifier.weight(1f))
                Button(onClick={vm.run()},enabled=!vm.running,colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF00E676),contentColor=Color.Black)){Text("▶ Run")}
                Button(onClick={vm.stop()},enabled=vm.running,colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFFF3D71),contentColor=Color.Black)){Text("■ Stop")}
            }
            Row(
                Modifier.fillMaxWidth().height(42.dp).background(Color(0xFF050505)),
                horizontalArrangement=Arrangement.Center,
                verticalAlignment=androidx.compose.ui.Alignment.CenterVertically
            ) {
                pages.forEachIndexed { index,label ->
                    TextButton(onClick={scope.launch { pager.animateScrollToPage(index) }}) {
                        Text(label,color=if(pager.currentPage==index) accent else Color(0xFF666666),fontSize=11.sp)
                    }
                }
            }
            HorizontalPager(state=pager,modifier=Modifier.weight(1f).fillMaxWidth(),beyondViewportPageCount=1) { page ->
                when(page) {
                    0 -> Column(Modifier.fillMaxSize().background(bg)) {
                        Text("main.py  •  ${vm.runtimeVersion.substringBefore('\n')}",color=Color.Gray,fontSize=11.sp,modifier=Modifier.padding(10.dp,6.dp))
                        AndroidView(
                            factory={context->PythonEditorView(context).also{view->
                                editorView=view
                                view.onCodeChanged=vm::updateCode
                                view.requestSmartCompletion=vm::requestCompletion
                                view.setCodeIfDifferent(vm.code)
                            }},
                            update={view->
                                if(revision>0)view.setCodeIfDifferent(vm.code)
                                view.applyPreferences(vm.editorFontSize,vm.wordWrap,vm.syntaxHighlighting,vm.fontName,
                                    vm.lineSpacing,vm.editorPadding,vm.typingAnimation,vm.animationDuration,
                                    vm.highlightDelay,vm.cursorStyle,vm.autocomplete,vm.ghostBrightness,
                                    vm.lineNumbers,vm.highlightCurrentLine)
                            },
                            modifier=Modifier.weight(1f).fillMaxWidth().background(bg)
                        )
                        if(vm.showToolbar) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(Color(0xFF050505)).padding(4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)){
                            listOf("Tab","(",")","[","]","{","}","\"","'",":","=").forEach{key->TextButton(onClick={if(key=="Tab" && editorView?.acceptGhostSuggestion()==true) Unit else editorView?.insertAtCursor(if(key=="Tab")"    " else key)}){Text(key)}}
                        }
                    }
                    1 -> Column(Modifier.fillMaxSize().padding(14.dp)) {
                        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                            Text("TERMINAL",color=accent,fontSize=16.sp,modifier=Modifier.weight(1f))
                            TextButton(onClick={vm.output=""}){Text("Clear")}
                        }
                        Text(vm.output.ifEmpty{"Ready"},color=text,fontFamily=when(vm.fontName){"Sans"->FontFamily.SansSerif;"Serif"->FontFamily.Serif;else->FontFamily.Monospace},fontSize=vm.terminalFontSize.sp,modifier=Modifier.weight(1f).fillMaxWidth().background(Color(0xFF030303)).padding(12.dp).animateContentSize().verticalScroll(rememberScrollState()))
                        if(vm.waitingInput) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            TextField(vm.input,{vm.input=it},singleLine=true,modifier=Modifier.weight(1f),placeholder={Text("Program input")})
                            Button(onClick={vm.submitInput()}){Text("Send")}
                        }
                    }
                    2 -> Column(Modifier.fillMaxSize().padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                            Column(Modifier.weight(1f)){Text("AI ASSISTANT",color=accent,fontSize=16.sp);Text("Only shares code when you allow it",color=Color.Gray,fontSize=10.sp)}
                            TextButton(onClick={showAiSettings=true}){Text(if(vm.hasAiKey)"Connection" else "Add key")}
                        }
                        Text(vm.aiReply,color=text,fontSize=14.sp,modifier=Modifier.weight(1f).fillMaxWidth().background(Color(0xFF050505)).padding(14.dp).animateContentSize().verticalScroll(rememberScrollState()))
                        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                            Checkbox(checked=vm.shareCode,onCheckedChange={vm.shareCode=it})
                            Text("Share current file with provider",color=Color.Gray,fontSize=12.sp)
                            Spacer(Modifier.weight(1f))
                            if(vm.aiBusy)CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp)
                        }
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            TextField(vm.aiPrompt,{vm.aiPrompt=it},modifier=Modifier.weight(1f),placeholder={Text("Ask about code or errors…")},maxLines=4)
                            Button(onClick={vm.askAi()},enabled=!vm.aiBusy&&vm.aiPrompt.isNotBlank()){Text("Send")}
                        }
                    }
                    else -> Column(Modifier.fillMaxSize().padding(18.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                        Text("CUSTOMIZE",color=accent,fontSize=18.sp)
                        Text("Editor font  ${vm.editorFontSize.toInt()} sp",color=text)
                        Slider(vm.editorFontSize,{vm.editorFontSize=it;vm.saveAppearance()},valueRange=12f..28f,steps=15)
                        Text("Terminal font  ${vm.terminalFontSize.toInt()} sp",color=text)
                        Slider(vm.terminalFontSize,{vm.terminalFontSize=it;vm.saveAppearance()},valueRange=10f..24f,steps=13)
                        Text("Interface scale  ${(vm.uiScale*100).toInt()}%",color=text)
                        Slider(vm.uiScale,{vm.uiScale=it;vm.saveAppearance()},valueRange=0.85f..1.25f,steps=7)
                        Text("Font family",color=text)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Monospace","Sans","Serif").forEach{font->FilterChip(selected=vm.fontName==font,onClick={vm.fontName=font;vm.saveAppearance()},label={Text(font)})}}
                        Text("Line spacing  ${"%.2f".format(vm.lineSpacing)}×",color=text)
                        Slider(vm.lineSpacing,{vm.lineSpacing=it;vm.saveAppearance()},valueRange=0.9f..1.8f)
                        Text("Editor padding  ${vm.editorPadding.toInt()} px",color=text)
                        Slider(vm.editorPadding,{vm.editorPadding=it;vm.saveAppearance()},valueRange=0f..48f,steps=11)
                        Text("Highlight delay  ${vm.highlightDelay.toInt()} ms",color=text)
                        Slider(vm.highlightDelay,{vm.highlightDelay=it;vm.saveAppearance()},valueRange=80f..700f,steps=14)
                        Text("Autosave delay  ${vm.autosaveDelay.toInt()} ms",color=text)
                        Slider(vm.autosaveDelay,{vm.autosaveDelay=it;vm.saveAppearance()},valueRange=150f..2000f,steps=18)
                        Text("Ghost text brightness  ${(vm.ghostBrightness*100).toInt()}%",color=text)
                        Slider(vm.ghostBrightness,{vm.ghostBrightness=it;vm.saveAppearance()},valueRange=0.15f..0.8f)
                        Text("Cursor color",color=text)
                        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Cyan","Magenta","Green","White").forEach{shade->FilterChip(selected=vm.cursorStyle==shade,onClick={vm.cursorStyle=shade;vm.saveAppearance()},label={Text(shade)})}}
                        SettingSwitch("Word wrap",vm.wordWrap){vm.wordWrap=it;vm.saveAppearance()}
                        SettingSwitch("Syntax highlighting",vm.syntaxHighlighting){vm.syntaxHighlighting=it;vm.saveAppearance()}
                        SettingSwitch("Ghost-text autocomplete",vm.autocomplete){vm.autocomplete=it;vm.saveAppearance()}
                        SettingSwitch("Line numbers",vm.lineNumbers){vm.lineNumbers=it;vm.saveAppearance()}
                        SettingSwitch("Highlight active line",vm.highlightCurrentLine){vm.highlightCurrentLine=it;vm.saveAppearance()}
                        SettingSwitch("Automatic saving",vm.autoSave){vm.autoSave=it;vm.saveAppearance()}
                        SettingSwitch("Programming toolbar",vm.showToolbar){vm.showToolbar=it;vm.saveAppearance()}
                        SettingSwitch("Page indicator dots",vm.showPageDots){vm.showPageDots=it;vm.saveAppearance()}
                        HorizontalDivider(color=Color(0xFF202020))
                        Text("Swipe left or right anywhere outside active text editing to move between pages.",color=Color.Gray,fontSize=12.sp)
                        Text("Python  ${vm.runtimeVersion.substringBefore('\n')}",color=Color.Gray,fontSize=11.sp)
                    }
                }
            }
            if(vm.showPageDots) Row(Modifier.fillMaxWidth().height(22.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                repeat(4){index->Box(Modifier.padding(horizontal=3.dp).size(if(index==pager.currentPage)8.dp else 5.dp).background(if(index==pager.currentPage)accent else Color.DarkGray,androidx.compose.foundation.shape.CircleShape))}
            }
        }
    }
    if(showAiSettings) AlertDialog(
        onDismissRequest={showAiSettings=false},containerColor=Color(0xFF0A0A0A),title={Text("AI connection")},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Paste an API key — Auto detects popular providers",fontSize=13.sp,color=Color(0xFFD4D4D4))
            TextField(keyDraft,{keyDraft=it},label={Text(if(vm.hasAiKey)"New API key (optional)" else "API key")},singleLine=true,visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation())
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                listOf("Auto","OpenAI","Gemini","Claude","OpenRouter","Groq","Custom").forEach{provider->
                    FilterChip(selected=providerDraft==provider,onClick={providerDraft=provider},label={Text(provider)})
                }
            }
            TextButton(onClick={showAdvancedAi=!showAdvancedAi}){Text(if(showAdvancedAi)"Hide advanced" else "Advanced")}
            if(showAdvancedAi){
                TextField(endpointDraft,{endpointDraft=it},label={Text("Custom endpoint")},singleLine=true)
                TextField(modelDraft,{modelDraft=it},label={Text("Model")},singleLine=true)
            }
            Row{
                TextButton(onClick={vm.saveAiSettings(keyDraft,endpointDraft,modelDraft,providerDraft);keyDraft="";vm.askAi(true)}){Text("Save & test")}
                if(vm.hasAiKey) TextButton(onClick={vm.removeAiKey()}){Text("Remove",color=Color(0xFFFF3D71))}
            }
        }},
        confirmButton={Button(onClick={vm.saveAiSettings(keyDraft,endpointDraft,modelDraft,providerDraft);keyDraft="";showAiSettings=false}){Text("Save")}},
        dismissButton={TextButton(onClick={showAiSettings=false}){Text("Cancel")}}
    )
}

@Composable private fun SettingSwitch(label:String,checked:Boolean,onChange:(Boolean)->Unit){
    Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
        Text(label,color=Color(0xFFE6F5FF),modifier=Modifier.weight(1f))
        Switch(checked=checked,onCheckedChange=onChange)
    }
}
