package com.miro.nabla

data class Insert(
    val element: Element,
    val attributes: AttributeMap = AttributeMap.empty(),
) : Operation {
    override val length get() = element.length

    override fun subOperation(offset: Int, length: Int): Operation {
        OperationUtils.checkRange(offset, offset + length, element.length)
        if (offset == 0 && length == element.length) {
            return this
        }
        return Insert(element.subElement(offset, length), attributes)
    }

    /**
     * Merges with a following [next] insert when their attributes match and their elements can be
     * combined (e.g. consecutive text). Returns `null` when they cannot be merged.
     */
    fun merge(next: Insert): Insert? {
        if (attributes != next.attributes) {
            return null
        }
        val merged = element.merge(next.element) ?: return null
        return Insert(merged, attributes)
    }
}
