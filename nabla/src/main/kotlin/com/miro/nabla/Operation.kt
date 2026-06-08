package com.miro.nabla

sealed interface Operation {
    val length: Int

    fun subOperation(offset: Int, length: Int): Operation
}