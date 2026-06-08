package com.miro.nabla

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Move (cut + paste) support. A cut is a [Delete] tagged with a [BufferId]; the matching paste is an
 * [Insert] of a [BufferElement] that the composer resolves from the cut's removed content. This
 * suite covers the representation, resolution during composition, and convergence (TP1) for
 * concurrency that does not intersect the moved range. Intersecting concurrency (partial delete,
 * insert-travels, overlapping moves) is handled by the redirect/chase engine in its own suite.
 */
class NablaMoveTest {

    private val composer = NablaComposer()
    private val transformer = NablaTransformer()

    private fun text(value: String) = StringElement(value)
    private fun doc(value: String) = NablaBuilder().insert(text(value))
    private fun bid(value: Long) = BufferId(value)

    private fun apply(document: NablaBuilder, change: NablaBuilder): NablaBuilder =
        composer.compose(document, change)

    // ----- representation -----

    @Test
    fun `cut keeps its buffer id when split`() {
        val cut = Delete(5, bid(1))
        assertEquals(Delete(2, bid(1)), cut.subOperation(0, 2))
        assertEquals(Delete(3, bid(1)), cut.subOperation(2, 3))
    }

    @Test
    fun `paste keeps its buffer id and length when split`() {
        val element = BufferElement(bid(1), 5)
        assertEquals(BufferElement(bid(1), 2), element.subElement(0, 2))
        assertEquals(BufferElement(bid(1), 3), element.subElement(2, 3))
    }

    @Test
    fun `builder does not merge a cut with a plain delete`() {
        val ops = NablaBuilder().delete(2).cut(3, bid(1)).ops
        assertEquals(listOf(Delete(2), Delete(3, bid(1))), ops)
    }

    @Test
    fun `builder keeps adjacent cuts distinct`() {
        val ops = NablaBuilder().cut(2, bid(1)).cut(3, bid(2)).ops
        assertEquals(listOf(Delete(2, bid(1)), Delete(3, bid(2))), ops)
    }

    // ----- resolution during composition -----

    @Test
    fun `compose resolves a move to the right`() {
        val moved = NablaBuilder().retain(1).cut(2, bid(1)).retain(3).paste(2, bid(1))
        assertEquals("ADEFBC", plainText(apply(doc("ABCDEF"), moved)))
    }

    @Test
    fun `compose resolves a move to the left`() {
        val moved = NablaBuilder().paste(2, bid(1)).retain(3).cut(2, bid(1))
        assertEquals("DEABCF", plainText(apply(doc("ABCDEF"), moved)))
    }

    @Test
    fun `move carries the formatting of the moved content`() {
        val document = NablaBuilder()
            .insert(text("A"))
            .insert(text("BC"), AttributeMap.of("bold" to true))
            .insert(text("DEF"))
        val moved = NablaBuilder().retain(1).cut(2, bid(1)).retain(3).paste(2, bid(1))
        val result = apply(document, moved)
        val boldRun = result.ops.filterIsInstance<Insert>()
            .single { it.attributes == AttributeMap.of("bold" to true) }
        assertEquals("BC", (boldRun.element as StringElement).value)
        assertEquals("ADEFBC", plainText(result))
    }

    @Test
    fun `cut and paste are linked by the same buffer id`() {
        val moved = NablaBuilder().retain(1).cut(2, bid(7)).retain(3).paste(2, bid(7))
        val cut = moved.ops.filterIsInstance<Delete>().single()
        val paste = moved.ops.filterIsInstance<Insert>().single().element as BufferElement
        assertEquals(cut.bufferId, paste.bufferId)
        assertNotEquals(null, cut.bufferId)
    }

    // ----- convergence (TP1) for non-intersecting concurrency -----

    private fun assertConverges(document: NablaBuilder, a: NablaBuilder, b: NablaBuilder) {
        val aPrime = transformer.transform(b, a, priority = false)
        val bPrime = transformer.transform(a, b, priority = true)

        assertEquals(composer.compose(a, bPrime), composer.compose(b, aPrime))

        val left = plainText(apply(apply(document, a), bPrime))
        val right = plainText(apply(apply(document, b), aPrime))
        assertEquals(left, right, "a∘transform(a,b) and b∘transform(b,a) must converge")
    }

    @Test
    fun `move converges with an insert between the source and the target`() {
        val moveAB = NablaBuilder().cut(2, bid(1)).retain(4).paste(2, bid(1))
        val insertMiddle = NablaBuilder().retain(4).insert(text("Z"))
        assertConverges(doc("ABCDEF"), moveAB, insertMiddle)
    }

    @Test
    fun `move converges with an insert outside the moved range`() {
        val moveDE = NablaBuilder().paste(2, bid(1)).retain(3).cut(2, bid(1))
        val insertEarly = NablaBuilder().retain(2).insert(text("Z"))
        assertConverges(doc("ABCDEF"), moveDE, insertEarly)
    }

    @Test
    fun `move converges with a delete outside the moved range`() {
        val moveBC = NablaBuilder().retain(1).cut(2, bid(1)).retain(3).paste(2, bid(1))
        val deleteTail = NablaBuilder().retain(5).delete(1)
        assertConverges(doc("ABCDEF"), moveBC, deleteTail)
    }

    @Test
    fun `two disjoint moves converge`() {
        val moveFront = NablaBuilder().cut(2, bid(1)).retain(2).paste(2, bid(1))
        val moveBack = NablaBuilder().retain(5).paste(2, bid(2)).retain(1).cut(2, bid(2))
        assertConverges(doc("ABCDEFGH"), moveFront, moveBack)
    }
}
