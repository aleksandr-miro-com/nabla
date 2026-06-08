package com.miro.nabla

data class Retain(
    override val length: Int,
    val attributes: AttributeMap = AttributeMap.empty(),
    val child: NablaBuilder? = null,
) : Operation {
    init {
        require(length > 0)
        require(child == null || length == 1) { "a child change applies to a single retained element" }
    }

    override fun subOperation(offset: Int, length: Int): Operation {
        OperationUtils.checkRange(offset, offset + length, this.length)
        if (offset == 0 && length == this.length) {
            return this
        }
        // A retain carrying a child has length 1 and is never split; a plain retain drops nothing.
        return Retain(length, attributes)
    }
}
