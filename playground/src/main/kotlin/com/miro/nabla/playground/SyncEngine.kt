package com.miro.nabla.playground

import com.miro.nabla.NablaBuilder

/** A client → server message: a change [op] created on top of revision [baseRevision]. */
data class Submission(val clientId: Int, val op: NablaBuilder, val baseRevision: Int)

/** A server → client message. */
sealed interface ServerMessage
/** Confirmation that the recipient's own outstanding op was applied. */
data object Acknowledgement : ServerMessage
/** Another client's change, already transformed into the server's coordinate space. */
data class RemoteOperation(val op: NablaBuilder) : ServerMessage

/** Authoritative replica: keeps the canonical document and an ordered history of applied ops. */
class Server {
    private val _history = mutableListOf<NablaBuilder>()
    val history: List<NablaBuilder> get() = _history

    var document: NablaBuilder = NablaBuilder()
        private set

    val revision: Int get() = _history.size

    /**
     * Applies a client [op] that was based on [baseRevision], transforming it past every op that
     * has been applied since. Returns the transformed op now in [history].
     */
    fun receive(op: NablaBuilder, baseRevision: Int): NablaBuilder {
        var transformed = op
        for (i in baseRevision until _history.size) {
            transformed = Ot.transformPair(transformed, _history[i]).first
        }
        document = Ot.compose(document, transformed)
        _history.add(transformed)
        return transformed
    }
}

/**
 * A client replica running the standard OT control algorithm: at most one [outstanding] op is in
 * flight, while further local edits accumulate in [buffer].
 */
class Client(val id: Int) {
    var document: NablaBuilder = NablaBuilder()
        private set
    var revision: Int = 0
        private set
    var outstanding: NablaBuilder? = null
        private set
    var buffer: NablaBuilder? = null
        private set

    val state: String
        get() = when {
            outstanding == null -> "synced"
            buffer == null -> "awaiting ack"
            else -> "awaiting + buffered"
        }

    /** Applies a local [change]; returns a [Submission] to send, or `null` if one is already in flight. */
    fun applyLocal(change: NablaBuilder): Submission? {
        document = Ot.compose(document, change)
        return if (outstanding == null) {
            outstanding = change
            Submission(id, change, revision)
        } else {
            buffer = buffer?.let { Ot.compose(it, change) } ?: change
            null
        }
    }

    /**
     * Applies an incoming remote [op], rebasing any in-flight/buffered work against it.
     * Returns the change actually applied to [document] (useful for moving the local caret).
     */
    fun applyRemote(op: NablaBuilder): NablaBuilder {
        val o = outstanding
        val docChange: NablaBuilder
        if (o == null) {
            docChange = op
        } else {
            val b = buffer
            if (b == null) {
                val (newOutstanding, change) = Ot.transformPair(o, op)
                outstanding = newOutstanding
                docChange = change
            } else {
                val (newOutstanding, op1) = Ot.transformPair(o, op)
                val (newBuffer, change) = Ot.transformPair(b, op1)
                outstanding = newOutstanding
                buffer = newBuffer
                docChange = change
            }
        }
        document = Ot.compose(document, docChange)
        revision += 1
        return docChange
    }

    /** Handles acknowledgement of the [outstanding] op; returns the buffered [Submission] if any. */
    fun applyAck(): Submission? {
        revision += 1
        val pending = buffer
        outstanding = null
        buffer = null
        return if (pending != null) {
            outstanding = pending
            Submission(id, pending, revision)
        } else {
            null
        }
    }
}

/**
 * Wires a [Server] to N [Client]s through pausable per-client links. Messages sit in [uplink] /
 * [downlink] queues; a disconnected client neither sends nor receives until reconnected.
 */
class Hub(clientCount: Int) {
    val server = Server()
    val clients = List(clientCount) { Client(it) }
    val uplink = List(clientCount) { ArrayDeque<Submission>() }
    val downlink = List(clientCount) { ArrayDeque<ServerMessage>() }
    val connected = BooleanArray(clientCount) { true }

    fun edit(clientId: Int, change: NablaBuilder) {
        clients[clientId].applyLocal(change)?.let { uplink[clientId].addLast(it) }
    }

    /** Removes the next submission from [clientId]'s uplink (if connected), without applying it. */
    fun dispatchUplink(clientId: Int): Submission? =
        if (connected[clientId] && uplink[clientId].isNotEmpty()) uplink[clientId].removeFirst() else null

    /** Applies a dispatched [submission] at the server and broadcasts the result. */
    fun applyUplink(submission: Submission) {
        val transformed = server.receive(submission.op, submission.baseRevision)
        for (other in clients.indices) {
            downlink[other].addLast(
                if (other == submission.clientId) Acknowledgement else RemoteOperation(transformed),
            )
        }
    }

    /** Removes the next message from [clientId]'s downlink (if connected), without applying it. */
    fun dispatchDownlink(clientId: Int): ServerMessage? =
        if (connected[clientId] && downlink[clientId].isNotEmpty()) downlink[clientId].removeFirst() else null

    /**
     * Applies a dispatched server [message] at [clientId]; a freed buffer may queue a new submission.
     * Returns the change applied to the client's document (for caret tracking), or `null` for an ack.
     */
    fun applyDownlink(clientId: Int, message: ServerMessage): NablaBuilder? = when (message) {
        Acknowledgement -> {
            clients[clientId].applyAck()?.let { uplink[clientId].addLast(it) }
            null
        }
        is RemoteOperation -> clients[clientId].applyRemote(message.op)
    }

    /** Dispatches and immediately applies one queued message. Returns `false` when nothing is deliverable. */
    fun step(): Boolean {
        for (i in clients.indices) {
            dispatchUplink(i)?.let { applyUplink(it); return true }
        }
        for (i in clients.indices) {
            dispatchDownlink(i)?.let { applyDownlink(i, it); return true }
        }
        return false
    }

    /** Drives [step] until every connected link is idle (used in tests and the "deliver all" button). */
    fun settle(maxSteps: Int = 100_000) {
        var steps = 0
        while (steps++ < maxSteps && step()) {
            // keep draining
        }
    }

    fun pendingMessages(clientId: Int): Int = uplink[clientId].size + downlink[clientId].size
}
