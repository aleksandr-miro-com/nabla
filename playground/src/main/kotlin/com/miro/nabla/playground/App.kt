package com.miro.nabla.playground

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miro.nabla.Delete
import com.miro.nabla.Insert
import com.miro.nabla.NablaBuilder
import com.miro.nabla.Retain

private data class ClientSnapshot(
    val id: Int,
    val text: String,
    val state: String,
    val revision: Int,
    val connected: Boolean,
    val buffer: NablaBuilder?,
    val toServer: List<NablaBuilder>,
    val fromServer: List<ServerMessage>,
)

private data class ServerSnapshot(val text: String, val revision: Int)

private val InsertFg = Color(0xFF2E7D32)
private val InsertBg = Color(0xFFE8F5E9)
private val DeleteFg = Color(0xFFC62828)
private val DeleteBg = Color(0xFFFFEBEE)
private val RetainFg = Color(0xFF1565C0)
private val RetainBg = Color(0xFFE3F2FD)
private val AckFg = Color(0xFF616161)
private val AckBg = Color(0xFFEEEEEE)

private fun colorFor(kind: PacketKind): Color = when (kind) {
    PacketKind.INSERT -> InsertFg
    PacketKind.DELETE -> DeleteFg
    PacketKind.RETAIN -> RetainFg
    PacketKind.ACK -> AckFg
}

@Composable
fun App(model: PlaygroundModel) {
    model.tick // subscribe to recomposition

    val clients = model.hub.clients.indices.map { i ->
        val client = model.hub.clients[i]
        ClientSnapshot(
            id = i,
            text = client.document.text(),
            state = client.state,
            revision = client.revision,
            connected = model.hub.connected[i],
            buffer = client.buffer,
            toServer = model.hub.uplink[i].map { it.op },
            fromServer = model.hub.downlink[i].toList(),
        )
    }
    val server = ServerSnapshot(
        text = model.hub.server.document.text(),
        revision = model.hub.server.revision,
    )

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("nabla — OT sync playground", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Edit either client. Take a client offline, type, then bring it back and Step its messages across.",
                fontSize = 12.sp,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(12.dp))

            GlobalControls(model)
            Spacer(Modifier.height(8.dp))

            NetworkLane(model)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (clients.isNotEmpty()) {
                    ClientPanel(clients[0], model, Modifier.weight(1f))
                }
                ServerPanel(server, Modifier.weight(1f))
                if (clients.size > 1) {
                    ClientPanel(clients[1], model, Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(12.dp))
            Legend()
        }
    }
}

@Composable
private fun GlobalControls(model: PlaygroundModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = model.playing, onCheckedChange = { model.playing = it })
                Text("Auto-play")
            }
            Button(onClick = { model.deliverAll() }) { Text("Deliver all") }
            Text(
                "Use each client's Step button to send its messages one at a time.",
                fontSize = 12.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

/** Animated link strip: packets fly between client and server nodes (aligned with the columns below). */
@Composable
private fun NetworkLane(model: PlaygroundModel) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(64.dp)) {
        val width = maxWidth
        val centerY = maxHeight / 2

        fun xOf(node: Node): Dp = when (node) {
            is Node.Client -> if (node.index == 0) width / 6 else width * 5 / 6
            Node.Server -> width / 2
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val cy = size.height / 2f
            val x0 = size.width / 6f
            val xs = size.width / 2f
            val x1 = size.width * 5f / 6f
            drawLine(Color.LightGray, Offset(x0, cy), Offset(xs, cy), strokeWidth = 2f)
            drawLine(Color.LightGray, Offset(xs, cy), Offset(x1, cy), strokeWidth = 2f)
            listOf(x0, xs, x1).forEach { drawCircle(Color.Gray, radius = 6f, center = Offset(it, cy)) }
        }

        model.packets.forEach { packet ->
            key(packet.id) {
                val progress = remember { Animatable(0f) }
                androidx.compose.runtime.LaunchedEffect(packet.id) {
                    progress.animateTo(1f, animationSpec = tween(durationMillis = 700))
                    packet.onArrive()
                    model.removePacket(packet.id)
                }
                val fromX = xOf(packet.from)
                val toX = xOf(packet.to)
                val x = fromX + (toX - fromX) * progress.value
                // Uplink rides above the wire, downlink below, so opposing traffic does not overlap.
                val y = if (packet.to == Node.Server) centerY - 18.dp else centerY + 4.dp
                Box(
                    modifier = Modifier
                        .offset(x = x - 10.dp, y = y)
                        .size(width = 20.dp, height = 14.dp)
                        .background(colorFor(packet.kind), RoundedCornerShape(3.dp)),
                )
            }
        }
    }
}

@Composable
private fun ClientPanel(client: ClientSnapshot, model: PlaygroundModel, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Client ${client.id}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                Checkbox(
                    checked = client.connected,
                    onCheckedChange = { model.setConnected(client.id, it) },
                )
                Text(if (client.connected) "online" else "offline", fontSize = 12.sp)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "rev ${client.revision} · ${client.state}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                )
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { model.stepClient(client.id) },
                    enabled = client.connected,
                ) { Text("Step") }
            }
            Spacer(Modifier.height(8.dp))
            NablaTextEditor(model, client.id, Modifier.fillMaxWidth())

            Spacer(Modifier.height(8.dp))
            Divider()
            Spacer(Modifier.height(8.dp))

            QueueRow("↑ to server", client.toServer.map { delta -> { DeltaChips(delta) } })
            Spacer(Modifier.height(4.dp))
            QueueRow(
                "↓ from server",
                client.fromServer.map { message ->
                    {
                        when (message) {
                            Acknowledgement -> OpChip("✓ ack", AckFg, AckBg)
                            is RemoteOperation -> DeltaChips(message.op)
                        }
                    }
                },
            )
            Spacer(Modifier.height(4.dp))
            QueueRow("local buffer", client.buffer?.let { listOf<@Composable () -> Unit> { DeltaChips(it) } } ?: emptyList())
        }
    }
}

@Composable
private fun ServerPanel(server: ServerSnapshot, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Server", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "rev ${server.revision} (authoritative · read-only)",
                fontSize = 12.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.05f),
                modifier = Modifier.fillMaxWidth().height(150.dp),
            ) {
                Text(
                    server.text.ifEmpty { "(empty)" },
                    modifier = Modifier.padding(8.dp),
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                )
            }
        }
    }
}

/** A labelled row of message "cards", each rendered by its own chip builder. */
@Composable
private fun QueueRow(label: String, messages: List<@Composable () -> Unit>) {
    Column {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
        Spacer(Modifier.height(2.dp))
        if (messages.isEmpty()) {
            Text("idle", fontSize = 11.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.3f))
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                messages.forEach { message ->
                    Surface(
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.03f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) { message() }
                    }
                }
            }
        }
    }
}

/** Renders a delta as a sequence of coloured op chips. */
@Composable
private fun DeltaChips(delta: NablaBuilder) {
    if (delta.ops.isEmpty()) {
        OpChip("∅", AckFg, AckBg)
        return
    }
    delta.ops.forEach { op ->
        when (op) {
            is Insert -> {
                val label = (op.element as? TextElement)?.text?.let { "“$it”" } ?: "embed"
                OpChip(label, InsertFg, InsertBg)
            }
            is Delete -> OpChip("⌫ ${op.length}", DeleteFg, DeleteBg)
            is Retain -> {
                val formatting = if (!op.attributes.isEmpty) " ✎" else ""
                OpChip("→ ${op.length}$formatting", RetainFg, RetainBg)
            }
        }
    }
}

@Composable
private fun OpChip(label: String, fg: Color, bg: Color) {
    Surface(color = bg, shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, fg.copy(alpha = 0.4f))) {
        Text(
            label,
            color = fg,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Legend:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        OpChip("“text”", InsertFg, InsertBg)
        Text("insert", fontSize = 12.sp)
        OpChip("⌫ n", DeleteFg, DeleteBg)
        Text("delete n", fontSize = 12.sp)
        OpChip("→ n", RetainFg, RetainBg)
        Text("retain n (✎ = sets formatting)", fontSize = 12.sp)
    }
}
