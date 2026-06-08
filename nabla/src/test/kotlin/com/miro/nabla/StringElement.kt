package com.miro.nabla

data class StringElement(val value: String) : TestElement {
    init {
        check(value.isNotEmpty())
    }

    override val length: Int get() = value.length

    override fun subElement(offset: Int, length: Int): Element {
        OperationUtils.checkRange(offset, offset + length, value.length)
        if (offset == 0 && length == value.length) {
            return this
        }
        return StringElement(value.substring(offset, offset + length))
    }

    override fun merge(other: Element): Element? =
        if (other is StringElement) StringElement(value + other.value) else null
}