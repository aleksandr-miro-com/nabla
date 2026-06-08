package com.miro.nabla.playground

import com.miro.nabla.BufferId
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * End-to-end sync of a first-class move against a concurrent edit, driven through the real
 * [Hub]/[Server]/[Client] control algorithm (as the playground does). Reproduces: document
 * "hello11122333", one client moves "11122333" to the front, the other deletes "22".
 */
class SyncMoveTest {

    private fun syncedHub(): Hub = Hub(2).apply {
        edit(0, insertChange(0, "hello11122333"))
        settle()
    }

    private fun assertAllConverged(hub: Hub, expected: String) {
        assertEquals(expected, hub.server.document.text(), "server")
        assertEquals(expected, hub.clients[0].document.text(), "client 0")
        assertEquals(expected, hub.clients[1].document.text(), "client 1")
    }

    @Test
    fun `move reaches the server before the concurrent delete`() {
        val hub = syncedHub()
        hub.edit(0, moveChange(start = 5, end = 13, dropAt = 0, bufferId = BufferId(1))) // "11122333" -> front
        hub.edit(1, deleteChange(at = 8, count = 2)) // delete "22"

        hub.applyUplink(hub.dispatchUplink(0)!!)
        hub.applyUplink(hub.dispatchUplink(1)!!)
        hub.settle()

        assertAllConverged(hub, "111333hello")
    }

    @Test
    fun `delete reaches the server before the move`() {
        val hub = syncedHub()
        hub.edit(0, moveChange(start = 5, end = 13, dropAt = 0, bufferId = BufferId(1)))
        hub.edit(1, deleteChange(at = 8, count = 2))

        hub.applyUplink(hub.dispatchUplink(1)!!)
        hub.applyUplink(hub.dispatchUplink(0)!!)
        hub.settle()

        assertAllConverged(hub, "111333hello")
    }
}
