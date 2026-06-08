package com.miro.nabla.playground

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miro.nabla.Insert
import com.miro.nabla.NablaBuilder
import kotlin.math.hypot

private data class Placed(val path: List<Int>, val x: Float, val y: Float, val color: String)

private const val NODE_RADIUS = 16f
private const val HIT_RADIUS = 22f

private fun parseColor(hex: String): Color =
    runCatching { Color(("ff" + hex.removePrefix("#")).toLong(16)) }.getOrDefault(Color.Gray)

/** Tidy top-down layout: leaves spread left-to-right, parents centred over their children. */
private fun layout(tree: NablaBuilder, areaWidth: Float): Pair<List<Placed>, List<Pair<Offset, Offset>>> {
    val placed = mutableListOf<Placed>()
    val edges = mutableListOf<Pair<Offset, Offset>>()
    val gap = 56f
    val levelHeight = 72f
    val topY = 34f
    var nextLeafX = 0f

    fun place(children: List<Insert>, depth: Int, parentPath: List<Int>): List<Float> {
        val centers = mutableListOf<Float>()
        children.forEachIndexed { i, op ->
            val node = op.element as NodeElement
            val color = op.attributes["color"] as? String ?: "#888888"
            val path = parentPath + i
            val y = topY + depth * levelHeight
            val kids = node.children.ops.filterIsInstance<Insert>()
            val cx = if (kids.isEmpty()) {
                (nextLeafX).also { nextLeafX += gap }
            } else {
                val kidCenters = place(kids, depth + 1, path)
                val center = (kidCenters.first() + kidCenters.last()) / 2f
                val kidY = topY + (depth + 1) * levelHeight
                kidCenters.forEach { kx -> edges.add(Offset(center, y) to Offset(kx, kidY)) }
                center
            }
            placed.add(Placed(path, cx, y, color))
            centers.add(cx)
        }
        return centers
    }

    place(tree.ops.filterIsInstance<Insert>(), 0, emptyList())
    val span = (nextLeafX - gap).coerceAtLeast(0f)
    val shift = maxOf(NODE_RADIUS + 8f, (areaWidth - span) / 2f)
    return placed.map { it.copy(x = it.x + shift) } to
        edges.map { (a, b) -> Offset(a.x + shift, a.y) to Offset(b.x + shift, b.y) }
}

@Composable
private fun TreeView(
    tree: NablaBuilder,
    selected: List<Int>?,
    onSelect: ((List<Int>?) -> Unit)?,
    onMove: ((src: List<Int>, dstParent: List<Int>) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(230.dp)
            .border(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.2f)),
    ) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val (placed, edges) = remember(tree, widthPx) { layout(tree, widthPx) }

        fun hit(at: Offset): Placed? = placed.firstOrNull { hypot(it.x - at.x, it.y - at.y) <= HIT_RADIUS }

        var dragSource by remember { mutableStateOf<List<Int>?>(null) }
        var dragAt by remember { mutableStateOf<Offset?>(null) }

        val interaction = if (onSelect == null) {
            Modifier
        } else {
            Modifier
                .pointerInput(placed) {
                    detectTapGestures { pos -> onSelect(hit(pos)?.path) }
                }
                .pointerInput(placed) {
                    detectDragGestures(
                        onDragStart = { pos -> dragSource = hit(pos)?.path; dragAt = pos },
                        onDrag = { change, _ -> dragAt = change.position; change.consume() },
                        onDragEnd = {
                            val src = dragSource
                            val target = dragAt?.let { hit(it) }
                            if (src != null && target != null) onMove?.invoke(src, target.path)
                            dragSource = null
                            dragAt = null
                        },
                        onDragCancel = { dragSource = null; dragAt = null },
                    )
                }
        }

        Canvas(Modifier.fillMaxSize().then(interaction)) {
            edges.forEach { (a, b) -> drawLine(Color(0xFFBDBDBD), a, b, strokeWidth = 2f) }
            placed.forEach { p ->
                drawCircle(parseColor(p.color), NODE_RADIUS, Offset(p.x, p.y))
                if (p.path == selected) {
                    drawCircle(Color(0xFF212121), NODE_RADIUS + 3f, Offset(p.x, p.y), style = Stroke(width = 2.5f))
                }
            }
            val source = dragSource?.let { src -> placed.firstOrNull { it.path == src } }
            val at = dragAt
            if (source != null && at != null) {
                drawLine(Color(0xFF1E88E5), Offset(source.x, source.y), at, strokeWidth = 2f)
                hit(at)?.takeIf { it.path != source.path }?.let {
                    drawCircle(Color(0xFF1E88E5), NODE_RADIUS + 5f, Offset(it.x, it.y), style = Stroke(width = 2f))
                }
            }
        }
    }
}

@Composable
fun TreeAppView(model: TreeModel) {
    model.tick // subscribe

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("nabla — tree OT playground", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Tap a circle to select. Add/Recolor/Delete act on the selection. Drag a circle onto " +
                "another to reparent it (a single cut + paste). Step or Deliver all to sync.",
            fontSize = 12.sp,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = model.playing, onCheckedChange = { model.playing = it })
                    Text("Auto-play")
                }
                Button(onClick = { model.deliverAll() }) { Text("Deliver all") }
            }
        }
        Spacer(Modifier.height(8.dp))
        NetworkLane(model.packets, model::removePacket)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TreeClientPanel(model, 0, Modifier.weight(1f))
            TreeServerPanel(model, Modifier.weight(1f))
            TreeClientPanel(model, 1, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TreeClientPanel(model: TreeModel, clientId: Int, modifier: Modifier = Modifier) {
    model.tick // subscribe
    val client = model.hub.clients[clientId]
    val connected = model.hub.connected[clientId]
    val selected = model.selection.getOrNull(clientId)

    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Client $clientId", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                Checkbox(checked = connected, onCheckedChange = { model.setConnected(clientId, it) })
                Text(if (connected) "online" else "offline", fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "rev ${client.revision} · ${client.state} · ${model.pending(clientId)} queued",
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                )
                Spacer(Modifier.weight(1f))
                Button(onClick = { model.stepClient(clientId) }, enabled = connected) { Text("Step") }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { model.addNode(clientId) }) {
                    Text(if (selected == null) "Add root" else "Add child")
                }
                OutlinedButton(onClick = { model.recolorSelected(clientId) }, enabled = selected != null) { Text("Recolor") }
                OutlinedButton(onClick = { model.removeSelected(clientId) }, enabled = selected != null) { Text("Delete") }
            }
            Spacer(Modifier.height(6.dp))
            TreeView(
                tree = model.tree(clientId),
                selected = selected,
                onSelect = { model.select(clientId, it) },
                onMove = { src, dst -> model.move(clientId, src, dst) },
            )
        }
    }
}

@Composable
private fun TreeServerPanel(model: TreeModel, modifier: Modifier = Modifier) {
    model.tick // subscribe
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text("Server", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                "rev ${model.hub.server.revision} (authoritative · read-only)",
                fontSize = 12.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(6.dp))
            TreeView(tree = model.hub.server.document, selected = null, onSelect = null, onMove = null)
        }
    }
}
