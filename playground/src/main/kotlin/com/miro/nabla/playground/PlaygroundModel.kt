package com.miro.nabla.playground

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.miro.nabla.BufferId
import com.miro.nabla.Delete
import com.miro.nabla.Insert
import com.miro.nabla.NablaBuilder

/** Where a packet sits in the topology. */
sealed interface Node {
    data class Client(val index: Int) : Node
    data object Server : Node
}

enum class PacketKind { INSERT, DELETE, RETAIN, ACK }

/** A message currently travelling along a link. [onArrive] applies its effect once the trip ends. */
data class Packet(
    val id: Long,
    val from: Node,
    val to: Node,
    val kind: PacketKind,
    val onArrive: () -> Unit,
)

/** Per-client editor caret/selection (UI state, not part of the synced document). */
class EditorState {
    var caret by mutableStateOf(0)
    var anchor by mutableStateOf<Int?>(null)
}

/**
 * Compose-facing wrapper around [Hub]. The hub is plain mutable state, so every mutating action
 * bumps [tick] to trigger recomposition; composables read [tick] to subscribe. In-flight messages
 * are modelled as [packets] that the UI animates and applies on arrival.
 */
class PlaygroundModel(clientCount: Int = 2) {
    val hub = Hub(clientCount)
    val editors = List(clientCount) { EditorState() }

    var tick by mutableStateOf(0)
        private set
    var playing by mutableStateOf(true)

    val packets = mutableStateListOf<Packet>()
    private var packetSeq = 0L

    // Move buffer ids: unique per (client, sequence) without coordination, and totally ordered.
    private var bufferSeq = 0L
    private fun newBufferId(clientId: Int): BufferId = BufferId((clientId.toLong() shl 40) or bufferSeq++)

    private fun changed() {
        tick++
    }

    // ----- editing (driven by the custom editor) -----

    fun doc(clientId: Int): String = hub.clients[clientId].document.text()

    fun caretOf(clientId: Int): Int = editors[clientId].caret.coerceIn(0, doc(clientId).length)

    /** Current selection as `[start, end)`, or `null` when there is no (non-empty) selection. */
    fun selectionRange(clientId: Int): Pair<Int, Int>? {
        val anchor = editors[clientId].anchor ?: return null
        val caret = editors[clientId].caret
        val start = minOf(anchor, caret)
        val end = maxOf(anchor, caret)
        return if (start < end) start to end else null
    }

    private fun clamp(clientId: Int) {
        val len = doc(clientId).length
        editors[clientId].caret = editors[clientId].caret.coerceIn(0, len)
        editors[clientId].anchor = editors[clientId].anchor?.coerceIn(0, len)
    }

    fun setSelection(clientId: Int, caret: Int, anchor: Int?) {
        val len = doc(clientId).length
        editors[clientId].caret = caret.coerceIn(0, len)
        editors[clientId].anchor = anchor?.coerceIn(0, len)
        changed()
    }

    fun moveCaret(clientId: Int, target: Int, extend: Boolean) {
        val t = target.coerceIn(0, doc(clientId).length)
        val editor = editors[clientId]
        if (extend) {
            if (editor.anchor == null) editor.anchor = editor.caret
            editor.caret = t
        } else {
            editor.anchor = null
            editor.caret = t
        }
        changed()
    }

    /** Inserts [s], replacing the selection if there is one. */
    fun typeText(clientId: Int, s: String) {
        clamp(clientId)
        val selection = selectionRange(clientId)
        val start = selection?.first ?: editors[clientId].caret
        val end = selection?.second ?: start
        applyEdit(clientId, replaceChange(start, end, s), caret = start + s.length)
    }

    fun backspace(clientId: Int) {
        clamp(clientId)
        val selection = selectionRange(clientId)
        if (selection != null) {
            applyEdit(clientId, replaceChange(selection.first, selection.second, ""), caret = selection.first)
            return
        }
        val caret = editors[clientId].caret
        if (caret == 0) return
        applyEdit(clientId, replaceChange(caret - 1, caret, ""), caret = caret - 1)
    }

    fun deleteForward(clientId: Int) {
        clamp(clientId)
        val selection = selectionRange(clientId)
        if (selection != null) {
            applyEdit(clientId, replaceChange(selection.first, selection.second, ""), caret = selection.first)
            return
        }
        val caret = editors[clientId].caret
        if (caret >= doc(clientId).length) return
        applyEdit(clientId, replaceChange(caret, caret + 1, ""), caret = caret)
    }

    /** Moves the current selection to [dropAt] as one first-class move (linked cut + paste). */
    fun moveSelection(clientId: Int, dropAt: Int) {
        clamp(clientId)
        val selection = selectionRange(clientId) ?: return
        val (start, end) = selection
        if (dropAt in start..end) return
        val newStart = if (dropAt <= start) dropAt else dropAt - (end - start)
        applyEdit(
            clientId,
            moveChange(start, end, dropAt, newBufferId(clientId)),
            caret = newStart + (end - start),
            anchor = newStart,
        )
    }

    private fun applyEdit(clientId: Int, change: NablaBuilder, caret: Int, anchor: Int? = null) {
        if (!change.hasEffect()) return
        hub.edit(clientId, change)
        editors[clientId].caret = caret
        editors[clientId].anchor = anchor
        changed()
    }

    // ----- network simulation -----

    /** Sends one queued message for [clientId] into transit (uplink first, then downlink). */
    fun stepClient(clientId: Int): Boolean {
        hub.dispatchUplink(clientId)?.let { submission ->
            packets.add(
                Packet(packetSeq++, Node.Client(clientId), Node.Server, kindOf(submission.op)) {
                    hub.applyUplink(submission)
                    changed()
                },
            )
            changed()
            return true
        }
        hub.dispatchDownlink(clientId)?.let { message ->
            packets.add(
                Packet(packetSeq++, Node.Server, Node.Client(clientId), kindOfMessage(message)) {
                    applyIncoming(clientId, message)
                    changed()
                },
            )
            changed()
            return true
        }
        return false
    }

    /** Applies a server message and slides this client's caret/selection through the resulting change. */
    private fun applyIncoming(clientId: Int, message: ServerMessage) {
        val docChange = hub.applyDownlink(clientId, message) ?: return
        val editor = editors[clientId]
        editor.caret = Ot.transformPosition(docChange, editor.caret)
        editor.anchor = editor.anchor?.let { Ot.transformPosition(docChange, it) }
    }

    fun removePacket(id: Long) {
        packets.removeAll { it.id == id }
    }

    /** Finishes any in-transit packets, then drains all remaining queues instantly (caret-aware). */
    fun deliverAll() {
        val inTransit = packets.toList()
        packets.clear()
        inTransit.forEach { it.onArrive() }
        var guard = 100_000
        while (guard-- > 0) {
            var progressed = false
            for (i in hub.clients.indices) {
                hub.dispatchUplink(i)?.let { hub.applyUplink(it); progressed = true }
            }
            for (i in hub.clients.indices) {
                hub.dispatchDownlink(i)?.let { applyIncoming(i, it); progressed = true }
            }
            if (!progressed) break
        }
        changed()
    }

    fun setConnected(clientId: Int, connected: Boolean) {
        hub.connected[clientId] = connected
        changed()
    }

}

/** Colour category for an in-flight delta: insert (green), delete (red), or pure retain (blue). */
fun kindOf(delta: NablaBuilder): PacketKind {
    var insert = false
    var delete = false
    for (op in delta.ops) {
        when (op) {
            is Insert -> insert = true
            is Delete -> delete = true
            else -> {}
        }
    }
    return when {
        insert -> PacketKind.INSERT
        delete -> PacketKind.DELETE
        else -> PacketKind.RETAIN
    }
}

fun kindOfMessage(message: ServerMessage): PacketKind = when (message) {
    Acknowledgement -> PacketKind.ACK
    is RemoteOperation -> kindOf(message.op)
}
