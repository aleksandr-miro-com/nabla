package com.miro.nabla

import kotlin.test.Test
import kotlin.test.assertEquals

class AttributeMapTest {
    private val attributes = AttributeMap.of("bold" to true, "color" to "red")

    @Test
    fun `compose returns right when left is null`() {
        assertEquals(attributes, AttributeMap.compose(AttributeMap.empty(), attributes))
    }

    @Test
    fun `compose returns left when right is null`() {
        assertEquals(attributes, AttributeMap.compose(attributes, AttributeMap.empty()))
    }

    @Test
    fun `compose returns null when both are null`() {
        assertEquals(AttributeMap.empty(), AttributeMap.compose(AttributeMap.empty(), AttributeMap.empty()))
    }

    @Test
    fun `compose adds missing keys`() {
        assertEquals(
            AttributeMap.of("bold" to true, "italic" to true, "color" to "red"),
            AttributeMap.compose(attributes, AttributeMap.of("italic" to true)),
        )
    }

    @Test
    fun `compose overwrites existing keys`() {
        assertEquals(
            AttributeMap.of("bold" to false, "color" to "blue"),
            AttributeMap.compose(attributes, AttributeMap.of("bold" to false, "color" to "blue")),
        )
    }

    @Test
    fun `compose removes keys set to null`() {
        assertEquals(
            AttributeMap.of("color" to "red"),
            AttributeMap.compose(attributes, AttributeMap.of("bold" to null)),
        )
    }

    @Test
    fun `compose returns null when everything is removed`() {
        assertEquals(
            AttributeMap.empty(),
            AttributeMap.compose(attributes, AttributeMap.of("bold" to null, "color" to null))
        )
    }

    @Test
    fun `compose ignores removal of missing keys`() {
        assertEquals(attributes, AttributeMap.compose(attributes, AttributeMap.of("italic" to null)))
    }

    // ----- diff() -----

    private val format = AttributeMap.of("bold" to true, "color" to "red")

    @Test
    fun `diff returns right when left is null`() {
        assertEquals(format, AttributeMap.diff(AttributeMap.empty(), format))
    }

    @Test
    fun `diff nulls every key when right is null`() {
        assertEquals(AttributeMap.of("bold" to null, "color" to null), AttributeMap.diff(format, AttributeMap.empty()))
    }

    @Test
    fun `diff returns null for identical maps`() {
        assertEquals(AttributeMap.empty(), AttributeMap.diff(format, format))
    }

    @Test
    fun `diff reports added keys`() {
        assertEquals(
            AttributeMap.of("italic" to true),
            AttributeMap.diff(format, AttributeMap.of("bold" to true, "italic" to true, "color" to "red")),
        )
    }

    @Test
    fun `diff reports removed keys as null`() {
        assertEquals(AttributeMap.of("color" to null), AttributeMap.diff(format, AttributeMap.of("bold" to true)))
    }

    @Test
    fun `diff reports overwritten keys`() {
        assertEquals(
            AttributeMap.of("color" to "blue"),
            AttributeMap.diff(format, AttributeMap.of("bold" to true, "color" to "blue")),
        )
    }

    // ----- invert() -----

    @Test
    fun `invert returns empty when attributes is null`() {
        assertEquals(AttributeMap.empty(), AttributeMap.invert(AttributeMap.empty(), AttributeMap.of("bold" to true)))
    }

    @Test
    fun `invert nulls keys when base is null`() {
        assertEquals(
            AttributeMap.of("bold" to null),
            AttributeMap.invert(AttributeMap.of("bold" to true), AttributeMap.empty())
        )
    }

    @Test
    fun `invert returns empty when both are null`() {
        assertEquals(AttributeMap.empty(), AttributeMap.invert(AttributeMap.empty(), AttributeMap.empty()))
    }

    @Test
    fun `invert nulls keys absent from base`() {
        assertEquals(
            AttributeMap.of("bold" to null),
            AttributeMap.invert(AttributeMap.of("bold" to true), AttributeMap.of("italic" to true)),
        )
    }

    @Test
    fun `invert restores base value for a nulled key`() {
        assertEquals(
            AttributeMap.of("bold" to true),
            AttributeMap.invert(AttributeMap.of("bold" to null), AttributeMap.of("bold" to true)),
        )
    }

    @Test
    fun `invert restores a replaced base value`() {
        val base = AttributeMap.of("color" to "blue")
        assertEquals(base, AttributeMap.invert(AttributeMap.of("color" to "red"), base))
    }

    @Test
    fun `invert ignores unchanged keys`() {
        assertEquals(
            AttributeMap.empty(),
            AttributeMap.invert(AttributeMap.of("color" to "red"), AttributeMap.of("color" to "red")),
        )
    }

    @Test
    fun `invert handles a combination of changes`() {
        val attributes = AttributeMap.of("bold" to true, "italic" to null, "color" to "red", "size" to "12px")
        val base = AttributeMap.of("font" to "serif", "italic" to true, "color" to "blue", "size" to "12px")
        assertEquals(
            AttributeMap.of("bold" to null, "italic" to true, "color" to "blue"),
            AttributeMap.invert(attributes, base),
        )
    }

    // ----- transform() -----

    private val left = AttributeMap.of("bold" to true, "color" to "red", "font" to null)
    private val right = AttributeMap.of("color" to "blue", "font" to "serif", "italic" to true)

    @Test
    fun `transform returns right when left is null`() {
        assertEquals(left, AttributeMap.transform(AttributeMap.empty(), left, false))
    }

    @Test
    fun `transform returns null when right is null`() {
        assertEquals(AttributeMap.empty(), AttributeMap.transform(left, AttributeMap.empty(), false))
    }

    @Test
    fun `transform returns null when both are null`() {
        assertEquals(AttributeMap.empty(), AttributeMap.transform(AttributeMap.empty(), AttributeMap.empty(), false))
    }

    @Test
    fun `transform keeps only non-conflicting keys with priority`() {
        assertEquals(AttributeMap.of("italic" to true), AttributeMap.transform(left, right, true))
    }

    @Test
    fun `transform returns right without priority`() {
        assertEquals(right, AttributeMap.transform(left, right, false))
    }
}
