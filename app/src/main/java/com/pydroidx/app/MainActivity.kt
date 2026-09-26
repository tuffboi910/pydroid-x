package com.pydroidx.app

import android.os.Bundle
import android.app.Activity
import android.content.Context
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
import android.view.Gravity
import android.view.KeyEvent
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Color
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
data class AiSlotConfig(val index: Int, val label: String, val provider: String, val endpoint: String, val model: String, val key: String)
data class CodeDiagnostic(val start: Int, val end: Int, val message: String, val fatal: Boolean = false)
data class SavedCode(val name: String, val modified: Long)
data class PendingCodeChange(val code: String, val fileName: String, val sourceSnapshot: String)

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
    var editorRevision by mutableIntStateOf(0)
        private set
    var output by mutableStateOf("")
    var running by mutableStateOf(false)
    var codeDiagnostics by mutableStateOf<List<CodeDiagnostic>>(emptyList())
    var waitingInput by mutableStateOf(false)
    var input by mutableStateOf("")
    val inputHistory = ConsoleInputHistory()
    var runtimeVersion by mutableStateOf("Loading Python…")
    var aiPrompt by mutableStateOf("")
    val aiMessages = mutableStateListOf<AiMessage>()
    var aiBusy by mutableStateOf(false)
    var aiFallbackNotice by mutableStateOf<String?>(null)
    var pendingCode by mutableStateOf<PendingCodeChange?>(null)
    var teachingOffer by mutableStateOf<String?>(null)
    var aiTestStatus by mutableStateOf<String?>(null)
    var attachedFileName by mutableStateOf<String?>(null)
    var attachedFileText by mutableStateOf<String?>(null)
    var aiEndpoint by mutableStateOf("https://api.openai.com/v1/chat/completions")
    var aiModel by mutableStateOf("gpt-4o-mini")
    var aiProvider by mutableStateOf("Auto")
    var ai2Endpoint by mutableStateOf("https://api.openai.com/v1/chat/completions")
    var ai2Model by mutableStateOf("")
    var ai2Provider by mutableStateOf("Auto")
    var ai3Endpoint by mutableStateOf("https://api.openai.com/v1/chat/completions")
    var ai3Model by mutableStateOf("")
    var ai3Provider by mutableStateOf("Auto")
    var shareCode by mutableStateOf(false)
    var editorFontSize by mutableFloatStateOf(16f)
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
    var backgroundHex by mutableStateOf("#000000")
    var motionStyle by mutableStateOf("Fluid spring")
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
    @Volatile private var stopRequested = false
    private var autosaveJob: Job? = null
    private var namingJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val outputBuffer = ConsoleOutputBuffer()
    @Volatile private var outputFlushScheduled = false
    private val outputFlushRunnable = Runnable {
        outputFlushScheduled = false
        output = outputBuffer.snapshot()
    }
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
        typingAnimation = settings.getBoolean("typing_animation", true)
        animationDuration = settings.getFloat("animation_duration", 120f)
        highlightDelay = settings.getFloat("highlight_delay", 220f)
        autosaveDelay = settings.getFloat("autosave_delay", 500f)
        showToolbar = settings.getBoolean("toolbar", true)
        showPageDots = settings.getBoolean("page_dots", true)
        cursorStyle = settings.getString("cursor", "Cyan") ?: "Cyan"
        autocomplete = settings.getBoolean("autocomplete", true)
        ghostBrightness = settings.getFloat("ghost_brightness", 0.48f)
        aiProvider = settings.getString("ai_provider", "Auto") ?: "Auto"
        ai2Provider = settings.getString("ai2_provider", "Auto") ?: "Auto"
        ai3Provider = settings.getString("ai3_provider", "Auto") ?: "Auto"
        fun storedEndpoint(key: String, provider: String): String {
            val value = settings.getString(key, null)?.trim().orEmpty()
            return if (provider == "Custom") value
            else value.takeIf { it.startsWith("https://") } ?: "https://api.openai.com/v1/chat/completions"
        }
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
        backgroundHex = settings.getString("background_hex", "#000000") ?: "#000000"
        motionStyle = settings.getString("motion_style", "Fluid spring") ?: "Fluid spring"
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
        projectDir = File(context.filesDir, "projects/default").apply { mkdirs() }
        val legacyStarter = "print(\"Hello Andrew\")\nname = input(\"What is your name? \")\nprint(\"Hello\", name)\n"
        val existing = projectDir.listFiles()?.filter { it.isFile && it.extension.equals("py",true) }.orEmpty()
        currentFileName = settings.getString("current_file","main.py") ?: "main.py"
        var current = File(projectDir,currentFileName)
        if (!current.exists()) current = existing.maxByOrNull { it.lastModified() } ?: File(projectDir,"main.py")
        if (!current.exists()) current.writeText(code)
        currentFileName = current.name
        code = current.readText()
        if (code == legacyStarter) {
            code = "print(\"Hello world!\")\n"
            current.writeText(code)
        }
        settings.edit().putString("current_file",currentFileName).apply()
        refreshSaved()
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
    fun slotConfigured(slot: Int) = !aiKeys.load(slot).isNullOrBlank()

    fun saveAiSettings(key: String, endpoint: String, model: String, provider: String, slot: Int = 0) {
        if (key.isNotBlank()) aiKeys.save(key, slot)
        val safeEndpoint = if (provider == "Custom") endpoint.trim()
            else endpoint.trim().takeIf { it.startsWith("https://") }
                ?: "https://api.openai.com/v1/chat/completions"
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
    ).filter { it.key.isNotBlank() }

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
        var slots = configuredAiSlots()
        if (preferredSlot != null) slots = slots.filter { it.index == preferredSlot }
        if (slots.isEmpty()) {
            if (testOnly) aiTestStatus = "No API key configured"
            else aiMessages.add(AiMessage(false, "Open AI settings and add an API key"))
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
                            aiFallbackNotice = "${slots[index-1].label} AI unavailable • switching to ${slot.label} AI"
                        }
                        return@runCatching AiClient.chat(
                            providerSetting=slot.provider,
                            endpoint=slot.endpoint,
                            apiKey=slot.key,
                            modelSetting=slot.model,
                            prompt=question,
                            code=if (!testOnly && shareCode) codeSnapshot else null,
                            history=historySnapshot,
                            revealDelayMs=if (typingAnimation) (animationDuration / 6.7f).toLong().coerceIn(4L, 120L) else 0L,
                            onStatus={ status -> viewModelScope.launch { aiFallbackNotice="${slot.label}: $status" } }
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
                        extractPythonFile(answer)?.let { proposed ->
                            if (currentFileName == fileNameSnapshot && code == codeSnapshot) {
                                pendingCode = PendingCodeChange(proposed, fileNameSnapshot, codeSnapshot)
                            } else {
                                aiMessages.add(AiMessage(false, "Code changed while Astro was working. Ask again before applying the edit."))
                            }
                        }
                        teachingOffer=extractTeachingOffer(answer)
                    }
                }
                aiBusy=false
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
        val replacement = change.code
        code = replacement + if (replacement.endsWith("\n")) "" else "\n"
        editorRevision++
        save()
        pendingCode = null
        aiMessages.add(AiMessage(false, "Applied to $currentFileName ✓"))
    }
    fun rejectPendingCode() { pendingCode = null }
    fun acceptTeaching() {
        val topic = teachingOffer ?: return
        teachingOffer = null
        aiPrompt = "Teach me $topic step by step. Keep it interactive and ask me one small question at a time."
        askAi()
    }
    fun rejectTeaching() { teachingOffer = null }
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
    private fun refreshSaved() {
        if (!::projectDir.isInitialized) return
        val files = projectDir.listFiles()?.filter { it.isFile && it.extension.equals("py",true) }
            ?.sortedByDescending { it.lastModified() }.orEmpty()
        savedCodes.clear()
        savedCodes.addAll(files.map { SavedCode(it.nameWithoutExtension.replace('_',' '),it.lastModified()) })
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
        if (old.exists() && old.renameTo(target)) {
            currentFileName = target.name
            settings.edit().putString("current_file",currentFileName).apply()
            refreshSaved()
        }
    }

    fun makeNewCode() {
        save()
        var number=1
        var file=File(projectDir,"untitled_$number.py")
        while(file.exists()) file=File(projectDir,"untitled_${++number}.py")
        file.writeText("")
        currentFileName=file.name
        code=""
        codeDiagnostics=emptyList()
        settings.edit().putString("current_file",currentFileName).apply()
        refreshSaved()
        editorRevision++
    }

    fun openSaved(displayName: String) {
        save()
        val file=projectDir.listFiles()?.firstOrNull {
            it.isFile && it.extension.equals("py",true) && it.nameWithoutExtension.replace('_',' ')==displayName
        } ?: return
        currentFileName=file.name
        code=file.readText()
        codeDiagnostics=emptyList()
        settings.edit().putString("current_file",currentFileName).apply()
        editorRevision++
        refreshSaved()
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
            launch(Dispatchers.IO) {
                if (::projectDir.isInitialized) {
                    File(projectDir,fileName).writeText(snapshot)
                    mainHandler.post { refreshSaved() }
                }
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
        val snapshot = code
        val fileName = currentFileName
        viewModelScope.launch(Dispatchers.IO) {
            if (::projectDir.isInitialized) {
                File(projectDir,fileName).writeText(snapshot)
                mainHandler.post { refreshSaved() }
            }
        }
    }
    private fun appendOutput(value: String) {
        outputBuffer.append(value)
        if (!outputFlushScheduled) {
            outputFlushScheduled = true
            mainHandler.postDelayed(outputFlushRunnable, 32)
        }
    }

    fun clearOutput() {
        outputBuffer.clear()
        mainHandler.removeCallbacks(outputFlushRunnable)
        outputFlushScheduled = false
        output = ""
    }

    fun run() {
        if (running) return
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
    fun stop() {
        if (!running) return
        stopRequested = true
        stdin.clear()
        stdin.offer(stopInputSignal)
        worker?.interrupt()
        appendOutput("\n[Stopping program…]\n")
        waitingInput = false
    }
    inner class Bridge {
        fun write(text: String, error: Boolean) { appendOutput(text) }
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
            waitingInput = false
            stopRequested = false
            worker = null
            stdin.clear()
        } }
    }

    override fun onCleared() {
        stopRequested = true
        stdin.offer(stopInputSignal)
        worker?.interrupt()
        super.onCleared()
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
    private var ghostSuffix: String? = null
    private var ghostCursorBack = 0
    private var diagnostics: List<CodeDiagnostic> = emptyList()
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
        if (!autocompleteEnabled) return@Runnable
        val snapshot = text.toString()
        val cursor = selectionStart
        if (snapshot.isEmpty() || cursor < 0 || cursor > snapshot.length) {
            ghostSuffix = null
            invalidate()
            return@Runnable
        }
        val lineStart = snapshot.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)) + 1
        val currentLine = snapshot.substring(lineStart.coerceIn(0,cursor), cursor)
        if (currentLine.isBlank()) { ghostSuffix = null; invalidate(); return@Runnable }
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
                    onCodeChanged?.invoke(s?.toString().orEmpty())
                    removeCallbacks(highlightRunnable)
                    postDelayed(highlightRunnable, highlightDelayMs)
                    post { updateGhostSuggestion() }
                    removeCallbacks(completionRunnable)
                    postDelayed(completionRunnable, 140)
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
        setOnFocusChangeListener { _, hasFocus ->
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
            ghostSuffix = null
            loadedRevision = revision
        }
        if (text.toString() == value) return
        applyingHighlight = true
        val cursor = selectionStart.coerceAtLeast(0).coerceAtMost(value.length)
        setText(value)
        setSelection(cursor)
        applyingHighlight = false
        highlightNow()
    }

    private fun restoreHistory(snapshot: EditorSnapshot?) {
        snapshot ?: return
        applyingHistory = true
        setText(snapshot.text)
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
        if (text.isEmpty() || cursor < 0 || cursor > text.length) {
            ghostSuffix=null
            invalidate()
            return
        }
        var start = cursor
        while (start > 0 && (text[start-1].isLetterOrDigit() || text[start-1]=='_')) start--
        val prefix = text.substring(start,cursor)
        val projectNames = Regex("\\b(?:def|class)\\s+([A-Za-z_]\\w*)|\\b([A-Za-z_]\\w*)\\s*=")
            .findAll(text).flatMap { it.groupValues.drop(1).asSequence() }.filter { it.isNotEmpty() }
        val localMatch = if (prefix.isNotEmpty()) completions.entries.firstOrNull {
            it.key.startsWith(prefix, ignoreCase = false) && it.key != prefix
        } else null
        val projectMatch = if (prefix.isNotEmpty()) projectNames.firstOrNull { it.startsWith(prefix) && it != prefix } else null
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
        if (editorLayout != null && editorLayout.lineCount > 0) {
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
        if (editorLayout != null && editorLayout.lineCount > 0) {
            val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.argb(72, 120, 132, 150)
                strokeWidth = resources.displayMetrics.density
            }
            val spaceWidth = paint.measureText(" ")
            val first = editorLayout.getLineForVertical((scrollY - totalPaddingTop).coerceAtLeast(0))
            val last = editorLayout.getLineForVertical((scrollY + height - totalPaddingTop).coerceAtLeast(0))
            val source = text.toString()
            for (lineIndex in first..last.coerceAtMost(editorLayout.lineCount - 1)) {
                val start = editorLayout.getLineStart(lineIndex)
                val end = editorLayout.getLineEnd(lineIndex).coerceAtMost(source.length)
                val lineText = source.substring(start, end).trimEnd('\n')
                val top = editorLayout.getLineTop(lineIndex) + totalPaddingTop - scrollY
                val bottom = editorLayout.getLineBottom(lineIndex) + totalPaddingTop - scrollY
                IndentationGuide.columns(lineText).forEach { column ->
                    val x = totalPaddingLeft - scrollX + column * spaceWidth
                    canvas.drawLine(x, top.toFloat(), x, bottom.toFloat(), guidePaint)
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
            val source = text.toString()
            for (visualLine in first..last.coerceAtMost(editorLayout.lineCount - 1)) {
                val lineStart = editorLayout.getLineStart(visualLine)
                val isLogicalLineStart = lineStart == 0 || source.getOrNull(lineStart - 1) == '\n'
                if (!isLogicalLineStart) continue
                val logicalLine = source.take(lineStart).count { it == '\n' } + 1
                val baseline = editorLayout.getLineBaseline(visualLine) + totalPaddingTop - scrollY
                canvas.drawText(logicalLine.toString(), right, baseline.toFloat(), numberPaint)
            }
        }
        val suffix=ghostSuffix ?: return
        val cursor=selectionStart
        val currentLayout=editorLayout ?: return
        if(currentLayout.lineCount<=0 || text.isEmpty() || cursor<0 || cursor>text.length) return
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
            Modifier.fillMaxWidth().background(Color(0xF20A0D10),shape)
                .border(1.dp,accent.copy(alpha=0.7f),shape).padding(horizontal=13.dp,vertical=10.dp),
            verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator(Modifier.size(18.dp),color=accent,strokeWidth=2.dp)
            Column {
                Text("AI FALLBACK",color=accent,fontSize=9.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp)
                Text(status,color=Color.White,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
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
        offset=androidx.compose.ui.unit.IntOffset(-18,92),
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
                modifier=Modifier.widthIn(min=285.dp,max=350.dp)
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
                        Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=10.dp),
                        verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            Modifier.size(40.dp).background(accent.copy(alpha=0.13f),androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                                .border(1.dp,accent.copy(alpha=0.75f),androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
                            contentAlignment=androidx.compose.ui.Alignment.Center
                        ) { Text("◆",color=Color(0xFF59F2DF),fontSize=20.sp) }
                        Column(Modifier.weight(1f)) {
                            Text(badge,color=accent,fontSize=8.sp,fontWeight=FontWeight.Bold,letterSpacing=1.1.sp)
                            Text(title,color=Color.White,fontSize=14.sp,fontWeight=FontWeight.Bold,maxLines=1)
                            Text(subtitle,color=Color(0xFFA9B0B8),fontSize=10.sp,maxLines=2)
                            Text("Swipe right to ignore",color=Color(0xFF6F7780),fontSize=8.sp)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(2.dp).background(accent.copy(alpha=0.85f)))
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp),
                        horizontalArrangement=Arrangement.End
                    ) {
                        TextButton(onClick={closeThen(onSecondary)},contentPadding=PaddingValues(horizontal=10.dp,vertical=4.dp)) {
                            Text(secondaryLabel,color=Color(0xFFB9C0C8),fontSize=11.sp)
                        }
                        Button(
                            onClick={closeThen(onPrimary)},
                            colors=ButtonDefaults.buttonColors(containerColor=accent,contentColor=Color.Black),
                            contentPadding=PaddingValues(horizontal=12.dp,vertical=4.dp)
                        ) { Text(primaryLabel,fontWeight=FontWeight.Bold,fontSize=11.sp) }
                    }
                }
            }
        }
    }
}

@Composable fun PyDroidX(vm: IdeViewModel) {
    fun safeColor(value:String,fallback:Long)=runCatching{Color(AndroidColor.parseColor(value))}.getOrDefault(Color(fallback))
    val bg = safeColor(vm.backgroundHex,0xFF000000)
    val text = Color(0xFFE6F5FF)
    val accent = safeColor(vm.accentHex,0xFF00E5FF)
    val revision = vm.editorRevision
    val pager = rememberPagerState(pageCount = { 5 })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val aiScroll = rememberScrollState()
    val consoleScroll = rememberScrollState()
    val consoleInputFocus = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val pages = listOf("FOLDERS", "PYTHON", "CONSOLE", "HELPER", "SETTINGS")
    var editorView by remember { mutableStateOf<PythonEditorView?>(null) }
    var showAiSettings by remember { mutableStateOf(false) }
    var selectedAiSlot by remember { mutableIntStateOf(0) }
    var keyDraft by remember { mutableStateOf("") }
    var endpointDraft by remember { mutableStateOf(vm.aiEndpoint) }
    var modelDraft by remember { mutableStateOf(vm.aiModel) }
    var providerDraft by remember { mutableStateOf(vm.aiProvider) }
    var settingsSection by remember { mutableStateOf("Overview") }
    var consoleInputValue by remember { mutableStateOf(TextFieldValue(vm.input)) }
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
    }
    LaunchedEffect(vm.aiMessages.size) {
        aiScroll.animateScrollTo(aiScroll.maxValue)
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
        Column(Modifier.fillMaxSize().background(bg).statusBarsPadding().padding(top=8.dp).imePadding()) {
            if(vm.showHeader) {
            Row(
                Modifier.fillMaxWidth().height(vm.headerHeight.coerceAtLeast(vm.tabHeight+8f).dp).padding(start=10.dp,end=10.dp,top=10.dp,bottom=8.dp),
                horizontalArrangement=Arrangement.spacedBy(10.dp),
                verticalAlignment=androidx.compose.ui.Alignment.CenterVertically
            ) {
                Row(
                    Modifier.weight(1f).height((vm.tabHeight+8).dp)
                        .background(safeColor(vm.tabBarHex,0xFF050505).copy(alpha=0.72f),androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                        .border(1.dp,glassEdge,androidx.compose.foundation.shape.RoundedCornerShape(18.dp)),
                    horizontalArrangement=Arrangement.SpaceEvenly,
                    verticalAlignment=androidx.compose.ui.Alignment.CenterVertically
                ) {
                    pages.forEachIndexed { index,label ->
                        TextButton(
                            onClick={scope.launch { pager.animateScrollToPage(index) }},
                            contentPadding=PaddingValues(horizontal=5.dp,vertical=0.dp)
                        ) {
                            Column(horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally) {
                                Text(label,color=if(pager.currentPage==index) Color.White else Color(0xFF66666D),fontSize=9.sp)
                                Spacer(Modifier.height(3.dp))
                                Box(
                                    Modifier.width(28.dp).height(2.dp).background(
                                        if(pager.currentPage==index) Color.White else Color.Transparent,
                                        androidx.compose.foundation.shape.RoundedCornerShape(1.dp)
                                    )
                                )
                            }
                        }
                    }
                }
                if(pager.currentPage==1) {
                    Button(
                        onClick={
                            if(vm.running) vm.stop()
                            else { vm.run(); scope.launch{pager.animateScrollToPage(2)} }
                        },
                        colors=ButtonDefaults.buttonColors(
                            containerColor=(if(vm.running) safeColor(vm.stopButtonHex,0xFFFF3D71) else safeColor(vm.runButtonHex,0xFF00E676)).copy(alpha=0.86f),
                            contentColor=Color.Black
                        ),
                        shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                        contentPadding=PaddingValues(horizontal=17.dp,vertical=9.dp),
                        modifier=Modifier.height((vm.tabHeight+8).dp)
                            .border(1.dp,Color.White.copy(alpha=0.24f),androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                    ){Text(if(vm.running)"■ Stop" else "▶ Start",fontWeight=FontWeight.Bold)}
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
                    0 -> Column(
                        Modifier.fillMaxSize().background(bg).padding(horizontal=18.dp,vertical=14.dp),
                        verticalArrangement=Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("SAVED",color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold)
                                Text("Your Python files",color=Color.Gray,fontSize=11.sp)
                            }
                            Button(
                                onClick={vm.makeNewCode();scope.launch{pager.animateScrollToPage(1)}},
                                colors=ButtonDefaults.buttonColors(containerColor=Color.White,contentColor=Color.Black),
                                shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                            ){Text("＋ Make new code",fontWeight=FontWeight.Bold)}
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
                                        onClick={vm.openSaved(saved.name);scope.launch{pager.animateScrollToPage(1)}},
                                        color=if(vm.currentFileName.substringBeforeLast('.').replace('_',' ')==saved.name) Color.White.copy(alpha=0.15f) else Color.White.copy(alpha=0.045f),
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
                                            Text("›",color=Color.Gray,fontSize=24.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> Column(Modifier.fillMaxSize().background(bg)) {
                        if(vm.showFileInfo) {
                        Row(
                            Modifier.fillMaxWidth().height(44.dp)
                                .background(Color(0xFF030303))
                                .border(1.dp,Color.White.copy(alpha=.10f))
                                .padding(start=14.dp,end=8.dp),
                            verticalAlignment=androidx.compose.ui.Alignment.CenterVertically
                        ){
                            Box(Modifier.size(24.dp)){
                                Box(
                                    Modifier.width(16.dp).height(12.dp)
                                        .background(Color.White,androidx.compose.foundation.shape.RoundedCornerShape(5.dp))
                                        .align(androidx.compose.ui.Alignment.TopStart)
                                )
                                Box(
                                    Modifier.width(16.dp).height(12.dp)
                                        .background(Color(0xFF9A9AA1),androidx.compose.foundation.shape.RoundedCornerShape(5.dp))
                                        .align(androidx.compose.ui.Alignment.BottomEnd)
                                )
                            }
                            Spacer(Modifier.width(9.dp))
                            Text(vm.currentFileName,color=Color(0xFFE8E8EC),fontSize=14.sp,fontFamily=FontFamily.Monospace)
                            Spacer(Modifier.width(7.dp))
                            Box(Modifier.size(6.dp).background(Color(0xFF8AB4F8),androidx.compose.foundation.shape.CircleShape))
                            Spacer(Modifier.weight(1f))
                            TextButton(
                                onClick={scope.launch{pager.animateScrollToPage(0)}},
                                contentPadding=PaddingValues(6.dp),modifier=Modifier.size(36.dp)
                            ){Text("×",color=Color(0xFF8C8C94),fontSize=22.sp)}
                            TextButton(
                                onClick={vm.makeNewCode()},
                                contentPadding=PaddingValues(6.dp),modifier=Modifier.size(36.dp)
                            ){Text("+",color=Color(0xFFBFC0C7),fontSize=22.sp)}
                        }
                        }
                        AndroidView(
                            factory={context->PythonEditorView(context).also{view->
                                editorView=view
                                view.onCodeChanged=vm::updateCode
                                view.requestSmartCompletion=vm::requestCompletion
                                view.requestCodeDiagnostics=vm::requestDiagnostics
                                view.setCodeIfDifferent(vm.code,revision)
                            }},
                            update={view->
                                view.setCodeIfDifferent(vm.code,revision)
                                view.applyPreferences(vm.editorFontSize,vm.wordWrap,vm.syntaxHighlighting,vm.fontName,
                                    vm.lineSpacing,vm.editorPadding,vm.highlightDelay,vm.cursorStyle,vm.autocomplete,vm.ghostBrightness,
                                    vm.lineNumbers,vm.highlightCurrentLine,vm.customFontPath,
                                    listOf(vm.editorTextHex,vm.commentHex,vm.stringHex,vm.numberHex,vm.keywordHex,vm.functionHex,vm.variableHex))
                            },
                            modifier=Modifier.weight(1f).fillMaxWidth().background(bg)
                        )
                        if(vm.showToolbar) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=4.dp),
                                horizontalArrangement=Arrangement.End
                            ){
                                Surface(
                                    color=Color(0xFF080808),
                                    shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                                    border=BorderStroke(1.dp,Color.White.copy(alpha=.14f))
                                ){
                                    Text(
                                        if(vm.codeDiagnostics.isEmpty())"✓  No issues" else "⚠  ${vm.codeDiagnostics.size} issue${if(vm.codeDiagnostics.size==1)"" else "s"}",
                                        color=if(vm.codeDiagnostics.isEmpty())Color(0xFFB8DDBE) else Color(0xFFFF6B72),
                                        fontSize=10.sp,modifier=Modifier.padding(horizontal=10.dp,vertical=6.dp)
                                    )
                                }
                            }
                            Surface(
                                color=safeColor(vm.toolbarHex,0xFF050505),
                                shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                                border=BorderStroke(1.dp,Color.White.copy(alpha=.16f)),
                                modifier=Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=5.dp)
                            ){
                                Row(
                                    Modifier.fillMaxWidth().height(vm.toolbarHeight.dp)
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal=7.dp,vertical=6.dp),
                                    horizontalArrangement=Arrangement.spacedBy(8.dp),
                                    verticalAlignment=androidx.compose.ui.Alignment.CenterVertically
                                ){
                                    listOf("Undo","Redo","Tab","(",")","[","]","{","}","\"",":","=").forEach{key->
                                        OutlinedButton(
                                            onClick={
                                                when {
                                                    key=="Undo" -> editorView?.undoCode()
                                                    key=="Redo" -> editorView?.redoCode()
                                                    key=="Tab"&&editorView?.acceptGhostSuggestion()==true -> Unit
                                                    else -> editorView?.insertAtCursor(if(key=="Tab")"    " else key)
                                                }
                                                
                                            },
                                            modifier=Modifier.width(if(key in listOf("Tab","Undo","Redo"))70.dp else 54.dp).fillMaxHeight(),
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
                        Modifier.fillMaxSize().background(bg).padding(horizontal=14.dp,vertical=12.dp)
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
                                onClick={if(vm.running) vm.stop() else vm.run()},
                                colors=ButtonDefaults.buttonColors(containerColor=Color.White,contentColor=Color.Black),
                                shape=androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                                contentPadding=PaddingValues(horizontal=18.dp,vertical=10.dp),
                                modifier=Modifier.border(1.dp,Color.White.copy(alpha=.24f),androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                            ){Text(if(vm.running)"■ Stop" else "▶ Start",fontWeight=FontWeight.Bold)}
                        }
                        Spacer(Modifier.height(20.dp))
                        Surface(
                            color=safeColor(vm.consoleBackgroundHex,0xFF030303),
                            shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                            border=BorderStroke(1.dp,Color.White.copy(alpha=.12f)),
                            modifier=Modifier.weight(1f).fillMaxWidth()
                        ){
                            Column(Modifier.fillMaxSize().padding(16.dp)){
                                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                                    Text(">>>",color=Color(0xFFB8B8BE),fontFamily=FontFamily.Monospace,fontSize=12.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if(vm.running)"running ${vm.currentFileName}" else vm.currentFileName,
                                        color=Color(0xFF6F6F77),fontFamily=FontFamily.Monospace,fontSize=11.sp
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    vm.output.ifEmpty{"Ready"},
                                    color=safeColor(vm.consoleTextHex,0xFFE8E8EC),
                                    fontFamily=FontFamily.Monospace,
                                    fontSize=vm.terminalFontSize.sp,
                                    lineHeight=(vm.terminalFontSize+6).sp,
                                    modifier=Modifier.weight(1f).fillMaxWidth().verticalScroll(consoleScroll)
                                )
                                ConsoleKeyToolbar(consoleInputValue,{consoleInputValue=it;vm.input=it.text},vm.inputHistory,consoleInputFocus,vm.input,vm.output.length,consoleScroll,safeColor(vm.toolbarHex,0xFF050505))
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
                                        Text(">>>",color=Color.White,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Bold)
                                        TextField(
                                            consoleInputValue,{consoleInputValue=it;vm.input=it.text},
                                            enabled=vm.waitingInput,
                                            singleLine=true,
                                            modifier=Modifier.weight(1f).focusRequester(consoleInputFocus),
                                            placeholder={Text(if(vm.waitingInput)"Type program input…" else "Waiting for Python input()",color=Color(0xFF66666E),fontSize=13.sp)},
                                            colors=TextFieldDefaults.colors(
                                                focusedContainerColor=Color.Transparent,unfocusedContainerColor=Color.Transparent,
                                                disabledContainerColor=Color.Transparent,focusedIndicatorColor=Color.Transparent,
                                                unfocusedIndicatorColor=Color.Transparent,disabledIndicatorColor=Color.Transparent,
                                                focusedTextColor=Color.White,disabledTextColor=Color(0xFF77777F)
                                            ),
                                            keyboardOptions=KeyboardOptions(imeAction=ImeAction.None)
                                        )
                                        Button(
                                            onClick={if(vm.waitingInput) vm.submitInput()},
                                            enabled=vm.waitingInput,
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
                            if(vm.aiBusy && vm.aiMessages.isNotEmpty()) Text("Astro is writing…",color=Color(0xFF8D929A),fontSize=11.sp)
                        }
                        vm.aiFallbackNotice?.let { status ->
                            SwitchingModelNotice(status=status,accent=accent,onFinished={vm.aiFallbackNotice=null})
                        }
                        vm.pendingCode?.let { change ->
                            AchievementNotice(
                                badge="ACTION REQUIRED",
                                title="Code change ready",
                                subtitle="Astro wants permission to update ${change.fileName}",
                                accent=accent,
                                primaryLabel="Apply",
                                secondaryLabel="Ignore",
                                onPrimary={vm.applyPendingCode()},
                                onSecondary={vm.rejectPendingCode()}
                            )
                        }
                        vm.teachingOffer?.let { topic ->
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
                            SettingsCategory("System","Runtime and interface switches",accent){settingsSection="System"}
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
                        if(settingsSection=="Console & Helper" || searchMatches("console","helper","chat","bubble","terminal","keyboard toolbar","run button","stop button","typing animation")){
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
                            listOf("Fluid spring","Soft fade","Subtle scale","Shared element","Smooth blur reveal","Layered depth","Gentle parallax","Card expansion","Natural sheet","Magnetic snap","Interactive swipe","Content morph","Keyboard lift").forEach{motion->
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
                        if(settingsSection=="Editor" || searchMatches("editor","font size","line spacing","highlight delay","autosave","ghost text","cursor","word wrap","syntax","autocomplete","line numbers","saving")){
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
                        if(settingsSection=="Fonts" || searchMatches("fonts","font family","font vault","typeface") || (settingsSearch.isNotBlank() && FONT_VAULT.any{it.first.contains(settingsSearch,true)})){
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
            if(vm.showPageDots) Row(Modifier.fillMaxWidth().height(22.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                repeat(5){index->Box(Modifier.padding(horizontal=3.dp).size(if(index==pager.currentPage)vm.pageDotSize.dp else (vm.pageDotSize*0.62f).dp).background(if(index==pager.currentPage)accent else Color.DarkGray,androidx.compose.foundation.shape.CircleShape))}
            }
            Text("w astro",color=Color(0xFF181818),fontSize=7.sp,modifier=Modifier.fillMaxWidth().padding(bottom=2.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        }
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
                TextField(
                    keyDraft,{keyDraft=it},
                    label={Text(if(vm.slotConfigured(selectedAiSlot)) "New API key  optional" else "API key")},
                    singleLine=true,
                    visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier=Modifier.fillMaxWidth()
                )
                Text("Provider",fontSize=11.sp,color=Color.Gray)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf("Auto","OpenAI","Gemini","Claude","OpenRouter","Groq","Custom").forEach { provider ->
                        FilterChip(selected=providerDraft==provider,onClick={providerDraft=provider},label={Text(provider)})
                    }
                }
                TextField(modelDraft,{modelDraft=it},label={Text("Model  e g gemini-3.6-flash")},singleLine=true,modifier=Modifier.fillMaxWidth())
                TextField(endpointDraft,{endpointDraft=it},label={Text("API endpoint or compatible link")},singleLine=true,modifier=Modifier.fillMaxWidth())
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
                    if(vm.slotConfigured(selectedAiSlot)) OutlinedButton(
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
