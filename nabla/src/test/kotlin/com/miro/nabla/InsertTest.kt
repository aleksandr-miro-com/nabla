package com.miro.nabla

import kotlin.test.Test
import kotlin.test.assertEquals

class InsertTest {
    @Test
    fun `length is always one`() {
        assertEquals(1, Insert(ObjectElement("test")).length)
    }

    @Test
    fun `length is equal to the string length`() {
        assertEquals(4, Insert(StringElement("test")).length)
    }
}
