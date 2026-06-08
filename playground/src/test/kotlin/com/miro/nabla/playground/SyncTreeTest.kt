package com.miro.nabla.playground

import com.miro.nabla.BufferId
import com.miro.nabla.NablaBuilder
import kotlin.test.Test
import kotlin.test.assertEquals

/** Tree edits driven through the real [Hub], including a cross-subtree move and concurrent edits. */
class SyncTreeTest {

    private fun Hub.tree(client: Int): NablaBuilder = clients[client].document

    private fun assertConverged(hub: Hub, expected: String) {
        assertEquals(expected, hub.server.document.renderTree(), "server")
        assertEquals(expected, hub.tree(0).renderTree(), "client 0")
        assertEquals(expected, hub.tree(1).renderTree(), "client 1")
    }

    /** Builds `red[green], blue` on client 0 and syncs it to everyone. */
    private fun seededHub(): Hub = Hub(2).apply {
        edit(0, addNodeChange(tree(0), emptyList(), nodeId = 1, color = "red")); settle()
        edit(0, addNodeChange(tree(0), emptyList(), nodeId = 2, color = "blue")); settle()
        edit(0, addNodeChange(tree(0), listOf(0), nodeId = 3, color = "green")); settle()
    }

    @Test
    fun `cross-subtree move syncs to every replica`() {
        val hub = seededHub()
        assertConverged(hub, "red[green],blue")

        // Move "green" (path [0,0]) out of "red" and under "blue" (path [1]).
        hub.edit(0, moveNodeChange(hub.tree(0), srcPath = listOf(0, 0), dstParentPath = listOf(1), bufferId = BufferId(1)))
        hub.settle()

        assertConverged(hub, "red,blue[green]")
    }

    @Test
    fun `move under a later sibling keeps the node (path crosses the cut)`() {
        val hub = Hub(2).apply {
            edit(0, addNodeChange(tree(0), emptyList(), 1, "red")); settle()
            edit(0, addNodeChange(tree(0), emptyList(), 2, "blue")); settle()
            edit(0, addNodeChange(tree(0), emptyList(), 3, "green")); settle()
        }
        assertConverged(hub, "red,blue,green")

        // Move "red" [0] under "green" [2] — the paste path crosses the cut at the root.
        hub.edit(0, moveNodeChange(hub.tree(0), srcPath = listOf(0), dstParentPath = listOf(2), bufferId = BufferId(1)))
        hub.settle()

        assertConverged(hub, "blue,green[red]")
    }

    @Test
    fun `concurrent edits in different subtrees converge`() {
        val hub = seededHub()

        // c0 recolours "green"; c1 adds an "orange" child under "blue" — different subtrees.
        hub.edit(0, recolorChange(nodePath = listOf(0, 0), color = "yellow"))
        hub.edit(1, addNodeChange(hub.tree(1), parentPath = listOf(1), nodeId = 4, color = "orange"))

        hub.applyUplink(hub.dispatchUplink(0)!!)
        hub.applyUplink(hub.dispatchUplink(1)!!)
        hub.settle()

        assertConverged(hub, "red[yellow],blue[orange]")
    }

    @Test
    fun `move a node while it is concurrently recolored`() {
        val hub = seededHub() // red[green], blue
        hub.edit(0, moveNodeChange(hub.tree(0), srcPath = listOf(0, 0), dstParentPath = listOf(1), bufferId = BufferId(1)))
        hub.edit(1, recolorChange(nodePath = listOf(0, 0), color = "yellow"))

        hub.applyUplink(hub.dispatchUplink(0)!!)
        hub.applyUplink(hub.dispatchUplink(1)!!)
        hub.settle()

        // The recolour follows "green" to its new home under "blue".
        assertConverged(hub, "red,blue[yellow]")
    }

    @Test
    fun `move a node while it is concurrently recolored, delete arrives first`() {
        val hub = seededHub()
        hub.edit(0, moveNodeChange(hub.tree(0), srcPath = listOf(0, 0), dstParentPath = listOf(1), bufferId = BufferId(1)))
        hub.edit(1, recolorChange(nodePath = listOf(0, 0), color = "yellow"))

        hub.applyUplink(hub.dispatchUplink(1)!!)
        hub.applyUplink(hub.dispatchUplink(0)!!)
        hub.settle()

        assertConverged(hub, "red,blue[yellow]")
    }

    @Test
    fun `concurrent moves crossing subtrees converge`() {
        val hub = Hub(2).apply {
            edit(0, addNodeChange(tree(0), emptyList(), 1, "red")); settle()
            edit(0, addNodeChange(tree(0), emptyList(), 2, "blue")); settle()
            edit(0, addNodeChange(tree(0), listOf(0), 3, "green")); settle()
            edit(0, addNodeChange(tree(0), listOf(1), 4, "orange")); settle()
        }
        assertConverged(hub, "red[green],blue[orange]")

        // c0: green -> blue ; c1: orange -> red (each into the other's subtree).
        hub.edit(0, moveNodeChange(hub.tree(0), listOf(0, 0), listOf(1), BufferId(100)))
        hub.edit(1, moveNodeChange(hub.tree(1), listOf(1, 0), listOf(0), BufferId(200)))

        hub.applyUplink(hub.dispatchUplink(0)!!)
        hub.applyUplink(hub.dispatchUplink(1)!!)
        hub.settle()

        assertConverged(hub, "red[orange],blue[green]")
    }

    @Test
    fun `canMove forbids no-op and cycles`() {
        // src [0,0] under its current parent [0] -> no-op; under itself/descendant -> cycle.
        assertEquals(false, canMove(srcPath = listOf(0, 0), dstParentPath = listOf(0)))
        assertEquals(false, canMove(srcPath = listOf(0), dstParentPath = listOf(0, 1)))
        assertEquals(true, canMove(srcPath = listOf(0, 0), dstParentPath = listOf(1)))
    }
}
