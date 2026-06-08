package com.miro.nabla

import kotlin.test.Test
import kotlin.test.assertEquals

class DeleteTest {
    @Test
    fun `length is the number of deleted elements`() {
        assertEquals(5, Delete(5).length)
    }
}
