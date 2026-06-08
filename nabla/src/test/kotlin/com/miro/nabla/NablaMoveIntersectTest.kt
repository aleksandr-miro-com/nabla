package com.miro.nabla

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Concurrency that INTERSECTS a move's source range (partial delete, insert-travels, overlapping
 * moves). These need the redirect/chase engine and are expected to fail until it lands.
 */
class NablaMoveIntersectTest {

    private val composer = NablaComposer()
    private val transformer = NablaTransformer()

    private fun text(value: String) = StringElement(value)
    private fun doc(value: String) = NablaBuilder().insert(text(value))
    private fun bid(value: Long) = BufferId(value)

    private fun assertConverges(document: NablaBuilder, a: NablaBuilder, b: NablaBuilder) {
        val aPrime = transformer.transform(b, a, priority = false)
        val bPrime = transformer.transform(a, b, priority = true)
        val left = composer.compose(composer.compose(document, a), bPrime)
        val right = composer.compose(composer.compose(document, b), aPrime)
        assertEquals(plainText(left), plainText(right), "must converge")
    }

    @Test
    fun `concurrent delete eats part of the moved range`() {
        // a moves "BCD" [1,4) to the end; b deletes "C" (index 2), which is inside the moved range.
        val moveBCD = NablaBuilder().retain(1).cut(3, bid(1)).retain(2).paste(3, bid(1))
        val deleteC = NablaBuilder().retain(2).delete(1)
        // Expected converged result: the move carries the surviving "BD" to the end -> "AEFBD".
        assertConverges(doc("ABCDEF"), moveBCD, deleteC)
    }

    @Test
    fun `move to start with concurrent delete inside the moved range`() {
        val document = doc("hello11122333")
        // User 1 moves "11122333" [5,13) to the beginning.
        val move = NablaBuilder().paste(8, bid(1)).retain(5).cut(8, bid(1))
        // User 2 deletes "22" [8,10), which sits inside the moved range.
        val delete = NablaBuilder().retain(8).delete(2)

        val aPrime = transformer.transform(delete, move, priority = false)
        val bPrime = transformer.transform(move, delete, priority = true)
        val left = composer.compose(composer.compose(document, move), bPrime)
        val right = composer.compose(composer.compose(document, delete), aPrime)

        assertEquals("111333hello", plainText(left))
        assertEquals("111333hello", plainText(right))
    }

    @Test
    fun `move to start with concurrent delete inside the moved range 2`() {
        val document = doc("hello111333")
        // User 1 moves "11122333" [5,13) to the beginning.
        val move = NablaBuilder().paste(6, bid(1)).retain(5).cut(6, bid(1))
        // User 2 deletes "22" [8,10), which sits inside the moved range.
        val insert = NablaBuilder().retain(8).insert(text("22"))

        val aPrime = transformer.transform(insert, move, priority = false)
        val bPrime = transformer.transform(move, insert, priority = true)
        val left = composer.compose(composer.compose(document, move), bPrime)
        val right = composer.compose(composer.compose(document, insert), aPrime)

        assertEquals("111333hello22", plainText(left))
        assertEquals("111333hello22", plainText(right))
    }

    @Test
    fun `move to start with concurrent delete inside the moved range 3`() {
        val document = doc("hello111333")
        // User 1 moves "11122333" [5,13) to the beginning.
        val move = NablaBuilder().paste(6, bid(1)).retain(5).cut(6, bid(1))
        // User 2 deletes "22" [8,10), which sits inside the moved range.
        val delete = NablaBuilder().retain(5).delete(6)

        val aPrime = transformer.transform(delete, move, priority = false)
        val bPrime = transformer.transform(move, delete, priority = true)
        val left = composer.compose(composer.compose(document, move), bPrime)
        val right = composer.compose(composer.compose(document, delete), aPrime)

        assertEquals("hello", plainText(left))
        assertEquals("hello", plainText(right))
    }
}
