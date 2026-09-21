package com.pydroidx.app

import android.os.Bundle
import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    var shareCode by mutableStateOf(false)
    var hasAiKey by mutableStateOf(false)
    private val stdin = LinkedBlockingQueue<String?>()
    @Volatile private var worker: Thread? = null
    private var autosaveJob: Job? = null
    lateinit var projectDir: File
    private lateinit var aiKeys: SecureAiKeyStore

    fun initialize(context: Context) {
        aiKeys = SecureAiKeyStore(context.applicationContext)
        hasAiKey = !aiKeys.load().isNullOrBlank()
        projectDir = File(context.filesDir, "projects/default").apply { mkdirs() }
        val main = File(projectDir, "main.py")
        if (main.exists()) code = main.readText() else main.writeText(code)
        editorRevision++
        thread { runtimeVersion = Python.getInstance().getModule("runner").callAttr("version").toString() }
    }
    fun saveAiSettings(key: String, endpoint: String, model: String) {
        if (key.isNotBlank()) aiKeys.save(key)
        aiEndpoint = endpoint.trim()
        aiModel = model.trim()
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
            val result = runCatching { AiClient.chat(aiEndpoint, key, aiModel, question, if (!testOnly && shareCode) code else null) }
            aiReply = result.getOrElse { "AI error: ${it.message ?: "Request failed"}" }
            aiBusy = false
        }
    }
    fun updateCode(value: String) {
        code = value
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(500)
            val snapshot = code
            launch(Dispatchers.IO) {
                if (::projectDir.isInitialized) File(projectDir, "main.py").writeText(snapshot)
            }
        }
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
    private var applyingHighlight = false
    private val keywords = setOf(
        "and", "as", "assert", "async", "await", "break", "case", "class", "continue",
        "def", "del", "elif", "else", "except", "finally", "for", "from", "global",
        "if", "import", "in", "is", "lambda", "match", "nonlocal", "not", "or", "pass",
        "raise", "return", "try", "while", "with", "yield"
    )
    private val constants = setOf("True", "False", "None", "NotImplemented", "Ellipsis")
    private val highlightRunnable = Runnable { highlightNow() }

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
                    postDelayed(highlightRunnable, 220)
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

    private fun highlightNow() {
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
                        next == '(' -> AndroidColor.rgb(220, 220, 170)
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
    val bg = Color.Black; val panel = Color.Black; val text = Color(0xFFD4D4D4); val accent = Color(0xFF569CD6)
    val density = LocalDensity.current
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val revision = vm.editorRevision
    var editorView by remember { mutableStateOf<PythonEditorView?>(null) }
    var bottomTab by remember { mutableStateOf("TERMINAL") }
    var showAiSettings by remember { mutableStateOf(false) }
    var keyDraft by remember { mutableStateOf("") }
    var endpointDraft by remember { mutableStateOf(vm.aiEndpoint) }
    var modelDraft by remember { mutableStateOf(vm.aiModel) }
    MaterialTheme(colorScheme = darkColorScheme(primary = accent, background = bg, surface = panel)) {
        Column(Modifier.fillMaxSize().background(bg).imePadding()) {
            Row(Modifier.fillMaxWidth().height(52.dp).background(panel).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PyDroid X", color=text, fontSize=18.sp, modifier=Modifier.weight(1f).padding(top=14.dp))
                Button(onClick={vm.run()}, enabled=!vm.running) { Text("Run") }
                OutlinedButton(onClick={vm.stop()}, enabled=vm.running) { Text("Stop") }
            }
            if (!keyboardVisible) Text("main.py  •  ${vm.runtimeVersion.substringBefore('\n')}", color=Color.Gray, fontSize=11.sp, modifier=Modifier.padding(10.dp,6.dp))
            AndroidView(
                factory = { context -> PythonEditorView(context).also { view -> editorView = view; view.onCodeChanged = vm::updateCode; view.setCodeIfDifferent(vm.code) } },
                update = { if (revision > 0) it.setCodeIfDifferent(vm.code) },
                modifier = Modifier.weight(3f).fillMaxWidth().background(Color.Black)
            )
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(panel).padding(4.dp), horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                listOf("Tab","(",")","[","]","{","}","\"","'",":","=").forEach { key -> TextButton(onClick={ editorView?.insertAtCursor(if(key=="Tab") "    " else key) }) { Text(key) } }
            }
            if (!keyboardVisible) Column(Modifier.fillMaxWidth().weight(1f).background(Color.Black).padding(8.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    TextButton(onClick={bottomTab="TERMINAL"}) { Text("TERMINAL", color=if(bottomTab=="TERMINAL") accent else Color.Gray, fontSize=12.sp) }
                    TextButton(onClick={bottomTab="AI"}) { Text("AI ASSISTANT", color=if(bottomTab=="AI") accent else Color.Gray, fontSize=12.sp) }
                    Spacer(Modifier.weight(1f))
                    if(bottomTab=="TERMINAL") TextButton(onClick={vm.output=""}){Text("Clear")}
                    else TextButton(onClick={showAiSettings=true}){Text(if(vm.hasAiKey) "Settings" else "Add key")}
                }
                if (bottomTab == "TERMINAL") {
                    Text(vm.output.ifEmpty{"Ready"},color=text,fontFamily=FontFamily.Monospace,fontSize=13.sp,modifier=Modifier.weight(1f).verticalScroll(rememberScrollState()))
                    if(vm.waitingInput) Row { TextField(vm.input,{vm.input=it},singleLine=true,modifier=Modifier.weight(1f),placeholder={Text("Program input")}); Button(onClick={vm.submitInput()}){Text("Send")} }
                } else {
                    Text(vm.aiReply, color=text, fontSize=13.sp, modifier=Modifier.weight(1f).fillMaxWidth().background(Color(0xFF080808)).padding(10.dp).verticalScroll(rememberScrollState()))
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked=vm.shareCode,onCheckedChange={vm.shareCode=it})
                        Text("Share current code",color=Color.Gray,fontSize=11.sp)
                        Spacer(Modifier.weight(1f))
                        if(vm.aiBusy) CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        TextField(vm.aiPrompt,{vm.aiPrompt=it},singleLine=true,modifier=Modifier.weight(1f),placeholder={Text("Ask AI…")})
                        Button(onClick={vm.askAi()},enabled=!vm.aiBusy && vm.aiPrompt.isNotBlank()){Text("Send")}
                    }
                }
            }
        }
    }
    if (showAiSettings) AlertDialog(
        onDismissRequest={showAiSettings=false},
        containerColor=Color(0xFF0A0A0A),
        title={Text("AI connection")},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("OpenAI-compatible provider. Your key is encrypted with Android Keystore and sent only to this endpoint.",fontSize=12.sp,color=Color.Gray)
            TextField(keyDraft,{keyDraft=it},label={Text(if(vm.hasAiKey) "API key (saved)" else "API key")},singleLine=true,visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation())
            TextField(endpointDraft,{endpointDraft=it},label={Text("Chat completions endpoint")},singleLine=true)
            TextField(modelDraft,{modelDraft=it},label={Text("Model")},singleLine=true)
            TextButton(onClick={vm.removeAiKey()}){Text("Remove saved key",color=Color(0xFFF85149))}
        }},
        confirmButton={Button(onClick={vm.saveAiSettings(keyDraft,endpointDraft,modelDraft); keyDraft=""; showAiSettings=false}){Text("Save")}},
        dismissButton={TextButton(onClick={showAiSettings=false}){Text("Cancel")}}
    )
}
