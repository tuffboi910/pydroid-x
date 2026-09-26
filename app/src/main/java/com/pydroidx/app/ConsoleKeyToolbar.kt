package com.pydroidx.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ConsoleKeyToolbar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    history: ConsoleInputHistory,
    focusRequester: FocusRequester,
    externalText: String,
    outputLength: Int,
    outputScroll: ScrollState,
    containerColor: Color = Color(0xFF050505)
) {
    var ctrl by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    var lastNonBlank by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(outputLength) {
        withFrameNanos { }
        outputScroll.scrollTo(outputScroll.maxValue)
    }
    LaunchedEffect(externalText) {
        if (externalText.isNotBlank()) {
            lastNonBlank = externalText
        } else if (lastNonBlank.isNotBlank()) {
            history.record(lastNonBlank)
            lastNonBlank = ""
        }
        if (value.text != externalText) {
            onValueChange(TextFieldValue(externalText, TextRange(externalText.length)))
        }
    }

    Row(
        Modifier.fillMaxWidth()
            .background(containerColor, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        listOf("Esc", "Ctrl", "Alt", "Tab", "←", "↑", "↓", "→", "Home", "End", "Pg↑", "Pg↓", "/", "-", "_", "|", "~").forEach { key ->
            OutlinedButton(
                onClick = {
                    when (key) {
                        "Ctrl" -> ctrl = !ctrl
                        "Alt" -> alt = !alt
                        "Pg↑" -> scope.launch { outputScroll.animateScrollTo((outputScroll.value - 480).coerceAtLeast(0)) }
                        "Pg↓" -> scope.launch { outputScroll.animateScrollTo((outputScroll.value + 480).coerceAtMost(outputScroll.maxValue)) }
                        else -> {
                            val state = ConsoleInputState(value.text, value.selection.start)
                            val next = when (key) {
                                "Esc" -> state
                                "←" -> ConsoleInputController.move(state, -1, ctrl || alt)
                                "→" -> ConsoleInputController.move(state, 1, ctrl || alt)
                                "↑" -> {
                                    val text = history.previous() ?: state.text
                                    ConsoleInputState(text, text.length)
                                }
                                "↓" -> {
                                    val text = history.next() ?: state.text
                                    ConsoleInputState(text, text.length)
                                }
                                "Home" -> ConsoleInputController.home(state)
                                "End" -> ConsoleInputController.end(state)
                                "Tab" -> ConsoleInputController.insert(state, "\t")
                                else -> ConsoleInputController.insert(state, key)
                            }
                            onValueChange(TextFieldValue(next.text, TextRange(next.cursor)))
                            ctrl = false
                            alt = false
                            focusRequester.requestFocus()
                        }
                    }
                },
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if ((key == "Ctrl" && ctrl) || (key == "Alt" && alt)) Color.White.copy(alpha = .16f) else Color.White.copy(alpha = .025f),
                    contentColor = Color.White
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = .16f)),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 13.dp),
                modifier = Modifier.height(38.dp)
            ) {
                Text(key, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            }
        }
    }
}
