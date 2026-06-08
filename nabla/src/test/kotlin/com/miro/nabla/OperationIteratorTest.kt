package com.miro.nabla

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OperationIteratorTest {
    private companion object {
        private val ops = listOf(
            Insert(StringElement("Hello")),
            Retain(3),
            Insert(ObjectElement(2)),
            Delete(4),
        )
    }

    @Test
    fun `hasNext is true when ops remain`() {
        assertTrue(OperationIterator(ops).hasNext())
    }

    @Test
    fun `hasNext is false when empty`() {
        assertFalse(OperationIterator(emptyList()).hasNext())
    }

    @Test
    fun `hasNext stays true before each remaining op`() {
        val iter = OperationIterator(ops)
        assertTrue(iter.hasNext())
        iter.next()
        assertTrue(iter.hasNext())
        iter.next()
        assertTrue(iter.hasNext())
        iter.next()
        assertTrue(iter.hasNext())
    }

    @Test
    fun `hasNext is true after a partial advance`() {
        val iter = OperationIterator(ops)
        iter.next(2)
        assertTrue(iter.hasNext())
    }

    @Test
    fun `hasNext is false with no ops left`() {
        assertFalse(OperationIterator(emptyList()).hasNext())
    }

    @Test
    fun `next returns each op then throws when exhausted`() {
        val iterator = OperationIterator(ops)
        for (op in ops) {
            assertEquals(op, iterator.next())
        }
        assertFailsWith<NoSuchElementException> { iterator.next() }
    }

    @Test
    fun `next with length`() {
        val iterator = OperationIterator(ops)
        assertEquals(Insert(StringElement("He")), iterator.next(2))
        assertEquals(Insert(StringElement("llo")), iterator.next(10))
        assertEquals(Retain(1), iterator.next(1))
        assertEquals(Retain(2), iterator.next(2))
    }

    @Test
    fun `remaining returns the unconsumed operations`() {
        val iterator = OperationIterator(ops)
        iterator.next(2)
        assertEquals(
            listOf(
                Insert(StringElement("llo")),
                Retain(3),
                Insert(ObjectElement(2)),
                Delete(4),
            ),
            iterator.remaining(),
        )
        iterator.next(3)
        assertEquals(
            listOf(
                Retain(3),
                Insert(ObjectElement(2)),
                Delete(4),
            ),
            iterator.remaining(),
        )
        iterator.next(3)
        iterator.next(2)
        iterator.next(4)
        assertEquals(emptyList(), iterator.remaining())
    }
}
