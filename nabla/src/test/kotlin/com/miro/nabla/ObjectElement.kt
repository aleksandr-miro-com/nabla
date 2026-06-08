package com.miro.nabla

data class ObjectElement(val value: Any? = null) : TestElement {
    override val length: Int get() = 1

    override fun subElement(offset: Int, length: Int): Element {
        require(offset == 0 && length == 1)
        return this
    }
}