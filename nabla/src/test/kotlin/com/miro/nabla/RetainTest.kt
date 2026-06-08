package com.miro.nabla

import kotlin.test.Test
import kotlin.test.assertEquals

class RetainTest {
    @Test
    fun `length is the number of retained elements`() {
        assertEquals(2, Retain(2).length)
    }
}
