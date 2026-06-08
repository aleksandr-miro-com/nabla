package com.miro.nabla

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NablaBuilderTest {

    private val ops = listOf(
        Insert(StringElement("abc")),
        Retain(1, AttributeMap.of("color" to "red")),
        Delete(4),
        Insert(StringElement("def"), AttributeMap.of("bold" to true)),
        Retain(6),
    )

    // ----- constructor -----

    @Test
    fun `constructor creates an empty delta`() {
        assertTrue(NablaBuilder().ops.isEmpty())
    }

    @Test
    fun `constructor accepts a list of ops`() {
        assertEquals(ops, NablaBuilder(ops).ops)
    }

    @Test
    fun `constructor copies another delta`() {
        val original = NablaBuilder(ops)
        val delta = NablaBuilder(original)
        assertEquals(original.ops, delta.ops)
        assertEquals(ops, delta.ops)
    }

    // ----- insert() -----

    @Test
    fun `insert appends a text op`() {
        val delta = NablaBuilder().insert(StringElement("test"))
        assertEquals(1, delta.ops.size)
        assertEquals(Insert(StringElement("test")), delta.ops[0])
    }

    @Test
    fun `insert with empty attributes appends a plain text op`() {
        val delta = NablaBuilder().insert(StringElement("test"), AttributeMap.empty())
        assertEquals(1, delta.ops.size)
        assertEquals(Insert(StringElement("test")), delta.ops[0])
    }

    @Test
    fun `insert appends an embed op`() {
        val delta = NablaBuilder().insert(ObjectElement(1))
        assertEquals(1, delta.ops.size)
        assertEquals(Insert(ObjectElement(1)), delta.ops[0])
    }

    @Test
    fun `insert appends an embed op with attributes`() {
        val attributes = AttributeMap.of("url" to "http://quilljs.com", "alt" to "Quill")
        val delta = NablaBuilder().insert(ObjectElement(1), attributes)
        assertEquals(1, delta.ops.size)
        assertEquals(Insert(ObjectElement(1), attributes), delta.ops[0])
    }

    @Test
    fun `insert appends a non-integer embed with attributes`() {
        val embed = ObjectElement("http://quilljs.com")
        val attributes = AttributeMap.of("alt" to "Quill")
        val delta = NablaBuilder().insert(embed, attributes)
        assertEquals(1, delta.ops.size)
        assertEquals(Insert(embed, attributes), delta.ops[0])
    }

    @Test
    fun `insert appends a text op with attributes`() {
        val delta = NablaBuilder().insert(StringElement("test"), AttributeMap.of("bold" to true))
        assertEquals(1, delta.ops.size)
        assertEquals(Insert(StringElement("test"), AttributeMap.of("bold" to true)), delta.ops[0])
    }

    @Test
    fun `insert is reordered before a preceding delete`() {
        val delta = NablaBuilder().delete(1).insert(StringElement("a"))
        val expected = NablaBuilder().insert(StringElement("a")).delete(1)
        assertEquals(expected.ops, delta.ops)
    }

    @Test
    fun `insert merges with text before a delete`() {
        val delta = NablaBuilder().insert(StringElement("a")).delete(1).insert(StringElement("b"))
        val expected = NablaBuilder().insert(StringElement("ab")).delete(1)
        assertEquals(expected.ops, delta.ops)
    }

    @Test
    fun `insert does not merge with an embed before a delete`() {
        val delta = NablaBuilder().insert(ObjectElement(1)).delete(1).insert(StringElement("a"))
        val expected = NablaBuilder().insert(ObjectElement(1)).insert(StringElement("a")).delete(1)
        assertEquals(expected.ops, delta.ops)
    }

    @Test
    fun `insert with empty attributes equals insert without`() {
        val delta = NablaBuilder().insert(StringElement("a"), AttributeMap.empty())
        val expected = NablaBuilder().insert(StringElement("a"))
        assertEquals(expected.ops, delta.ops)
    }

    // ----- delete() -----

    @Test
    fun `delete of zero is dropped`() {
        assertTrue(NablaBuilder().delete(0).ops.isEmpty())
    }

    @Test
    fun `delete of a positive length appends a delete op`() {
        val delta = NablaBuilder().delete(1)
        assertEquals(1, delta.ops.size)
        assertEquals(Delete(1), delta.ops[0])
    }

    // ----- retain() -----

    @Test
    fun `retain of zero is dropped`() {
        assertTrue(NablaBuilder().retain(0).ops.isEmpty())
    }

    @Test
    fun `retain appends a retain op`() {
        val delta = NablaBuilder().retain(2)
        assertEquals(1, delta.ops.size)
        assertEquals(Retain(2), delta.ops[0])
    }

    @Test
    fun `retain with empty attributes appends a plain retain op`() {
        val delta = NablaBuilder().retain(2, AttributeMap.empty())
        assertEquals(1, delta.ops.size)
        assertEquals(Retain(2), delta.ops[0])
    }

    @Test
    fun `retain appends a retain op with attributes`() {
        val delta = NablaBuilder().retain(1, AttributeMap.of("bold" to true))
        assertEquals(1, delta.ops.size)
        assertEquals(Retain(1, AttributeMap.of("bold" to true)), delta.ops[0])
    }

    @Test
    fun `retain with empty attributes equals retain without`() {
        val delta = NablaBuilder().retain(2, AttributeMap.empty()).delete(1)
        val expected = NablaBuilder().retain(2).delete(1)
        assertEquals(expected.ops, delta.ops)
    }

    // ----- push() -----

    @Test
    fun `push appends into an empty delta`() {
        val delta = NablaBuilder()
        delta.push(Insert(StringElement("test")))
        assertEquals(1, delta.ops.size)
    }

    @Test
    fun `push merges consecutive deletes`() {
        val delta = NablaBuilder().delete(2)
        delta.push(Delete(3))
        assertEquals(1, delta.ops.size)
        assertEquals(Delete(5), delta.ops[0])
    }

    @Test
    fun `push merges consecutive text`() {
        val delta = NablaBuilder().insert(StringElement("a"))
        delta.push(Insert(StringElement("b")))
        assertEquals(1, delta.ops.size)
        assertEquals(Insert(StringElement("ab")), delta.ops[0])
    }

    @Test
    fun `push merges consecutive text with matching attributes`() {
        val delta = NablaBuilder().insert(StringElement("a"), AttributeMap.of("bold" to true))
        delta.push(Insert(StringElement("b"), AttributeMap.of("bold" to true)))
        assertEquals(1, delta.ops.size)
        assertEquals(Insert(StringElement("ab"), AttributeMap.of("bold" to true)), delta.ops[0])
    }

    @Test
    fun `push merges consecutive retains with matching attributes`() {
        val delta = NablaBuilder().retain(1, AttributeMap.of("bold" to true))
        delta.push(Retain(3, AttributeMap.of("bold" to true)))
        assertEquals(1, delta.ops.size)
        assertEquals(Retain(4, AttributeMap.of("bold" to true)), delta.ops[0])
    }

    @Test
    fun `push does not merge text with mismatched attributes`() {
        val delta = NablaBuilder().insert(StringElement("a"), AttributeMap.of("bold" to true))
        delta.push(Insert(StringElement("b")))
        assertEquals(2, delta.ops.size)
    }

    @Test
    fun `push does not merge retains with mismatched attributes`() {
        val delta = NablaBuilder().retain(1, AttributeMap.of("bold" to true))
        delta.push(Retain(3))
        assertEquals(2, delta.ops.size)
    }

    @Test
    fun `push does not merge consecutive embeds`() {
        val delta = NablaBuilder().insert(ObjectElement(1), AttributeMap.of("alt" to "Description"))
        delta.push(Insert(ObjectElement("http://quilljs.com"), AttributeMap.of("alt" to "Description")))
        assertEquals(2, delta.ops.size)
    }
}
