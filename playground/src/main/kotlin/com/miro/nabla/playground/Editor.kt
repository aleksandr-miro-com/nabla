package com.miro.nabla.playground

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class DragMode { SELECT, MOVE }

/**
 * A minimal hand-rolled text editor: key presses are translated straight into nabla ops (no
 * diffing), the mouse places/extends the caret, and dragging an existing selection moves the text
 * (emitting a single combined delete+insert change).
 */
@Composable
fun NablaTextEditor(model: PlaygroundModel, clientId: Int, modifier: Modifier = Modifier) {
    model.tick // subscribe to recomposition
    val text = model.doc(clientId)

    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var focused by remember { mutableStateOf(false) }
    var dragMode by remember { mutableStateOf<DragMode?>(null) }
    var dropTarget by remember { mutableStateOf<Int?>(null) }
    val focusRequester = remember { FocusRequester() }

    val selectionColor = Color(0x553F51B5)
    val caretColor = MaterialTheme.colors.onSurface
    val dropColor = Color(0xFFE65100)

    Box(
        modifier
            .height(160.dp)
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.3f))
            .padding(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .onKeyEvent { event -> handleKey(model, clientId, text, event) }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { position ->
                        focusRequester.requestFocus()
                        val resolved = layout
                        // Only move the caret for clicks on the text; clicking the empty area below
                        // it just focuses and keeps the caret where it was (rather than jumping to end).
                        if (resolved != null && position.y <= resolved.size.height) {
                            model.setSelection(clientId, resolved.getOffsetForPosition(position), null)
                        }
                    })
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { position ->
                            focusRequester.requestFocus()
                            val resolved = layout
                            if (resolved != null && position.y <= resolved.size.height) {
                                val offset = resolved.getOffsetForPosition(position)
                                val selection = model.selectionRange(clientId)
                                if (selection != null && offset in selection.first until selection.second) {
                                    dragMode = DragMode.MOVE
                                    dropTarget = offset
                                } else {
                                    dragMode = DragMode.SELECT
                                    model.setSelection(clientId, offset, offset)
                                }
                            }
                        },
                        onDrag = { change, _ ->
                            val resolved = layout
                            if (resolved != null) {
                                val offset = resolved.getOffsetForPosition(change.position)
                                if (dragMode == DragMode.MOVE) {
                                    dropTarget = offset
                                } else {
                                    model.moveCaret(clientId, offset, extend = true)
                                }
                            }
                            change.consume()
                        },
                        onDragEnd = {
                            if (dragMode == DragMode.MOVE) dropTarget?.let { model.moveSelection(clientId, it) }
                            dragMode = null
                            dropTarget = null
                        },
                        onDragCancel = {
                            dragMode = null
                            dropTarget = null
                        },
                    )
                }
                .drawWithContent {
                    val resolved = layout
                    if (resolved != null) {
                        model.selectionRange(clientId)?.let { (start, end) ->
                            drawPath(resolved.getPathForRange(start, end), color = selectionColor)
                        }
                    }
                    drawContent()
                    if (resolved != null) {
                        val caret = model.caretOf(clientId)
                        val rect = resolved.getCursorRect(caret)
                        drawLine(
                            caretColor.copy(alpha = if (focused) 1f else 0.35f),
                            Offset(rect.left, rect.top),
                            Offset(rect.left, rect.bottom),
                            strokeWidth = 2f,
                        )
                        if (dragMode == DragMode.MOVE) {
                            dropTarget?.let { target ->
                                val dropRect = resolved.getCursorRect(target.coerceIn(0, text.length))
                                drawLine(
                                    dropColor,
                                    Offset(dropRect.left, dropRect.top),
                                    Offset(dropRect.left, dropRect.bottom),
                                    strokeWidth = 2f,
                                )
                            }
                        }
                    }
                },
        ) {
            BasicText(
                text = text,
                onTextLayout = { layout = it },
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = MaterialTheme.colors.onSurface,
                ),
            )
        }
    }
}

private fun handleKey(model: PlaygroundModel, clientId: Int, text: String, event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val shift = event.isShiftPressed
    return when (event.key) {
        Key.Backspace -> { model.backspace(clientId); true }
        Key.Delete -> { model.deleteForward(clientId); true }
        Key.Enter, Key.NumPadEnter -> { model.typeText(clientId, "\n"); true }
        Key.DirectionLeft -> { model.moveCaret(clientId, model.caretOf(clientId) - 1, shift); true }
        Key.DirectionRight -> { model.moveCaret(clientId, model.caretOf(clientId) + 1, shift); true }
        Key.MoveHome -> { model.moveCaret(clientId, 0, shift); true }
        Key.MoveEnd -> { model.moveCaret(clientId, text.length, shift); true }
        else -> {
            val codePoint = event.utf16CodePoint
            val isTyping = codePoint != 0 &&
                !event.isCtrlPressed && !event.isMetaPressed && !event.isAltPressed &&
                !Character.isISOControl(codePoint)
            if (isTyping) {
                model.typeText(clientId, String(Character.toChars(codePoint)))
                true
            } else {
                false
            }
        }
    }
}
