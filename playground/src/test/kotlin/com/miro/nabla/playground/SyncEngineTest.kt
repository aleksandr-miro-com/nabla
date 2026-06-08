package com.miro.nabla.playground

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncEngineTest {

    private fun Hub.assertConverged() {
        val server = server.document.text()
        for (client in clients) {
            assertEquals(server, client.document.text(), "client ${client.id} diverged from server")
            assertTrue(client.outstanding == null, "client ${client.id} still has an outstanding op")
            assertTrue(client.buffer == null, "client ${client.id} still has a buffered op")
        }
    }

    @Test
    fun `sequential edits apply in order`() {
        val hub = Hub(clientCount = 2)
        hub.edit(0, insertChange(0, "Hello"))
        hub.settle()
        hub.edit(1, insertChange(5, " world"))
        hub.settle()
        assertEquals("Hello world", hub.server.document.text())
        hub.assertConverged()
    }

    @Test
    fun `concurrent inserts at the same position converge`() {
        val hub = Hub(clientCount = 2)
        hub.edit(0, insertChange(0, "Hello"))
        hub.edit(1, insertChange(0, "World"))
        hub.settle()
        hub.assertConverged()
        // Both clients agree with whatever single ordering the server committed.
        assertTrue(hub.server.document.text() in setOf("HelloWorld", "WorldHello"))
    }

    @Test
    fun `concurrent insert and delete converge`() {
        val hub = Hub(clientCount = 2)
        hub.edit(0, insertChange(0, "Hello"))
        hub.settle()
        // Concurrently: client 0 inserts mid-word, client 1 deletes a range.
        hub.edit(0, insertChange(2, "XYZ"))
        hub.edit(1, deleteChange(1, 3))
        hub.settle()
        hub.assertConverged()
    }

    @Test
    fun `offline editing reconciles on reconnect`() {
        val hub = Hub(clientCount = 2)
        hub.edit(0, insertChange(0, "shared"))
        hub.settle()

        // Client 0 goes offline and keeps typing; client 1 keeps editing online.
        hub.connected[0] = false
        hub.edit(0, insertChange(6, "-A1"))
        hub.edit(0, insertChange(9, "-A2"))
        hub.edit(1, insertChange(0, "B-"))
        hub.settle() // client 1 + server sync; client 0's work is stuck in its queues

        assertTrue(hub.pendingMessages(0) > 0)

        hub.connected[0] = true
        hub.settle()
        hub.assertConverged()
    }

    @Test
    fun `three way concurrent edits converge`() {
        val hub = Hub(clientCount = 3)
        hub.edit(0, insertChange(0, "base"))
        hub.settle()
        hub.edit(0, insertChange(0, "A"))
        hub.edit(1, insertChange(4, "B"))
        hub.edit(2, insertChange(2, "C"))
        hub.settle()
        hub.assertConverged()
    }

    @Test
    fun `interleaved offline edits from both clients converge`() {
        val hub = Hub(clientCount = 2)
        hub.edit(0, insertChange(0, "0123456789"))
        hub.settle()

        hub.connected[0] = false
        hub.connected[1] = false
        hub.edit(0, insertChange(0, "<"))
        hub.edit(0, deleteChange(5, 2))
        hub.edit(1, insertChange(10, ">"))
        hub.edit(1, deleteChange(0, 3))

        hub.connected[0] = true
        hub.connected[1] = true
        hub.settle()
        hub.assertConverged()
    }
}
