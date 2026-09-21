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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaquo.python.Python
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

class IdeViewModel : ViewModel() {
    var code by mutableStateOf("print(\"Hello Andrew\")\nname = input(\"What is your name? \")\nprint(\"Hello\", name)\n")
    var output by mutableStateOf("")
    var running by mutableStateOf(false)
    var waitingInput by mutableStateOf(false)
    var input by mutableStateOf("")
    var runtimeVersion by mutableStateOf("Loading Python…")
    private val stdin = LinkedBlockingQueue<String?>()
    @Volatile private var worker: Thread? = null
    lateinit var projectDir: File

    fun initialize(dir: File) {
        projectDir = File(dir, "projects/default").apply { mkdirs() }
        val main = File(projectDir, "main.py")
        if (main.exists()) code = main.readText() else main.writeText(code)
        thread { runtimeVersion = Python.getInstance().getModule("runner").callAttr("version").toString() }
    }
    fun save() = File(projectDir, "main.py").writeText(code)
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

@Composable fun PyDroidX(vm: IdeViewModel) {
    val bg = Color(0xFF0B0E14); val panel = Color(0xFF11151E); val text = Color(0xFFD8DEE9); val accent = Color(0xFF7AA2F7)
    MaterialTheme(colorScheme = darkColorScheme(primary = accent, background = bg, surface = panel)) {
        Column(Modifier.fillMaxSize().background(bg).imePadding()) {
            Row(Modifier.fillMaxWidth().height(52.dp).background(panel).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PyDroid X", color=text, fontSize=18.sp, modifier=Modifier.weight(1f).padding(top=14.dp))
                Button(onClick={vm.run()}, enabled=!vm.running) { Text("Run") }
                OutlinedButton(onClick={vm.stop()}, enabled=vm.running) { Text("Stop") }
            }
            Text("main.py  •  ${vm.runtimeVersion.substringBefore('\n')}", color=Color.Gray, fontSize=11.sp, modifier=Modifier.padding(10.dp,6.dp))
            BasicTextField(value=vm.code, onValueChange={vm.code=it; vm.save()}, textStyle=TextStyle(color=text,fontFamily=FontFamily.Monospace,fontSize=16.sp,lineHeight=22.sp), modifier=Modifier.weight(1f).fillMaxWidth().padding(10.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(panel).padding(4.dp), horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                listOf("Tab","(",")","[","]","{","}","\"","'",":","=").forEach { key -> TextButton(onClick={ vm.code += if(key=="Tab") "    " else key }) { Text(key) } }
            }
            Column(Modifier.fillMaxWidth().heightIn(min=150.dp,max=260.dp).background(Color(0xFF080A0F)).padding(8.dp)) {
                Row { Text("TERMINAL",color=accent,fontSize=12.sp,modifier=Modifier.weight(1f)); TextButton(onClick={vm.output=""}){Text("Clear")} }
                Text(vm.output.ifEmpty{"Ready"},color=text,fontFamily=FontFamily.Monospace,fontSize=13.sp,modifier=Modifier.weight(1f).verticalScroll(rememberScrollState()))
                if(vm.waitingInput) Row { TextField(vm.input,{vm.input=it},singleLine=true,modifier=Modifier.weight(1f),placeholder={Text("Program input")}); Button(onClick={vm.submitInput()}){Text("Send")} }
            }
        }
    }
}
