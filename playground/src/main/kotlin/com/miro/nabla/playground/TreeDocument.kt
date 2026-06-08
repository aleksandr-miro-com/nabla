package com.miro.nabla.playground

import com.miro.nabla.AttributeMap
import com.miro.nabla.BufferId
import com.miro.nabla.Insert
import com.miro.nabla.NablaBuilder

/** A node's colour, stored as an attribute on its Insert/Retain. */
fun colorAttributes(color: String): AttributeMap = AttributeMap.of("color" to color)

/** The children list at [path] (an empty path is the top-level forest). */
fun childrenAt(tree: NablaBuilder, path: List<Int>): NablaBuilder {
    var current = tree
    for (index in path) {
        val node = (current.ops.getOrNull(index) as? Insert)?.element as? NodeElement ?: return NablaBuilder()
        current = node.children
    }
    return current
}

/** The node insert at [path], or null. */
fun nodeAt(tree: NablaBuilder, path: List<Int>): Insert? {
    if (path.isEmpty()) return null
    return childrenAt(tree, path.dropLast(1)).ops.getOrNull(path.last()) as? Insert
}

fun childCountAt(tree: NablaBuilder, path: List<Int>): Int = childrenAt(tree, path).ops.size

/** Wraps a children-list change [local] so it applies at the children list located at [path]. */
fun atContainer(path: List<Int>, local: NablaBuilder): NablaBuilder {
    if (path.isEmpty()) return local
    return NablaBuilder().retain(path[0]).retain(1, child = atContainer(path.drop(1), local))
}

/** Appends a new child node (id [nodeId], colour [color]) to the node at [parentPath]. */
fun addNodeChange(tree: NablaBuilder, parentPath: List<Int>, nodeId: Long, color: String): NablaBuilder =
    atContainer(
        parentPath,
        NablaBuilder().retain(childCountAt(tree, parentPath)).insert(NodeElement(nodeId), colorAttributes(color)),
    )

/** Recolours the node at [nodePath]. */
fun recolorChange(nodePath: List<Int>, color: String): NablaBuilder =
    atContainer(nodePath.dropLast(1), NablaBuilder().retain(nodePath.last()).retain(1, colorAttributes(color)))

/** Removes the node at [nodePath] (and its subtree). */
fun removeNodeChange(nodePath: List<Int>): NablaBuilder =
    atContainer(nodePath.dropLast(1), NablaBuilder().retain(nodePath.last()).delete(1))

/**
 * Moves the node at [srcPath] to become the last child of the node at [dstParentPath], as one
 * first-class move (linked cut + paste over a global buffer). The caller must ensure [dstParentPath]
 * is neither [srcPath], a descendant of it, nor its current parent (see [canMove]).
 *
 * The delta is built in a single pass over the original tree, so the cut and the navigation toward
 * the paste use the same (original) coordinates — composing two separately-coordinated deltas would
 * mis-place the paste whenever its path crosses the cut.
 */
fun moveNodeChange(tree: NablaBuilder, srcPath: List<Int>, dstParentPath: List<Int>, bufferId: BufferId): NablaBuilder =
    buildMove(
        here = emptyList(),
        actions = listOf(
            MoveAction(srcPath.dropLast(1), srcPath.last(), isCut = true, bufferId),
            MoveAction(dstParentPath, childCountAt(tree, dstParentPath), isCut = false, bufferId),
        ),
    )

private data class MoveAction(val container: List<Int>, val index: Int, val isCut: Boolean, val bufferId: BufferId)

/** Emits the children-list change at container [here] for all [actions] that fall in or below it. */
private fun buildMove(here: List<Int>, actions: List<MoveAction>): NablaBuilder {
    val builder = NablaBuilder()
    val local = actions.filter { it.container == here }
    val deeper = actions.filter { it.container.size > here.size && it.container.subList(0, here.size) == here }
    val navByChild = deeper.groupBy { it.container[here.size] }
    val cutByIndex = local.filter { it.isCut }.associateBy { it.index }

    // Node-consuming stops (cuts + navigations), in index order; pastes append at the end.
    var pos = 0
    for (index in (cutByIndex.keys + navByChild.keys).toSortedSet()) {
        if (index > pos) builder.retain(index - pos)
        val cut = cutByIndex[index]
        if (cut != null) builder.cut(1, cut.bufferId) else builder.retain(1, child = buildMove(here + index, navByChild.getValue(index)))
        pos = index + 1
    }
    for (paste in local.filter { !it.isCut }.sortedBy { it.index }) {
        if (paste.index > pos) builder.retain(paste.index - pos)
        builder.paste(1, paste.bufferId)
        pos = paste.index
    }
    return builder
}

/** Whether [srcPath] may be reparented under [dstParentPath] without a no-op or a cycle. */
fun canMove(srcPath: List<Int>, dstParentPath: List<Int>): Boolean {
    if (srcPath.isEmpty()) return false
    if (dstParentPath == srcPath.dropLast(1)) return false // already its parent
    return !isPrefix(srcPath, dstParentPath) // dst is src or a descendant -> cycle
}

private fun isPrefix(prefix: List<Int>, path: List<Int>): Boolean =
    prefix.size <= path.size && path.subList(0, prefix.size) == prefix

/** Renders a tree by node colour, e.g. `red,blue[green]` (for tests/debug). */
fun NablaBuilder.renderTree(): String = ops.joinToString(",") { op ->
    op as Insert
    val color = op.attributes["color"] ?: "?"
    val node = op.element as NodeElement
    val children = if (node.children.ops.isEmpty()) "" else "[${node.children.renderTree()}]"
    "$color$children"
}
