package com.miro.nabla.playground

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.miro.nabla.BufferId
import com.miro.nabla.NablaBuilder

/**
 * Compose-facing wrapper around a [Hub] whose document is a tree of [NodeElement]s. Mirrors
 * [PlaygroundModel]'s shape (a [tick] bumped on every mutation), with tree edits instead of text.
 */
class TreeModel(clientCount: Int = 2) {
    val hub = Hub(clientCount)

    var tick by mutableStateOf(0)
        private set
    var playing by mutableStateOf(true)

    /** Per-client selected node path (null = nothing selected). */
    val selection = mutableStateListOf<List<Int>?>().apply { repeat(clientCount) { add(null) } }

    /** Messages currently travelling along a link (animated by the UI, applied on arrival). */
    val packets = mutableStateListOf<Packet>()
    private var packetSeq = 0L

    private var nodeSeq = 0L
    private var bufferSeq = 0L
    private var colorSeq = 0

    private fun changed() {
        tick++
    }

    private fun newNodeId(clientId: Int): Long = (clientId.toLong() shl 40) or nodeSeq++
    private fun newBufferId(clientId: Int): BufferId = BufferId((clientId.toLong() shl 40) or bufferSeq++)
    private fun nextColor(): String = Palette[colorSeq++ % Palette.size]

    fun tree(clientId: Int): NablaBuilder = hub.clients[clientId].document

    fun select(clientId: Int, path: List<Int>?) {
        selection[clientId] = path
        changed()
    }

    private fun edit(clientId: Int, change: NablaBuilder) {
        if (change.ops.isEmpty()) return
        hub.edit(clientId, change)
        changed()
    }

    /** Adds a child to the selected node, or a new root when nothing is selected. */
    fun addNode(clientId: Int) {
        val parent = selection[clientId] ?: emptyList()
        edit(clientId, addNodeChange(tree(clientId), parent, newNodeId(clientId), nextColor()))
    }

    fun recolorSelected(clientId: Int) {
        val path = selection[clientId] ?: return
        edit(clientId, recolorChange(path, nextColor()))
    }

    fun removeSelected(clientId: Int) {
        val path = selection[clientId] ?: return
        edit(clientId, removeNodeChange(path))
        selection[clientId] = null
    }

    /** Reparents [srcPath] to become the last child of the node at [dstParentPath], if allowed. */
    fun move(clientId: Int, srcPath: List<Int>, dstParentPath: List<Int>) {
        if (!canMove(srcPath, dstParentPath)) return
        edit(clientId, moveNodeChange(tree(clientId), srcPath, dstParentPath, newBufferId(clientId)))
        selection[clientId] = null
    }

    // ----- network (apply immediately; no packet animation) -----

    /** Sends one queued message for [clientId] into transit; it applies when its packet arrives. */
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
                    hub.applyDownlink(clientId, message)
                    selection[clientId] = null // paths may have shifted under the remote change
                    changed()
                },
            )
            changed()
            return true
        }
        return false
    }

    fun removePacket(id: Long) {
        packets.removeAll { it.id == id }
    }

    /** Finishes any in-transit packets, then drains all remaining queues instantly. */
    fun deliverAll() {
        val inTransit = packets.toList()
        packets.clear()
        inTransit.forEach { it.onArrive() }
        hub.settle()
        for (i in hub.clients.indices) selection[i] = null
        changed()
    }

    fun setConnected(clientId: Int, connected: Boolean) {
        hub.connected[clientId] = connected
        changed()
    }

    fun pending(clientId: Int): Int = hub.pendingMessages(clientId)

    companion object {
        val Palette = listOf("#E53935", "#FB8C00", "#43A047", "#8E24AA", "#1E88E5", "#00897B")
    }
}
