package com.pydroidx.app

import android.os.Bundle
import android.net.Uri
import android.app.Activity
import android.animation.ValueAnimator
import android.content.Context
import android.app.ActivityManager
import android.content.ClipData
import android.widget.Toast
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaquo.python.Python
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import kotlin.math.absoluteValue
import java.net.URL

data class AiMessage(val fromUser: Boolean, val text: String)
data class AiSlotConfig(val index: Int, val label: String, val provider: String, val endpoint: String, val model: String, val key: String)
data class CodeDiagnostic(val start: Int, val end: Int, val message: String, val fatal: Boolean = false)
data class RuntimeIssue(
    val title: String, val explanation: String, val line: Int, val codeLine: String,
    val hint: String, val details: String, val fileName: String,
    val replaceFrom: String = "", val replaceTo: String = ""
)
data class SavedCode(val name: String, val modified: Long, val fileName: String)
data class PendingCodeChange(val code: String, val fileName: String, val sourceSnapshot: String)

private fun issueLineNumber(source: String, offset: Int): Int =
    source.take(offset.coerceIn(0, source.length)).count { it == '\n' } + 1

private fun issueLineText(source: String, offset: Int): String {
    val safeOffset = offset.coerceIn(0, source.length)
    val start = (source.lastIndexOf('\n', (safeOffset - 1).coerceAtLeast(0)) + 1).coerceAtMost(safeOffset)
    val end = source.indexOf('\n', safeOffset).let { if (it < 0) source.length else it }
    return source.substring(start, end)
}

private fun issueWhy(message: String): String = when {
    message.startsWith("Undefined name:", ignoreCase = true) ->
        "Python could not find this name in the current scope, imports, or built-ins."
    "indent" in message.lowercase() ->
        "Python uses indentation to decide which statements belong inside a block."
    "unterminated" in message.lowercase() || "never closed" in message.lowercase() ->
        "A string or bracket was opened but Python reached the end before it was closed."
    else -> "Python's parser could not match this part of the file to valid Python syntax."
}

private fun issueFix(message: String): String = when {
    message.startsWith("Undefined name:", ignoreCase = true) ->
        "Check the spelling. If it is correct, define the name or import it before this line."
    "indent" in message.lowercase() ->
        "Align this line with its block and indent the block body consistently."
    "unterminated" in message.lowercase() || "never closed" in message.lowercase() ->
        "Add the missing closing quote or bracket, then check the surrounding line."
    "expected ':'" in message.lowercase() ->
        "Add a colon after the if, for, while, def, or class header."
    else -> "Read the parser message, then check the highlighted token and the line before it."
}

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
    @Volatile var code = "print(\"Hello world!\")\n"
    var currentFileName by mutableStateOf("main.py")
    val savedCodes = mutableStateListOf<SavedCode>()
    val deletedFiles = mutableStateListOf<TrashedProjectFile>()
    val openFileTabs = mutableStateListOf<String>()
    val projectNames = mutableStateListOf<String>()
    var currentProjectName by mutableStateOf("default")
    var editorRevision by mutableIntStateOf(0)
        private set
    var output by mutableStateOf("")
    var running by mutableStateOf(false)
    var codeDiagnostics by mutableStateOf<List<CodeDiagnostic>>(emptyList())
    var runtimeIssue by mutableStateOf<RuntimeIssue?>(null)
    var projectSearchResults by mutableStateOf<List<ProjectSearchHit>>(emptyList())
    var projectSearchBusy by mutableStateOf(false)
    private var projectSearchToken = 0
    var waitingInput by mutableStateOf(false)
    var input by mutableStateOf("")
    val inputHistory = ConsoleInputHistory()
    var runtimeVersion by mutableStateOf("Loading Python…")
    var saveError by mutableStateOf<String?>(null)
    var consoleMode by mutableStateOf("Python")
    var aiPrompt by mutableStateOf("")
    val aiMessages = mutableStateListOf<AiMessage>()
    var aiBusy by mutableStateOf(false)
    var aiFallbackNotice by mutableStateOf<String?>(null)
    var aiProgress by mutableStateOf<String?>(null)
    var pendingCode by mutableStateOf<PendingCodeChange?>(null)
    var teachingOffer by mutableStateOf<String?>(null)
    var showCodeNotice by mutableStateOf(false)
    var showTeachingNotice by mutableStateOf(false)
    private var assistantReplyCount = 0
    var localContextSize by mutableIntStateOf(4096)
    var localThreads by mutableIntStateOf(4)
    var localResponseTokens by mutableIntStateOf(1024)
    var aiTestStatus by mutableStateOf<String?>(null)
    var attachedFileName by mutableStateOf<String?>(null)
    var attachedFileText by mutableStateOf<String?>(null)
    var aiEndpoint by mutableStateOf("https://api.openai.com/v1/chat/completions")
    var aiModel by mutableStateOf("gpt-4o-mini")
    var aiProvider by mutableStateOf("Auto")
    var localImportStatus by mutableStateOf<String?>(null)
    private lateinit var appContext: Context
    var ai2Endpoint by mutableStateOf("https://api.openai.com/v1/chat/completions")
    var ai2Model by mutableStateOf("")
    var ai2Provider by mutableStateOf("Auto")
    var ai3Endpoint by mutableStateOf("https://api.openai.com/v1/chat/completions")
    var ai3Model by mutableStateOf("")
    var ai3Provider by mutableStateOf("Auto")
    var shareCode by mutableStateOf(false)
    var editorFontSize by mutableFloatStateOf(16f)
    var tabWidth by mutableIntStateOf(4)
    var terminalFontSize by mutableFloatStateOf(13f)
    var uiScale by mutableFloatStateOf(1f)
    var wordWrap by mutableStateOf(true)
    var syntaxHighlighting by mutableStateOf(true)
    var autoSave by mutableStateOf(true)
    var fontName by mutableStateOf("Monospace")
    var lineSpacing by mutableFloatStateOf(1.12f)
    var editorPadding by mutableFloatStateOf(20f)
    var typingAnimation by mutableStateOf(true)
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
    var accentHex by mutableStateOf("#FFFFFF")
    var backgroundHex by mutableStateOf("#0B0F14")
    var motionStyle by mutableStateOf("Aurora glide")
    var motionIntensity by mutableFloatStateOf(0.7f)
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
    var consoleBackgroundHex by mutableStateOf("#080B0F")
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
    @Volatile private var stopRequested = false
    private var autosaveJob: Job? = null
    private var namingJob: Job? = null
    private val editorFileStates = mutableMapOf<String, EditorFileState>()
    private val editorStateJobs = mutableMapOf<String, Job>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val outputBuffer = ConsoleOutputBuffer()
    private val outputFlushScheduled = AtomicBoolean(false)
    private val outputFlushRunnable = Runnable {
        outputFlushScheduled.set(false)
        output = outputBuffer.visibleTail()
    }
    lateinit var projectDir: File
    private lateinit var projectsRoot: File
    private lateinit var aiKeys: SecureAiKeyStore
    private lateinit var settings: android.content.SharedPreferences

    private fun readProjectText(file: File): String = AtomicFile(file).openRead()
        .bufferedReader(Charsets.UTF_8).use { it.readText() }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        aiKeys = SecureAiKeyStore(context.applicationContext)
        settings = context.getSharedPreferences("ide_settings", Context.MODE_PRIVATE)
        // Migrate old defaults once; subsequent custom appearance choices are respected.
        if (!settings.getBoolean("mobile_editor_v2", false)) {
            val edit = settings.edit().putBoolean("mobile_editor_v2", true).putBoolean("page_dots", false)
            listOf("background_hex" to "#11161D", "console_background_hex" to "#11161D",
                "toolbar_hex" to "#181F29", "tab_bar_hex" to "#181F29").forEach { (key, value) ->
                if (settings.getString(key, "").orEmpty().uppercase() in listOf("", "#000000", "#030303", "#050505", "#0B0F14", "#080B0F")) edit.putString(key, value)
            }
            edit.apply()
        }
        editorFontSize = settings.getFloat("editor_font", 16f)
        tabWidth = settings.getInt("tab_width", 4).coerceIn(1, 8)
        terminalFontSize = settings.getFloat("terminal_font", 13f)
        uiScale = settings.getFloat("ui_scale", 1f)
        wordWrap = settings.getBoolean("word_wrap", true)
        syntaxHighlighting = settings.getBoolean("syntax", true)
        autoSave = settings.getBoolean("autosave", true)
        fontName = settings.getString("font", "Monospace") ?: "Monospace"
        lineSpacing = settings.getFloat("line_spacing", 1.12f)
        editorPadding = settings.getFloat("editor_padding", 20f)
        typingAnimation = settings.getBoolean("typing_animation", true)
        animationDuration = settings.getFloat("animation_duration", 120f)
        highlightDelay = settings.getFloat("highlight_delay", 220f)
        autosaveDelay = settings.getFloat("autosave_delay", 500f)
        showToolbar = settings.getBoolean("toolbar", true)
        showPageDots = settings.getBoolean("page_dots", true)
        cursorStyle = settings.getString("cursor", "Cyan") ?: "Cyan"
        autocomplete = settings.getBoolean("autocomplete", true)
        ghostBrightness = settings.getFloat("ghost_brightness", 0.48f)
        localContextSize = settings.getInt("local_context",4096).coerceIn(512,8192)
        localThreads = settings.getInt("local_threads",4).coerceIn(2,8)
        localResponseTokens = settings.getInt("local_tokens",1024).coerceIn(128,2048)
        aiProvider = settings.getString("ai_provider", "Auto") ?: "Auto"
        ai2Provider = settings.getString("ai2_provider", "Auto") ?: "Auto"
        ai3Provider = settings.getString("ai3_provider", "Auto") ?: "Auto"
        fun storedEndpoint(key: String, provider: String): String =
            AiEndpointPolicy.normalizeForStorage(provider, settings.getString(key, null).orEmpty())
        aiEndpoint = storedEndpoint("ai_endpoint", aiProvider)
        aiModel = settings.getString("ai_model", "gpt-4o-mini")
            ?.trim()?.takeIf { it.isNotEmpty() } ?: "gpt-4o-mini"
        ai2Endpoint = storedEndpoint("ai2_endpoint", ai2Provider)
        ai2Model = settings.getString("ai2_model", "") ?: ""
        ai3Endpoint = storedEndpoint("ai3_endpoint", ai3Provider)
        ai3Model = settings.getString("ai3_model", "") ?: ""
        lineNumbers = settings.getBoolean("line_numbers", true)
        highlightCurrentLine = settings.getBoolean("current_line", true)
        accentHex = settings.getString("accent_hex", "#FFFFFF") ?: "#FFFFFF"
        backgroundHex = settings.getString("background_hex", "#11161D") ?: "#11161D"
        motionStyle = settings.getString("motion_style", "Aurora glide") ?: "Aurora glide"
        motionIntensity = settings.getFloat("motion_intensity", 0.7f)
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
        consoleBackgroundHex=settings.getString("console_background_hex","#11161D")?:"#11161D"
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
        projectsRoot = File(context.filesDir, "projects").apply { mkdirs() }
        currentProjectName = ProjectWorkspace.safeName(settings.getString("current_project","default") ?: "default")
        projectDir = File(projectsRoot,currentProjectName).apply { mkdirs() }
        val legacyStarter = "print(\"Hello Andrew\")\nname = input(\"What is your name? \")\nprint(\"Hello\", name)\n"
        val existing = projectDir.listFiles()?.filter { it.isFile && it.extension.equals("py",true) }.orEmpty()
        currentFileName = settings.getString("current_file","main.py") ?: "main.py"
        var current = File(projectDir,currentFileName)
        if (!current.exists()) current = existing.maxByOrNull { it.lastModified() } ?: File(projectDir,"main.py")
        if (!current.exists() && !File(current.path + ".bak").exists()) current.writeText(code)
        currentFileName = current.name
        code = readProjectText(current)
        if (code == legacyStarter) {
            code = "print(\"Hello world!\")\n"
            current.writeText(code)
        }
        settings.edit().putString("current_file",currentFileName).putString("current_project",currentProjectName).apply()
        refreshProjects()
        refreshSaved()
        restoreOpenTabs()
        editorRevision++
        thread {
            val version = runCatching { Python.getInstance().getModule("runner").callAttr("version").toString() }
                .getOrDefault("Python unavailable")
            mainHandler.post { runtimeVersion = version }
        }
    }
    fun providerForSlot(slot: Int) = when(slot) { 1 -> ai2Provider; 2 -> ai3Provider; else -> aiProvider }
    fun endpointForSlot(slot: Int) = when(slot) { 1 -> ai2Endpoint; 2 -> ai3Endpoint; else -> aiEndpoint }
    fun modelForSlot(slot: Int) = when(slot) { 1 -> ai2Model; 2 -> ai3Model; else -> aiModel }
    fun saveLocalPerformance(contextSize: Int = localContextSize, threads: Int = localThreads, responseTokens: Int = localResponseTokens) {
        localContextSize = contextSize
        localThreads = threads
        localResponseTokens = responseTokens
        settings.edit().putInt("local_context",contextSize).putInt("local_threads",threads).putInt("local_tokens",responseTokens).apply()
    }
    fun localModelForSlot(slot: Int): String = settings.getString("local_model_$slot", "").orEmpty()
    fun slotConfigured(slot: Int) = if (providerForSlot(slot) == "On-device") File(localModelForSlot(slot)).isFile else !aiKeys.load(slot).isNullOrBlank()
    fun importLocalModel(uri: Uri, slot: Int) {
        localImportStatus = "Importing model…"
        thread(name="PY4U-model-import") {
            val result = runCatching { LocalAiRuntime.importModel(appContext, uri) { status -> mainHandler.post { localImportStatus=status } } }
            mainHandler.post {
                result.onSuccess { file ->
                    settings.edit().putString("local_model_$slot", file.absolutePath).apply()
                    localImportStatus = "Ready: ${file.name} (${file.length() / 1048576} MB)"
                }.onFailure { localImportStatus = "Import failed: ${it.message}" }
            }
        }
    }
    fun removeLocalModel(slot: Int) {
        settings.edit().remove("local_model_$slot").apply()
        localImportStatus = "Model removed from this AI slot"
    }

    fun saveAiSettings(key: String, endpoint: String, model: String, provider: String, slot: Int = 0) {
        if (key.isNotBlank()) aiKeys.save(key, slot)
        val safeEndpoint = if (provider == "On-device") "" else AiEndpointPolicy.normalizeForStorage(provider, endpoint)
        val safeModel = model.trim()
        when(slot) {
            1 -> { ai2Provider=provider; ai2Endpoint=safeEndpoint; ai2Model=safeModel }
            2 -> { ai3Provider=provider; ai3Endpoint=safeEndpoint; ai3Model=safeModel }
            else -> { aiProvider=provider; aiEndpoint=safeEndpoint; aiModel=safeModel.ifEmpty { "gpt-4o-mini" } }
        }
        val prefix = when(slot) { 1 -> "ai2"; 2 -> "ai3"; else -> "ai" }
        settings.edit().putString("${prefix}_endpoint",safeEndpoint)
            .putString("${prefix}_model",if(slot==0) aiModel else safeModel)
            .putString("${prefix}_provider",provider).apply()
    }

    fun removeAiKey(slot: Int = 0) {
        aiKeys.clear(slot)
    }

    private fun configuredAiSlots(): List<AiSlotConfig> = listOf(
        AiSlotConfig(0,"Main",aiProvider,aiEndpoint,aiModel,aiKeys.load(0).orEmpty()),
        AiSlotConfig(1,"Second",ai2Provider,ai2Endpoint,ai2Model,aiKeys.load(1).orEmpty()),
        AiSlotConfig(2,"Third",ai3Provider,ai3Endpoint,ai3Model,aiKeys.load(2).orEmpty())
    ).filter { if (it.provider == "On-device") File(localModelForSlot(it.index)).isFile else it.key.isNotBlank() }

    private fun trimAiMessages() {
        while (aiMessages.size > 80) aiMessages.removeAt(0)
    }

    fun attachFile(name: String, content: String) {
        attachedFileName = name.takeLast(80)
        attachedFileText = content.take(100_000)
    }
    fun clearAttachment() {
        attachedFileName = null
        attachedFileText = null
    }

    fun askAi(testOnly: Boolean = false, preferredSlot: Int? = null) {
        if (aiBusy) return
        if (!testOnly) { showCodeNotice=false; showTeachingNotice=false }
        aiProgress=null
        var slots = configuredAiSlots()
        if (preferredSlot != null) slots = slots.filter { it.index == preferredSlot }
        if (slots.isEmpty()) {
            if (testOnly) aiTestStatus = "Choose an API key or import an on-device GGUF model"
            else aiMessages.add(AiMessage(false, "Open AI settings and add an API key or GGUF model"))
            return
        }
        val typedQuestion = if (testOnly) "Reply with: Connection successful" else aiPrompt.trim()
        if (typedQuestion.isBlank() && attachedFileText.isNullOrBlank()) return
        val attachmentNameSnapshot = attachedFileName
        val attachmentTextSnapshot = attachedFileText
        val fileNameSnapshot = currentFileName
        val codeSnapshot = code
        val question = if (testOnly) typedQuestion else buildString {
            append(typedQuestion.ifBlank { "Review the attached file" })
            if (shareCode) {
                append("\n\nIDE context — current file: ").append(fileNameSnapshot)
                append("\nProject files: ")
                append(projectDir.listFiles()?.asSequence()?.filter { it.isFile && it.extension.equals("py",true) }
                    ?.map { it.name }?.take(40)?.joinToString(", ").orEmpty())
                if (codeDiagnostics.isNotEmpty()) {
                    append("\nProblems: ")
                    codeDiagnostics.take(8).forEach { issue ->
                        append("\nLine ").append(issueLineNumber(codeSnapshot,issue.start))
                            .append(": ").append(issue.message.take(150))
                    }
                }
                val consoleContext = outputBuffer.visibleTail(1600)
                if (consoleContext.isNotBlank()) append("\nRecent Console output:\n").append(consoleContext)
            }
            if (!attachmentTextSnapshot.isNullOrBlank()) {
                append("\n\nAttached file: ").append(attachmentNameSnapshot ?: "attachment")
                append("\n\u0060\u0060\u0060\n").append(attachmentTextSnapshot).append("\n\u0060\u0060\u0060")
            }
        }
        if (!testOnly) {
            val visibleQuestion = typedQuestion.ifBlank { "Review this attachment" } +
                if (attachmentNameSnapshot != null) "\nAttached  $attachmentNameSnapshot" else ""
            aiMessages.add(AiMessage(true, visibleQuestion))
            aiPrompt = ""
            clearAttachment()
            aiMessages.add(AiMessage(false, ""))
            trimAiMessages()
        } else {
            aiTestStatus = "Testing connection…"
        }
        aiBusy = true
        thread(name = "PY4U-AI") {
            val historySnapshot = if (!testOnly) aiMessages.dropLast(2).toList() else emptyList()
            val result = runCatching {
                var lastFailure: Throwable? = null
                slots.forEachIndexed { index, slot ->
                    try {
                        if (index > 0) viewModelScope.launch {
                            val message = "${slots[index-1].label} AI unavailable • switching to ${slot.label} AI"
                            aiProgress = message
                            if ((assistantReplyCount+1) % 3 == 0) aiFallbackNotice = message
                        }
                        return@runCatching if (slot.provider == "On-device") LocalAiRuntime.chat(
                            appContext, localModelForSlot(slot.index), question,
                            if (!testOnly && shareCode) codeSnapshot else null, historySnapshot,
                            localContextSize, localThreads, localResponseTokens,
                            onStatus={ status -> viewModelScope.launch { aiProgress="${slot.label}: $status" } },
                            onPartial={ partial -> viewModelScope.launch { if (!testOnly && aiMessages.isNotEmpty()) aiMessages[aiMessages.lastIndex] = AiMessage(false, partial) } }
                        ) else AiClient.chat(
                            providerSetting=slot.provider,
                            endpoint=slot.endpoint,
                            apiKey=slot.key,
                            modelSetting=slot.model,
                            prompt=question,
                            code=if (!testOnly && shareCode) codeSnapshot else null,
                            history=historySnapshot,
                            revealDelayMs=if (typingAnimation) (animationDuration / 6.7f).toLong().coerceIn(4L, 120L) else 0L,
                            onStatus={ status -> viewModelScope.launch { aiProgress="${slot.label}: $status" } }
                        ) { partial ->
                            viewModelScope.launch {
                                if (!testOnly && aiMessages.isNotEmpty()) {
                                    aiMessages[aiMessages.lastIndex] = AiMessage(false, partial)
                                }
                            }
                        }
                    } catch (failure: Throwable) {
                        lastFailure = failure
                    }
                }
                throw lastFailure ?: IllegalStateException("No configured AI connection worked")
            }
            val succeeded = result.isSuccess
            val answer = result.getOrElse { "AI error: ${it.message ?: "All three AI connections failed"}" }
            viewModelScope.launch {
                val cleanAnswer = answer.replace(Regex("\\s*\\[TEACH:[^]]+]",RegexOption.IGNORE_CASE),"").trimEnd()
                if (testOnly) {
                    aiTestStatus = cleanAnswer
                } else {
                    if (aiMessages.isNotEmpty()) aiMessages[aiMessages.lastIndex] = AiMessage(false,cleanAnswer)
                    if (succeeded) {
                        assistantReplyCount++
                        extractPythonFile(answer)?.let { proposed ->
                            if (currentFileName == fileNameSnapshot && code == codeSnapshot) {
                                pendingCode = PendingCodeChange(proposed, fileNameSnapshot, codeSnapshot)
                                showCodeNotice = assistantReplyCount % 3 == 0
                            } else {
                                aiMessages.add(AiMessage(false, "Code changed while Astro was working. Ask again before applying the edit."))
                            }
                        }
                        teachingOffer=extractTeachingOffer(answer)
                        showTeachingNotice = teachingOffer != null && assistantReplyCount % 3 == 0 && pendingCode == null
                    }
                }
                aiBusy=false
                aiProgress=null
            }
        }
    }

    private fun extractPythonFile(answer: String): String? {
        val match = Regex("```(?:python|py)\\s*\\n([\\s\\S]*?)```", RegexOption.IGNORE_CASE).find(answer) ?: return null
        return match.groupValues[1].trimEnd().takeIf { it.isNotBlank() }
    }
    private fun extractTeachingOffer(answer: String): String? =
        Regex("\\[TEACH:([^]]+)]", RegexOption.IGNORE_CASE).find(answer)?.groupValues?.get(1)?.trim()

    fun applyPendingCode() {
        val change = pendingCode ?: return
        if (currentFileName != change.fileName || code != change.sourceSnapshot) {
            pendingCode = null
            aiMessages.add(AiMessage(false, "That edit is stale because the file changed. Ask Astro again before applying it."))
            return
        }
        val target = File(projectDir, currentFileName)
        ProjectFileWriter.checkpoint(target, change.sourceSnapshot) { result ->
            mainHandler.post {
                if (result.isFailure) {
                    aiMessages.add(AiMessage(false, "Couldn’t protect the current file: ${result.exceptionOrNull()?.message}"))
                } else if (pendingCode == change && projectDir == target.parentFile &&
                    currentFileName == change.fileName && code == change.sourceSnapshot) {
                    val replacement = change.code
                    code = replacement + if (replacement.endsWith("\n")) "" else "\n"
                    editorRevision++
                    save()
                    pendingCode = null
                    showCodeNotice=false
                    aiMessages.add(AiMessage(false, "Applied to $currentFileName ✓"))
                }
            }
        }
    }
    fun rejectPendingCode() { pendingCode = null; showCodeNotice=false }
    fun acceptTeaching() {
        val topic = teachingOffer ?: return
        teachingOffer = null
        showTeachingNotice=false
        aiPrompt = "Teach me $topic step by step. Keep it interactive and ask me one small question at a time."
        askAi()
    }
    fun rejectTeaching() { teachingOffer = null; showTeachingNotice=false }
    private val completionWorker = java.util.concurrent.ThreadPoolExecutor(
        1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS, LinkedBlockingQueue()
    )
    private var completionTask: java.util.concurrent.Future<*>? = null
    fun requestCompletion(source: String, cursor: Int, deliver: (CompletionResult) -> Unit) {
        if (!autocomplete || !::projectDir.isInitialized) { deliver(CompletionResult(emptyList())); return }
        completionTask?.cancel(false)
        completionWorker.purge()
        completionTask = completionWorker.submit {
            val items = runCatching {
                val raw = Python.getInstance().getModule("runner")
                    .callAttr("complete", source, cursor, projectDir.absolutePath).toString()
                val rawItems = JSONObject(raw).optJSONArray("items") ?: JSONArray()
                (0 until rawItems.length()).map { index ->
                    rawItems.getJSONObject(index).let { item ->
                        CompletionItem(item.optString("label"), item.optString("insert_text"),
                            item.optInt("replace_start", cursor), item.optInt("replace_end", cursor),
                            item.optInt("cursor_back", 0), item.optString("type"),
                            item.optString("signature"), item.optString("doc"))
                    }
                }
            }.getOrDefault(emptyList())
            deliver(CompletionResult(items))
        }
    }
    fun requestDiagnostics(source: String, deliver: (List<CodeDiagnostic>) -> Unit) {
        thread(name="PY4U-Diagnostics") {
            val diagnostics = runCatching {
                val raw = Python.getInstance().getModule("runner").callAttr("diagnose", source).toString()
                val values = JSONArray(raw)
                (0 until values.length()).map { index ->
                    values.getJSONObject(index).let {
                        CodeDiagnostic(it.getInt("start"), it.getInt("end"), it.optString("message"), it.optBoolean("fatal", false))
                    }
                }
            }.getOrDefault(emptyList())
            viewModelScope.launch {
                if (code == source) codeDiagnostics = diagnostics
            }
            deliver(diagnostics)
        }
    }
    private fun refreshProjects() {
        if (!::projectsRoot.isInitialized) return
        val names = projectsRoot.listFiles()?.filter { it.isDirectory }?.map { it.name }?.sorted().orEmpty()
        projectNames.clear()
        projectNames.addAll(names)
    }

    private fun refreshSaved() {
        if (!::projectDir.isInitialized) return
        val files = projectDir.listFiles()?.filter { it.isFile && it.extension.equals("py",true) }
            ?.sortedByDescending { it.lastModified() }.orEmpty()
        savedCodes.clear()
        savedCodes.addAll(files.map { SavedCode(it.nameWithoutExtension.replace('_',' '),it.lastModified(),it.name) })
        deletedFiles.clear()
        deletedFiles.addAll(ProjectFileTrash.list(projectDir))
    }

    private fun openTabsKey(project: String = currentProjectName): String = "open_tabs::$project"

    private fun persistOpenTabs() {
        if (!::settings.isInitialized) return
        settings.edit().putString(openTabsKey(),OpenFileTabsCodec.encode(openFileTabs)).apply()
    }

    private fun restoreOpenTabs() {
        if (!::settings.isInitialized || !::projectDir.isInitialized) return
        val existing=projectDir.listFiles()?.filter { it.isFile && it.extension.equals("py",true) }
            ?.map { it.name }?.toSet().orEmpty()
        val restored=OpenFileTabsCodec.decode(settings.getString(openTabsKey(),null))
            .filter { it in existing }.takeLast(12).toMutableList()
        if(currentFileName in existing) {
            restored.remove(currentFileName)
            restored.add(currentFileName)
        }
        openFileTabs.clear()
        openFileTabs.addAll(restored)
        persistOpenTabs()
    }

    private fun touchOpenTab(fileName: String) {
        if (fileName !in openFileTabs) openFileTabs.add(fileName)
        while(openFileTabs.size>12) {
            val removable=openFileTabs.indexOfFirst { it!=currentFileName }
            if(removable<0) break
            openFileTabs.removeAt(removable)
        }
        persistOpenTabs()
    }

    fun closeFileTab(fileName: String) {
        if (running || fileName !in openFileTabs) return
        if(fileName!=currentFileName) {
            openFileTabs.remove(fileName)
            persistOpenTabs()
            return
        }
        if(openFileTabs.size<=1) return
        save()
        val oldIndex=openFileTabs.indexOf(fileName)
        openFileTabs.remove(fileName)
        val next=openFileTabs.getOrNull(oldIndex.coerceAtMost(openFileTabs.lastIndex))
            ?: openFileTabs.lastOrNull()
        persistOpenTabs()
        if(next!=null) openProjectFile(next)
    }

    fun makeNewProject(template: String = "Blank") {
        if (running || !::projectsRoot.isInitialized) return
        save()
        autosaveJob?.cancel()
        namingJob?.cancel()
        val name = ProjectWorkspace.nextName(projectNames, "Project")
        val directory = File(projectsRoot,name)
        if (!directory.isDirectory && !directory.mkdirs()) {
            saveError = "Couldn’t create project $name"
            return
        }
        if (runCatching { File(directory,"main.py").writeText(ProjectWorkspace.templateSource(template)) }.isFailure) {
            saveError = "Couldn’t create starter file in $name"
            return
        }
        switchProject(name)
    }

    fun switchProject(name: String) {
        if (running || !::projectsRoot.isInitialized) return
        val safeName = ProjectWorkspace.safeName(name)
        val targetDir = File(projectsRoot,safeName)
        if (!targetDir.exists() || !targetDir.isDirectory || targetDir.parentFile != projectsRoot) return
        if (targetDir == projectDir) return
        save()
        autosaveJob?.cancel()
        namingJob?.cancel()
        projectDir = targetDir
        currentProjectName = safeName
        val files = projectDir.listFiles()?.filter { it.isFile && it.extension.equals("py",true) }.orEmpty()
        var current = files.maxByOrNull { it.lastModified() } ?: File(projectDir,"main.py")
        if (!current.exists() && !File(current.path + ".bak").exists()) current.writeText("print(\"Hello world!\")\n")
        currentFileName = current.name
        code = readProjectText(current)
        codeDiagnostics = emptyList()
        pendingCode = null
        shareCode = false
        clearAttachment()
        settings.edit().putString("current_project",currentProjectName).putString("current_file",currentFileName).apply()
        refreshProjects()
        refreshSaved()
        restoreOpenTabs()
        editorRevision++
    }

    private fun uniqueFile(base: String): File {
        val clean = base.lowercase().replace(Regex("[^a-z0-9]+"),"_").trim('_').take(36).ifBlank { "new_code" }
        var candidate = File(projectDir,"${clean}.py")
        var number = 2
        while(candidate.exists() && candidate.name != currentFileName) candidate=File(projectDir,"${clean}_${number++}.py")
        return candidate
    }

    private fun localPurposeName(source: String): String {
        Regex("""(?m)^\s*class\s+([A-Za-z_]\w*)""").find(source)?.groupValues?.get(1)?.let { return it }
        Regex("""(?m)^\s*def\s+([A-Za-z_]\w*)""").find(source)?.groupValues?.get(1)?.let { return it }
        val lower=source.lowercase()
        return when {
            "calculator" in lower || ("input(" in lower && listOf("+","-","*","/").any { it in source }) -> "calculator"
            "password" in lower -> "password_tool"
            "random" in lower -> "random_generator"
            "game" in lower || "score" in lower -> "python_game"
            "http" in lower || "requests" in lower -> "web_request"
            "json" in lower -> "json_tool"
            "while " in lower -> "loop_program"
            "input(" in lower -> "interactive_program"
            "hello world" in lower -> "hello_world"
            else -> "python_code"
        }
    }

    private fun renameCurrentByPurpose(snapshot: String) {
        if (!currentFileName.startsWith("untitled_") || snapshot.isBlank()) return
        if (code != snapshot) return
        val old = File(projectDir,currentFileName)
        val target = uniqueFile(localPurposeName(snapshot))
        autosaveJob?.cancel()
        // The writer commits the latest contents before moving the file. Future
        // autosaves use the new name, so a delayed write cannot recreate untitled.py.
        persistCode(projectDir, old.name, snapshot)
        currentFileName = target.name
        settings.edit().putString("current_file",currentFileName).apply()
        ProjectFileWriter.rename(old, target) { result ->
            mainHandler.post {
                if (result.isFailure) saveError = result.exceptionOrNull()?.message
                if (::projectDir.isInitialized && projectDir == target.parentFile) refreshSaved()
            }
        }
    }

    fun makeNewCode() {
        if (running || !::projectDir.isInitialized) return
        save()
        var number=1
        var file=File(projectDir,"untitled_$number.py")
        while(file.exists()) file=File(projectDir,"untitled_${++number}.py")
        file.writeText("")
        currentFileName=file.name
        code=""
        codeDiagnostics=emptyList()
        pendingCode=null
        shareCode=false
        settings.edit().putString("current_file",currentFileName).apply()
        refreshSaved()
        touchOpenTab(currentFileName)
        editorRevision++
    }

    private fun projectPythonFile(fileName: String): File? {
        if (!::projectDir.isInitialized || fileName.contains('/') || fileName.contains('\\')) return null
        val file=File(projectDir,fileName)
        return file.takeIf { it.isFile && it.parentFile==projectDir && it.extension.equals("py",true) }
    }

    private fun editorStateKey(project: String = currentProjectName, file: String = currentFileName): String =
        "editor_state::$project::$file"

    fun editorFileState(): EditorFileState {
        if (!::settings.isInitialized) return EditorFileState()
        val key=editorStateKey()
        return editorFileStates[key]
            ?: EditorFileStateCodec.decode(settings.getString(key,null))
                ?.also { editorFileStates[key]=it }
            ?: EditorFileState()
    }

    fun rememberEditorFileState(state: EditorFileState) {
        if (!::settings.isInitialized) return
        val key=editorStateKey()
        editorFileStates[key]=state
        editorStateJobs.remove(key)?.cancel()
        editorStateJobs[key]=viewModelScope.launch {
            delay(350)
            settings.edit().putString(key,EditorFileStateCodec.encode(state)).apply()
            editorStateJobs.remove(key)
        }
    }

    private fun migrateEditorFileState(oldName: String, newName: String) {
        if (!::settings.isInitialized || oldName==newName) return
        val oldKey=editorStateKey(file=oldName)
        val newKey=editorStateKey(file=newName)
        val state=editorFileStates.remove(oldKey) ?: EditorFileStateCodec.decode(settings.getString(oldKey,null))
        editorStateJobs.remove(oldKey)?.cancel()
        editorStateJobs.remove(newKey)?.cancel()
        val edit=settings.edit().remove(oldKey)
        if(state!=null) {
            editorFileStates[newKey]=state
            edit.putString(newKey,EditorFileStateCodec.encode(state))
        }
        edit.apply()
    }

    fun openSaved(displayName: String) {
        if (running || !::projectDir.isInitialized) return
        val file=projectDir.listFiles()?.firstOrNull {
            it.isFile && it.extension.equals("py",true) && it.nameWithoutExtension.replace('_',' ')==displayName
        } ?: return
        openProjectFile(file.name)
    }

    fun openProjectFile(fileName: String) {
        if (running || !::projectDir.isInitialized) return
        val file=projectPythonFile(fileName) ?: return
        save()
        currentFileName=file.name
        code=readProjectText(file)
        codeDiagnostics=emptyList()
        pendingCode=null
        shareCode=false
        settings.edit().putString("current_file",currentFileName).apply()
        touchOpenTab(currentFileName)
        editorRevision++
        refreshSaved()
    }

    fun renameProjectFile(fileName: String, requestedName: String) {
        if (running) return
        val source=projectPythonFile(fileName) ?: return
        val target=File(projectDir,ProjectFileTrash.safePythonFileName(requestedName))
        if (target==source) return
        if (target.exists()) {
            saveError="${target.name} already exists"
            return
        }
        val wasCurrent=source.name==currentFileName
        if (wasCurrent) save()
        namingJob?.cancel()
        ProjectFileWriter.rename(source,target) { result ->
            mainHandler.post {
                result.onSuccess {
                    migrateEditorFileState(source.name,target.name)
                    val tabIndex=openFileTabs.indexOf(source.name)
                    if(tabIndex>=0) {
                        openFileTabs[tabIndex]=target.name
                        persistOpenTabs()
                    }
                    if (wasCurrent) {
                        currentFileName=target.name
                        settings.edit().putString("current_file",currentFileName).apply()
                        editorRevision++
                    }
                    saveError=null
                    refreshSaved()
                }.onFailure { saveError=it.message ?: "Couldn’t rename ${source.name}" }
            }
        }
    }

    fun duplicateProjectFile(fileName: String) {
        if (running) return
        val source=projectPythonFile(fileName) ?: return
        if (source.name==currentFileName) save()
        val target=ProjectFileTrash.duplicateTarget(projectDir,source)
        ProjectFileWriter.duplicate(source,target) { result ->
            mainHandler.post {
                result.onSuccess { saveError=null;refreshSaved() }
                    .onFailure { saveError=it.message ?: "Couldn’t duplicate ${source.name}" }
            }
        }
    }

    fun deleteProjectFile(fileName: String) {
        if (running) return
        val source=projectPythonFile(fileName) ?: return
        val wasCurrent=source.name==currentFileName
        if (wasCurrent) save()
        namingJob?.cancel()
        ProjectFileWriter.trash(source) { result ->
            result.onFailure { error ->
                mainHandler.post { saveError=error.message ?: "Couldn’t delete ${source.name}" }
            }.onSuccess {
                mainHandler.post {
                    openFileTabs.remove(source.name)
                    persistOpenTabs()
                }
                if (!wasCurrent) {
                    mainHandler.post { saveError=null;refreshSaved() }
                    return@onSuccess
                }
                val preferredTab=openFileTabs.firstOrNull { tabName ->
                    File(projectDir,tabName).isFile
                }
                val replacement=preferredTab?.let { File(projectDir,it) } ?: projectDir.listFiles()?.filter {
                    it.isFile && it.extension.equals("py",true)
                }?.maxByOrNull { it.lastModified() }
                if (replacement != null) {
                    val loaded=runCatching { readProjectText(replacement) }
                    mainHandler.post {
                        loaded.onSuccess { text ->
                            currentFileName=replacement.name
                            touchOpenTab(currentFileName)
                            code=text
                            codeDiagnostics=emptyList()
                            pendingCode=null
                            settings.edit().putString("current_file",currentFileName).apply()
                            editorRevision++
                            saveError=null
                            refreshSaved()
                        }.onFailure { saveError=it.message ?: "Couldn’t open another file";refreshSaved() }
                    }
                } else {
                    val fallback=File(projectDir,"main.py")
                    ProjectFileWriter.enqueue(fallback,"") { created ->
                        mainHandler.post {
                            created.onSuccess {
                                currentFileName=fallback.name
                                touchOpenTab(currentFileName)
                                code=""
                                codeDiagnostics=emptyList()
                                pendingCode=null
                                settings.edit().putString("current_file",currentFileName).apply()
                                editorRevision++
                                saveError=null
                            }.onFailure { saveError=it.message ?: "Couldn’t create main.py" }
                            refreshSaved()
                        }
                    }
                }
            }
        }
    }

    fun restoreDeletedFile(trashName: String) {
        if (running || !::projectDir.isInitialized) return
        ProjectFileWriter.restore(projectDir,trashName) { result ->
            mainHandler.post {
                result.onSuccess { saveError=null;refreshSaved() }
                    .onFailure { saveError=it.message ?: "Couldn’t restore deleted file" }
            }
        }
    }

    fun searchProject(query: String) {
        if (!::projectDir.isInitialized) return
        val token=++projectSearchToken
        val directory=projectDir
        val needle=query.trim()
        if (needle.isEmpty()) {
            projectSearchBusy=false
            projectSearchResults=emptyList()
            return
        }
        projectSearchBusy=true
        thread(name="PY4U-ProjectSearch") {
            val results=runCatching { ProjectSearch.search(directory,needle) }.getOrDefault(emptyList())
            viewModelScope.launch {
                if (token==projectSearchToken && ::projectDir.isInitialized && projectDir==directory) {
                    projectSearchResults=results
                    projectSearchBusy=false
                }
            }
        }
    }

    fun updateCode(value: String) {
        code = value
        codeDiagnostics = emptyList()
        if (currentFileName.startsWith("untitled_") && value.trim().length >= 8) {
            namingJob?.cancel()
            namingJob=viewModelScope.launch {
                delay(1800)
                renameCurrentByPurpose(code)
            }
        }
        if (!autoSave) return
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(autosaveDelay.toLong())
            val snapshot = code
            val fileName = currentFileName
            val directory = projectDir
            persistCode(directory, fileName, snapshot)
        }
    }
    fun saveAppearance() {
        if (!::settings.isInitialized) return
        settings.edit().putFloat("editor_font",editorFontSize).putFloat("terminal_font",terminalFontSize)
            .putInt("tab_width",tabWidth).putFloat("ui_scale",uiScale).putBoolean("word_wrap",wordWrap)
            .putBoolean("syntax",syntaxHighlighting).putBoolean("autosave",autoSave).apply()
        settings.edit().putString("font",fontName).putFloat("line_spacing",lineSpacing)
            .putFloat("editor_padding",editorPadding).putBoolean("typing_animation",typingAnimation)
            .putFloat("animation_duration",animationDuration).putFloat("highlight_delay",highlightDelay)
            .putFloat("autosave_delay",autosaveDelay).putBoolean("toolbar",showToolbar)
            .putBoolean("page_dots",showPageDots).putString("cursor",cursorStyle).apply()
        settings.edit().putBoolean("autocomplete",autocomplete).putFloat("ghost_brightness",ghostBrightness).apply()
        settings.edit().putBoolean("line_numbers",lineNumbers).putBoolean("current_line",highlightCurrentLine).apply()
        settings.edit().putString("accent_hex",accentHex).putString("background_hex",backgroundHex)
            .putString("motion_style",motionStyle)
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
                    setRequestProperty("User-Agent", "PY4U")
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
        if (::projectDir.isInitialized) persistCode(projectDir, currentFileName, code)
    }
    fun historyVersions(): List<File> = if (::projectDir.isInitialized)
        ProjectFileHistory.versions(File(projectDir, currentFileName)) else emptyList()

    fun historyPreview(version: File): String {
        if (!::projectDir.isInitialized) return "Couldn’t read snapshot"
        val target=File(projectDir,currentFileName)
        if (version !in ProjectFileHistory.versions(target)) return "Couldn’t read snapshot"
        return runCatching { readProjectText(version).take(3000) }.getOrDefault("Couldn’t read snapshot")
    }

    fun restoreHistory(version: File) {
        if (!::projectDir.isInitialized) return
        val target = File(projectDir, currentFileName)
        if (version !in ProjectFileHistory.versions(target)) return
        val currentSnapshot = code
        viewModelScope.launch {
            val recovered = withContext(Dispatchers.IO) { runCatching { readProjectText(version) } }
            recovered.onSuccess { snapshot ->
                if (projectDir == target.parentFile && currentFileName == target.name && code == currentSnapshot) {
                    ProjectFileWriter.checkpoint(target, currentSnapshot) { result ->
                        mainHandler.post {
                            if (result.isFailure) saveError = "Couldn’t protect current code: ${result.exceptionOrNull()?.message}"
                            else if (projectDir == target.parentFile && currentFileName == target.name && code == currentSnapshot) {
                                code = snapshot
                                codeDiagnostics = emptyList()
                                editorRevision++
                                save()
                            }
                        }
                    }
                }
            }.onFailure { saveError = "Couldn’t restore ${version.name}: ${it.message}" }
        }
    }
    private fun persistCode(directory: File, fileName: String, snapshot: String) {
        ProjectFileWriter.enqueue(File(directory, fileName), snapshot) { result ->
            mainHandler.post {
                if (result.isFailure) {
                    saveError = "Couldn’t save $fileName: ${result.exceptionOrNull()?.message ?: "storage error"}"
                } else if (::projectDir.isInitialized && projectDir == directory) {
                    saveError = null
                    refreshSaved()
                }
            }
        }
    }
    private fun appendOutput(value: String) {
        // Python may write one tiny fragment at a time. Buffer on its worker thread
        // and schedule just one UI update for the next output frame.
        outputBuffer.append(value)
        if (outputFlushScheduled.compareAndSet(false, true)) {
            mainHandler.postDelayed(outputFlushRunnable, 32)
        }
    }

    fun clearOutput() {
        outputBuffer.clear()
        mainHandler.removeCallbacks(outputFlushRunnable)
        outputFlushScheduled.set(false)
        output = ""
        runtimeIssue = null
    }
    fun fullOutput(): String = outputBuffer.snapshot()

    fun applyRuntimeFix() {
        val issue = runtimeIssue ?: return
        if (File(issue.fileName).name == currentFileName && issue.replaceFrom.isNotBlank() && issue.replaceTo.isNotBlank()) {
            val lines = code.lines().toMutableList()
            val index = issue.line - 1
            if (index in lines.indices && lines[index].contains(issue.replaceFrom)) {
                lines[index] = lines[index].replaceFirst(issue.replaceFrom, issue.replaceTo)
                code = lines.joinToString("\n").let { if (it.endsWith("\n")) it else "$it\n" }
                editorRevision++
                save()
                clearOutput()
                return
            }
        }
        aiPrompt = "Fix this Python error in $currentFileName without changing unrelated code:\n${issue.title} on line ${issue.line}: ${issue.explanation}\n${issue.hint}"
    }

    fun prepareRuntimeQuestion() {
        val issue = runtimeIssue ?: return
        aiPrompt = "Explain and fix this Python error in simple words:\n${issue.title} on line ${issue.line}\n${issue.explanation}\nCode: ${issue.codeLine}\nHint: ${issue.hint}"
    }
    fun openRuntimeSource(issue: RuntimeIssue): Boolean {
        if (running || !::projectDir.isInitialized) return false
        val candidate = File(issue.fileName).let { if (it.isAbsolute) it else File(projectDir, issue.fileName) }
        val file = runCatching { candidate.canonicalFile }.getOrNull() ?: return false
        if (file.parentFile != projectDir.canonicalFile || !file.isFile || !file.name.endsWith(".py", true)) return false
        if (file.name != currentFileName) {
            save()
            currentFileName = file.name
            code = readProjectText(file)
            codeDiagnostics = emptyList()
            settings.edit().putString("current_file", currentFileName).apply()
            editorRevision++
            refreshSaved()
        }
        return true
    }

    fun run() {
        if (running || !::projectDir.isInitialized) return
        val fatalDiagnostics = codeDiagnostics.filter { it.fatal }
        if (fatalDiagnostics.isNotEmpty()) {
            clearOutput()
            appendOutput(buildString {
                append("Fix these syntax errors before running:\n")
                fatalDiagnostics.take(8).forEach { issue ->
                    val line = code.take(issue.start.coerceIn(0, code.length)).count { it == '\n' } + 1
                    append("\nLine ").append(line).append(": ").append(issue.message)
                }
                if (fatalDiagnostics.size > 8) append("\n\n…and ${fatalDiagnostics.size - 8} more")
            })
            return
        }
        save()
        val sourceSnapshot = code
        val fileSnapshot = currentFileName
        stdin.clear()
        input = ""
        waitingInput = false
        stopRequested = false
        clearOutput()
        running = true
        worker = thread(name = "PY4U-Python") {
            runCatching {
                Python.getInstance().getModule("runner").callAttr("run_code", sourceSnapshot, fileSnapshot, projectDir.absolutePath, Bridge())
            }.onFailure { failure ->
                mainHandler.post {
                    appendOutput("\nRuntime error: ${failure.message ?: "Python stopped unexpectedly"}\n")
                    running = false
                    waitingInput = false
                }
            }
        }
    }
    fun submitInput() { if (waitingInput) { stdin.offer(input); input = ""; waitingInput = false } }
    fun submitConsoleEntry() {
        if (waitingInput) { submitInput(); return }
        if (consoleMode != "Terminal" || running || input.isBlank()) return
        val command = input.trim()
        inputHistory.record(command)
        input = ""
        if (command == "clear") { clearOutput(); return }
        running = true
        worker = thread(name = "PY4U-Terminal") {
            runCatching { Python.getInstance().getModule("runner").callAttr("run_terminal_command", command, projectDir.absolutePath, Bridge()) }
                .onFailure { failure -> mainHandler.post { appendOutput("Terminal error: ${failure.message ?: "command failed"}\n"); running = false } }
        }
    }
    fun stop() {
        if (!running) return
        stopRequested = true
        stdin.clear()
        stdin.offer(stopInputSignal)
        appendOutput("\n[Stopping program…]\n")
        input = ""
        waitingInput = false
    }
    inner class Bridge {
        fun write(text: String, error: Boolean) { appendOutput(text) }
        fun reportError(title: String, explanation: String, line: Int, codeLine: String, hint: String, details: String, fileName: String, replaceFrom: String, replaceTo: String) {
            mainHandler.post { runtimeIssue = RuntimeIssue(title, explanation, line, codeLine, hint, details, fileName, replaceFrom, replaceTo) }
        }
        fun shouldStop(): Boolean = stopRequested
        fun readLine(): String? {
            if (stopRequested) return null
            mainHandler.post { waitingInput = true }
            return try { stdin.take().takeUnless { it == stopInputSignal || stopRequested } }
            catch (_: InterruptedException) { null }
        }
        fun exited(code: Int) { mainHandler.post {
            appendOutput("\n[Process exited with code $code]\n")
            running = false
            input = ""
            waitingInput = false
            stopRequested = false
            worker = null
            stdin.clear()
        } }
    }

    override fun onCleared() {
        completionTask?.cancel(true)
        completionWorker.shutdownNow()
        editorStateJobs.values.forEach { it.cancel() }
        if (::settings.isInitialized && editorFileStates.isNotEmpty()) {
            val edit=settings.edit()
            editorFileStates.forEach { (key,state) ->
                edit.putString(key,EditorFileStateCodec.encode(state))
            }
            edit.apply()
        }
        stopRequested = true
        stdin.offer(stopInputSignal)
        super.onCleared()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { val vm: IdeViewModel = viewModel(); LaunchedEffect(Unit) { vm.initialize(applicationContext) }; PyDroidX(vm) }
    }
}

@Composable
private fun SwitchingModelNotice(status: String, accent: Color, onFinished: () -> Unit) {
    var visible by remember(status) { mutableStateOf(false) }
    LaunchedEffect(status) {
        visible=true
        delay(2400)
        visible=false
        delay(220)
        onFinished()
    }
    AnimatedVisibility(
        visible=visible,
        enter=slideInVertically(initialOffsetY={-it})+fadeIn()+scaleIn(initialScale=0.96f),
        exit=slideOutVertically(targetOffsetY={-it/2})+fadeOut()+scaleOut(targetScale=0.98f)
    ) {
        val shape=androidx.compose.foundation.shape.RoundedCornerShape(9.dp)
        Row(
            Modifier.widthIn(max=270.dp).background(Color(0xF20A0D10),shape)
                .border(1.dp,accent.copy(alpha=0.7f),shape).padding(horizontal=9.dp,vertical=6.dp),
            verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator(Modifier.size(14.dp),color=accent,strokeWidth=2.dp)
            Column {
                Text("AI FALLBACK",color=accent,fontSize=9.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp)
                Text(status,color=Color.White,fontSize=10.sp,fontWeight=FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AchievementNotice(
    badge: String,
    title: String,
    subtitle: String,
    accent: Color,
    primaryLabel: String,
    secondaryLabel: String,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit
) {
    var visible by remember(title, badge) { mutableStateOf(false) }
    var dragX by remember(title, badge) { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    LaunchedEffect(title, badge) { visible = true }
    fun closeThen(action: () -> Unit) {
        visible = false
        scope.launch { delay(230); action() }
    }
    Popup(
        alignment=androidx.compose.ui.Alignment.TopEnd,
        offset=androidx.compose.ui.unit.IntOffset(-12,80),
        properties=PopupProperties(focusable=false)
    ) {
        AnimatedVisibility(
            visible=visible,
            enter=slideInVertically(initialOffsetY={-it})+fadeIn()+scaleIn(initialScale=0.94f),
            exit=slideOutHorizontally(targetOffsetX={it})+fadeOut()+scaleOut(targetScale=0.97f)
        ) {
            val shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            Surface(
                color=Color(0xF20A0D10),
                contentColor=Color.White,
                shape=shape,
                border=BorderStroke(1.dp,accent.copy(alpha=0.72f)),
                shadowElevation=18.dp,
                modifier=Modifier.widthIn(min=210.dp,max=270.dp)
                    .graphicsLayer { translationX=dragX }
                    .pointerInput(title,badge) {
                        detectHorizontalDragGestures(
                            onDragEnd={
                                if(dragX>=threshold) closeThen(onSecondary)
                                else dragX=0f
                            },
                            onDragCancel={dragX=0f},
                            onHorizontalDrag={change,amount->
                                change.consume()
                                dragX=(dragX+amount).coerceAtLeast(0f)
                            }
                        )
                    }
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal=9.dp,vertical=6.dp),
                        verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(7.dp)
                    ) {
                        Box(
                            Modifier.size(26.dp).background(accent.copy(alpha=0.13f),androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                                .border(1.dp,accent.copy(alpha=0.75f),androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
                            contentAlignment=androidx.compose.ui.Alignment.Center
                        ) { Text("◆",color=Color(0xFF59F2DF),fontSize=14.sp) }
                        Column(Modifier.weight(1f)) {
                            Text(badge,color=accent,fontSize=8.sp,fontWeight=FontWeight.Bold,letterSpacing=1.1.sp)
                            Text(title,color=Color.White,fontSize=12.sp,fontWeight=FontWeight.Bold,maxLines=1)
                            Text(subtitle,color=Color(0xFFA9B0B8),fontSize=9.sp,maxLines=1)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(accent.copy(alpha=0.6f)))
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal=6.dp,vertical=2.dp),
                        horizontalArrangement=Arrangement.End
                    ) {
                        TextButton(onClick={closeThen(onSecondary)},contentPadding=PaddingValues(horizontal=6.dp,vertical=2.dp)) {
                            Text(secondaryLabel,color=Color(0xFFB9C0C8),fontSize=11.sp)
                        }
                        Button(
                            onClick={closeThen(onPrimary)},
                            colors=ButtonDefaults.buttonColors(containerColor=accent,contentColor=Color.Black),
                            contentPadding=PaddingValues(horizontal=8.dp,vertical=2.dp)
                        ) { Text(primaryLabel,fontWeight=FontWeight.Bold,fontSize=11.sp) }
                    }
                }
            }
        }
    }
}

@Composable fun PyDroidX(vm: IdeViewModel) {
    fun safeColor(value:String,fallback:Long)=runCatching{Color(AndroidColor.parseColor(value))}.getOrDefault(Color(fallback))
    val bg = safeColor(vm.backgroundHex,0xFF0B0F14)
    val text = Color(0xFFE6F5FF)
    val accent = safeColor(vm.accentHex,0xFF00E5FF)
    val revision = vm.editorRevision
    val pager = rememberPagerState(pageCount = { 5 })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val aiScroll = rememberScrollState()
    val consoleScroll = rememberScrollState()
    var consoleAutoScroll by remember { mutableStateOf(true) }
    val consoleInputFocus = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val pages = listOf("FOLDERS", "PYTHON", "CONSOLE", "HELPER", "SETTINGS")
    val pageIcons = listOf("Folders", "Python", "Console", "Helper", "Settings")
    var editorView by remember { mutableStateOf<PythonEditorView?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, editorView) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && vm.autoSave) {
                editorView?.flushCodeChange()
                vm.save()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var showFind by remember { mutableStateOf(false) }
    var showReplace by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var showGoToLine by remember { mutableStateOf(false) }
    var lineDraft by remember { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    var showPalette by remember { mutableStateOf(false) }
    var paletteQuery by remember { mutableStateOf("") }
    var showQuickOpen by remember { mutableStateOf(false) }
    var showProjectSearch by remember { mutableStateOf(false) }
    var projectSearchQuery by remember { mutableStateOf("") }
    var showConsoleSearch by remember { mutableStateOf(false) }
    var consoleSearchQuery by remember { mutableStateOf("") }
    var consoleSearchSnapshot by remember { mutableStateOf("") }
    var fileActionTarget by remember { mutableStateOf<SavedCode?>(null) }
    var renameTarget by remember { mutableStateOf<SavedCode?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var showTrash by remember { mutableStateOf(false) }
    var showProjectTemplates by remember { mutableStateOf(false) }
    var quickOpenQuery by remember { mutableStateOf("") }
    var chosenVersion by remember { mutableStateOf<File?>(null) }
    var historyPreview by remember { mutableStateOf("") }
    LaunchedEffect(chosenVersion) {
        historyPreview = chosenVersion?.let { version ->
            withContext(Dispatchers.IO) { vm.historyPreview(version) }
        }.orEmpty()
    }
    var showAiSettings by remember { mutableStateOf(false) }
    var showCodePreview by remember { mutableStateOf(false) }
    var showProblems by remember { mutableStateOf(false) }
    var selectedProblem by remember { mutableStateOf<CodeDiagnostic?>(null) }
    var selectedAiSlot by remember { mutableIntStateOf(0) }
    var keyDraft by remember { mutableStateOf("") }
    var endpointDraft by remember { mutableStateOf(vm.aiEndpoint) }
    var modelDraft by remember { mutableStateOf(vm.aiModel) }
    var providerDraft by remember { mutableStateOf(vm.aiProvider) }
    var settingsSection by remember { mutableStateOf("Overview") }
    var consoleInputValue by remember { mutableStateOf(TextFieldValue(vm.input)) }
    var runBurst by remember { mutableIntStateOf(0) }
    var runOrigin by remember { mutableStateOf(Offset.Zero) }
    var headerRunOrigin by remember { mutableStateOf(Offset.Zero) }
    var consoleRunOrigin by remember { mutableStateOf(Offset.Zero) }
    val motionAllowed = vm.motionEnabled && ValueAnimator.areAnimatorsEnabled()
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.saveAiSettings(keyDraft, endpointDraft, modelDraft, "On-device", selectedAiSlot)
            vm.importLocalModel(uri, selectedAiSlot)
        }
    }
    val attachmentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "attachment"
            val content = runCatching {
                context.contentResolver.openInputStream(uri)?.reader()?.buffered()?.use { reader ->
                    val output = StringBuilder()
                    val buffer = CharArray(8192)
                    while (output.length < 100_000) {
                        val count = reader.read(buffer, 0, minOf(buffer.size, 100_000 - output.length))
                        if (count < 0) break
                        output.append(buffer, 0, count)
                    }
                    output.toString()
                }
            }.getOrNull()
            if (content != null) vm.attachFile(name,content)
            else Toast.makeText(context,"Could not read that attachment",Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(selectedAiSlot) {
        keyDraft = ""
        providerDraft = vm.providerForSlot(selectedAiSlot)
        endpointDraft = vm.endpointForSlot(selectedAiSlot)
        modelDraft = vm.modelForSlot(selectedAiSlot)
        vm.aiTestStatus = null
    }
    LaunchedEffect(vm.aiMessages.size) {
        aiScroll.animateScrollTo(aiScroll.maxValue)
    }
    LaunchedEffect(pager.currentPage) {
        editorView?.flushCodeChange()
    }
    LaunchedEffect(vm.waitingInput, pager.currentPage) {
        if (vm.waitingInput && pager.currentPage == 2) {
            delay(120)
            consoleInputFocus.requestFocus()
            keyboardController?.show()
        }
    }
    var lastBackPress by remember { mutableLongStateOf(0L) }

    BackHandler(enabled=!showAiSettings) {
        val now=android.os.SystemClock.elapsedRealtime()
        if(now-lastBackPress<2000L) (context as? Activity)?.finish()
        else {
            lastBackPress=now
            Toast.makeText(context,"Swipe back again to exit",Toast.LENGTH_SHORT).show()
        }
    }

    val glass=Color.White.copy(alpha=0.065f)
    val glassEdge=Color.White.copy(alpha=0.16f)
    val baseDensity=LocalDensity.current
    val scaledDensity=remember(baseDensity.density,baseDensity.fontScale,vm.uiScale) {
        Density(baseDensity.density * vm.uiScale, baseDensity.fontScale)
    }
    CompositionLocalProvider(LocalDensity provides scaledDensity) {
    MaterialTheme(colorScheme = darkColorScheme(primary=accent,background=bg,surface=Color.Transparent,surfaceVariant=glass,outline=glassEdge)) {
        val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        Box(Modifier.fillMaxSize().background(bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            vm.saveError?.let { error ->
                Text(error + " · Tap to retry", color=Color.White, fontSize=12.sp,
                    modifier=Modifier.fillMaxWidth().background(Color(0xFF8B2835))
                        .clickable { editorView?.flushCodeChange(); vm.save() }
                        .padding(horizontal=12.dp, vertical=8.dp))
            }
            if(vm.showHeader) {
                BoxWithConstraints(
                    Modifier.fillMaxWidth().padding(horizontal=8.dp, vertical=6.dp)
                        .height(vm.headerHeight.coerceIn(56f, 76f).dp)
                        .background(Color(0xFF181F29), androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                ) {
                    val tabWidth = (maxWidth - if(pager.currentPage==1) 72.dp else 0.dp) / pages.size
                    val indicatorX by animateDpAsState(
                        targetValue=tabWidth*pager.currentPage + 6.dp,
                        animationSpec=if(motionAllowed) spring(dampingRatio=Spring.DampingRatioMediumBouncy,
                            stiffness=Spring.StiffnessMediumLow) else tween(0), label="tab position")
                    val indicatorWidth by animateDpAsState(
                        targetValue=tabWidth - 12.dp,
                        animationSpec=if(motionAllowed) spring(stiffness=Spring.StiffnessMediumLow) else tween(0),
                        label="tab width")
                    Row(Modifier.fillMaxSize(), verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        pages.forEachIndexed { index, label ->
                            val active = pager.currentPage == index
                            val tint by androidx.compose.animation.animateColorAsState(
                                if(active) Color(0xFF32B5FF) else Color(0xFFA4ADBA),
                                animationSpec=tween(if(motionAllowed) 260 else 0), label="tab color")
                            val tabInteraction = remember { MutableInteractionSource() }
                            val pressed by tabInteraction.collectIsPressedAsState()
                            val iconScale by animateFloatAsState(
                                if (motionAllowed && pressed) 0.83f else if(active && motionAllowed) 1.13f else 1f,
                                animationSpec=spring(dampingRatio=Spring.DampingRatioMediumBouncy,stiffness=Spring.StiffnessMedium), label="tab icon")
                            Column(
                                Modifier.weight(1f).fillMaxHeight().clickable(interactionSource=tabInteraction,indication=null) {
                                    scope.launch { pager.animateScrollToPage(index) }
                                }.padding(top=8.dp),
                                horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally,
                                verticalArrangement=Arrangement.SpaceBetween
                            ) {
                                if(index==1) Image(painterResource(R.drawable.ic_python_editor), "Python",
                                    Modifier.size(22.dp).graphicsLayer { scaleX=iconScale;scaleY=iconScale })
                                else IdeGlyph(pageIcons[index], tint,
                                    Modifier.graphicsLayer { scaleX=iconScale;scaleY=iconScale })
                                Text(label, color=tint, fontSize=7.sp, maxLines=1, softWrap=false)
                                Spacer(Modifier.height(2.dp))
                            }
                        }
                        if(pager.currentPage==1) {
                            Button(
                                onClick={editorView?.flushCodeChange();if(vm.running) vm.stop() else {
                                    runOrigin=headerRunOrigin;runBurst++; vm.run(); scope.launch{pager.animateScrollToPage(2)}
                                }},
                                colors=ButtonDefaults.buttonColors(
                                    containerColor=if(vm.running) safeColor(vm.stopButtonHex,0xFFFF3D71) else safeColor(vm.runButtonHex,0xFF00E676),
                                    contentColor=Color.Black),
                                shape=androidx.compose.foundation.shape.RoundedCornerShape(9.dp),
                                contentPadding=PaddingValues(0.dp),
                                modifier=Modifier.padding(horizontal=5.dp).width(62.dp).height(44.dp)
                                    .onGloballyPositioned { coords ->
                                        headerRunOrigin=coords.positionInRoot()+Offset(coords.size.width/2f,coords.size.height/2f)
                                    }
                            ) {
                                AnimatedContent(targetState=vm.running, label="run button",
                                    transitionSpec={
                                        (slideInVertically(tween(180)){it/2}+fadeIn(tween(180))) togetherWith
                                            (slideOutVertically(tween(180)){-it/2}+fadeOut(tween(180)))
                                    }) { running ->
                                    Text(if(running) "■ Stop" else "▶ Start",fontSize=12.sp,
                                        fontWeight=FontWeight.Bold,maxLines=1)
                                }
                            }
                        }
                    }
                    Box(Modifier.align(androidx.compose.ui.Alignment.BottomStart).offset(x=indicatorX)
                        .width(indicatorWidth.coerceAtLeast(10.dp)).height(3.dp)
                        .background(Brush.horizontalGradient(listOf(Color(0xFF24A8FF),Color(0xFF52F5D1))),
                            androidx.compose.foundation.shape.RoundedCornerShape(4.dp)))
                }
            }
            HorizontalPager(
                state=pager,
                modifier=Modifier.weight(1f).fillMaxWidth(),
                beyondViewportPageCount=1,
                userScrollEnabled=pager.currentPage != 1 && pager.currentPage != 2
            ) { page ->
                val rawOffset = (pager.currentPage - page) + pager.currentPageOffsetFraction
                val distance = rawOffset.absoluteValue.coerceIn(0f,1f)
                val motionModifier = Modifier.fillMaxSize().graphicsLayer {
                    translationX=0f;translationY=0f;rotationY=0f
                    scaleX=1f;scaleY=1f;alpha=1f
                    if(motionAllowed && !keyboardOpen) {
                        val amount = vm.motionIntensity.coerceIn(0f,1f)
                        when(vm.motionStyle) {
                            "Aurora glide" -> {
                                translationX=rawOffset*size.width*0.06f*amount
                                rotationY=rawOffset*9f*amount
                                scaleX=1f-distance*0.075f*amount;scaleY=scaleX
                                alpha=1f-distance*0.18f*amount
                            }
                            "Fluid spring" -> { translationX=rawOffset*size.width*0.07f*amount;scaleX=1f-distance*0.06f*amount;scaleY=scaleX;rotationY=rawOffset*5f*amount }
                            "Soft fade" -> alpha = 1f - distance * 0.35f * amount
                            "Subtle scale" -> { scaleX=1f-distance*0.05f*amount;scaleY=scaleX }
                            "Shared element" -> { scaleX=1f-distance*0.03f*amount;scaleY=scaleX;alpha=1f-distance*0.12f*amount }
                            "Smooth blur reveal" -> { alpha=1f-distance*0.25f*amount;scaleX=1f-distance*0.025f*amount;scaleY=scaleX }
                            "Layered depth" -> { translationX=rawOffset*size.width*0.08f*amount;scaleX=1f-distance*0.06f*amount;scaleY=scaleX }
                            "Gentle parallax" -> translationX=rawOffset*size.width*0.12f*amount
                            "Card expansion" -> { scaleX=0.92f+0.08f*(1f-distance*amount);scaleY=scaleX;alpha=1f-distance*0.18f*amount }
                            "Natural sheet" -> { translationY=distance*40f*amount;alpha=1f-distance*0.18f*amount }
                            "Magnetic snap" -> { scaleX=1f-distance*0.018f*amount;scaleY=scaleX }
                            "Interactive swipe" -> { translationX=rawOffset*size.width*0.055f*amount;alpha=1f-distance*0.08f*amount }
                            "Content morph" -> { scaleX=1f-distance*0.04f*amount;scaleY=1f-distance*0.015f*amount;alpha=1f-distance*0.15f*amount }
                            "Keyboard lift" -> translationY=-distance*18f*amount
                            else -> { scaleX=1f-distance*0.02f*amount;scaleY=scaleX }
                        }
                    }
                }
                Box(motionModifier) { when(page) {
                    0 -> Column(
                        Modifier.fillMaxSize().background(bg).padding(horizontal=18.dp,vertical=14.dp),
                        verticalArrangement=Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("FOLDERS",color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold)
                                Text("Project  •  ${vm.currentProjectName}",color=Color.Gray,fontSize=11.sp)
                            }
                            Button(
                                onClick={vm.makeNewCode();scope.launch{pager.animateScrollToPage(1)}},
                                colors=ButtonDefaults.buttonColors(
                                    containerColor=(if(vm.running) safeColor(vm.stopButtonHex,0xFFFF3D71) else safeColor(vm.runButtonHex,0xFF00E676)).copy(alpha=0.90f),
                                    contentColor=Color.Black
                                ),
                                shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                            ){Text("＋ Make new code",fontWeight=FontWeight.Bold)}
                        }
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement=Arrangement.spacedBy(7.dp)
                        ) {
                            vm.projectNames.forEach { project ->
                                FilterChip(
                                    selected=project==vm.currentProjectName,
                                    onClick={vm.switchProject(project)},
                                    label={Text(project,maxLines=1)}
                                )
                            }
                            OutlinedButton(
                                onClick={showProjectTemplates=true},
                                shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                            ){Text("＋ Project")}
                            if(vm.deletedFiles.isNotEmpty()) {
                                OutlinedButton(
                                    onClick={showTrash=true},
                                    shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                                ){Text("Trash ${vm.deletedFiles.size}")}
                            }
                        }
                        HorizontalDivider(color=Color.White.copy(alpha=0.12f))
                        if(vm.savedCodes.isEmpty()) {
                            Box(Modifier.fillMaxSize(),contentAlignment=androidx.compose.ui.Alignment.Center) {
                                Text("No saved code yet",color=Color.Gray)
                            }
                        } else {
                            Column(
                                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                                verticalArrangement=Arrangement.spacedBy(7.dp)
                            ) {
                                vm.savedCodes.forEach { saved ->
                                    Surface(
                                        onClick={vm.openProjectFile(saved.fileName);scope.launch{pager.animateScrollToPage(1)}},
                                        color=if(vm.currentFileName==saved.fileName) Color.White.copy(alpha=0.15f) else Color.White.copy(alpha=0.045f),
                                        shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                                        modifier=Modifier.fillMaxWidth().border(1.dp,Color.White.copy(alpha=0.10f),androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                                    ) {
                                        Row(Modifier.padding(horizontal=15.dp,vertical=14.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                            Text("⌘",color=Color.White,fontSize=18.sp)
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(saved.name,color=Color.White,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
                                                Text(".py  •  auto-saved",color=Color.Gray,fontSize=10.sp)
                                            }
                                            TextButton(
                                                onClick={fileActionTarget=saved},
                                                contentPadding=PaddingValues(horizontal=7.dp,vertical=2.dp)
                                            ) { Text("⋮",color=Color.LightGray,fontSize=20.sp) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> Column(Modifier.fillMaxSize().background(bg)) {
                        if(vm.showFileInfo) {
                        Row(
                            Modifier.fillMaxWidth().height(48.dp).padding(horizontal=8.dp,vertical=2.dp)
                                .background(Color(0xFF181F29),androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                .border(1.dp,Color.White.copy(alpha=.06f),androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                .padding(start=12.dp,end=6.dp),
                            verticalAlignment=androidx.compose.ui.Alignment.CenterVertically
                        ){
                                Image(
                                painter=painterResource(com.pydroidx.app.R.drawable.ic_python_editor),
                                contentDescription="Python file",
                                modifier=Modifier.size(24.dp)
                            )
                                Spacer(Modifier.width(9.dp))
                                Text(vm.currentFileName,color=Color.White,fontSize=14.sp,fontWeight=FontWeight.SemiBold,
                                    maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,modifier=Modifier.weight(1f,fill=false).widthIn(max=112.dp))
                                if (vm.codeDiagnostics.isNotEmpty()) {
                                    TextButton(
                                        onClick={showProblems=true},
                                        contentPadding=PaddingValues(horizontal=4.dp),
                                        modifier=Modifier.height(34.dp)
                                    ) {
                                        IdeGlyph("Problems",Color(0xFFFF7B86),Modifier.size(17.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text(vm.codeDiagnostics.size.toString(),color=Color(0xFFFF9AA3),fontSize=10.sp)
                                    }
                                }
                            Spacer(Modifier.width(7.dp))
                            Box(Modifier.size(6.dp).background(Color(0xFF8AB4F8),androidx.compose.foundation.shape.CircleShape))
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick={showFind=true},modifier=Modifier.width(42.dp)) {
                                Text("Find",fontSize=11.sp)
                            }
                            IconButton(onClick={editorView?.undoCode()},modifier=Modifier.size(34.dp)) {
                                IdeGlyph("Undo",Color(0xFFD8D9E0))
                            }
                            IconButton(onClick={editorView?.redoCode()},modifier=Modifier.size(34.dp)) {
                                IdeGlyph("Redo",Color(0xFFD8D9E0))
                            }
                            Spacer(Modifier.weight(0.1f))
                            TextButton(
                                onClick={scope.launch{pager.animateScrollToPage(0)}},
                                contentPadding=PaddingValues(6.dp),modifier=Modifier.size(36.dp)
                            ){IdeGlyph("Close",Color(0xFF8C8C94))}
                        }
                        }
                        if(vm.openFileTabs.isNotEmpty()) {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min=34.dp,max=40.dp)
                                    .horizontalScroll(rememberScrollState())
                                    .background(safeColor(vm.tabBarHex,0xFF181F29))
                                    .padding(horizontal=6.dp,vertical=3.dp),
                                horizontalArrangement=Arrangement.spacedBy(5.dp),
                                verticalAlignment=androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                vm.openFileTabs.forEach { fileName ->
                                    val active=fileName==vm.currentFileName
                                    Surface(
                                        color=if(active) Color.White.copy(alpha=.14f) else Color.White.copy(alpha=.045f),
                                        shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                        border=BorderStroke(1.dp,Color.White.copy(alpha=if(active).18f else .08f))
                                    ) {
                                        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                            Text(
                                                fileName,
                                                color=if(active) Color.White else Color(0xFFB0B2BA),
                                                fontSize=11.sp,
                                                maxLines=1,
                                                overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                modifier=Modifier.widthIn(max=130.dp)
                                                    .clickable(enabled=!vm.running) {
                                                        editorView?.flushCodeChange()
                                                        vm.openProjectFile(fileName)
                                                    }
                                                    .padding(start=9.dp,end=5.dp,top=6.dp,bottom=6.dp)
                                            )
                                            if(vm.openFileTabs.size>1) {
                                                Text(
                                                    "×",
                                                    color=Color(0xFF8C8C94),
                                                    fontSize=16.sp,
                                                    modifier=Modifier.clickable(enabled=!vm.running) { vm.closeFileTab(fileName) }
                                                        .padding(horizontal=7.dp,vertical=4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (showFind) {
                            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),
                                verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                OutlinedTextField(findQuery,{findQuery=it},label={Text("Find in file")},
                                    singleLine=true,modifier=Modifier.weight(1f))
                                TextButton(onClick={
                                    if (editorView?.findNext(findQuery) != true && findQuery.isNotEmpty())
                                        Toast.makeText(context,"No matches",Toast.LENGTH_SHORT).show()
                                }) { Text("Next") }
                                TextButton(onClick={showFind=false;showReplace=false}) { Text("×") }
                            }
                            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp)) {
                                TextButton(onClick={showReplace=!showReplace}) { Text("Replace") }
                                TextButton(onClick={showGoToLine=true}) { Text("Go to line") }
                                TextButton(onClick={showHistory=true;chosenVersion=null}) { Text("History") }
                            }
                            if (showReplace) Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),
                                verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                OutlinedTextField(replacement,{replacement=it},label={Text("Replace with")},
                                    singleLine=true,modifier=Modifier.weight(1f))
                                TextButton(onClick={editorView?.replaceSelection(findQuery,replacement)}) {
                                    Text("One")
                                }
                                TextButton(onClick={
                                    val count=editorView?.replaceAllMatches(findQuery,replacement) ?: 0
                                    Toast.makeText(context,"Replaced $count",Toast.LENGTH_SHORT).show()
                                }) { Text("All") }
                            }
                        }
                        AndroidView(
                            factory={context->PythonEditorView(context).also{view->
                                editorView=view
                                view.onFindRequested={ replace -> showFind=true;showReplace=replace }
                                view.onSaveRequested=vm::save
                                view.onRunRequested={if (!vm.running) { vm.run();scope.launch{pager.animateScrollToPage(2)} }}
                                view.onPaletteRequested={showPalette=true}
                                view.onQuickOpenRequested={showQuickOpen=true}
                                view.onCodeChanged=vm::updateCode
                                view.onFileStateChanged=vm::rememberEditorFileState
                                view.onDiagnosticTap={selectedProblem=it}
                                view.requestSmartCompletion=vm::requestCompletion
                                view.requestCodeDiagnostics=vm::requestDiagnostics
                                view.setCodeIfDifferent(vm.code,revision)
                                view.restoreFileStateOnce(vm.editorFileState(),revision)
                            }},
                            update={view->
                                view.onFindRequested={ replace -> showFind=true;showReplace=replace }
                                view.onSaveRequested=vm::save
                                view.onRunRequested={if (!vm.running) { vm.run();scope.launch{pager.animateScrollToPage(2)} }}
                                view.onPaletteRequested={showPalette=true}
                                view.onQuickOpenRequested={showQuickOpen=true}
                                view.onFileStateChanged=vm::rememberEditorFileState
                                view.onDiagnosticTap={selectedProblem=it}
                                view.setBackgroundColor(bg.toArgb())
                                view.setCodeIfDifferent(vm.code,revision)
                                view.restoreFileStateOnce(vm.editorFileState(),revision)
                                view.applyPreferences(vm.editorFontSize,vm.wordWrap,vm.syntaxHighlighting,vm.fontName,
                                    vm.lineSpacing,vm.editorPadding,vm.highlightDelay,vm.cursorStyle,vm.autocomplete,vm.ghostBrightness,
                                    vm.lineNumbers,vm.highlightCurrentLine,vm.customFontPath,
                                    listOf(vm.editorTextHex,vm.commentHex,vm.stringHex,vm.numberHex,vm.keywordHex,vm.functionHex,vm.variableHex),vm.tabWidth)
                            },
                            modifier=Modifier.weight(1f).fillMaxWidth().background(bg)
                        )
                        if(vm.showToolbar) {
                            Surface(
                                color=safeColor(vm.toolbarHex,0xFF050505),
                                shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                                border=BorderStroke(1.dp,Color.White.copy(alpha=.16f)),
                                modifier=Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=5.dp)
                            ){
                                Row(
                                    Modifier.fillMaxWidth().height(vm.toolbarHeight.coerceIn(44f, 60f).dp)
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal=7.dp,vertical=6.dp),
                                    horizontalArrangement=Arrangement.spacedBy(8.dp),
                                    verticalAlignment=androidx.compose.ui.Alignment.CenterVertically
                                ){
                                    val matchingPairs = mapOf("(" to ")", "[" to "]", "{" to "}", "\"" to "\"", "'" to "'")
                                    listOf("⌘","Astro","Tab","(",")","[","]","{","}","\"","'",":","=","#").forEach{key->
                                        OutlinedButton(
                                            onClick={
                                                when {
                                                    key=="⌘" -> showPalette=true
                                                    key=="Astro" -> {
                                                        val view=editorView
                                                        val start=minOf(view?.selectionStart ?: 0,view?.selectionEnd ?: 0).coerceAtLeast(0)
                                                        val end=maxOf(view?.selectionStart ?: 0,view?.selectionEnd ?: 0).coerceAtMost(view?.text?.length ?: 0)
                                                        val selection=if(view!=null && end>start) view.text.subSequence(start,end).toString().take(8000) else ""
                                                        vm.aiPrompt=if(selection.isNotBlank())
                                                            "Question about this selection in ${vm.currentFileName}:\n```python\n$selection\n```\n"
                                                            else "Question about ${vm.currentFileName}: "
                                                        scope.launch{pager.animateScrollToPage(3)}
                                                    }
                                                    key=="Tab"&&editorView?.acceptGhostSuggestion()==true -> Unit
                                                    key=="Tab"&&editorView?.indentSelection()==true -> Unit
                                                    key=="Tab" -> editorView?.insertAtCursor(" ".repeat(vm.tabWidth))
                                                    key=="#"&&editorView?.toggleCommentSelection()==true -> Unit
                                                    key in matchingPairs -> editorView?.insertPair(key, matchingPairs.getValue(key))
                                                    key in setOf(")", "]", "}") -> editorView?.insertClosingOrSkip(key)
                                                    else -> editorView?.insertAtCursor(if(key=="Tab")"    " else key)
                                                }
                                                
                                            },
                                            modifier=Modifier.width(if(key=="Astro")56.dp else if(key=="Tab")46.dp else 36.dp).fillMaxHeight(),
                                            shape=androidx.compose.foundation.shape.RoundedCornerShape(11.dp),
                                            border=BorderStroke(1.dp,Color.White.copy(alpha=.14f)),
                                            colors=ButtonDefaults.outlinedButtonColors(
                                                containerColor=Color.White.copy(alpha=.025f),contentColor=Color.White
                                            ),
                                            contentPadding=PaddingValues(0.dp)
                                        ){Text(key,fontSize=16.sp)}
                                    }
                                }
                            }
                        }
                    }
                    2 -> Column(
                        Modifier.fillMaxSize().background(bg).padding(horizontal=10.dp,vertical=6.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                            Box(Modifier.size(39.dp)){
                                Box(
                                    Modifier.width(25.dp).height(18.dp)
                                        .background(Color.White,androidx.compose.foundation.shape.RoundedCornerShape(7.dp))
                                        .align(androidx.compose.ui.Alignment.TopStart)
                                ){
                                    Box(Modifier.size(4.dp).background(Color.Black,androidx.compose.foundation.shape.CircleShape).align(androidx.compose.ui.Alignment.TopStart).offset(6.dp,4.dp))
                                }
                                Box(
                                    Modifier.width(25.dp).height(18.dp)
                                        .background(Color(0xFFB8B8BE),androidx.compose.foundation.shape.RoundedCornerShape(7.dp))
                                        .align(androidx.compose.ui.Alignment.BottomEnd)
                                ){
                                    Box(Modifier.size(4.dp).background(Color.Black,androidx.compose.foundation.shape.CircleShape).align(androidx.compose.ui.Alignment.BottomEnd).offset((-6).dp,(-4).dp))
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)){
                                Text("PYTHON CONSOLE",color=Color.White,fontSize=17.sp,fontWeight=FontWeight.SemiBold,letterSpacing=1.1.sp)
                                Text("CPython 3.14",color=Color(0xFF77777F),fontSize=10.sp,fontFamily=FontFamily.Monospace)
                            }
                            TextButton(onClick={vm.clearOutput()},contentPadding=PaddingValues(8.dp),modifier=Modifier.size(42.dp)){
                                Text("⌫",color=Color(0xFFB8B8BE),fontSize=21.sp)
                            }
                            Spacer(Modifier.width(6.dp))
                            Button(
                                onClick={editorView?.flushCodeChange();if(vm.running) vm.stop() else {runOrigin=consoleRunOrigin;runBurst++;vm.run()}},
                                colors=ButtonDefaults.buttonColors(containerColor=Color.White,contentColor=Color.Black),
                                shape=androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                                contentPadding=PaddingValues(horizontal=18.dp,vertical=10.dp),
                                modifier=Modifier.border(1.dp,Color.White.copy(alpha=.24f),androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                                    .onGloballyPositioned { coords ->
                                        consoleRunOrigin=coords.positionInRoot()+Offset(coords.size.width/2f,coords.size.height/2f)
                                    }
                            ){Text(if(vm.running)"■ Stop" else "▶ Start",fontWeight=FontWeight.Bold)}
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            listOf("Python","Terminal").forEach { mode ->
                                val selected=vm.consoleMode==mode
                                val modeColor by androidx.compose.animation.animateColorAsState(
                                    if(selected) Color(0xFF655C7A) else Color.Transparent,
                                    animationSpec=tween(if(motionAllowed) 220 else 0),label="console mode")
                                val modeScale by animateFloatAsState(if(selected && motionAllowed) 1.04f else 1f,
                                    animationSpec=spring(dampingRatio=Spring.DampingRatioMediumBouncy),label="console mode scale")
                                OutlinedButton(onClick={if(!vm.running){vm.consoleMode=mode;vm.input="";consoleInputValue=TextFieldValue("")}},
                                    colors=ButtonDefaults.outlinedButtonColors(containerColor=modeColor,contentColor=Color.White),
                                    border=BorderStroke(1.dp,Color.White.copy(alpha=if(selected).24f else .14f)),
                                    shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                    modifier=Modifier.graphicsLayer { scaleX=modeScale;scaleY=modeScale }){Text(mode)}
                            }
                            TextButton(onClick={consoleAutoScroll=!consoleAutoScroll}) {
                                Text(if(consoleAutoScroll) "Auto on" else "Auto off",fontSize=11.sp)
                            }
                            TextButton(onClick={
                                consoleSearchSnapshot=vm.fullOutput()
                                consoleSearchQuery=""
                                showConsoleSearch=true
                            }) { Text("Search",fontSize=11.sp) }
                            TextButton(onClick={
                                val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("PY4U Console",vm.fullOutput()))
                            }) { Text("Copy",fontSize=11.sp) }
                        }
                        Spacer(Modifier.height(14.dp))
                        Surface(
                            color=safeColor(vm.consoleBackgroundHex,0xFF030303),
                            shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                            border=BorderStroke(1.dp,Color.White.copy(alpha=.12f)),
                            modifier=Modifier.weight(1f).fillMaxWidth()
                        ){
                            Column(Modifier.fillMaxSize().padding(10.dp)){
                                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                                    AnimatedContent(targetState=vm.consoleMode,label="console prompt",
                                        transitionSpec={ (slideInVertically(tween(180)){it/2}+fadeIn(tween(180))) togetherWith
                                            (slideOutVertically(tween(140)){-it/2}+fadeOut(tween(140))) }) { currentMode ->
                                        Text(if(currentMode=="Terminal") "$" else ">>>",color=Color(0xFFB8B8BE),fontFamily=FontFamily.Monospace,fontSize=12.sp)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if(vm.running)"running ${vm.currentFileName}" else vm.currentFileName,
                                        color=Color(0xFF6F6F77),fontFamily=FontFamily.Monospace,fontSize=11.sp
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                Box(Modifier.weight(1f).fillMaxWidth()) {
                                    Text(vm.output.ifEmpty{"Ready"},color=safeColor(vm.consoleTextHex,0xFFE8E8EC),
                                        fontFamily=FontFamily.Monospace,fontSize=vm.terminalFontSize.sp,
                                        lineHeight=(vm.terminalFontSize+6).sp,
                                        modifier=Modifier.fillMaxSize().verticalScroll(consoleScroll).padding(bottom=if(vm.runtimeIssue!=null)180.dp else 8.dp))
                                    vm.runtimeIssue?.let { issue ->
                                        var showDetails by remember(issue.details) { mutableStateOf(false) }
                                        Surface(color=Color(0xFF151316),shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                                            border=BorderStroke(1.dp,Color(0xFFFF6B81).copy(alpha=.48f)),
                                            modifier=Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                                                .fillMaxWidth().animateContentSize()) {
                                            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                                Text("⚠ ${issue.title}",color=Color(0xFFFF8798),fontWeight=FontWeight.Bold,fontSize=14.sp)
                                                Text("${File(issue.fileName).name}:${issue.line} · ${issue.explanation}",
                                                    color=Color.White,fontSize=13.sp,lineHeight=18.sp,
                                                    modifier=Modifier.clickable {
                                                        if (vm.openRuntimeSource(issue)) scope.launch {
                                                            pager.animateScrollToPage(1)
                                                            editorView?.goToLine(issue.line)
                                                        }
                                                    })
                                                Text("Tap the file and line to open it",color=Color(0xFF9B9BA4),fontSize=10.sp)
                                                if(issue.codeLine.isNotBlank()) Text(issue.codeLine,color=Color(0xFFB8C7FF),fontFamily=FontFamily.Monospace,fontSize=12.sp)
                                                AnimatedVisibility(showDetails) {
                                                    Text(issue.details,color=Color(0xFF9B9BA4),fontFamily=FontFamily.Monospace,fontSize=11.sp,maxLines=6)
                                                }
                                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                                    listOf("Details","Fix","Ask Astro").forEach { label ->
                                                        OutlinedButton(onClick={ when(label){
                                                            "Details" -> showDetails=!showDetails
                                                            "Fix" -> { vm.applyRuntimeFix(); if(vm.runtimeIssue!=null) scope.launch{pager.animateScrollToPage(3)} }
                                                            else -> { vm.prepareRuntimeQuestion(); scope.launch{pager.animateScrollToPage(3)} }
                                                        }},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=4.dp,vertical=7.dp),
                                                            border=BorderStroke(1.dp,Color.White.copy(alpha=.18f)),
                                                            colors=ButtonDefaults.outlinedButtonColors(contentColor=Color.White)) {
                                                            Text(label,fontSize=11.sp,maxLines=1)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                ConsoleKeyToolbar(consoleInputValue,{consoleInputValue=it;vm.input=it.text},vm.inputHistory,consoleInputFocus,vm.input,vm.output.length,consoleScroll,safeColor(vm.toolbarHex,0xFF050505),consoleAutoScroll)
                                Surface(
                                    color=safeColor(vm.consoleBackgroundHex,0xFF050505),
                                    shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                                    border=BorderStroke(1.dp,Color.White.copy(alpha=.16f)),
                                    modifier=Modifier.fillMaxWidth()
                                ){
                                    Row(
                                        Modifier.padding(start=14.dp,end=7.dp,top=5.dp,bottom=5.dp),
                                        verticalAlignment=androidx.compose.ui.Alignment.CenterVertically
                                    ){
                                        Text(if(vm.consoleMode=="Terminal")"$" else ">>>",color=Color.White,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Bold)
                                        TextField(
                                            consoleInputValue,{consoleInputValue=it;vm.input=it.text},
                                            enabled=vm.waitingInput||(vm.consoleMode=="Terminal"&&!vm.running),
                                            singleLine=true,
                                            textStyle=androidx.compose.ui.text.TextStyle(
                                                color=Color.White,
                                                fontFamily=FontFamily.Monospace,
                                                fontSize=16.sp,
                                                fontWeight=FontWeight.Medium
                                            ),
                                            modifier=Modifier.weight(1f).heightIn(min=52.dp).focusRequester(consoleInputFocus),
                                            placeholder={Text(when{vm.waitingInput->"Type program input…";vm.consoleMode=="Terminal"->"Type help, ls, pwd, cat…";else->"Waiting for Python input()"},color=Color(0xFF66666E),fontSize=13.sp)},
                                            colors=TextFieldDefaults.colors(
                                                focusedContainerColor=Color.Transparent,unfocusedContainerColor=Color.Transparent,
                                                disabledContainerColor=Color.Transparent,focusedIndicatorColor=Color.Transparent,
                                                unfocusedIndicatorColor=Color.Transparent,disabledIndicatorColor=Color.Transparent,
                                                focusedTextColor=Color.White,unfocusedTextColor=Color.White,
                                                disabledTextColor=Color.White.copy(alpha=.62f),
                                                cursorColor=Color(0xFF00E5FF),
                                                errorCursorColor=Color(0xFFFF6B81)
                                            ),
                                            keyboardOptions=KeyboardOptions(imeAction=ImeAction.Send),
                                            keyboardActions=KeyboardActions(onSend={vm.submitConsoleEntry()})
                                        )
                                        Button(
                                            onClick={vm.submitConsoleEntry()},
                                            enabled=vm.waitingInput||(vm.consoleMode=="Terminal"&&!vm.running&&vm.input.isNotBlank()),
                                            shape=androidx.compose.foundation.shape.CircleShape,
                                            contentPadding=PaddingValues(0.dp),
                                            colors=ButtonDefaults.buttonColors(
                                                containerColor=Color.White,contentColor=Color.Black,
                                                disabledContainerColor=Color(0xFF1A1A1D),disabledContentColor=Color(0xFF66666E)
                                            ),
                                            modifier=Modifier.size(42.dp)
                                        ){Text("➜",fontSize=20.sp)}
                                    }
                                }
                            }
                        }
                    }
                    3 -> Column(
                        Modifier.fillMaxSize().background(bg).padding(horizontal=14.dp,vertical=10.dp),
                        verticalArrangement=Arrangement.spacedBy(9.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text("PY4U  •  CODE ANYWHERE",color=Color(0xFF858993),fontSize=8.sp,letterSpacing=1.2.sp)
                                Text("Astro",color=Color.White,fontSize=25.sp,fontWeight=FontWeight.Bold)
                                Text("Your AI coding companion",color=Color(0xFF858993),fontSize=13.sp)
                            }
                            TextButton(onClick={showAiSettings=true},contentPadding=PaddingValues(horizontal=8.dp,vertical=2.dp)) {
                                Text(
                                    "PLAN\nCODE\nLEARN\nTOGETHER",
                                    color=Color(0xFF777B84),fontSize=8.sp,lineHeight=11.sp,
                                    textAlign=androidx.compose.ui.text.style.TextAlign.Start,
                                    letterSpacing=1.sp
                                )
                            }
                        }
                        Column(
                            Modifier.weight(1f).fillMaxWidth().verticalScroll(aiScroll),
                            verticalArrangement=Arrangement.spacedBy(16.dp)
                        ) {
                            if(vm.aiMessages.isEmpty()) {
                                Column(Modifier.fillMaxWidth().padding(top=34.dp),horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally) {
                                    Surface(
                                        color=Color(0xFF080808),shape=androidx.compose.foundation.shape.CircleShape,
                                        border=BorderStroke(1.dp,Color(0xFF5B6069)),modifier=Modifier.size(68.dp)
                                    ) {
                                        Box(contentAlignment=androidx.compose.ui.Alignment.Center) {
                                            Column(horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally) {
                                                Text("✦",color=Color.White,fontSize=16.sp)
                                                Text("PY4U",color=Color.White,fontSize=12.sp,fontWeight=FontWeight.Bold)
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Text("Ask Astro about Python or your code",color=Color(0xFF858993),fontSize=13.sp)
                                }
                            }
                            vm.aiMessages.forEach { message ->
                                if(message.fromUser) {
                                    Column(Modifier.fillMaxWidth(),horizontalAlignment=androidx.compose.ui.Alignment.End) {
                                        Text("You   now",color=Color(0xFF858993),fontSize=10.sp,modifier=Modifier.padding(end=8.dp,bottom=4.dp))
                                        Surface(
                                            color=safeColor(vm.userBubbleHex,0xFF292A2D),
                                            contentColor=Color.White,
                                            shape=androidx.compose.foundation.shape.RoundedCornerShape(vm.bubbleRadius.dp),
                                            modifier=Modifier.widthIn(max=vm.bubbleWidth.dp)
                                        ) {
                                            MarkdownMessage(message.text,Color.White,Modifier.padding(horizontal=14.dp,vertical=11.dp))
                                        }
                                    }
                                } else if(message.text.isNotBlank()) {
                                    Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.Top,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                                        Surface(
                                            color=Color(0xFF080808),shape=androidx.compose.foundation.shape.CircleShape,
                                            border=BorderStroke(1.dp,Color(0xFF5B6069)),modifier=Modifier.size(45.dp)
                                        ) {
                                            Box(contentAlignment=androidx.compose.ui.Alignment.Center) {
                                                Column(horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally) {
                                                    Text("✦",color=Color.White,fontSize=11.sp)
                                                    Text("PY4U",color=Color.White,fontSize=8.sp,fontWeight=FontWeight.Bold)
                                                }
                                            }
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text("Astro   now",color=Color(0xFF858993),fontSize=10.sp,modifier=Modifier.padding(start=3.dp,bottom=5.dp))
                                            Surface(
                                                color=safeColor(vm.helperBubbleHex,0xFF030303),contentColor=Color.White,
                                                shape=androidx.compose.foundation.shape.RoundedCornerShape(vm.bubbleRadius.dp),
                                                border=BorderStroke(1.dp,Color(0xFF393C42)),
                                                modifier=Modifier.fillMaxWidth()
                                            ) {
                                                MarkdownMessage(message.text,Color.White,Modifier.padding(horizontal=14.dp,vertical=13.dp))
                                            }
                                        }
                                    }
                                }
                            }
                            if(vm.aiBusy && vm.aiMessages.isNotEmpty()) Text(vm.aiProgress ?: "Astro is writing…",color=Color(0xFF8D929A),fontSize=11.sp)
                        }
                        vm.aiFallbackNotice?.let { status ->
                            SwitchingModelNotice(status=status,accent=accent,onFinished={vm.aiFallbackNotice=null})
                        }
                        vm.pendingCode?.takeIf { vm.showCodeNotice }?.let { change ->
                            AchievementNotice(
                                badge="ACTION REQUIRED",
                                title="Code change ready",
                                subtitle="Astro wants permission to update ${change.fileName}",
                                accent=accent,
                                primaryLabel="Preview",
                                secondaryLabel="Ignore",
                                onPrimary={showCodePreview=true;vm.showCodeNotice=false},
                                onSecondary={vm.rejectPendingCode()}
                            )
                        }
                        vm.teachingOffer?.takeIf { vm.showTeachingNotice }?.let { topic ->
                            AchievementNotice(
                                badge="NEW LESSON UNLOCKED",
                                title=topic,
                                subtitle="A short interactive Python lesson is ready",
                                accent=accent,
                                primaryLabel="Teach me",
                                secondaryLabel="Ignore",
                                onPrimary={vm.acceptTeaching()},
                                onSecondary={vm.rejectTeaching()}
                            )
                        }
                        vm.pendingCode?.let { change ->
                            Surface(color=accent.copy(alpha=.10f),shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                border=BorderStroke(1.dp,accent.copy(alpha=.4f))) {
                                Row(Modifier.fillMaxWidth().padding(9.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Code edit ready · ${change.fileName}",color=Color.White,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                                        Text("Review the changed lines before applying",color=Color.LightGray,fontSize=10.sp)
                                    }
                                    TextButton(onClick={vm.rejectPendingCode()}) { Text("Dismiss",fontSize=11.sp) }
                                    Button(onClick={showCodePreview=true},contentPadding=PaddingValues(horizontal=8.dp,vertical=2.dp)) { Text("Preview",fontSize=11.sp) }
                                }
                            }
                        }
                        vm.teachingOffer?.let { topic ->
                            Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                Text("Lesson: $topic",color=Color.LightGray,fontSize=11.sp,modifier=Modifier.weight(1f),maxLines=1)
                                TextButton(onClick={vm.rejectTeaching()}) { Text("Dismiss",fontSize=11.sp) }
                                TextButton(onClick={vm.acceptTeaching()}) { Text("Teach me",fontSize=11.sp) }
                            }
                        }
                        if(vm.attachedFileName!=null || vm.shareCode) {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement=Arrangement.spacedBy(7.dp)
                            ) {
                                vm.attachedFileName?.let { name ->
                                    AssistChip(
                                        onClick={vm.clearAttachment()},
                                        label={Text("Attached  $name  ×",maxLines=1)},
                                        colors=AssistChipDefaults.assistChipColors(labelColor=Color.White,containerColor=Color(0xFF202124))
                                    )
                                }
                                if(vm.shareCode) AssistChip(
                                    onClick={vm.shareCode=false},
                                    label={Text("Sharing  ${vm.currentFileName}  ×")},
                                    colors=AssistChipDefaults.assistChipColors(labelColor=Color.White,containerColor=Color(0xFF202124))
                                )
                            }
                        }
                        Surface(
                            color=Color(0xFF050505),
                            shape=androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
                            border=BorderStroke(1.dp,Color(0xFF42454B)),
                            modifier=Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=6.dp),
                                verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement=Arrangement.spacedBy(5.dp)
                            ) {
                                TextButton(
                                    onClick={attachmentLauncher.launch("*/*")},
                                    contentPadding=PaddingValues(8.dp),
                                    modifier=Modifier.size(42.dp)
                                ){Text("📎",color=Color.White,fontSize=20.sp)}
                                TextField(
                                    vm.aiPrompt,{vm.aiPrompt=it},
                                    modifier=Modifier.weight(1f),
                                    placeholder={Text("Message Astro…",color=Color(0xFF777B84))},
                                    maxLines=4,
                                    colors=TextFieldDefaults.colors(
                                        focusedContainerColor=Color.Transparent,unfocusedContainerColor=Color.Transparent,
                                        focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent,
                                        focusedTextColor=Color.White,unfocusedTextColor=Color.White
                                    )
                                )
                                TextButton(
                                    onClick={vm.shareCode=!vm.shareCode},
                                    contentPadding=PaddingValues(5.dp),
                                    modifier=Modifier.size(37.dp)
                                ){Text("</>",color=if(vm.shareCode) Color.White else Color(0xFF777B84),fontSize=10.sp,fontWeight=FontWeight.Bold)}
                                Button(
                                    onClick={vm.askAi()},
                                    enabled=!vm.aiBusy&&(vm.aiPrompt.isNotBlank()||vm.attachedFileName!=null),
                                    colors=ButtonDefaults.buttonColors(containerColor=Color.White,contentColor=Color.Black,disabledContainerColor=Color(0xFF34363A)),
                                    contentPadding=PaddingValues(0.dp),
                                    modifier=Modifier.size(44.dp),
                                    shape=androidx.compose.foundation.shape.CircleShape
                                ){Text("➤",fontSize=18.sp)}
                            }
                        }
                    }
                    else -> Column(Modifier.fillMaxSize().padding(horizontal=18.dp,vertical=12.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                            if(settingsSection!="Overview") TextButton(onClick={settingsSection="Overview"},contentPadding=PaddingValues(end=12.dp)){Text("‹ Back")}
                            Column(Modifier.weight(1f)){Text(if(settingsSection=="Overview") "SETTINGS" else settingsSection.uppercase(),color=accent,fontSize=18.sp);Text(if(settingsSection=="Overview") "Make PY4U yours" else "Focused controls",color=Color.Gray,fontSize=11.sp)}
                        }
                        TextField(vm.settingsQuery,{vm.settingsQuery=it},singleLine=true,modifier=Modifier.fillMaxWidth(),placeholder={Text("Search every setting…")})
                        val settingsSearch = vm.settingsQuery.trim()
                        fun searchMatches(vararg terms:String):Boolean =
                            settingsSearch.isNotBlank() && terms.any { it.contains(settingsSearch,true) || settingsSearch.contains(it,true) }
                        if(settingsSection=="Overview" && settingsSearch.isBlank()){
                            SettingsCategory("Appearance","Theme, accents and component colors",accent){settingsSection="Appearance"}
                            SettingsCategory("Editor","Text, cursor, autocomplete and saving",accent){settingsSection="Editor"}
                            SettingsCategory("Fonts","100 downloadable typefaces",accent){settingsSection="Fonts"}
                            SettingsCategory("Motion","13 quiet interface animations",accent){settingsSection="Motion"}
                            SettingsCategory("Layout","Header, tabs, toolbar and spacing",accent){settingsSection="Layout"}
                            SettingsCategory("Console & Helper","Output and chat appearance",accent){settingsSection="Console & Helper"}
                            SettingsCategory("AI","API keys, offline GGUF models and fallback",accent){settingsSection="AI"}
                            SettingsCategory("System","Runtime and interface switches",accent){settingsSection="System"}
                        }
                        if(settingsSection=="AI" || searchMatches("ai", "api key", "offline model", "gguf")) {
                            Text("ASTRO AI", color=accent, fontSize=12.sp, fontWeight=FontWeight.Bold)
                            Text("Choose an API provider or import a GGUF file to run a model on this phone. Main, Second and Third can act as fallbacks.", color=Color.LightGray, fontSize=12.sp)
                            Button(onClick={showAiSettings=true}) { Text("Open AI connections") }
                            listOf(0,1,2).forEach { slot ->
                                val provider = vm.providerForSlot(slot)
                                Text("${listOf("Main","Second","Third")[slot]} · $provider · ${if(vm.slotConfigured(slot)) "Ready" else "Not configured"}", color=Color.LightGray, fontSize=12.sp)
                            }
                        }
                        if(settingsSection=="Appearance" || searchMatches("appearance","theme","accent color","background","editor token colors","comments","strings","numbers","keywords","functions","variables")){
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
                        if(settingsSection=="Console & Helper" || searchMatches("console","helper","chat","bubble","terminal","keyboard toolbar","run button","stop button","typing animation","component colors")){
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
                        SettingSwitch("Astro typing animation",vm.typingAnimation){vm.typingAnimation=it;vm.saveAppearance()}
                        if(vm.typingAnimation) {
                            Text("Astro word delay  ${(vm.animationDuration/6.7f).toInt()} ms",color=text)
                            Slider(vm.animationDuration,{vm.animationDuration=it;vm.saveAppearance()},valueRange=30f..400f)
                        }
                        }
                        if(settingsSection=="Motion" || searchMatches("motion","animation","fade","scale","parallax","spring")){
                        Text("MOTION",color=accent,fontSize=12.sp)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){
                            listOf("Aurora glide","Fluid spring","Soft fade","Subtle scale","Shared element","Smooth blur reveal","Layered depth","Gentle parallax","Card expansion","Natural sheet","Magnetic snap","Interactive swipe","Content morph","Keyboard lift").forEach{motion->
                                FilterChip(selected=vm.motionStyle==motion,onClick={vm.motionStyle=motion;vm.saveAppearance()},label={Text(motion)})
                            }
                        }
                        Text("Motion intensity  ${(vm.motionIntensity*100).toInt()}%",color=text)
                        Slider(vm.motionIntensity,{vm.motionIntensity=it;vm.saveAppearance()},valueRange=0.1f..1f)
                        SettingSwitch("Interface motion",vm.motionEnabled){vm.motionEnabled=it;vm.saveAppearance()}
                        }
                        if(settingsSection=="Layout" || searchMatches("layout","header","tab","toolbar height","page dot","interface scale","padding","file information")){
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
                        if(settingsSection=="Editor" || searchMatches("editor","font size","line spacing","highlight delay","autosave","ghost text","cursor","word wrap","syntax","autocomplete","line numbers","saving","tab width","indentation")){
                        Text("Editor font  ${vm.editorFontSize.toInt()} sp",color=text)
                        Slider(vm.editorFontSize,{vm.editorFontSize=it;vm.saveAppearance()},valueRange=12f..28f,steps=15)
                        Text("Tab width  ${vm.tabWidth} spaces",color=text)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            listOf(2, 4, 8).forEach { width ->
                                FilterChip(selected=vm.tabWidth==width,onClick={vm.tabWidth=width;vm.saveAppearance()},label={Text("$width spaces")})
                            }
                        }
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
                        if(settingsSection=="Fonts" || searchMatches("fonts","font family","font vault","typeface") || (settingsSearch.isNotBlank() && FONT_VAULT.any{it.first.contains(settingsSearch,true)})){
                        Text("Font family",color=text)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Monospace","Sans","Serif").forEach{font->FilterChip(selected=vm.fontName==font,onClick={vm.fontName=font;vm.customFontPath="";vm.saveAppearance()},label={Text(font)})}}
                        Text("FONT VAULT  •  ${FONT_VAULT.size} REAL FONTS",color=accent,fontSize=12.sp)
                        Text(vm.fontStatus,color=Color.Gray,fontSize=11.sp)
                        val genericFontSearch = searchMatches("fonts","font family","font vault","typeface")
                        FONT_VAULT.filter { settingsSearch.isBlank() || genericFontSearch || it.first.contains(settingsSearch,true) }.forEach { font ->
                            Surface(color=Color.White.copy(alpha=0.055f),shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth().border(1.dp,Color.White.copy(alpha=0.12f),androidx.compose.foundation.shape.RoundedCornerShape(14.dp))){
                            Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                                Column(Modifier.weight(1f)){Text(font.first,color=text,fontSize=14.sp);Text("Google Fonts · OFL",color=Color.DarkGray,fontSize=9.sp)}
                                TextButton(onClick={vm.installVaultFont(context,font.first,font.second)},enabled=!vm.fontStatus.startsWith("Downloading")){Text(if(vm.fontName==font.first)"Installed" else "Download")}
                            }}
                        }
                        }
                        if(settingsSection=="System" || searchMatches("system","programming toolbar","page indicator","runtime","python")){
                        SettingSwitch("Programming toolbar",vm.showToolbar){vm.showToolbar=it;vm.saveAppearance()}
                        SettingSwitch("Page indicator dots",vm.showPageDots){vm.showPageDots=it;vm.saveAppearance()}
                        HorizontalDivider(color=Color(0xFF202020))
                        Text("Swipe left or right anywhere outside active text editing to move between pages.",color=Color.Gray,fontSize=12.sp)
                        Text("Python  ${vm.runtimeVersion.substringBefore('\n')}",color=Color.Gray,fontSize=11.sp)
                        }
                    }
                } }
            }
            if(vm.showPageDots && !keyboardOpen) Row(Modifier.fillMaxWidth().height(22.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                repeat(5){index->Box(Modifier.padding(horizontal=3.dp).size(if(index==pager.currentPage)vm.pageDotSize.dp else (vm.pageDotSize*0.62f).dp).background(if(index==pager.currentPage)accent else Color.DarkGray,androidx.compose.foundation.shape.CircleShape))}
            }
        }
        RunBurst(runBurst,vm.currentFileName,motionAllowed,runOrigin)
        }
    }
    fileActionTarget?.let { saved ->
        AlertDialog(
            onDismissRequest={fileActionTarget=null},containerColor=Color(0xFF171A20),
            title={Text(saved.fileName)},
            text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                TextButton(onClick={
                    vm.duplicateProjectFile(saved.fileName)
                    fileActionTarget=null
                },modifier=Modifier.fillMaxWidth()) { Text("Duplicate") }
                TextButton(onClick={
                    renameTarget=saved
                    renameDraft=saved.name
                    fileActionTarget=null
                },modifier=Modifier.fillMaxWidth()) { Text("Rename") }
                TextButton(onClick={
                    vm.deleteProjectFile(saved.fileName)
                    fileActionTarget=null
                },modifier=Modifier.fillMaxWidth()) { Text("Move to Trash",color=Color(0xFFFF9AA3)) }
            }},
            confirmButton={TextButton(onClick={fileActionTarget=null}) { Text("Close") }}
        )
    }
    renameTarget?.let { saved ->
        AlertDialog(
            onDismissRequest={renameTarget=null},containerColor=Color(0xFF171A20),
            title={Text("Rename ${saved.fileName}")},
            text={OutlinedTextField(
                value=renameDraft,
                onValueChange={renameDraft=it.take(60)},
                label={Text("File name")},
                singleLine=true
            )},
            confirmButton={TextButton(
                enabled=renameDraft.isNotBlank(),
                onClick={
                    vm.renameProjectFile(saved.fileName,renameDraft)
                    renameTarget=null
                }
            ) { Text("Rename") }},
            dismissButton={TextButton(onClick={renameTarget=null}) { Text("Cancel") }}
        )
    }
    if(showTrash) AlertDialog(
        onDismissRequest={showTrash=false},containerColor=Color(0xFF171A20),
        title={Text("Recently deleted")},
        text={Column(Modifier.heightIn(max=430.dp).verticalScroll(rememberScrollState())) {
            if(vm.deletedFiles.isEmpty()) Text("Trash is empty.",color=Color.LightGray)
            vm.deletedFiles.forEach { deleted ->
                TextButton(onClick={
                    vm.restoreDeletedFile(deleted.trashName)
                    showTrash=false
                },modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(deleted.originalName,color=Color.White)
                        Text("Tap to restore",color=Color.Gray,fontSize=10.sp)
                    }
                }
            }
        }},
        confirmButton={TextButton(onClick={showTrash=false}) { Text("Close") }}
    )
    if(showProjectTemplates) AlertDialog(
        onDismissRequest={showProjectTemplates=false},containerColor=Color(0xFF171A20),
        title={Text("New project")},
        text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState())) {
            ProjectWorkspace.templates.forEach { template ->
                TextButton(onClick={vm.makeNewProject(template);showProjectTemplates=false},
                    modifier=Modifier.fillMaxWidth()) { Text(template) }
            }
        }},
        confirmButton={TextButton(onClick={showProjectTemplates=false}) { Text("Cancel") }}
    )
    if(showPalette) AlertDialog(
        onDismissRequest={showPalette=false},containerColor=Color(0xFF171A20),
        title={Text("Commands")},
        text={Column(Modifier.heightIn(max=430.dp)) {
            OutlinedTextField(paletteQuery,{paletteQuery=it},label={Text("Search commands")},
                singleLine=true,modifier=Modifier.fillMaxWidth())
            val commands = listOf(if(vm.running) "Stop program" else "Run Python file",
                "Save file","Find in file","Find in project","Replace in file","Go to line","Open file",
                "Open Settings","Ask Astro")
            Column(Modifier.verticalScroll(rememberScrollState())) {
                commands.filter { it.contains(paletteQuery,true) }.forEach { command ->
                    TextButton(onClick={
                        showPalette=false
                        when(command) {
                            "Stop program" -> vm.stop()
                            "Run Python file" -> {editorView?.flushCodeChange();vm.run();scope.launch{pager.animateScrollToPage(2)}}
                            "Save file" -> {editorView?.flushCodeChange();vm.save()}
                            "Find in file" -> {showFind=true;scope.launch{pager.animateScrollToPage(1)}}
                            "Find in project" -> {
                                editorView?.flushCodeChange()
                                vm.save()
                                projectSearchQuery=""
                                vm.searchProject("")
                                showProjectSearch=true
                            }
                            "Replace in file" -> {showFind=true;showReplace=true;scope.launch{pager.animateScrollToPage(1)}}
                            "Go to line" -> showGoToLine=true
                            "Open file" -> showQuickOpen=true
                            "Open Settings" -> scope.launch{pager.animateScrollToPage(4)}
                            "Ask Astro" -> scope.launch{pager.animateScrollToPage(3)}
                        }
                    },modifier=Modifier.fillMaxWidth()) { Text(command) }
                }
            }
        }},
        confirmButton={TextButton(onClick={showPalette=false}) { Text("Close") }}
    )
    if(showConsoleSearch) AlertDialog(
        onDismissRequest={showConsoleSearch=false},containerColor=Color(0xFF171A20),
        title={Text("Search Console")},
        text={Column(Modifier.heightIn(max=480.dp)) {
            OutlinedTextField(
                value=consoleSearchQuery,
                onValueChange={consoleSearchQuery=it},
                label={Text("Find output")},
                singleLine=true,
                modifier=Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            val hits = remember(consoleSearchSnapshot,consoleSearchQuery) {
                ConsoleSearch.search(consoleSearchSnapshot,consoleSearchQuery)
            }
            when {
                consoleSearchQuery.isBlank() -> Text("Search the retained Console output.",color=Color.LightGray)
                hits.isEmpty() -> Text("No matches.",color=Color.LightGray)
                else -> {
                    Text("${hits.size} match${if(hits.size==1) "" else "es"}",color=Color.Gray,fontSize=11.sp)
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        hits.forEach { hit ->
                            TextButton(onClick={
                                val totalLines=(consoleSearchSnapshot.count { it=='\n' }+1).coerceAtLeast(1)
                                val target=if(totalLines<=1) 0 else
                                    ((consoleScroll.maxValue.toLong()*(hit.line-1))/(totalLines-1)).toInt()
                                showConsoleSearch=false
                                scope.launch { consoleScroll.animateScrollTo(target.coerceIn(0,consoleScroll.maxValue)) }
                            },modifier=Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text("Line ${hit.line} · column ${hit.column}",color=accent,fontSize=11.sp,fontWeight=FontWeight.SemiBold)
                                    Text(hit.lineText.trim().take(180),color=Color.LightGray,fontFamily=FontFamily.Monospace,fontSize=11.sp,maxLines=2)
                                }
                            }
                        }
                    }
                }
            }
        }},
        confirmButton={TextButton(onClick={showConsoleSearch=false}) { Text("Close") }}
    )
    if(showProjectSearch) AlertDialog(
        onDismissRequest={showProjectSearch=false},containerColor=Color(0xFF171A20),
        title={Text("Find in project")},
        text={Column(Modifier.heightIn(max=480.dp)) {
            OutlinedTextField(
                value=projectSearchQuery,
                onValueChange={
                    projectSearchQuery=it
                    vm.searchProject(it)
                },
                label={Text("Search Python files")},
                singleLine=true,
                modifier=Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            when {
                projectSearchQuery.isBlank() -> Text("Type to search every Python file in this project.",color=Color.LightGray)
                vm.projectSearchBusy -> Text("Searching…",color=Color.LightGray)
                vm.projectSearchResults.isEmpty() -> Text("No matches.",color=Color.LightGray)
                else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    vm.projectSearchResults.forEach { hit ->
                        TextButton(
                            enabled=!vm.running,
                            onClick={
                                editorView?.flushCodeChange()
                                vm.openProjectFile(hit.fileName)
                                showProjectSearch=false
                                scope.launch {
                                    pager.animateScrollToPage(1)
                                    delay(90)
                                    editorView?.goToLine(hit.line)
                                }
                            },
                            modifier=Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text("${hit.fileName}:${hit.line}",color=accent,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                                Text(hit.lineText.trim().take(160),color=Color.LightGray,fontFamily=FontFamily.Monospace,fontSize=11.sp,maxLines=2)
                            }
                        }
                    }
                }
            }
        }},
        confirmButton={TextButton(onClick={showProjectSearch=false}) { Text("Close") }}
    )
    if(showQuickOpen) AlertDialog(
        onDismissRequest={showQuickOpen=false},containerColor=Color(0xFF171A20),
        title={Text("Open Python file")},
        text={Column(Modifier.heightIn(max=430.dp)) {
            OutlinedTextField(quickOpenQuery,{quickOpenQuery=it},label={Text("File name")},
                singleLine=true,modifier=Modifier.fillMaxWidth())
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if(vm.running) Text("Stop the program before switching files.",color=Color.LightGray)
                vm.savedCodes.filter { it.name.contains(quickOpenQuery,true) }.forEach { saved ->
                    TextButton(enabled=!vm.running,onClick={
                        editorView?.flushCodeChange()
                        vm.openProjectFile(saved.fileName)
                        showQuickOpen=false
                        scope.launch{pager.animateScrollToPage(1)}
                    },modifier=Modifier.fillMaxWidth()) { Text(saved.name + ".py") }
                }
            }
        }},
        confirmButton={TextButton(onClick={showQuickOpen=false}) { Text("Close") }}
    )
    if(showHistory) AlertDialog(
        onDismissRequest={showHistory=false},containerColor=Color(0xFF171A20),
        title={Text("History · ${vm.currentFileName}")},
        text={Column(Modifier.heightIn(max=450.dp).verticalScroll(rememberScrollState())) {
            val versions = remember(vm.currentFileName, showHistory) { vm.historyVersions() }
            if (versions.isEmpty()) Text("No earlier versions yet.",color=Color.LightGray)
            versions.forEach { version ->
                TextButton(onClick={chosenVersion=version}) {
                    Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(version.nameWithoutExtension.toLongOrNull() ?: 0L)),
                        color=if(chosenVersion==version) accent else Color.White)
                }
            }
            if (chosenVersion != null) {
                Text("Preview (first 3,000 characters)",color=accent,fontSize=11.sp)
                Text(historyPreview,color=Color.LightGray,fontFamily=FontFamily.Monospace,fontSize=11.sp)
            }
        }},
        confirmButton={TextButton(enabled=chosenVersion!=null,onClick={
            chosenVersion?.let(vm::restoreHistory)
            showHistory=false
        }) { Text("Restore") }},
        dismissButton={TextButton(onClick={showHistory=false}) { Text("Cancel") }}
    )
    if(showGoToLine) AlertDialog(
        onDismissRequest={showGoToLine=false},containerColor=Color(0xFF171A20),
        title={Text("Go to line")},
        text={OutlinedTextField(lineDraft,{lineDraft=it.filter(Char::isDigit).take(8)},
            label={Text("Line number")},singleLine=true)},
        confirmButton={TextButton(onClick={
            lineDraft.toIntOrNull()?.let { editorView?.goToLine(it) }
            showGoToLine=false
        }) { Text("Go") }},
        dismissButton={TextButton(onClick={showGoToLine=false}) { Text("Cancel") }}
    )
    if(showProblems) AlertDialog(
        onDismissRequest={showProblems=false},containerColor=Color(0xFF171A20),
        title={Text("Problems · ${vm.currentFileName}")},
        text={Column(Modifier.heightIn(max=450.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if (vm.codeDiagnostics.isEmpty()) Text("No problems in this file.",color=Color.LightGray)
            vm.codeDiagnostics.forEach { issue ->
                TextButton(onClick={showProblems=false;selectedProblem=issue},modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text("Line ${issueLineNumber(vm.code,issue.start)} · ${issue.message}",color=if(issue.fatal) Color(0xFFFF9AA3) else Color(0xFFFFC88A),fontSize=13.sp)
                        Text(issueLineText(vm.code,issue.start).trim().take(120),color=Color.LightGray,fontFamily=FontFamily.Monospace,fontSize=11.sp,maxLines=1)
                    }
                }
            }
        }},
        confirmButton={TextButton(onClick={showProblems=false}) { Text("Close") }}
    )
    selectedProblem?.let { issue ->
        val line = issueLineNumber(vm.code,issue.start)
        AlertDialog(
            onDismissRequest={selectedProblem=null},containerColor=Color(0xFF171A20),
            title={Text("Problem · line $line")},
            text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("What happened",color=accent,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                Text(issue.message,color=Color.White,fontSize=13.sp)
                Text("Where",color=accent,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                Text("${vm.currentFileName}:$line  ${issueLineText(vm.code,issue.start).trim().take(120)}",color=Color.LightGray,fontFamily=FontFamily.Monospace,fontSize=12.sp)
                Text("Why Python dislikes it",color=accent,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                Text(issueWhy(issue.message),color=Color.LightGray,fontSize=13.sp)
                Text("Possible fix",color=accent,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                Text(issueFix(issue.message),color=Color.LightGray,fontSize=13.sp)
            }},
            confirmButton={TextButton(onClick={
                selectedProblem=null
                editorView?.let { view ->
                    val offset = issue.start.coerceIn(0,view.text?.length ?: 0)
                    view.setSelection(offset)
                    view.requestFocus()
                }
            }) { Text("Go to code") }},
            dismissButton={TextButton(onClick={selectedProblem=null}) { Text("Close") }}
        )
    }
    if(showCodePreview) vm.pendingCode?.let { change ->
        val preview = remember(change) { codeChangePreview(change.sourceSnapshot, change.code) }
        AlertDialog(
            onDismissRequest={showCodePreview=false},containerColor=Color(0xFF171A20),
            title={Text("Review edit · ${change.fileName}",fontSize=17.sp)},
            text={Column(Modifier.heightIn(max=450.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(preview.summary,color=accent,fontSize=12.sp)
                Text("BEFORE",color=Color(0xFFFF8895),fontSize=10.sp,fontWeight=FontWeight.Bold)
                Text(preview.before,color=Color(0xFFFFC0C7),fontFamily=FontFamily.Monospace,fontSize=12.sp,
                    modifier=Modifier.fillMaxWidth().background(Color(0xFF292126)).padding(10.dp))
                Text("AFTER",color=Color(0xFF81DDAF),fontSize=10.sp,fontWeight=FontWeight.Bold)
                Text(preview.after,color=Color(0xFFB7F5D4),fontFamily=FontFamily.Monospace,fontSize=12.sp,
                    modifier=Modifier.fillMaxWidth().background(Color(0xFF1E2B25)).padding(10.dp))
                Text("Only changed lines shown. Your file stays untouched until you tap Apply.",color=Color.Gray,fontSize=10.sp)
            }},
            confirmButton={Button(onClick={vm.applyPendingCode();showCodePreview=false}) { Text("Apply edit") }},
            dismissButton={TextButton(onClick={showCodePreview=false}) { Text("Keep editing") }}
        )
    }
    if(showAiSettings) AlertDialog(
        onDismissRequest={showAiSettings=false},
        containerColor=Color(0xFF0A0A0A),
        title={Text("AI fallback chain")},
        text={
            Column(
                Modifier.heightIn(max=560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(10.dp)
            ) {
                Text("Configure up to three independent providers  PY4U tries them in order",fontSize=12.sp,color=Color(0xFFB8BEC7))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf("Main","Second","Third").forEachIndexed { index,label ->
                        FilterChip(
                            selected=selectedAiSlot==index,
                            onClick={selectedAiSlot=index},
                            label={Text(label)},
                            leadingIcon=if(vm.slotConfigured(index)) {{Text("✓",color=Color(0xFF00E676))}} else null,
                            modifier=Modifier.weight(1f)
                        )
                    }
                }
                Text("${listOf("MAIN","SECOND","THIRD")[selectedAiSlot]} AI",color=MaterialTheme.colorScheme.primary,fontSize=11.sp,fontWeight=FontWeight.Bold)
                Text("Connection type",fontSize=11.sp,color=Color.Gray)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=providerDraft!="On-device",onClick={if(providerDraft=="On-device") providerDraft="Auto"},label={Text("API key")})
                    FilterChip(selected=providerDraft=="On-device",onClick={providerDraft="On-device"},label={Text("On-device GGUF")})
                }
                if(providerDraft=="On-device") {
                    Text("Runs offline on this phone. Import a quantized .gguf model; large models need plenty of storage and RAM.",color=Color.LightGray,fontSize=12.sp)
                    Button(onClick={modelPicker.launch(arrayOf("*/*"))}) { Text("Choose GGUF file") }
                    Text("PERFORMANCE",color=MaterialTheme.colorScheme.primary,fontSize=11.sp,fontWeight=FontWeight.Bold)
                    Text("Context size · more code/history uses more RAM",color=Color.LightGray,fontSize=11.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        listOf(2048,4096,8192).forEach { value ->
                            FilterChip(selected=vm.localContextSize==value,onClick={vm.saveLocalPerformance(contextSize=value)},label={Text("$value")})
                        }
                    }
                    Text("CPU threads",color=Color.LightGray,fontSize=11.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        listOf(2,4,6,8).forEach { value ->
                            FilterChip(selected=vm.localThreads==value,onClick={vm.saveLocalPerformance(threads=value)},label={Text("$value")})
                        }
                    }
                    Text("Max reply tokens",color=Color.LightGray,fontSize=11.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        listOf(256,512,1024,2048).forEach { value ->
                            FilterChip(selected=vm.localResponseTokens==value,onClick={vm.saveLocalPerformance(responseTokens=value)},label={Text("$value")})
                        }
                    }
                    val selectedPath = vm.localModelForSlot(selectedAiSlot)
                    if(selectedPath.isNotBlank()) {
                        val modelBytes = File(selectedPath).length()
                        val memory = ActivityManager.MemoryInfo()
                        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
                        Text("Selected: ${File(selectedPath).name} · ${modelBytes / 1048576} MB", color=Color.LightGray, fontSize=12.sp)
                        if(modelBytes > memory.totalMem / 2) Text("⚠ This model is over half your phone's RAM. Loading may fail; try a smaller quantized GGUF.",color=Color(0xFFFFBB77),fontSize=11.sp)
                        OutlinedButton(onClick={vm.removeLocalModel(selectedAiSlot)}) { Text("Remove from slot") }
                    }
                    vm.localImportStatus?.let { Text(it,color=Color.LightGray,fontSize=12.sp) }
                } else {
                    TextField(keyDraft,{keyDraft=it},label={Text(if(vm.slotConfigured(selectedAiSlot)) "New API key (optional)" else "API key")},singleLine=true,
                        visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
                    Text("Provider",fontSize=11.sp,color=Color.Gray)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        listOf("Auto","OpenAI","Gemini","Claude","OpenRouter","Groq","Custom").forEach { provider ->
                            FilterChip(selected=providerDraft==provider,onClick={providerDraft=provider},label={Text(provider)})
                        }
                    }
                    TextField(modelDraft,{modelDraft=it},label={Text("Model name")},singleLine=true,modifier=Modifier.fillMaxWidth())
                    TextField(endpointDraft,{endpointDraft=it},label={Text("API endpoint or compatible link")},singleLine=true,modifier=Modifier.fillMaxWidth())
                }
                Text(
                    when(selectedAiSlot) { 0->"Used first";1->"Used automatically if Main fails";else->"Used if Main and Second fail" },
                    color=Color.Gray,fontSize=11.sp
                )
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick={
                            vm.saveAiSettings(keyDraft,endpointDraft,modelDraft,providerDraft,selectedAiSlot)
                            keyDraft=""
                            vm.askAi(testOnly=true,preferredSlot=selectedAiSlot)
                        },
                        modifier=Modifier.weight(1f)
                    ){Text("Save & test")}
                    if(providerDraft!="On-device" && vm.slotConfigured(selectedAiSlot)) OutlinedButton(
                        onClick={vm.removeAiKey(selectedAiSlot)},
                        colors=ButtonDefaults.outlinedButtonColors(contentColor=Color(0xFFFF3D71))
                    ){Text("Remove")}
                }
                vm.aiTestStatus?.let { status ->
                    Text(status,color=if(status.contains("successful",true)) Color(0xFF7EE787) else Color(0xFFB8BEC7),fontSize=11.sp)
                }
            }
        },
        confirmButton={
            Button(onClick={
                vm.saveAiSettings(keyDraft,endpointDraft,modelDraft,providerDraft,selectedAiSlot)
                keyDraft=""
                showAiSettings=false
            }){Text("Save")}
        },
        dismissButton={TextButton(onClick={showAiSettings=false}){Text("Cancel")}}
    )
    }
}

private data class CodeChangePreview(val summary: String, val before: String, val after: String)

private fun codeChangePreview(original: String, proposed: String): CodeChangePreview {
    val oldLines = original.lines()
    val newLines = proposed.lines()
    val prefix = oldLines.zip(newLines).takeWhile { (a,b) -> a==b }.size
    var suffix = 0
    while (suffix < oldLines.size-prefix && suffix < newLines.size-prefix &&
        oldLines[oldLines.lastIndex-suffix] == newLines[newLines.lastIndex-suffix]) suffix++
    val removed = oldLines.subList(prefix,oldLines.size-suffix)
    val added = newLines.subList(prefix,newLines.size-suffix)
    fun render(lines: List<String>): String = if (lines.isEmpty()) "(no lines)" else
        lines.take(80).mapIndexed { index,line -> "${prefix+index+1}: $line" }.joinToString("\n") +
            if (lines.size>80) "\n… ${lines.size-80} more lines" else ""
    return CodeChangePreview("Line ${prefix+1} · ${removed.size} removed, ${added.size} added",render(removed),render(added))
}

@Composable private fun SettingSwitch(label:String,checked:Boolean,onChange:(Boolean)->Unit){
    Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
        Text(label,color=Color(0xFFE6F5FF),modifier=Modifier.weight(1f))
        Switch(checked=checked,onCheckedChange=onChange)
    }
}

@Composable private fun SettingsCategory(title:String,subtitle:String,accent:Color,onClick:()->Unit){
    val shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed) .975f else 1f,
        animationSpec=spring(dampingRatio=Spring.DampingRatioMediumBouncy),label="setting press")
    val surfaceColor by androidx.compose.animation.animateColorAsState(
        if(pressed) accent.copy(alpha=.14f) else Color.White.copy(alpha=.06f),label="setting glow")
    Surface(
        color=surfaceColor,
        shape=shape,
        modifier=Modifier.fillMaxWidth().graphicsLayer { scaleX=scale;scaleY=scale }
            .border(1.dp,Color.White.copy(alpha=0.14f),shape)
            .clickable(interactionSource=interaction,indication=null,onClick=onClick)
    ){
        Row(Modifier.padding(horizontal=16.dp,vertical=15.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
            Box(Modifier.size(38.dp).background(accent.copy(alpha=0.13f),androidx.compose.foundation.shape.RoundedCornerShape(11.dp)),contentAlignment=androidx.compose.ui.Alignment.Center) {
                IdeGlyph(title,accent,Modifier.size(21.dp))
            }
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

private fun pythonCodeColors(source:String):AnnotatedString=buildAnnotatedString {
    val keywords=setOf("and","as","assert","async","await","break","class","continue","def","del","elif","else","except","False","finally","for","from","global","if","import","in","is","lambda","None","nonlocal","not","or","pass","raise","return","True","try","while","with","yield")
    val builtins=setOf("print","input","len","range","str","int","float","list","dict","set","tuple","bool","open","enumerate","zip","map","filter","sum","min","max","abs","round","sorted","type","isinstance","super","property")
    var i=0
    while(i<source.length){
        val start=i
        when {
            source[i]=='#' -> {
                while(i<source.length&&source[i]!='\n') i++
                withStyle(SpanStyle(color=Color(0xFF6A9955))){append(source.substring(start,i))}
            }
            source[i]=='"'||source[i]=='\'' -> {
                val quote=source[i++]
                while(i<source.length){
                    if(source[i]=='\\'&&i+1<source.length){i+=2;continue}
                    if(source[i++]==quote) break
                }
                withStyle(SpanStyle(color=Color(0xFFA7E36D))){append(source.substring(start,i))}
            }
            source[i].isDigit() -> {
                while(i<source.length&&(source[i].isDigit()||source[i]=='.')) i++
                withStyle(SpanStyle(color=Color(0xFFB5CEA8))){append(source.substring(start,i))}
            }
            source[i].isLetter()||source[i]=='_' -> {
                i++
                while(i<source.length&&(source[i].isLetterOrDigit()||source[i]=='_')) i++
                val word=source.substring(start,i)
                val style=when {
                    word in keywords -> SpanStyle(color=Color(0xFFC586C0),fontWeight=FontWeight.SemiBold)
                    word in builtins -> SpanStyle(color=Color(0xFF4FC1FF))
                    else -> SpanStyle(color=Color(0xFFD4D4D4))
                }
                withStyle(style){append(word)}
            }
            else -> {append(source[i]);i++}
        }
    }
}

@Composable private fun AstroCodeBlock(code:String,language:String="python"){
    val context=LocalContext.current
    val shape=androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
    Surface(
        color=Color(0xFF050505),
        shape=shape,
        modifier=Modifier.fillMaxWidth().border(1.dp,Color.White.copy(alpha=.18f),shape)
    ){
        Column{
            Row(
                Modifier.fillMaxWidth().background(Color.White.copy(alpha=.045f)).padding(horizontal=12.dp,vertical=8.dp),
                verticalAlignment=androidx.compose.ui.Alignment.CenterVertically
            ){
                Text(language.ifBlank{"python"},color=Color(0xFFB7B7BD),fontSize=11.sp,fontFamily=FontFamily.Monospace)
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick={
                        val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("PY4U code",code))
                    },
                    contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp)
                ){Text("▱  Copy",color=Color(0xFFEDEDF2),fontSize=11.sp)}
            }
            HorizontalDivider(color=Color.White.copy(alpha=.12f))
            Text(
                pythonCodeColors(code),
                fontFamily=FontFamily.Monospace,
                fontSize=13.sp,
                lineHeight=19.sp,
                modifier=Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp)
            )
        }
    }
}

@Composable private fun MarkdownMessage(source:String,color:Color,modifier:Modifier=Modifier){
    val fence=String(CharArray(3){96.toChar()})
    Column(modifier,verticalArrangement=Arrangement.spacedBy(6.dp)){
        var inCode=false
        var language="python"
        val code=StringBuilder()
        source.lines().forEach { raw ->
            val line=raw.trimEnd()
            if(line.trimStart().startsWith(fence)){
                if(inCode){
                    AstroCodeBlock(code.toString().trimEnd(),language)
                    code.clear()
                }else{
                    language=line.trimStart().removePrefix(fence).trim().ifBlank{"python"}
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
                val numbered=Regex("^\\d+\\.\\s+").containsMatchIn(trimmed)
                val clean=when{
                    heading>0->trimmed.drop(heading).trimStart()
                    bullet->"• "+trimmed.drop(2)
                    numbered->trimmed
                    else->line
                }
                Text(
                    markdownInline(clean),
                    color=color,
                    fontSize=if(heading>0)(19-heading).sp else 14.sp,
                    lineHeight=20.sp,
                    fontWeight=if(heading>0)FontWeight.Bold else FontWeight.Normal
                )
            }
        }
        if(inCode&&code.isNotEmpty()) AstroCodeBlock(code.toString().trimEnd(),language)
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
