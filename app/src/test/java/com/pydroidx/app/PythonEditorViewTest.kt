package com.pydroidx.app

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Looper
import android.view.MotionEvent
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.View
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28], application=Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class PythonEditorViewTest {
    private lateinit var editor: PythonEditorView
    private val looper get() = shadowOf(Looper.getMainLooper())
    private var requests = 0
    private val items = listOf(
        CompletionItem("vars()", "vars()", 0, 2, 1, "function", "vars(object)", "Return object attributes."),
        CompletionItem("ValueError()", "ValueError()", 0, 2, 1, "class", "ValueError()", "An inappropriate value."))

    @Before fun setup() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        editor = PythonEditorView(activity)
        activity.setContentView(editor)
        editor.requestFocus()
        editor.requestSmartCompletion = { _, _, deliver -> requests++; deliver(CompletionResult(items)) }
        editor.setText("va"); editor.setSelection(2)
        editor.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY))
        editor.layout(0, 0, 1080, 1200)
    }
    @Test fun waitsOneSecondAndRestartsOnEveryKeystroke() {
        looper.idleFor(Duration.ofMillis(999)); assertEquals(0, requests)
        editor.text.append("r")
        looper.idleFor(Duration.ofMillis(999)); assertEquals(0, requests)
        looper.idleFor(Duration.ofMillis(1)); assertEquals(1, requests)
    }
    @Test fun toolbarPairLeavesCaretBetweenCharacters() {
        editor.setText("ab")
        editor.setSelection(1)
        editor.insertPair("(", ")")
        assertEquals("a()b", editor.text.toString())
        assertEquals(2, editor.selectionStart)
        assertEquals(editor.selectionStart, editor.selectionEnd)
    }
    @Test fun toolbarPairWrapsSelectedText() {
        editor.setText("value")
        editor.setSelection(1, 4)
        editor.insertPair("{", "}")
        assertEquals("v{alu}e", editor.text.toString())
        assertEquals(2, editor.selectionStart)
        assertEquals(5, editor.selectionEnd)
    }
    @Test fun codeMirrorBatchesRapidTypingAndCanFlushImmediately() {
        val updates = mutableListOf<String>()
        editor.onCodeChanged = updates::add
        editor.text.append("r")
        looper.idleFor(Duration.ofMillis(80))
        editor.text.append("i")
        looper.idleFor(Duration.ofMillis(119))
        assertTrue(updates.isEmpty())
        looper.idleFor(Duration.ofMillis(1))
        assertEquals(listOf("vari"), updates)

        editor.text.append("able")
        editor.flushCodeChange()
        assertEquals("variable", updates.last())
        looper.idleFor(Duration.ofMillis(200))
        assertEquals(2, updates.size)
    }

    @Test fun externalFileSwitchCancelsPendingOldTextSync() {
        val updates = mutableListOf<String>()
        editor.onCodeChanged = updates::add
        editor.text.append(" old")
        editor.setCodeIfDifferent("new file", revision = 1)
        looper.idleFor(Duration.ofMillis(200))
        assertEquals("new file", editor.text.toString())
        assertTrue(updates.isEmpty())
    }

    @Test fun undoGroupsRapidCharacterTyping() {
        editor.setCodeIfDifferent("", revision = 2)
        editor.text.append("a")
        editor.text.append("b")
        editor.undoCode()
        assertEquals("", editor.text.toString())
    }

    @Test fun typedOpeningPairPlacesCaretBetweenAndBackspaceRemovesEmptyPair() {
        editor.setCodeIfDifferent("", revision = 3)
        editor.text.insert(0, "(")
        assertEquals("()", editor.text.toString())
        assertEquals(1, editor.selectionStart)
        editor.onKeyDown(KeyEvent.KEYCODE_DEL, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        assertEquals("", editor.text.toString())
    }

    @Test fun composingImeTextDoesNotInsertSmartPair() {
        editor.setCodeIfDifferent("", revision = 9)
        val connection = editor.onCreateInputConnection(EditorInfo())
        assertTrue(connection.setComposingText("(", 1))
        assertEquals("(", editor.text.toString())
    }

    @Test fun typingExistingCloserSkipsDuplicate() {
        editor.setCodeIfDifferent(")", revision = 4)
        editor.setSelection(0)
        editor.text.insert(0, ")")
        assertEquals(")", editor.text.toString())
        assertEquals(1, editor.selectionStart)
    }

    @Test fun enterAfterColonAddsConfiguredIndent() {
        editor.setCodeIfDifferent("if ready:", revision = 5)
        editor.setSelection(editor.text.length)
        editor.text.insert(editor.selectionStart, "\n")
        assertEquals("if ready:\n    ", editor.text.toString())
        assertEquals(editor.text.length, editor.selectionStart)
    }

    @Test fun indentAndCommentSelectionPreserveCodeAndUndoAsSingleAction() {
        editor.setCodeIfDifferent("one\ntwo", revision = 6)
        editor.setSelection(0, editor.text.length)
        assertTrue(editor.indentSelection())
        assertEquals("    one\n    two", editor.text.toString())
        editor.undoCode()
        assertEquals("one\ntwo", editor.text.toString())

        editor.setSelection(0, editor.text.length)
        assertTrue(editor.toggleCommentSelection())
        assertEquals("# one\n# two", editor.text.toString())
        assertTrue(editor.toggleCommentSelection())
        assertEquals("one\ntwo", editor.text.toString())
    }
    @Test fun closingToolbarKeySkipsExistingCloser() {
        editor.setText("()")
        editor.setSelection(1)
        editor.insertClosingOrSkip(")")
        assertEquals("()", editor.text.toString())
        assertEquals(2, editor.selectionStart)
    }
    private fun row(): RectF {
        looper.idleFor(Duration.ofMillis(1000))
        editor.draw(Canvas(Bitmap.createBitmap(1080,1200,Bitmap.Config.ARGB_8888)))
        val field = PythonEditorView::class.java.getDeclaredField("completionPopupBounds")
        field.isAccessible = true
        return field.get(editor) as RectF
    }
    private fun touch(action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(0, 0, action, x, y, 0)
        editor.onTouchEvent(event); event.recycle()
    }
    @Test fun tappingSecondRowInsertsThatItemOnlyOnRelease() {
        val bounds = row()
        val x = bounds.centerX(); val y = bounds.top + bounds.height() * .75f
        touch(MotionEvent.ACTION_DOWN, x, y)
        assertEquals("va",editor.text.toString())
        touch(MotionEvent.ACTION_MOVE, x+1, y+1)
        touch(MotionEvent.ACTION_UP, x+1, y+1)
        assertEquals("ValueError()", editor.text.toString())
        assertEquals(11, editor.selectionStart)
        editor.undoCode(); assertEquals("va", editor.text.toString())
        editor.redoCode(); assertEquals("ValueError()", editor.text.toString())
    }
    @Test fun draggingOrCancellingDoesNotInsert() {
        val bounds = row()
        val x=bounds.centerX(); val y=bounds.top+bounds.height()*.75f
        touch(MotionEvent.ACTION_DOWN,x,y)
        touch(MotionEvent.ACTION_MOVE,x+100,y)
        touch(MotionEvent.ACTION_UP,x,y)
        assertEquals("va",editor.text.toString())
        touch(MotionEvent.ACTION_DOWN,x,y)
        touch(MotionEvent.ACTION_CANCEL,x,y)
        assertEquals("va",editor.text.toString())
    }
    @Test fun pendingResultCannotApplyAfterTyping() {
        var deliver: ((CompletionResult)->Unit)? = null
        editor.requestSmartCompletion = { _, _, callback -> deliver=callback }
        looper.idleFor(Duration.ofMillis(1000))
        editor.text.append("r")
        deliver!!(CompletionResult(items)); looper.idle()
        assertFalse(editor.acceptGhostSuggestion())
        assertEquals("var",editor.text.toString())
    }
}
