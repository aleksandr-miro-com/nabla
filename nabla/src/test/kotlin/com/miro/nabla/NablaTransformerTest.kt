package com.miro.nabla

import kotlin.test.Test
import kotlin.test.assertEquals

// Ports quill-delta's transform()/transformPosition() suites, minus the `custom embed handler`
// block: this architecture has no object-retain or embed-handler registry.
class NablaTransformerTest {

    private val transformer = NablaTransformer()

    private fun text(value: String) = StringElement(value)
    private fun attrs(vararg pairs: Pair<String, Any?>) = AttributeMap.of(*pairs)

    private fun transform(a: NablaBuilder, b: NablaBuilder, priority: Boolean) =
        transformer.transform(a, b, priority).ops

    // ----- transform() -----

    @Test
    fun `insert against insert`() {
        val a = NablaBuilder().insert(text("A"))
        val b = NablaBuilder().insert(text("B"))
        assertEquals(NablaBuilder().retain(1).insert(text("B")).ops, transform(a, b, true))
        assertEquals(NablaBuilder().insert(text("B")).ops, transform(a, b, false))
    }

    @Test
    fun `insert against retain`() {
        val a = NablaBuilder().insert(text("A"))
        val b = NablaBuilder().retain(1, attrs("bold" to true, "color" to "red"))
        val expected = NablaBuilder().retain(1).retain(1, attrs("bold" to true, "color" to "red"))
        assertEquals(expected.ops, transform(a, b, true))
    }

    @Test
    fun `insert against delete`() {
        val a = NablaBuilder().insert(text("A"))
        val b = NablaBuilder().delete(1)
        val expected = NablaBuilder().retain(1).delete(1)
        assertEquals(expected.ops, transform(a, b, true))
    }

    @Test
    fun `delete against insert`() {
        val a = NablaBuilder().delete(1)
        val b = NablaBuilder().insert(text("B"))
        val expected = NablaBuilder().insert(text("B"))
        assertEquals(expected.ops, transform(a, b, true))
    }

    @Test
    fun `delete against retain`() {
        val a = NablaBuilder().delete(1)
        val b = NablaBuilder().retain(1, attrs("bold" to true, "color" to "red"))
        assertEquals(NablaBuilder().ops, transform(a, b, true))
    }

    @Test
    fun `delete against delete`() {
        val a = NablaBuilder().delete(1)
        val b = NablaBuilder().delete(1)
        assertEquals(NablaBuilder().ops, transform(a, b, true))
    }

    @Test
    fun `retain against insert`() {
        val a = NablaBuilder().retain(1, attrs("color" to "blue"))
        val b = NablaBuilder().insert(text("B"))
        val expected = NablaBuilder().insert(text("B"))
        assertEquals(expected.ops, transform(a, b, true))
    }

    @Test
    fun `retain against retain with priority`() {
        val a = NablaBuilder().retain(1, attrs("color" to "blue"))
        val b = NablaBuilder().retain(1, attrs("bold" to true, "color" to "red"))
        assertEquals(NablaBuilder().retain(1, attrs("bold" to true)).ops, transform(a, b, true))
        assertEquals(NablaBuilder().ops, transform(b, a, true))
    }

    @Test
    fun `retain against retain without priority`() {
        val a = NablaBuilder().retain(1, attrs("color" to "blue"))
        val b = NablaBuilder().retain(1, attrs("bold" to true, "color" to "red"))
        assertEquals(
            NablaBuilder().retain(1, attrs("bold" to true, "color" to "red")).ops,
            transform(a, b, false),
        )
        assertEquals(NablaBuilder().retain(1, attrs("color" to "blue")).ops, transform(b, a, false))
    }

    @Test
    fun `retain against delete`() {
        val a = NablaBuilder().retain(1, attrs("color" to "blue"))
        val b = NablaBuilder().delete(1)
        val expected = NablaBuilder().delete(1)
        assertEquals(expected.ops, transform(a, b, true))
    }

    @Test
    fun `alternating edits`() {
        val a = NablaBuilder().retain(2).insert(text("si")).delete(5)
        val b = NablaBuilder().retain(1).insert(text("e")).delete(5).retain(1).insert(text("ow"))
        val expected1 = NablaBuilder()
            .retain(1).insert(text("e")).delete(1).retain(2).insert(text("ow"))
        val expected2 = NablaBuilder().retain(2).insert(text("si")).delete(1)
        assertEquals(expected1.ops, transform(a, b, false))
        assertEquals(expected2.ops, transform(b, a, false))
    }

    @Test
    fun `conflicting appends`() {
        val a = NablaBuilder().retain(3).insert(text("aa"))
        val b = NablaBuilder().retain(3).insert(text("bb"))
        val expected1 = NablaBuilder().retain(5).insert(text("bb"))
        val expected2 = NablaBuilder().retain(3).insert(text("aa"))
        assertEquals(expected1.ops, transform(a, b, true))
        assertEquals(expected2.ops, transform(b, a, false))
    }

    @Test
    fun `prepend and append`() {
        val a = NablaBuilder().insert(text("aa"))
        val b = NablaBuilder().retain(3).insert(text("bb"))
        val expected1 = NablaBuilder().retain(5).insert(text("bb"))
        val expected2 = NablaBuilder().insert(text("aa"))
        assertEquals(expected1.ops, transform(a, b, false))
        assertEquals(expected2.ops, transform(b, a, false))
    }

    @Test
    fun `trailing deletes with differing lengths`() {
        val a = NablaBuilder().retain(2).delete(1)
        val b = NablaBuilder().delete(3)
        val expected1 = NablaBuilder().delete(2)
        assertEquals(expected1.ops, transform(a, b, false))
        assertEquals(NablaBuilder().ops, transform(b, a, false))
    }

    @Test
    fun `transform does not mutate its inputs`() {
        val a1 = NablaBuilder().insert(text("A"))
        val a2 = NablaBuilder().insert(text("A"))
        val b1 = NablaBuilder().insert(text("B"))
        val b2 = NablaBuilder().insert(text("B"))
        val expected = NablaBuilder().retain(1).insert(text("B"))
        assertEquals(expected.ops, transform(a1, b1, true))
        assertEquals(a2.ops, a1.ops)
        assertEquals(b2.ops, b1.ops)
    }

    // ----- transformPosition() -----

    @Test
    fun `insert before position`() {
        val delta = NablaBuilder().insert(text("A"))
        assertEquals(3, transformer.transformPosition(delta, 2))
    }

    @Test
    fun `insert after position`() {
        val delta = NablaBuilder().retain(2).insert(text("A"))
        assertEquals(1, transformer.transformPosition(delta, 1))
    }

    @Test
    fun `insert at position`() {
        val delta = NablaBuilder().retain(2).insert(text("A"))
        assertEquals(2, transformer.transformPosition(delta, 2, true))
        assertEquals(3, transformer.transformPosition(delta, 2, false))
    }

    @Test
    fun `delete before position`() {
        val delta = NablaBuilder().delete(2)
        assertEquals(2, transformer.transformPosition(delta, 4))
    }

    @Test
    fun `delete after position`() {
        val delta = NablaBuilder().retain(4).delete(2)
        assertEquals(2, transformer.transformPosition(delta, 2))
    }

    @Test
    fun `delete across position`() {
        val delta = NablaBuilder().retain(1).delete(4)
        assertEquals(1, transformer.transformPosition(delta, 2))
    }

    @Test
    fun `insert and delete before position`() {
        val delta = NablaBuilder().retain(2).insert(text("A")).delete(2)
        assertEquals(3, transformer.transformPosition(delta, 4))
    }

    @Test
    fun `insert before and delete across position`() {
        val delta = NablaBuilder().retain(2).insert(text("A")).delete(4)
        assertEquals(3, transformer.transformPosition(delta, 4))
    }

    @Test
    fun `delete before and delete across position`() {
        val delta = NablaBuilder().delete(1).retain(1).delete(4)
        assertEquals(1, transformer.transformPosition(delta, 4))
    }
}
