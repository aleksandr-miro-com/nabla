package com.miro.nabla

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Compose over a tree document: a [NablaBuilder] of node inserts where each [NodeTestElement] holds
 * a nested [NablaBuilder] of children. A change recurses into a node via a [Retain] carrying a
 * child sub-change. Cut/paste buffers are global, so a node can move between different subtrees.
 */
class NablaTreeComposeTest {

    private val composer = NablaComposer()

    private fun bid(value: Long) = BufferId(value)

    private fun compose(document: NablaBuilder, change: NablaBuilder) =
        renderTree(composer.compose(document, change))

    // root "r" with two leaf children: r[a,b]
    private fun doc() = NablaBuilder().insert(node("r", NablaBuilder().insert(node("a")).insert(node("b"))))

    @Test
    fun `recolor a nested leaf`() {
        val change = NablaBuilder().retain(1, child = NablaBuilder().retain(1, AttributeMap.of("color" to "red")))
        assertEquals("r[a:red,b]", compose(doc(), change))
    }

    @Test
    fun `insert a nested child`() {
        val change = NablaBuilder().retain(1, child = NablaBuilder().retain(2).insert(node("c")))
        assertEquals("r[a,b,c]", compose(doc(), change))
    }

    @Test
    fun `delete a nested child`() {
        val change = NablaBuilder().retain(1, child = NablaBuilder().retain(1).delete(1))
        assertEquals("r[a]", compose(doc(), change))
    }

    @Test
    fun `move a node within the same subtree`() {
        // r[a,b,c] -> move "a" to the end of its parent's children.
        val document = NablaBuilder().insert(
            node("r", NablaBuilder().insert(node("a")).insert(node("b")).insert(node("c"))),
        )
        val change = NablaBuilder().retain(1, child = NablaBuilder().cut(1, bid(1)).retain(2).paste(1, bid(1)))
        assertEquals("r[b,c,a]", compose(document, change))
    }

    @Test
    fun `move a node into a different subtree`() {
        // r[ A[x], B[y] ] -> move "x" out of A and into B after "y" (global buffer links them).
        val document = NablaBuilder().insert(
            node(
                "r",
                NablaBuilder()
                    .insert(node("A", NablaBuilder().insert(node("x"))))
                    .insert(node("B", NablaBuilder().insert(node("y")))),
            ),
        )
        val change = NablaBuilder().retain(
            1,
            child = NablaBuilder()
                .retain(1, child = NablaBuilder().cut(1, bid(1))) // A: cut "x"
                .retain(1, child = NablaBuilder().retain(1).paste(1, bid(1))), // B: paste after "y"
        )
        assertEquals("r[A,B[y,x]]", compose(document, change))
    }
}
