package com.miro.nabla

data class Retain(
    override val length: Int,
    val attributes: AttributeMap = AttributeMap.empty()
) : Operation {
    init {
        require(length > 0)
    }

    override fun subOperation(offset: Int, length: Int): Operation {
        OperationUtils.checkRange(offset, offset + length, this.length)
        if (offset == 0 && length == this.length) {
            return this
        }
        return Retain(length, attributes)
    }
}