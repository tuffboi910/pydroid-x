package com.pydroidx.app

import android.os.Bundle
import android.app.Activity
import android.content.Context
import android.widget.Toast
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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.graphicsLayer
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
import org.json.JSONArray
import kotlin.math.absoluteValue
import java.net.URL

data class AiMessage(val fromUser: Boolean, val text: String)

private val FONT_VAULT = """
Fira Code|firacode,JetBrains Mono|jetbrainsmono,Source Code Pro|sourcecodepro,IBM Plex Mono|ibmplexmono,Cascadia Code|cascadiacode,Victor Mono|victormono,Space Mono|spacemono,Inconsolata|inconsolata,Roboto Mono|robotomono,Noto Sans Mono|notosansmono,
Anonymous Pro|anonymouspro,Cousine|cousine,Cutive Mono|cutivemono,DM Mono|dmmono,Fragment Mono|fragmentmono,Geist Mono|geistmono,Lekton|lekton,Major Mono Display|majormonodisplay,Martian Mono|martianmono,Nanum Gothic Coding|nanumgothiccoding,
Nova Mono|novamono,Overpass Mono|overpassmono,PT Mono|ptmono,Red Hat Mono|redhatmono,Share Tech Mono|sharetechmono,Sono|sono,Spline Sans Mono|splinesansmono,Syne Mono|synemono,Ubuntu Mono|ubuntumono,Xanh Mono|xanhmono,
Azeret Mono|azeretmono,B612 Mono|b612mono,BPdots|bpdots,Chivo Mono|chivomono,Code New Roman|codenewroman,Faculty Glyphic|facultyglyphic,Funnel Display|funneldisplay,Geist|geist,Golos Text|golostext,Google Sans Code|googlesanscode,
Atkinson Hyperlegible Mono|atkinsonhyperlegiblemono,Intel One Mono|intelonemono,LXGW WenKai Mono TC|lxgwwenkaimonotc,Maple Mono|maplemono,M PLUS 1 Code|mplus1code,Monofett|monofett,Monomaniac One|monomaniacone,Monoton|monoton,OCR B|ocrb,Offside|offside,
Oxygen Mono|oxygenmono,Playwrite US Modern|playwriteusmodern,Recursive|recursive,Roboto Flex|robotoflex,Schibsted Grotesk|schibstedgrotesk,Sixtyfour|sixtyfour,Sixtyfour Convergence|sixtyfourconvergence,Sometype Mono|sometypemono,Tektur|tektur,Tomorrow|tomorrow,
Trispace|trispace,Ubuntu Sans Mono|ubuntusansmono,Unbounded|unbounded,Workbench|workbench,Young Serif|youngserif,Albert Sans|albertsans,Archivo|archivo,Archivo Black|archivoblack,Assistant|assistant,Barlow|barlow,
Barlow Condensed|barlowcondensed,Barlow Semi Condensed|barlowsemicondensed,Be Vietnam Pro|bevietnampro,Bitter|bitter,Cabin|cabin,Commissioner|commissioner,DM Sans|dmsans,Exo 2|exo2,Inter|inter,Karla|karla,
Manrope|manrope,Merriweather Sans|merriweathersans,Mohave|mohave,Montserrat|montserrat,Mulish|mulish,Nunito|nunito,Onest|onest,Open Sans|opensans,Outfit|outfit,Oxanium|oxanium,
Plus Jakarta Sans|plusjakartasans,Prompt|prompt,Public Sans|publicsans,Quicksand|quicksand,Rajdhani|rajdhani,Raleway|raleway,Rubik|rubik,Sora|sora,Urbanist|urbanist,Work Sans|worksans
""".trimIndent().replace("\n","").split(",").mapNotNull { item ->
    val parts=item.trim().split("|"); if(parts.size==2) parts[0] to parts[1] else null
}

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
    val aiMessages = mutableStateListOf<AiMessage>()
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
    var accentHex by mutableStateOf("#00E5FF")
    var backgroundHex by mutableStateOf("#000000")
    var motionStyle by mutableStateOf("Fluid spring")
    var motionSpeed by mutableFloatStateOf(1f)
    var motionIntensity by mutableFloatStateOf(0.5f)
    var motionEnabled by mutableStateOf(true)
    var settingsQuery by mutableStateOf("")
    var customFontPath by mutableStateOf("")
    var fontStatus by mutableStateOf("100-font vault ready")
    var editorTextHex by mutableStateOf("#D4D4D4")
    var commentHex by mutableStateOf("#6A9955")
    var stringHex by mutableStateOf("#CE9178")
    var numberHex by mutableStateOf("#B5CEA8")
    var keywordHex by mutableStateOf("#C586C0")
    var functionHex by mutableStateOf("#DCDCAA")
    var variableHex by mutableStateOf("#9CDCFE")
    var consoleTextHex by mutableStateOf("#E6F5FF")
    var consoleBackgroundHex by mutableStateOf("#030303")
    var toolbarHex by mutableStateOf("#050505")
    var tabBarHex by mutableStateOf("#050505")
    var userBubbleHex by mutableStateOf("#082F36")
    var helperBubbleHex by mutableStateOf("#00E5FF")
    var runButtonHex by mutableStateOf("#00E676")
    var stopButtonHex by mutableStateOf("#FF3D71")
    var headerHeight by mutableFloatStateOf(68f)
    var tabHeight by mutableFloatStateOf(42f)
    var toolbarHeight by mutableFloatStateOf(52f)
    var bubbleRadius by mutableFloatStateOf(16f)
    var bubbleWidth by mutableFloatStateOf(310f)
    var pageDotSize by mutableFloatStateOf(8f)
    var showHeader by mutableStateOf(true)
    var showFileInfo by mutableStateOf(true)
    private val stdin = LinkedBlockingQueue<String>()
    private val stopInputSignal = "\u0000PYDROIDX_STOP\u0000"
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
        accentHex = settings.getString("accent_hex", "#00E5FF") ?: "#00E5FF"
        backgroundHex = settings.getString("background_hex", "#000000") ?: "#000000"
        motionStyle = settings.getString("motion_style", "Fluid spring") ?: "Fluid spring"
        motionSpeed = settings.getFloat("motion_speed", 1f)
        motionIntensity = settings.getFloat("motion_intensity", 0.5f)
        motionEnabled = settings.getBoolean("motion_enabled", true)
        customFontPath = settings.getString("custom_font_path", "") ?: ""
        editorTextHex=settings.getString("editor_text_hex","#D4D4D4")?:"#D4D4D4"
        commentHex=settings.getString("comment_hex","#6A9955")?:"#6A9955"
        stringHex=settings.getString("string_hex","#CE9178")?:"#CE9178"
        numberHex=settings.getString("number_hex","#B5CEA8")?:"#B5CEA8"
        keywordHex=settings.getString("keyword_hex","#C586C0")?:"#C586C0"
        functionHex=settings.getString("function_hex","#DCDCAA")?:"#DCDCAA"
        variableHex=settings.getString("variable_hex","#9CDCFE")?:"#9CDCFE"
        consoleTextHex=settings.getString("console_text_hex","#E6F5FF")?:"#E6F5FF"
        consoleBackgroundHex=settings.getString("console_background_hex","#030303")?:"#030303"
        toolbarHex=settings.getString("toolbar_hex","#050505")?:"#050505"
        tabBarHex=settings.getString("tab_bar_hex","#050505")?:"#050505"
        userBubbleHex=settings.getString("user_bubble_hex","#082F36")?:"#082F36"
        helperBubbleHex=settings.getString("helper_bubble_hex","#00E5FF")?:"#00E5FF"
        runButtonHex=settings.getString("run_button_hex","#00E676")?:"#00E676"
        stopButtonHex=settings.getString("stop_button_hex","#FF3D71")?:"#FF3D71"
        headerHeight=settings.getFloat("header_height",68f);tabHeight=settings.getFloat("tab_height",42f)
        toolbarHeight=settings.getFloat("toolbar_height",52f);bubbleRadius=settings.getFloat("bubble_radius",16f)
        bubbleWidth=settings.getFloat("bubble_width",310f);pageDotSize=settings.getFloat("page_dot_size",8f)
        showHeader=settings.getBoolean("show_header",true);showFileInfo=settings.getBoolean("show_file_info",true)
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
        if (!testOnly) {
            aiMessages.add(AiMessage(true, question))
            aiPrompt = ""
        }
        aiBusy = true
        aiReply = if (testOnly) "Testing connection…" else "Thinking…"
        thread(name = "PyDroidX-AI") {
            val result = runCatching { AiClient.chat(aiProvider, aiEndpoint, key, aiModel, question, if (!testOnly && shareCode) code else null) }
            val answer = result.getOrElse { "AI error: ${it.message ?: "Request failed"}" }
            viewModelScope.launch {
                aiReply = answer
                if (!testOnly) aiMessages.add(AiMessage(false, answer))
                aiBusy = false
            }
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
        settings.edit().putString("accent_hex",accentHex).putString("background_hex",backgroundHex)
            .putString("motion_style",motionStyle).putFloat("motion_speed",motionSpeed)
            .putFloat("motion_intensity",motionIntensity).putBoolean("motion_enabled",motionEnabled).apply()
        settings.edit().putString("custom_font_path",customFontPath).apply()
        settings.edit().putString("editor_text_hex",editorTextHex).putString("comment_hex",commentHex)
            .putString("string_hex",stringHex).putString("number_hex",numberHex).putString("keyword_hex",keywordHex)
            .putString("function_hex",functionHex).putString("variable_hex",variableHex)
            .putString("console_text_hex",consoleTextHex).putString("console_background_hex",consoleBackgroundHex)
            .putString("toolbar_hex",toolbarHex).putString("tab_bar_hex",tabBarHex)
            .putString("user_bubble_hex",userBubbleHex).putString("helper_bubble_hex",helperBubbleHex)
            .putString("run_button_hex",runButtonHex).putString("stop_button_hex",stopButtonHex)
            .putFloat("header_height",headerHeight).putFloat("tab_height",tabHeight).putFloat("toolbar_height",toolbarHeight)
            .putFloat("bubble_radius",bubbleRadius).putFloat("bubble_width",bubbleWidth).putFloat("page_dot_size",pageDotSize)
            .putBoolean("show_header",showHeader).putBoolean("show_file_info",showFileInfo).apply()
    }
    fun installVaultFont(context: Context, displayName: String, slug: String) {
        if (fontStatus.startsWith("Downloading")) return
        fontStatus = "Downloading $displayName…"
        thread(name="PyDroidX-Font") {
            val result = runCatching {
                val api = URL("https://api.github.com/repos/google/fonts/contents/ofl/$slug").openConnection().apply {
                    setRequestProperty("User-Agent", "PyDroid-X")
                    connectTimeout=15_000;readTimeout=30_000
                }.getInputStream().bufferedReader().use { it.readText() }
                val files = JSONArray(api)
                val fontUrl = (0 until files.length()).asSequence().map { files.getJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".ttf",true) }?.getString("download_url")
                    ?: error("No Android font file found")
                val dir=File(context.filesDir,"fonts").apply{mkdirs()}
                val target=File(dir,"$slug.ttf")
                URL(fontUrl).openStream().use { input -> target.outputStream().use { input.copyTo(it) } }
                target.absolutePath
            }
            viewModelScope.launch {
                result.onSuccess { path -> customFontPath=path;fontName=displayName;fontStatus="$displayName installed";saveAppearance() }
                    .onFailure { fontStatus="Couldn’t install $displayName: ${it.message}" }
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
    fun stop() {
        stdin.offer(stopInputSignal)
        worker?.interrupt()
        output += "\n[Stopping program…]\n"
        running = false
        waitingInput = false
    }
    inner class Bridge {
        fun write(text: String, error: Boolean) { output += text }
        fun readLine(): String? {
            waitingInput = true
            return try { stdin.take().takeUnless { it == stopInputSignal } }
            catch (_: InterruptedException) { null }
        }
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
    private var editorTextColor=AndroidColor.rgb(212,212,212)
    private var commentColor=AndroidColor.rgb(106,153,85)
    private var stringColor=AndroidColor.rgb(206,145,120)
    private var numberColor=AndroidColor.rgb(181,206,168)
    private var keywordColor=AndroidColor.rgb(197,134,192)
    private var functionColor=AndroidColor.rgb(220,220,170)
    private var variableColor=AndroidColor.rgb(156,220,254)
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
                         ghostBrightness: Float, lineNumbers: Boolean, currentLine: Boolean,
                         customFontPath: String, palette: List<String>) {
        textSize = font
        setHorizontallyScrolling(!wrap)
        isHorizontalScrollBarEnabled = !wrap
        highlightingEnabled = syntax
        highlightDelayMs = highlightDelay.toLong()
        autocompleteEnabled = autocomplete
        ghostAlpha = (ghostBrightness * 255).toInt().coerceIn(35,210)
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
        applyingHighlight = false
    }
}

@Composable fun PyDroidX(vm: IdeViewModel) {
    fun safeColor(value:String,fallback:Long)=runCatching{Color(AndroidColor.parseColor(value))}.getOrDefault(Color(fallback))
    val bg = safeColor(vm.backgroundHex,0xFF000000)
    val text = Color(0xFFE6F5FF)
    val accent = safeColor(vm.accentHex,0xFF00E5FF)
    val revision = vm.editorRevision
    val pager = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val aiScroll = rememberScrollState()
    val pages = listOf("PYTHON", "CONSOLE", "HELPER", "SETTINGS")
    var editorView by remember { mutableStateOf<PythonEditorView?>(null) }
    var showAiSettings by remember { mutableStateOf(false) }
    var keyDraft by remember { mutableStateOf("") }
    var endpointDraft by remember { mutableStateOf(vm.aiEndpoint) }
    var modelDraft by remember { mutableStateOf(vm.aiModel) }
    var providerDraft by remember { mutableStateOf(vm.aiProvider) }
    var settingsSection by remember { mutableStateOf("Overview") }
    LaunchedEffect(vm.aiMessages.size) {
        aiScroll.animateScrollTo(aiScroll.maxValue)
    }
    var showAdvancedAi by remember { mutableStateOf(false) }
    var lastBackPress by remember { mutableLongStateOf(0L) }

    BackHandler(enabled=!showAiSettings) {
        val now=android.os.SystemClock.elapsedRealtime()
        if(now-lastBackPress<2000L) (context as? Activity)?.finish()
        else {
            lastBackPress=now
            Toast.makeText(context,"Swipe back again to exit",Toast.LENGTH_SHORT).show()
        }
    }

    val glassShape=androidx.compose.foundation.shape.RoundedCornerShape(22.dp)
    val glass=Color.White.copy(alpha=0.065f)
    val glassEdge=Color.White.copy(alpha=0.16f)
    val liquidBackground=Brush.verticalGradient(listOf(bg,accent.copy(alpha=0.10f),bg,bg))
    MaterialTheme(colorScheme = darkColorScheme(primary=accent,background=bg,surface=Color.Transparent,surfaceVariant=glass,outline=glassEdge)) {
        Column(Modifier.fillMaxSize().background(liquidBackground).imePadding()) {
            if(vm.showHeader) Row(
                Modifier.fillMaxWidth().height(((vm.headerHeight + 39f) * vm.uiScale).dp)
                    .padding(start=10.dp,end=10.dp,top=10.dp,bottom=5.dp)
                    .background(glass,glassShape).border(1.dp,glassEdge,glassShape)
                    .padding(horizontal=12.dp,vertical=6.dp),
                horizontalArrangement=Arrangement.spacedBy(10.dp),
                verticalAlignment=androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("PyDroid X",color=text,fontSize=(18*vm.uiScale).sp,modifier=Modifier.weight(1f).offset(y=(-19).dp))
                Row(Modifier.offset(x=(-20).dp,y=19.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    Button(onClick={vm.run();scope.launch{pager.animateScrollToPage(1)}},enabled=!vm.running,colors=ButtonDefaults.buttonColors(containerColor=safeColor(vm.runButtonHex,0xFF00E676),contentColor=Color.Black)){Text("▶ Run")}
                    Button(onClick={vm.stop()},enabled=vm.running,colors=ButtonDefaults.buttonColors(containerColor=safeColor(vm.stopButtonHex,0xFFFF3D71),contentColor=Color.Black)){Text("■ Stop")}
                }
            }
            Row(
                Modifier.fillMaxWidth().height((vm.tabHeight+8).dp).padding(horizontal=10.dp,vertical=4.dp)
                    .background(safeColor(vm.tabBarHex,0xFF050505).copy(alpha=0.72f),androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                    .border(1.dp,glassEdge,androidx.compose.foundation.shape.RoundedCornerShape(18.dp)),
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
                val rawOffset = (pager.currentPage - page) + pager.currentPageOffsetFraction
                val distance = rawOffset.absoluteValue.coerceIn(0f,1f)
                val motionModifier = Modifier.fillMaxSize().graphicsLayer {
                    if(vm.motionEnabled) {
                        val amount = vm.motionIntensity.coerceIn(0f,1f)
                        when(vm.motionStyle) {
                            "Soft fade" -> alpha = 1f - distance * 0.35f * amount
                            "Subtle scale" -> { scaleX=1f-distance*0.05f*amount;scaleY=scaleX }
                            "Shared element" -> { scaleX=1f-distance*0.03f*amount;scaleY=scaleX;alpha=1f-distance*0.12f*amount }
                            "Smooth blur reveal" -> { alpha=1f-distance*0.25f*amount;scaleX=1f-distance*0.025f*amount;scaleY=scaleX }
                            "Layered depth" -> { translationX=rawOffset*size.width*0.08f*amount;scaleX=1f-distance*0.06f*amount;scaleY=scaleX }
                            "Gentle parallax" -> translationX=rawOffset*size.width*0.12f*amount
                            "Card expansion" -> { scaleX=0.92f+0.08f*(1f-distance*amount);scaleY=scaleX;alpha=1f-distance*0.18f*amount }
                            "Natural sheet" -> { translationY=distance*40f*amount;alpha=1f-distance*0.18f*amount }
                            "Magnetic snap" -> { scaleX=1f-distance*0.018f*amount;scaleY=scaleX }
                            "Content morph" -> { scaleX=1f-distance*0.04f*amount;scaleY=1f-distance*0.015f*amount;alpha=1f-distance*0.15f*amount }
                            "Keyboard lift" -> translationY=-distance*18f*amount
                            "Layer glide" -> { translationX=rawOffset*30f*amount;alpha=1f-distance*0.18f*amount }
                            else -> { scaleX=1f-distance*0.02f*amount;scaleY=scaleX }
                        }
                    }
                }
                Box(motionModifier) { when(page) {
                    0 -> Column(Modifier.fillMaxSize().background(bg)) {
                        if(vm.showFileInfo) Text("main.py  •  ${vm.runtimeVersion.substringBefore('\n')}",color=Color.Gray,fontSize=11.sp,modifier=Modifier.padding(10.dp,6.dp))
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
                                    vm.lineNumbers,vm.highlightCurrentLine,vm.customFontPath,
                                    listOf(vm.editorTextHex,vm.commentHex,vm.stringHex,vm.numberHex,vm.keywordHex,vm.functionHex,vm.variableHex))
                            },
                            modifier=Modifier.weight(1f).fillMaxWidth().background(bg)
                        )
                        if(vm.showToolbar) Row(Modifier.fillMaxWidth().height(vm.toolbarHeight.dp).horizontalScroll(rememberScrollState()).background(safeColor(vm.toolbarHex,0xFF050505)).padding(4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)){
                            listOf("Tab","(",")","[","]","{","}","\"","'",":","=").forEach{key->TextButton(onClick={if(key=="Tab" && editorView?.acceptGhostSuggestion()==true) Unit else editorView?.insertAtCursor(if(key=="Tab")"    " else key)}){Text(key)}}
                        }
                    }
                    1 -> Column(Modifier.fillMaxSize().padding(14.dp)) {
                        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                            Text("CONSOLE",color=accent,fontSize=16.sp,modifier=Modifier.weight(1f))
                            TextButton(onClick={vm.output=""}){Text("Clear")}
                        }
                        Text(vm.output.ifEmpty{"Ready"},color=safeColor(vm.consoleTextHex,0xFFE6F5FF),fontFamily=when(vm.fontName){"Sans"->FontFamily.SansSerif;"Serif"->FontFamily.Serif;else->FontFamily.Monospace},fontSize=vm.terminalFontSize.sp,modifier=Modifier.weight(1f).fillMaxWidth().background(safeColor(vm.consoleBackgroundHex,0xFF030303).copy(alpha=0.74f),glassShape).border(1.dp,glassEdge,glassShape).padding(12.dp).animateContentSize().verticalScroll(rememberScrollState()))
                        if(vm.waitingInput) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            TextField(vm.input,{vm.input=it},singleLine=true,modifier=Modifier.weight(1f),placeholder={Text("Program input")})
                            Button(onClick={vm.submitInput()}){Text("Send")}
                        }
                    }
                    2 -> Column(Modifier.fillMaxSize().padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                            Column(Modifier.weight(1f)){Text("HELPER",color=accent,fontSize=16.sp);Text("Only shares code when you allow it",color=Color.Gray,fontSize=10.sp)}
                            TextButton(onClick={showAiSettings=true}){Text(if(vm.hasAiKey)"Connection" else "Add key")}
                        }
                        Column(
                            Modifier.weight(1f).fillMaxWidth().background(glass,glassShape).border(1.dp,glassEdge,glassShape)
                                .padding(12.dp).verticalScroll(aiScroll),
                            verticalArrangement=Arrangement.spacedBy(10.dp)
                        ) {
                            if(vm.aiMessages.isEmpty()) Text(vm.aiReply,color=Color.Gray,fontSize=14.sp)
                            vm.aiMessages.forEach { message ->
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=if(message.fromUser) Arrangement.End else Arrangement.Start) {
                                    Surface(
                                        color=(if(message.fromUser) safeColor(vm.userBubbleHex,0xFF082F36) else safeColor(vm.helperBubbleHex,0xFF00E5FF)).copy(alpha=0.82f),
                                        contentColor=if(message.fromUser) Color(0xFF9FF8FF) else Color.Black,
                                        shape=androidx.compose.foundation.shape.RoundedCornerShape(vm.bubbleRadius.dp),
                                        modifier=Modifier.widthIn(max=vm.bubbleWidth.dp)
                                    ) {
                                        MarkdownMessage(
                                            message.text,
                                            if(message.fromUser) Color(0xFF9FF8FF) else Color.Black,
                                            Modifier.padding(horizontal=14.dp,vertical=11.dp)
                                        )
                                    }
                                }
                            }
                            if(vm.aiBusy && vm.aiMessages.isNotEmpty()) Text("Thinking…",color=accent,fontSize=12.sp)
                        }
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
                    else -> Column(Modifier.fillMaxSize().padding(horizontal=18.dp,vertical=12.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                            if(settingsSection!="Overview") TextButton(onClick={settingsSection="Overview"},contentPadding=PaddingValues(end=12.dp)){Text("‹ Back")}
                            Column(Modifier.weight(1f)){Text(if(settingsSection=="Overview") "SETTINGS" else settingsSection.uppercase(),color=accent,fontSize=18.sp);Text(if(settingsSection=="Overview") "Make PyDroid X yours" else "Focused controls",color=Color.Gray,fontSize=11.sp)}
                        }
                        TextField(vm.settingsQuery,{vm.settingsQuery=it},singleLine=true,modifier=Modifier.fillMaxWidth(),placeholder={Text("Search every setting…")})
                        if(settingsSection=="Overview" && vm.settingsQuery.isBlank()){
                            SettingsCategory("Appearance","Theme, accents and component colors",accent){settingsSection="Appearance"}
                            SettingsCategory("Editor","Text, cursor, autocomplete and saving",accent){settingsSection="Editor"}
                            SettingsCategory("Fonts","100 downloadable typefaces",accent){settingsSection="Fonts"}
                            SettingsCategory("Motion","13 quiet interface animations",accent){settingsSection="Motion"}
                            SettingsCategory("Layout","Header, tabs, toolbar and spacing",accent){settingsSection="Layout"}
                            SettingsCategory("Console & Helper","Output and chat appearance",accent){settingsSection="Console & Helper"}
                            SettingsCategory("System","Runtime and interface switches",accent){settingsSection="System"}
                        }
                        if(settingsSection=="Appearance" || vm.settingsQuery.isNotBlank()){
                        Text("ACCENT COLOR",color=accent,fontSize=12.sp)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            listOf("#00E5FF","#0A84FF","#30D158","#BF5AF2","#FF375F","#FFD60A","#FF9F0A","#FFFFFF").forEach{hex->
                                val swatch=safeColor(hex,0xFF00E5FF)
                                FilterChip(selected=vm.accentHex==hex,onClick={vm.accentHex=hex;vm.saveAppearance()},label={Box(Modifier.size(22.dp).background(swatch,androidx.compose.foundation.shape.CircleShape))})
                            }
                        }
                        Text("BACKGROUND",color=accent,fontSize=12.sp)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            listOf("#000000","#030303","#080B10","#0B0F14","#101014","#111827","#160B1C").forEach{hex->
                                val swatch=safeColor(hex,0xFF000000)
                                FilterChip(selected=vm.backgroundHex==hex,onClick={vm.backgroundHex=hex;vm.saveAppearance()},label={Box(Modifier.size(22.dp).background(swatch,androidx.compose.foundation.shape.CircleShape))})
                            }
                        }
                        Text("EDITOR TOKEN COLORS",color=accent,fontSize=12.sp)
                        ColorSetting("Editor text",vm.editorTextHex){vm.editorTextHex=it;vm.saveAppearance()}
                        ColorSetting("Comments",vm.commentHex){vm.commentHex=it;vm.saveAppearance()}
                        ColorSetting("Strings",vm.stringHex){vm.stringHex=it;vm.saveAppearance()}
                        ColorSetting("Numbers",vm.numberHex){vm.numberHex=it;vm.saveAppearance()}
                        ColorSetting("Keywords",vm.keywordHex){vm.keywordHex=it;vm.saveAppearance()}
                        ColorSetting("Functions",vm.functionHex){vm.functionHex=it;vm.saveAppearance()}
                        ColorSetting("Variables",vm.variableHex){vm.variableHex=it;vm.saveAppearance()}
                        }
                        if(settingsSection=="Console & Helper" || vm.settingsQuery.isNotBlank()){
                        Text("COMPONENT COLORS",color=accent,fontSize=12.sp)
                        ColorSetting("Console text",vm.consoleTextHex){vm.consoleTextHex=it;vm.saveAppearance()}
                        ColorSetting("Console background",vm.consoleBackgroundHex){vm.consoleBackgroundHex=it;vm.saveAppearance()}
                        ColorSetting("Keyboard toolbar",vm.toolbarHex){vm.toolbarHex=it;vm.saveAppearance()}
                        ColorSetting("Tab bar",vm.tabBarHex){vm.tabBarHex=it;vm.saveAppearance()}
                        ColorSetting("Your chat bubble",vm.userBubbleHex){vm.userBubbleHex=it;vm.saveAppearance()}
                        ColorSetting("Helper bubble",vm.helperBubbleHex){vm.helperBubbleHex=it;vm.saveAppearance()}
                        ColorSetting("Run button",vm.runButtonHex){vm.runButtonHex=it;vm.saveAppearance()}
                        ColorSetting("Stop button",vm.stopButtonHex){vm.stopButtonHex=it;vm.saveAppearance()}
                        Text("Chat bubble corners  ${vm.bubbleRadius.toInt()} dp",color=text)
                        Slider(vm.bubbleRadius,{vm.bubbleRadius=it;vm.saveAppearance()},valueRange=0f..36f)
                        Text("Chat bubble width  ${vm.bubbleWidth.toInt()} dp",color=text)
                        Slider(vm.bubbleWidth,{vm.bubbleWidth=it;vm.saveAppearance()},valueRange=180f..420f)
                        Text("Terminal font  ${vm.terminalFontSize.toInt()} sp",color=text)
                        Slider(vm.terminalFontSize,{vm.terminalFontSize=it;vm.saveAppearance()},valueRange=10f..24f,steps=13)
                        }
                        if(settingsSection=="Motion" || vm.settingsQuery.isNotBlank()){
                        Text("MOTION",color=accent,fontSize=12.sp)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){
                            listOf("Fluid spring","Soft fade","Subtle scale","Shared element","Smooth blur reveal","Layered depth","Gentle parallax","Card expansion","Natural sheet","Magnetic snap","Interactive swipe","Content morph","Keyboard lift").forEach{motion->
                                FilterChip(selected=vm.motionStyle==motion,onClick={vm.motionStyle=motion;vm.saveAppearance()},label={Text(motion)})
                            }
                        }
                        Text("Motion intensity  ${(vm.motionIntensity*100).toInt()}%",color=text)
                        Slider(vm.motionIntensity,{vm.motionIntensity=it;vm.saveAppearance()},valueRange=0.1f..1f)
                        SettingSwitch("Interface motion",vm.motionEnabled){vm.motionEnabled=it;vm.saveAppearance()}
                        }
                        if(settingsSection=="Layout" || vm.settingsQuery.isNotBlank()){
                        Text("Header height  ${vm.headerHeight.toInt()} dp",color=text)
                        Slider(vm.headerHeight,{vm.headerHeight=it;vm.saveAppearance()},valueRange=48f..110f)
                        Text("Tab bar height  ${vm.tabHeight.toInt()} dp",color=text)
                        Slider(vm.tabHeight,{vm.tabHeight=it;vm.saveAppearance()},valueRange=32f..72f)
                        Text("Keyboard toolbar height  ${vm.toolbarHeight.toInt()} dp",color=text)
                        Slider(vm.toolbarHeight,{vm.toolbarHeight=it;vm.saveAppearance()},valueRange=38f..90f)
                        Text("Page dot size  ${vm.pageDotSize.toInt()} dp",color=text)
                        Slider(vm.pageDotSize,{vm.pageDotSize=it;vm.saveAppearance()},valueRange=3f..16f)
                        SettingSwitch("Show top header",vm.showHeader){vm.showHeader=it;vm.saveAppearance()}
                        SettingSwitch("Show file information",vm.showFileInfo){vm.showFileInfo=it;vm.saveAppearance()}
                        Text("Interface scale  ${(vm.uiScale*100).toInt()}%",color=text)
                        Slider(vm.uiScale,{vm.uiScale=it;vm.saveAppearance()},valueRange=0.85f..1.25f,steps=7)
                        Text("Editor padding  ${vm.editorPadding.toInt()} px",color=text)
                        Slider(vm.editorPadding,{vm.editorPadding=it;vm.saveAppearance()},valueRange=0f..48f,steps=11)
                        }
                        if(settingsSection=="Editor" || vm.settingsQuery.isNotBlank()){
                        Text("Editor font  ${vm.editorFontSize.toInt()} sp",color=text)
                        Slider(vm.editorFontSize,{vm.editorFontSize=it;vm.saveAppearance()},valueRange=12f..28f,steps=15)
                        Text("Line spacing  ${"%.2f".format(vm.lineSpacing)}×",color=text)
                        Slider(vm.lineSpacing,{vm.lineSpacing=it;vm.saveAppearance()},valueRange=0.9f..1.8f)
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
                        }
                        if(settingsSection=="Fonts" || (vm.settingsQuery.isNotBlank() && FONT_VAULT.any{it.first.contains(vm.settingsQuery,true)})){
                        Text("Font family",color=text)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Monospace","Sans","Serif").forEach{font->FilterChip(selected=vm.fontName==font,onClick={vm.fontName=font;vm.customFontPath="";vm.saveAppearance()},label={Text(font)})}}
                        Text("FONT VAULT  •  ${FONT_VAULT.size} REAL FONTS",color=accent,fontSize=12.sp)
                        Text(vm.fontStatus,color=Color.Gray,fontSize=11.sp)
                        FONT_VAULT.filter { vm.settingsQuery.isBlank() || it.first.contains(vm.settingsQuery,true) }.forEach { font ->
                            Surface(color=Color.White.copy(alpha=0.055f),shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth().border(1.dp,Color.White.copy(alpha=0.12f),androidx.compose.foundation.shape.RoundedCornerShape(14.dp))){
                            Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                                Column(Modifier.weight(1f)){Text(font.first,color=text,fontSize=14.sp);Text("Google Fonts · OFL",color=Color.DarkGray,fontSize=9.sp)}
                                TextButton(onClick={vm.installVaultFont(context,font.first,font.second)},enabled=!vm.fontStatus.startsWith("Downloading")){Text(if(vm.fontName==font.first)"Installed" else "Download")}
                            }}
                        }
                        }
                        if(settingsSection=="System" || vm.settingsQuery.isNotBlank()){
                        SettingSwitch("Programming toolbar",vm.showToolbar){vm.showToolbar=it;vm.saveAppearance()}
                        SettingSwitch("Page indicator dots",vm.showPageDots){vm.showPageDots=it;vm.saveAppearance()}
                        HorizontalDivider(color=Color(0xFF202020))
                        Text("Swipe left or right anywhere outside active text editing to move between pages.",color=Color.Gray,fontSize=12.sp)
                        Text("Python  ${vm.runtimeVersion.substringBefore('\n')}",color=Color.Gray,fontSize=11.sp)
                        }
                    }
                } }
            }
            if(vm.showPageDots) Row(Modifier.fillMaxWidth().height(22.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                repeat(4){index->Box(Modifier.padding(horizontal=3.dp).size(if(index==pager.currentPage)vm.pageDotSize.dp else (vm.pageDotSize*0.62f).dp).background(if(index==pager.currentPage)accent else Color.DarkGray,androidx.compose.foundation.shape.CircleShape))}
            }
            Text("w astro",color=Color(0xFF181818),fontSize=7.sp,modifier=Modifier.fillMaxWidth().padding(bottom=2.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
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

@Composable private fun SettingsCategory(title:String,subtitle:String,accent:Color,onClick:()->Unit){
    val shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
    Surface(
        onClick=onClick,
        color=Color.White.copy(alpha=0.06f),
        shape=shape,
        modifier=Modifier.fillMaxWidth().border(1.dp,Color.White.copy(alpha=0.14f),shape)
    ){
        Row(Modifier.padding(horizontal=16.dp,vertical=15.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
            Box(Modifier.size(9.dp).background(accent,androidx.compose.foundation.shape.CircleShape))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)){Text(title,color=Color(0xFFE6F5FF),fontSize=15.sp);Text(subtitle,color=Color.Gray,fontSize=11.sp)}
            Text("›",color=Color.Gray,fontSize=24.sp)
        }
    }
}

private fun markdownInline(source:String):AnnotatedString=buildAnnotatedString {
    var i=0
    while(i<source.length){
        when {
            source.startsWith("**",i) -> {
                val end=source.indexOf("**",i+2)
                if(end>i){withStyle(SpanStyle(fontWeight=FontWeight.Bold)){append(source.substring(i+2,end))};i=end+2}else{append("**");i+=2}
            }
            source[i]=='`' -> {
                val end=source.indexOf('`',i+1)
                if(end>i){withStyle(SpanStyle(fontFamily=FontFamily.Monospace,background=Color(0x33000000))){append(source.substring(i+1,end))};i=end+1}else{append('`');i++}
            }
            source[i]=='*' -> {
                val end=source.indexOf('*',i+1)
                if(end>i){withStyle(SpanStyle(fontStyle=FontStyle.Italic)){append(source.substring(i+1,end))};i=end+1}else{append('*');i++}
            }
            else -> {append(source[i]);i++}
        }
    }
}

@Composable private fun MarkdownMessage(source:String,color:Color,modifier:Modifier=Modifier){
    Column(modifier,verticalArrangement=Arrangement.spacedBy(5.dp)){
        var inCode=false
        val code=StringBuilder()
        source.lines().forEach { raw ->
            val line=raw.trimEnd()
            if(line.trimStart().startsWith("```")){
                if(inCode){
                    Surface(color=Color(0x22000000),shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp),modifier=Modifier.fillMaxWidth()){
                        Text(code.toString().trimEnd(),color=color,fontFamily=FontFamily.Monospace,fontSize=13.sp,modifier=Modifier.padding(9.dp))
                    }
                    code.clear()
                }
                inCode=!inCode
            }else if(inCode){
                code.appendLine(raw)
            }else if(line.isBlank()){
                Spacer(Modifier.height(3.dp))
            }else{
                val trimmed=line.trimStart()
                val heading=trimmed.takeWhile{it=='#'}.length.coerceAtMost(3)
                val bullet=trimmed.startsWith("- ")||trimmed.startsWith("* ")
                val clean=when{heading>0->trimmed.drop(heading).trimStart();bullet->"• "+trimmed.drop(2);else->line}
                Text(markdownInline(clean),color=color,fontSize=if(heading>0)(19-heading).sp else 14.sp,fontWeight=if(heading>0)FontWeight.Bold else FontWeight.Normal)
            }
        }
        if(inCode&&code.isNotEmpty()) Surface(color=Color(0x22000000),shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp),modifier=Modifier.fillMaxWidth()){
            Text(code.toString().trimEnd(),color=color,fontFamily=FontFamily.Monospace,fontSize=13.sp,modifier=Modifier.padding(9.dp))
        }
    }
}

@Composable private fun ColorSetting(label:String,value:String,onChange:(String)->Unit){
    val colors=listOf("#FFFFFF","#D4D4D4","#6A9955","#CE9178","#B5CEA8","#C586C0","#DCDCAA","#9CDCFE","#00E5FF","#0A84FF","#30D158","#BF5AF2","#FF375F","#FFD60A","#FF9F0A","#000000")
    Column(verticalArrangement=Arrangement.spacedBy(5.dp)){
        Text("$label  $value",color=Color(0xFFE6F5FF),fontSize=13.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            colors.forEach{hex->
                val swatch=runCatching{Color(AndroidColor.parseColor(hex))}.getOrDefault(Color.White)
                FilterChip(selected=value==hex,onClick={onChange(hex)},label={Box(Modifier.size(18.dp).background(swatch,androidx.compose.foundation.shape.CircleShape))})
            }
        }
    }
}
