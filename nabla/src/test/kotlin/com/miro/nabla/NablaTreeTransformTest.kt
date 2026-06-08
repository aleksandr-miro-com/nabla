package com.miro.nabla

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Transform over a tree document: two concurrent changes are rebased against each other, recursing
 * into nested children via [Retain] child sub-changes. Covers convergence (TP1) for concurrency
 * within the same subtree (cross-subtree concurrent moves still pending the global redirect maps).
 */
class NablaTreeTransformTest {

    private val composer = NablaComposer()
    private val transformer = NablaTransformer()

    private fun bid(value: Long) = BufferId(value)

    /** Asserts the two apply-orders converge, and returns the converged tree. */
    private fun assertConverges(document: NablaBuilder, a: NablaBuilder, b: NablaBuilder): String {
        val aPrime = transformer.transform(b, a, priority = false)
        val bPrime = transformer.transform(a, b, priority = true)
        val left = composer.compose(composer.compose(document, a), bPrime)
        val right = composer.compose(composer.compose(document, b), aPrime)
        assertEquals(renderTree(left), renderTree(right), "tree must converge")
        return renderTree(left)
    }

    private fun doc() = NablaBuilder().insert(node("r", NablaBuilder().insert(node("a")).insert(node("b"))))

    @Test
    fun `concurrent recolor and insert in the same children`() {
        val recolorA = NablaBuilder().retain(1, child = NablaBuilder().retain(1, AttributeMap.of("color" to "red")))
        val insertC = NablaBuilder().retain(1, child = NablaBuilder().retain(2).insert(node("c")))
        assertEquals("r[a:red,b,c]", assertConverges(doc(), recolorA, insertC))
    }

    @Test
    fun `concurrent recolor of two different children`() {
        val recolorA = NablaBuilder().retain(1, child = NablaBuilder().retain(1, AttributeMap.of("color" to "red")))
        val recolorB = NablaBuilder().retain(1, child = NablaBuilder().retain(1).retain(1, AttributeMap.of("color" to "blue")))
        assertEquals("r[a:red,b:blue]", assertConverges(doc(), recolorA, recolorB))
    }

    @Test
    fun `concurrent recolor of the same child resolves by priority`() {
        val toRed = NablaBuilder().retain(1, child = NablaBuilder().retain(1, AttributeMap.of("color" to "red")))
        val toBlue = NablaBuilder().retain(1, child = NablaBuilder().retain(1, AttributeMap.of("color" to "blue")))
        // transform(a, b, priority=true) keeps a's color; both orders agree on the winner.
        assertEquals("r[a:red,b]", assertConverges(doc(), toRed, toBlue))
    }

    @Test
    fun `concurrent edits in different subtrees`() {
        val document = NablaBuilder().insert(
            node(
                "r",
                NablaBuilder()
                    .insert(node("A", NablaBuilder().insert(node("x"))))
                    .insert(node("B", NablaBuilder().insert(node("y")))),
            ),
        )
        val editA = NablaBuilder().retain(
            1,
            child = NablaBuilder().retain(1, child = NablaBuilder().retain(1).insert(node("x2"))),
        )
        val editB = NablaBuilder().retain(
            1,
            child = NablaBuilder().retain(1).retain(1, child = NablaBuilder().retain(1).insert(node("y2"))),
        )
        assertEquals("r[A[x,x2],B[y,y2]]", assertConverges(document, editA, editB))
    }

    @Test
    fun `concurrent move and recolor within the same children`() {
        val document = NablaBuilder().insert(
            node("r", NablaBuilder().insert(node("a")).insert(node("b")).insert(node("c"))),
        )
        // a: move "a" to the end. b: recolor "c".
        val move = NablaBuilder().retain(1, child = NablaBuilder().cut(1, bid(1)).retain(2).paste(1, bid(1)))
        val recolorC = NablaBuilder().retain(1, child = NablaBuilder().retain(2).retain(1, AttributeMap.of("color" to "green")))
        assertEquals("r[b,c:green,a]", assertConverges(document, move, recolorC))
    }
}
