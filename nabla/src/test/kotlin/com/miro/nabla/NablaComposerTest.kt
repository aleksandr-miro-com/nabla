package com.miro.nabla

import kotlin.test.Test
import kotlin.test.assertEquals

// Ports quill-delta's compose() suite, minus the `retain embed` and `custom embed handler` cases:
// this architecture has no object-retain (Retain.length is an Int) and no embed-handler registry,
// so those are not representable.
class NablaComposerTest {

    private val composer = NablaComposer()

    private fun text(value: String) = StringElement(value)
    private fun embed(value: Any?) = ObjectElement(value)
    private fun attrs(vararg pairs: Pair<String, Any?>) = AttributeMap.of(*pairs)

    private fun compose(a: NablaBuilder, b: NablaBuilder) = composer.compose(a, b).ops

    @Test
    fun `insert over insert`() {
        val a = NablaBuilder().insert(text("A"))
        val b = NablaBuilder().insert(text("B"))
        val expected = NablaBuilder().insert(text("B")).insert(text("A"))
        assertEquals(expected.ops, compose(a, b))
    }

    @Test
    fun `retain formats an insert`() {
        val a = NablaBuilder().insert(text("A"))
        val b = NablaBuilder().retain(1, attrs("bold" to true, "color" to "red", "font" to null))
        val expected = NablaBuilder().insert(text("A"), attrs("bold" to true, "color" to "red"))
        assertEquals(expected.ops, compose(a, b))
    }

    @Test
    fun `delete cancels an insert`() {
        val a = NablaBuilder().insert(text("A"))
        val b = NablaBuilder().delete(1)
        assertEquals(NablaBuilder().ops, compose(a, b))
    }

    @Test
    fun `insert before a delete`() {
        val a = NablaBuilder().delete(1)
        val b = NablaBuilder().insert(text("B"))
        val expected = NablaBuilder().insert(text("B")).delete(1)
        assertEquals(expected.ops, compose(a, b))
    }

    @Test
    fun `retain after a delete`() {
        val a = NablaBuilder().delete(1)
        val b = NablaBuilder().retain(1, attrs("bold" to true, "color" to "red"))
        val expected = NablaBuilder().delete(1).retain(1, attrs("bold" to true, "color" to "red"))
        assertEquals(expected.ops, compose(a, b))
    }

    @Test
    fun `delete merges with delete`() {
        val a = NablaBuilder().delete(1)
        val b = NablaBuilder().delete(1)
        val expected = NablaBuilder().delete(2)
        assertEquals(expected.ops, compose(a, b))
    }

    @Test
    fun `insert before a retain`() {
        val a = NablaBuilder().retain(1, attrs("color" to "blue"))
        val b = NablaBuilder().insert(text("B"))
        val expected = NablaBuilder().insert(text("B")).retain(1, attrs("color" to "blue"))
        assertEquals(expected.ops, compose(a, b))
    }

    @Test
    fun `retain composes attributes`() {
        val a = NablaBuilder().retain(1, attrs("color" to "blue"))
        val b = NablaBuilder().retain(1, attrs("bold" to true, "color" to "red", "font" to null))
        val expected = NablaBuilder().retain(1, attrs("bold" to true, "color" to "red", "font" to null))
        assertEquals(expected.ops, compose(a, b))
    }

    @Test
    fun `delete over a retain`() {
        val a = NablaBuilder().retain(1, attrs("color" to "blue"))
        val b = NablaBuilder().delete(1)
        val expected = NablaBuilder().delete(1)
        assertEquals(expected.ops, compose(a, b))
    }

    @Test
    fun `insert in the middle of text`() {
        val a = NablaBuilder().insert(text("Hello"))
        val b = NablaBuilder().retain(3).insert(text("X"))
        val expected = NablaBuilder().insert(text("HelXlo"))
        assertEquals(expected.ops, compose(a, b))
    }

    @Test
    fun `insert and delete ordering`() {
        val a = NablaBuilder().insert(text("Hello"))
        val b = NablaBuilder().insert(text("Hello"))
        val insertFirst = NablaBuilder().retain(3).insert(text("X")).delete(1)
        val deleteFirst = NablaBuilder().retain(3).delete(1).insert(text("X"))
        val expected = NablaBuilder().insert(text("HelXo"))
        assertEquals(expected.ops, compose(a, insertFirst))
        assertEquals(expected.ops, compose(b, deleteFirst))
    }

    @Test
    fun `retain formats an embed insert`() {
        val a = NablaBuilder().insert(embed(1), attrs("src" to "http://quilljs.com/image.png"))
        val b = NablaBuilder().retain(1, attrs("alt" to "logo"))
        val expected = NablaBuilder()
            .insert(embed(1), attrs("src" to "http://quilljs.com/image.png", "alt" to "logo"))
        assertEquals(expected.ops, compose(a, b))
    }

    @Test
    fun `delete entire text`() {
        val a = NablaBuilder().retain(4).insert(text("Hello"))
        val b = NablaBuilder().delete(9)
        val expected = NablaBuilder().delete(4)
        assertEquals(expected.ops, compose(a, b))
    }

    @Test
    fun `retain beyond the text length`() {
        val a = NablaBuilder().insert(text("Hello"))
        val b = NablaBuilder().retain(10)
        val expected = NablaBuilder().insert(text("Hello"))
        assertEquals(expected.ops, compose(a, b))
    }

    @Test
    fun `attribute-less retain over an embed`() {
        val a = NablaBuilder().insert(embed(1))
        val b = NablaBuilder().retain(1)
        val expected = NablaBuilder().insert(embed(1))
        assertEquals(expected.ops, compose(a, b))
    }

    @Test
    fun `remove all attributes`() {
        val a = NablaBuilder().insert(text("A"), attrs("bold" to true))
        val b = NablaBuilder().retain(1, attrs("bold" to null))
        val expected = NablaBuilder().insert(text("A"))
        assertEquals(expected.ops, compose(a, b))
    }

    @Test
    fun `remove all embed attributes`() {
        val a = NablaBuilder().insert(embed(2), attrs("bold" to true))
        val b = NablaBuilder().retain(1, attrs("bold" to null))
        val expected = NablaBuilder().insert(embed(2))
        assertEquals(expected.ops, compose(a, b))
    }

    @Test
    fun `compose does not mutate its inputs`() {
        val attr = attrs("bold" to true)
        val a1 = NablaBuilder().insert(text("Test"), attr)
        val a2 = NablaBuilder().insert(text("Test"), attr)
        val b1 = NablaBuilder().retain(1, attrs("color" to "red")).delete(2)
        val b2 = NablaBuilder().retain(1, attrs("color" to "red")).delete(2)
        val expected = NablaBuilder()
            .insert(text("T"), attrs("color" to "red", "bold" to true))
            .insert(text("t"), attr)
        assertEquals(expected.ops, compose(a1, b1))
        assertEquals(a2.ops, a1.ops)
        assertEquals(b2.ops, b1.ops)
    }

    @Test
    fun `retain start optimization`() {
        val a = NablaBuilder()
            .insert(text("A"), attrs("bold" to true))
            .insert(text("B"))
            .insert(text("C"), attrs("bold" to true))
            .delete(1)
        val b = NablaBuilder().retain(3).insert(text("D"))
        val expected = NablaBuilder()
            .insert(text("A"), attrs("bold" to true))
            .insert(text("B"))
            .insert(text("C"), attrs("bold" to true))
            .insert(text("D"))
            .delete(1)
        assertEquals(expected.ops, compose(a, b))
    }

    @Test
    fun `retain start optimization with split`() {
        val a = NablaBuilder()
            .insert(text("A"), attrs("bold" to true))
            .insert(text("B"))
            .insert(text("C"), attrs("bold" to true))
            .retain(5)
            .delete(1)
        val b = NablaBuilder().retain(4).insert(text("D"))
        val expected = NablaBuilder()
            .insert(text("A"), attrs("bold" to true))
            .insert(text("B"))
            .insert(text("C"), attrs("bold" to true))
            .retain(1)
            .insert(text("D"))
            .retain(4)
            .delete(1)
        assertEquals(expected.ops, compose(a, b))
    }

    @Test
    fun `retain end optimization`() {
        val a = NablaBuilder()
            .insert(text("A"), attrs("bold" to true))
            .insert(text("B"))
            .insert(text("C"), attrs("bold" to true))
        val b = NablaBuilder().delete(1)
        val expected = NablaBuilder()
            .insert(text("B"))
            .insert(text("C"), attrs("bold" to true))
        assertEquals(expected.ops, compose(a, b))
    }

    @Test
    fun `retain end optimization with join`() {
        val a = NablaBuilder()
            .insert(text("A"), attrs("bold" to true))
            .insert(text("B"))
            .insert(text("C"), attrs("bold" to true))
            .insert(text("D"))
            .insert(text("E"), attrs("bold" to true))
            .insert(text("F"))
        val b = NablaBuilder().retain(1).delete(1)
        val expected = NablaBuilder()
            .insert(text("AC"), attrs("bold" to true))
            .insert(text("D"))
            .insert(text("E"), attrs("bold" to true))
            .insert(text("F"))
        assertEquals(expected.ops, compose(a, b))
    }
}
