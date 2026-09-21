package com.pydroidx.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
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
    var code by mutableStateOf("print(\"Hello Andrew\")\nname = input(\"What is your name? \")\nprint(\"Hello\", name)\n")
    var output by mutableStateOf("")
    var running by mutableStateOf(false)
    var waitingInput by mutableStateOf(false)
    var input by mutableStateOf("")
    var runtimeVersion by mutableStateOf("Loading Python…")
    private val stdin = LinkedBlockingQueue<String?>()
    @Volatile private var worker: Thread? = null
    private var autosaveJob: Job? = null
    lateinit var projectDir: File

    fun initialize(dir: File) {
        projectDir = File(dir, "projects/default").apply { mkdirs() }
        val main = File(projectDir, "main.py")
        if (main.exists()) code = main.readText() else main.writeText(code)
        thread { runtimeVersion = Python.getInstance().getModule("runner").callAttr("version").toString() }
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
        setContent { val vm: IdeViewModel = viewModel(); LaunchedEffect(Unit) { vm.initialize(filesDir) }; PyDroidX(vm) }
    }
}

private object PythonSyntaxTransformation : VisualTransformation {
    private val keywords = setOf(
        "and", "as", "assert", "async", "await", "break", "case", "class", "continue",
        "def", "del", "elif", "else", "except", "finally", "for", "from", "global",
        "if", "import", "in", "is", "lambda", "match", "nonlocal", "not", "or", "pass",
        "raise", "return", "try", "while", "with", "yield"
    )
    private val constants = setOf("True", "False", "None", "NotImplemented", "Ellipsis")

    override fun filter(text: AnnotatedString): TransformedText {
        val source = text.text
        val styled = AnnotatedString.Builder(source)
        var i = 0
        while (i < source.length) {
            val start = i
            when {
                source[i] == '#' -> {
                    while (i < source.length && source[i] != '\n') i++
                    styled.addStyle(SpanStyle(color = Color(0xFF6A9955)), start, i)
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
                    styled.addStyle(SpanStyle(color = Color(0xFFCE9178)), start, i)
                }
                source[i].isDigit() -> {
                    while (i < source.length && (source[i].isDigit() || source[i] in ".xXabcdefABCDEF_")) i++
                    styled.addStyle(SpanStyle(color = Color(0xFFB5CEA8)), start, i)
                }
                source[i].isLetter() || source[i] == '_' -> {
                    while (i < source.length && (source[i].isLetterOrDigit() || source[i] == '_')) i++
                    val word = source.substring(start, i)
                    var lookAhead = i
                    while (lookAhead < source.length && source[lookAhead].isWhitespace()) lookAhead++
                    val next = source.getOrNull(lookAhead)
                    val color = when {
                        word in keywords -> Color(0xFFC586C0)
                        word in constants -> Color(0xFF569CD6)
                        next == '(' -> Color(0xFFDCDCAA)
                        else -> Color(0xFF9CDCFE)
                    }
                    styled.addStyle(SpanStyle(color = color), start, i)
                }
                else -> i++
            }
        }
        return TransformedText(styled.toAnnotatedString(), OffsetMapping.Identity)
    }
}

@Composable fun PyDroidX(vm: IdeViewModel) {
    val bg = Color.Black; val panel = Color.Black; val text = Color(0xFFD4D4D4); val accent = Color(0xFF569CD6)
    val density = LocalDensity.current
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0
    MaterialTheme(colorScheme = darkColorScheme(primary = accent, background = bg, surface = panel)) {
        Column(Modifier.fillMaxSize().background(bg).imePadding()) {
            Row(Modifier.fillMaxWidth().height(52.dp).background(panel).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PyDroid X", color=text, fontSize=18.sp, modifier=Modifier.weight(1f).padding(top=14.dp))
                Button(onClick={vm.run()}, enabled=!vm.running) { Text("Run") }
                OutlinedButton(onClick={vm.stop()}, enabled=vm.running) { Text("Stop") }
            }
            if (!keyboardVisible) Text("main.py  •  ${vm.runtimeVersion.substringBefore('\n')}", color=Color.Gray, fontSize=11.sp, modifier=Modifier.padding(10.dp,6.dp))
            BasicTextField(value=vm.code, onValueChange=vm::updateCode, visualTransformation=PythonSyntaxTransformation, textStyle=TextStyle(color=text,fontFamily=FontFamily.Monospace,fontSize=16.sp,lineHeight=22.sp), modifier=Modifier.weight(1f).fillMaxWidth().background(Color.Black).padding(10.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(panel).padding(4.dp), horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                listOf("Tab","(",")","[","]","{","}","\"","'",":","=").forEach { key -> TextButton(onClick={ vm.updateCode(vm.code + if(key=="Tab") "    " else key) }) { Text(key) } }
            }
            if (!keyboardVisible) Column(Modifier.fillMaxWidth().heightIn(min=150.dp,max=260.dp).background(Color.Black).padding(8.dp)) {
                Row { Text("TERMINAL",color=accent,fontSize=12.sp,modifier=Modifier.weight(1f)); TextButton(onClick={vm.output=""}){Text("Clear")} }
                Text(vm.output.ifEmpty{"Ready"},color=text,fontFamily=FontFamily.Monospace,fontSize=13.sp,modifier=Modifier.weight(1f).verticalScroll(rememberScrollState()))
                if(vm.waitingInput) Row { TextField(vm.input,{vm.input=it},singleLine=true,modifier=Modifier.weight(1f),placeholder={Text("Program input")}); Button(onClick={vm.submitInput()}){Text("Send")} }
            }
        }
    }
}
